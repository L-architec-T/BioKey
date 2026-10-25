#include "BlePresenceWatcher.h"

#include <array>
#include <atomic>
#include <chrono>
#include <cstring>
#include <ctime>
#include <thread>
#include <unordered_map>

#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <spdlog/spdlog.h>
#include <systemd/sd-bus.h>

namespace {

constexpr uint16_t COMPANY_ID = 0xFFFF;
constexpr size_t PAYLOAD_SIZE = 15;
constexpr uint8_t PAYLOAD_VERSION = 1;
constexpr uint8_t FLAG_DISARM = 0x01;
constexpr int64_t SLOT_SECONDS = 10;
constexpr const char *ADAPTER_PATH = "/org/bluez/hci0";

std::atomic<bool> g_Scanning{false};

struct KnownDevice {
  PairedDevice device;
  std::array<uint8_t, 4> tag{};
};

struct WatchContext {
  const std::function<void(const BlePresenceWatcher::Sighting &)> *onSighting{};
  std::vector<KnownDevice> devices{};
  std::chrono::steady_clock::time_point devicesLoadedAt{};
  // BlueZ object path -> last verified beacon on it. RSSI-only updates (no new manufacturer
  // data, which only changes once per slot) refresh presence for a path verified recently.
  struct PathState {
    BlePresenceWatcher::Sighting sighting;
    std::chrono::steady_clock::time_point verifiedAt;
  };
  std::unordered_map<std::string, PathState> paths{};
};

std::array<uint8_t, 4> DeviceTag(const std::string &deviceId) {
  std::array<uint8_t, EVP_MAX_MD_SIZE> digest{};
  unsigned int len{};
  EVP_Digest(deviceId.data(), deviceId.size(), digest.data(), &len, EVP_sha256(), nullptr);
  return {digest[0], digest[1], digest[2], digest[3]};
}

bool TokenMatches(const std::string &encKey, int64_t slot, uint8_t flags, uint8_t timeoutByte, const uint8_t *token) {
  std::array<uint8_t, 14> msg{'B', 'K', 'P', '1'};
  for(int i = 0; i < 8; i++)
    msg[4 + i] = static_cast<uint8_t>(static_cast<uint64_t>(slot) >> (56 - 8 * i));
  msg[12] = flags;
  msg[13] = timeoutByte;
  std::array<uint8_t, EVP_MAX_MD_SIZE> mac{};
  unsigned int len{};
  if(!HMAC(EVP_sha256(), encKey.data(), static_cast<int>(encKey.size()), msg.data(), msg.size(), mac.data(), &len))
    return false;
  return CRYPTO_memcmp(mac.data(), token, 8) == 0;
}

void ReloadDevicesIfStale(WatchContext &ctx) {
  auto now = std::chrono::steady_clock::now();
  if(!ctx.devices.empty() && now - ctx.devicesLoadedAt < std::chrono::minutes(1))
    return;
  ctx.devices.clear();
  for(const auto &device : PairedDevicesStorage::GetDevices())
    ctx.devices.push_back({device, DeviceTag(device.id)});
  ctx.devicesLoadedAt = now;
}

void HandleManufacturerData(WatchContext &ctx, const std::string &path, const uint8_t *data, size_t size) {
  if(size != PAYLOAD_SIZE || data[0] != PAYLOAD_VERSION)
    return;
  ReloadDevicesIfStale(ctx);
  auto flags = data[1];
  auto timeoutByte = data[6];
  auto nowSlot = static_cast<int64_t>(std::time(nullptr)) / SLOT_SECONDS;
  for(const auto &known : ctx.devices) {
    if(std::memcmp(known.tag.data(), data + 2, 4) != 0)
      continue;
    // A few slots of tolerance for clock skew between phone and PC.
    for(auto slot = nowSlot - 3; slot <= nowSlot + 1; slot++) {
      if(!TokenMatches(known.device.encryptionKey, slot, flags, timeoutByte, data + 7))
        continue;
      BlePresenceWatcher::Sighting sighting{known.device, timeoutByte * 10, (flags & FLAG_DISARM) != 0};
      ctx.paths[path] = {sighting, std::chrono::steady_clock::now()};
      (*ctx.onSighting)(sighting);
      return;
    }
  }
}

void HandleRssiUpdate(WatchContext &ctx, const std::string &path) {
  auto it = ctx.paths.find(path);
  if(it == ctx.paths.end() || it->second.sighting.disarm)
    return;
  if(std::chrono::steady_clock::now() - it->second.verifiedAt > std::chrono::seconds(3 * SLOT_SECONDS))
    return;
  (*ctx.onSighting)(it->second.sighting);
}

// Reads one Device1 a{sv} property dict (message positioned right before it).
int ReadDeviceProperties(sd_bus_message *m, WatchContext &ctx, const std::string &path) {
  int r = sd_bus_message_enter_container(m, SD_BUS_TYPE_ARRAY, "{sv}");
  if(r < 0)
    return r;
  while((r = sd_bus_message_enter_container(m, SD_BUS_TYPE_DICT_ENTRY, "sv")) > 0) {
    const char *key{};
    if((r = sd_bus_message_read(m, "s", &key)) < 0)
      return r;
    if(std::strcmp(key, "ManufacturerData") == 0) {
      if((r = sd_bus_message_enter_container(m, SD_BUS_TYPE_VARIANT, "a{qv}")) < 0)
        return r;
      if((r = sd_bus_message_enter_container(m, SD_BUS_TYPE_ARRAY, "{qv}")) < 0)
        return r;
      while((r = sd_bus_message_enter_container(m, SD_BUS_TYPE_DICT_ENTRY, "qv")) > 0) {
        uint16_t company{};
        if((r = sd_bus_message_read(m, "q", &company)) < 0)
          return r;
        if((r = sd_bus_message_enter_container(m, SD_BUS_TYPE_VARIANT, "ay")) < 0)
          return r;
        const void *bytes{};
        size_t size{};
        if((r = sd_bus_message_read_array(m, 'y', &bytes, &size)) < 0)
          return r;
        if(company == COMPANY_ID)
          HandleManufacturerData(ctx, path, static_cast<const uint8_t *>(bytes), size);
        sd_bus_message_exit_container(m); // variant
        sd_bus_message_exit_container(m); // dict entry
      }
      sd_bus_message_exit_container(m); // array
      sd_bus_message_exit_container(m); // variant
    } else {
      if(std::strcmp(key, "RSSI") == 0)
        HandleRssiUpdate(ctx, path);
      if((r = sd_bus_message_skip(m, "v")) < 0)
        return r;
    }
    sd_bus_message_exit_container(m); // dict entry
  }
  if(r < 0)
    return r;
  return sd_bus_message_exit_container(m);
}

int OnPropertiesChanged(sd_bus_message *m, void *userdata, sd_bus_error *) {
  auto &ctx = *static_cast<WatchContext *>(userdata);
  const char *iface{};
  if(sd_bus_message_read(m, "s", &iface) < 0 || std::strcmp(iface, "org.bluez.Device1") != 0)
    return 0;
  const char *path = sd_bus_message_get_path(m);
  ReadDeviceProperties(m, ctx, path ? path : "");
  return 0;
}

int OnInterfacesAdded(sd_bus_message *m, void *userdata, sd_bus_error *) {
  auto &ctx = *static_cast<WatchContext *>(userdata);
  const char *path{};
  if(sd_bus_message_read(m, "o", &path) < 0)
    return 0;
  if(sd_bus_message_enter_container(m, SD_BUS_TYPE_ARRAY, "{sa{sv}}") < 0)
    return 0;
  while(sd_bus_message_enter_container(m, SD_BUS_TYPE_DICT_ENTRY, "sa{sv}") > 0) {
    const char *iface{};
    if(sd_bus_message_read(m, "s", &iface) < 0)
      return 0;
    if(std::strcmp(iface, "org.bluez.Device1") == 0) {
      if(ReadDeviceProperties(m, ctx, path) < 0)
        return 0;
    } else if(sd_bus_message_skip(m, "a{sv}") < 0) {
      return 0;
    }
    sd_bus_message_exit_container(m);
  }
  return 0;
}

bool StartLeDiscovery(sd_bus *bus) {
  sd_bus_message *m{};
  sd_bus_error err = SD_BUS_ERROR_NULL;
  int r = sd_bus_message_new_method_call(bus, &m, "org.bluez", ADAPTER_PATH, "org.bluez.Adapter1", "SetDiscoveryFilter");
  if(r >= 0)
    r = sd_bus_message_append(m, "a{sv}", 2, "Transport", "s", "le", "DuplicateData", "b", 1);
  if(r >= 0)
    r = sd_bus_call(bus, m, 0, &err, nullptr);
  sd_bus_message_unref(m);
  if(r < 0) {
    spdlog::warn("BLE presence: SetDiscoveryFilter failed: {}", err.message ? err.message : strerror(-r));
    sd_bus_error_free(&err);
    return false;
  }
  r = sd_bus_call_method(bus, "org.bluez", ADAPTER_PATH, "org.bluez.Adapter1", "StartDiscovery", &err, nullptr, "");
  // InProgress just means our own earlier session is still running.
  if(r < 0 && !sd_bus_error_has_name(&err, "org.bluez.Error.InProgress")) {
    spdlog::warn("BLE presence: StartDiscovery failed: {}", err.message ? err.message : strerror(-r));
    sd_bus_error_free(&err);
    return false;
  }
  sd_bus_error_free(&err);
  return true;
}

bool IsDiscovering(sd_bus *bus) {
  int discovering{};
  sd_bus_error err = SD_BUS_ERROR_NULL;
  int r = sd_bus_get_property_trivial(bus, "org.bluez", ADAPTER_PATH, "org.bluez.Adapter1", "Discovering", &err, 'b', &discovering);
  sd_bus_error_free(&err);
  return r >= 0 && discovering;
}

} // namespace

