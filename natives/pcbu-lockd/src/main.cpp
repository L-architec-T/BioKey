#include <algorithm>
#include <chrono>
#include <csignal>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <thread>

#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>

#include "connection/BaseConnection.h"
#include "connection/Packets.h"
#include "connection/SocketDefs.h"
#include "platform/PlatformHelper.h"
#include "storage/AppSettings.h"
#include "storage/LoggingSystem.h"
#include "storage/PairedDevicesStorage.h"
#include "utils/CryptUtils.h"
#include "utils/StringUtils.h"

#ifdef LINUX
#include "Login1UnlockWatcher.h"
#endif

namespace {

volatile std::sig_atomic_t g_ShouldRun = 1;

void HandleStopSignal(int) {
  g_ShouldRun = 0;
}

std::optional<std::pair<PairedDevice, std::vector<uint8_t>>> VerifyRemoteCommand(const PacketLockRequest &request) {
  auto device = PairedDevicesStorage::GetDeviceByID(request.deviceId);
  if(!device.has_value()) {
    spdlog::warn("Remote command for unknown device ID.");
    return {};
  }
  auto encBytes = StringUtils::FromHexString(request.encData);
  auto cryptResult = CryptUtils::DecryptAESPacket(encBytes, device->encryptionKey);
  if(cryptResult.result != PacketCryptResult::OK) {
    spdlog::warn("Remote command failed decryption/anti-replay check for device '{}'.", device->deviceName);
    return {};
  }
  return std::make_pair(device.value(), cryptResult.data);
}

#ifdef LINUX
bool IsAuthInProgress(const std::string &userName) {
  std::error_code ec;
  for(const auto &entry : std::filesystem::directory_iterator("/proc", ec)) {
    if(ec || !entry.is_directory())
      continue;
    const auto &pidName = entry.path().filename().string();
    if(!std::ranges::all_of(pidName, [](unsigned char c) { return std::isdigit(c); }))
      continue;

    std::ifstream cmdlineFile(entry.path() / "cmdline", std::ios::binary);
    if(!cmdlineFile)
      continue;
    std::string cmdline((std::istreambuf_iterator<char>(cmdlineFile)), std::istreambuf_iterator<char>());
    std::vector<std::string> args{};
    std::size_t start = 0;
    for(std::size_t i = 0; i < cmdline.size(); ++i) {
      if(cmdline[i] == '\0') {
        args.emplace_back(cmdline.substr(start, i - start));
        start = i + 1;
      }
    }
    if(args.size() >= 2 && args[0].ends_with("pcbu_auth") && args[1] == userName)
      return true;
  }
  return false;
}

void HandleSelectUserCommand(const PairedDevice &device) {
  if(IsAuthInProgress(device.userName)) {
    spdlog::info("Select-user request from '{}' ignored: a login for '{}' is already in progress.", device.deviceName, device.userName);
    return;
  }

  auto order = PlatformHelper::GetGreeterUserOrder();
  auto it = std::ranges::find(order, device.userName);
  if(it == order.end()) {
    spdlog::warn("Select-user request from '{}' but '{}' isn't in the greeter's user list.", device.deviceName, device.userName);
    return;
  }
  auto index = static_cast<int>(std::distance(order.begin(), it));
  spdlog::info("Selecting greeter user '{}' (index {}) for device '{}'.", device.userName, index, device.deviceName);
  if(!PlatformHelper::SelectGreeterUser(index))
    spdlog::error("Failed to select greeter user '{}'.", device.userName);
}
#endif

#ifdef LINUX
void SelectGreeterUserOnStartup() {
  auto devices = PairedDevicesStorage::GetDevices();
  if(devices.empty())
    return;

  constexpr int MAX_ATTEMPTS = 15;
  for(int attempt = 0; attempt < MAX_ATTEMPTS; ++attempt) {
    if(!PlatformHelper::GetGreeterUserOrder().empty()) {
      HandleSelectUserCommand(devices.front());
      return;
    }
    std::this_thread::sleep_for(std::chrono::seconds(1));
  }
  spdlog::warn("Greeter never became ready for an auto-selected user at startup (gave up after {}s).", MAX_ATTEMPTS);
}
#endif

void HandleSuspendCommand(const PairedDevice &device) {
  spdlog::info("Suspend request from '{}'.", device.deviceName);
  if(!PlatformHelper::Suspend())
    spdlog::error("Verified suspend request but Suspend() failed.");
}

#ifdef LINUX
bool ValidateMacroSteps(const nlohmann::json &macroJson) {
  if(!macroJson.contains("steps") || !macroJson["steps"].is_array()) {
    spdlog::warn("setMacro payload missing a 'steps' array.");
    return false;
  }
  for(const auto &step : macroJson["steps"]) {
    if(!step.contains("type") || !step["type"].is_string()) {
      spdlog::warn("setMacro step missing a string 'type'.");
      return false;
    }
    auto type = step["type"].get<std::string>();
    if(type == "KEY_COMBO") {
      if(!step.contains("keys") || !step["keys"].is_array() || step["keys"].empty()) {
        spdlog::warn("setMacro KEY_COMBO step needs a non-empty 'keys' array.");
        return false;
      }
      for(const auto &key : step["keys"]) {
        if(!key.is_string() || !PlatformHelper::IsValidMacroKeyName(key.get<std::string>())) {
          spdlog::warn("setMacro KEY_COMBO step has an unrecognized key name.");
          return false;
        }
      }
    } else if(type == "TIMEOUT") {
      if(!step.contains("ms") || !step["ms"].is_number_integer()) {
        spdlog::warn("setMacro TIMEOUT step needs an integer 'ms'.");
        return false;
      }
      auto ms = step["ms"].get<int64_t>();
      if(ms < 0 || ms > 30'000) {
        spdlog::warn("setMacro TIMEOUT step's ms is out of the [0, 30000] range.");
        return false;
      }
    } else if(type == "TYPE_TEXT") {
      if(!step.contains("text") || !step["text"].is_string()) {
        spdlog::warn("setMacro TYPE_TEXT step needs a string 'text'.");
        return false;
      }
      for(auto c : step["text"].get<std::string>()) {
        auto uc = static_cast<unsigned char>(c);
        if(uc < 0x20 || uc > 0x7E) {
          spdlog::warn("setMacro TYPE_TEXT step contains a non-ASCII-printable character.");
          return false;
        }
      }
    } else {
      spdlog::warn("setMacro step has an unrecognized type '{}'.", type);
      return false;
    }
  }
  return true;
}

void HandleSetMacroCommand(const PairedDevice &device, const std::vector<uint8_t> &plaintext) {
  std::string macroStr(plaintext.begin(), plaintext.end());
  try {
    auto macroJson = nlohmann::json::parse(macroStr);
    if(!ValidateMacroSteps(macroJson)) {
      spdlog::warn("Rejected invalid setMacro payload from '{}'.", device.deviceName);
      return;
    }
    PairedDevicesStorage::SetMacroSteps(device.id, macroStr);
    spdlog::info("Stored a {}-step post-unlock macro for '{}'.", macroJson["steps"].size(), device.deviceName);
  } catch(const std::exception &ex) {
    spdlog::warn("Rejected malformed setMacro payload from '{}': {}", device.deviceName, ex.what());
  }
}
#endif

} // namespace

