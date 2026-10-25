#ifndef PCBU_DESKTOP_BLEPRESENCEWATCHER_H
#define PCBU_DESKTOP_BLEPRESENCEWATCHER_H

#include <functional>

#include "storage/PairedDevicesStorage.h"

// Bluetooth flavour of the proximity lock: the phone advertises a BLE beacon whose
// manufacturer data (company 0xFFFF, reserved for testing/internal use) carries
//   [0] version (1)  [1] flags (bit 0 = disarm)  [2..5] SHA-256(deviceId)[0..4]
//   [6] timeout / 10 s  [7..14] HMAC-SHA256(encKey, "BKP1" | slot (u64 BE) | flags | timeout)[0..8]
// with slot = unix time / 10 s. The HMAC keeps a nearby device from replaying a stale
// beacon (beyond a few slots) or forging one to keep the PC unlocked.
class BlePresenceWatcher {
public:
  struct Sighting {
    PairedDevice device;
    int timeoutS{};
    bool disarm{};
  };

  // Blocking: scans through BlueZ for the daemon's whole lifetime, reconnecting if
  // bluetoothd or the adapter goes away. [onSighting] runs on this thread.
  static void Run(const std::function<void(const Sighting &)> &onSighting);

  // False while the PC itself can't scan (no adapter, powered off, bluetoothd down): absence
  // of beacons then says nothing about the phone, so callers must not count it.
  static bool IsScanning();

private:
  BlePresenceWatcher() = default;
};

#endif