bool BlePresenceWatcher::IsScanning() {
  return g_Scanning;
}

void BlePresenceWatcher::Run(const std::function<void(const Sighting &)> &onSighting) {
  bool loggedFailure = false;
  while(true) {
    sd_bus *bus{};
    WatchContext ctx{};
    ctx.onSighting = &onSighting;
    // Discovery started over this connection lasts as long as the connection itself does
    // (BlueZ stops a client's discovery when it leaves the bus), hence one long-lived bus.
    if(sd_bus_open_system(&bus) >= 0 &&
       sd_bus_match_signal(bus, nullptr, "org.bluez", nullptr, "org.freedesktop.DBus.Properties", "PropertiesChanged",
                           OnPropertiesChanged, &ctx) >= 0 &&
       sd_bus_match_signal(bus, nullptr, "org.bluez", "/", "org.freedesktop.DBus.ObjectManager", "InterfacesAdded",
                           OnInterfacesAdded, &ctx) >= 0 &&
       StartLeDiscovery(bus)) {
      spdlog::info("BLE presence: scanning on {}.", ADAPTER_PATH);
      loggedFailure = false;
      g_Scanning = true;
      auto lastCheck = std::chrono::steady_clock::now();
      while(true) {
        int r = sd_bus_process(bus, nullptr);
        if(r < 0)
          break;
        if(r == 0 && sd_bus_wait(bus, 2'000'000) < 0)
          break;
        // Discovery also stops when the adapter is powered off/on or bluetoothd restarts.
        if(std::chrono::steady_clock::now() - lastCheck > std::chrono::seconds(15)) {
          lastCheck = std::chrono::steady_clock::now();
          if(!IsDiscovering(bus) && !StartLeDiscovery(bus))
            break;
        }
      }
      g_Scanning = false;
      spdlog::warn("BLE presence: scanning stopped; retrying.");
    } else if(!loggedFailure) {
      spdlog::warn("BLE presence: Bluetooth unavailable; retrying every 10s.");
      loggedFailure = true;
    }
    sd_bus_flush_close_unref(bus);
    std::this_thread::sleep_for(std::chrono::seconds(10));
  }
}