int main() {
  LoggingSystem::Init("lockd");
  std::signal(SIGINT, HandleStopSignal);
  std::signal(SIGTERM, HandleStopSignal);

  WSA_STARTUP

  SOCKET sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if(sock == SOCKET_INVALID) {
    spdlog::error("socket() failed. (Code={})", SOCKET_LAST_ERROR);
    return 1;
  }

  auto port = AppSettings::Get().lockListenPort;
  struct sockaddr_in address {};
  address.sin_family = AF_INET;
  address.sin_addr.s_addr = INADDR_ANY;
  address.sin_port = htons(port);
  if(bind(sock, reinterpret_cast<struct sockaddr *>(&address), sizeof(address)) < 0) {
    spdlog::error("bind() on port {} failed. (Code={})", port, SOCKET_LAST_ERROR);
    SOCKET_CLOSE(sock);
    return 1;
  }
  spdlog::info("pcbu-lockd listening on UDP port {}.", port);

#ifdef LINUX
  // Detached, not joined inline: the retry can take up to ~15s if the greeter isn't up yet,
  // and there's no reason to delay this socket answering phone-driven commands while it waits.
  std::thread(SelectGreeterUserOnStartup).detach();
  // Independent, long-lived, detached for the daemon's whole lifetime (same lifecycle class as
  // the thread above) — watches for a session actually unlocking (not just PAM auth succeeding)
  // and replays any configured macro; has no coordination with the UDP loop below, since the
  // macro push (setMacro) and the macro replay trigger are deliberately decoupled flows.
  std::thread(Login1UnlockWatcher::Run).detach();
#endif

  // Blocking recv with a timeout, not a fully blocking one, purely so the signal-driven
  // stop flag gets checked periodically instead of only after the next datagram arrives.
  struct timeval timeout {};
  timeout.tv_sec = 1;
  setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, reinterpret_cast<const char *>(&timeout), sizeof(timeout));

  std::vector<uint8_t> buffer(4096);
  while(g_ShouldRun) {
    struct sockaddr_in fromAddr {};
    socklen_t fromLen = sizeof(fromAddr);
    auto received = recvfrom(sock, reinterpret_cast<char *>(buffer.data()), static_cast<int>(buffer.size()), 0,
                              reinterpret_cast<struct sockaddr *>(&fromAddr), &fromLen);
    if(received < 0) {
      auto err = SOCKET_LAST_ERROR;
      if(err == SOCKET_ERROR_TRY_AGAIN || err == SOCKET_ERROR_WOULD_BLOCK)
        continue;
      spdlog::error("recvfrom() failed. (Code={})", err);
      continue;
    }
    if(received == 0)
      continue;

    auto jsonStr = std::string(reinterpret_cast<const char *>(buffer.data()), static_cast<size_t>(received));
    auto request = PacketLockRequest::FromJson(jsonStr);
    if(!request.has_value()) {
      spdlog::warn("Malformed lock request datagram; ignoring.");
      continue;
    }

    auto verified = VerifyRemoteCommand(request.value());
    if(!verified.has_value())
      continue;
    auto &[device, plaintext] = verified.value();

    if(request->cmd == "selectUser") {
#ifdef LINUX
      HandleSelectUserCommand(device);
#else
      spdlog::warn("selectUser command received but isn't supported on this platform.");
#endif
    } else if(request->cmd == "suspend") {
      HandleSuspendCommand(device);
    } else if(request->cmd == "setMacro") {
#ifdef LINUX
      HandleSetMacroCommand(device, plaintext);
#else
      spdlog::warn("setMacro command received but isn't supported on this platform.");
#endif
    } else {
      spdlog::info("Verified lock request from '{}'.", device.deviceName);
      if(!PlatformHelper::LockScreen())
        spdlog::error("Verified lock request but LockScreen() failed.");
    }
  }

  spdlog::info("pcbu-lockd shutting down.");
  SOCKET_CLOSE(sock);
  LoggingSystem::Destroy();
  return 0;
}
