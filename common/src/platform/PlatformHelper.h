#ifndef PCBU_DESKTOP_PLATFORMHELPER_H
#define PCBU_DESKTOP_PLATFORMHELPER_H

#include <cstdint>
#include <functional>
#include <spdlog/spdlog.h>
#include <string>
#include <vector>

enum class PlatformLoginResult {
  SUCCESS,
  INVALID_USER,
  INVALID_PASSWORD,
  ACCOUNT_LOCKED,
  ACCOUNT_RESTRICTED,
  PW_EXPIRED,
  ACCOUNT_DISABLED,
  LOGON_TYPE_DENIED,
  UNKNOWN_ERROR
};

struct PlatformLoginStatus {
  PlatformLoginResult result{PlatformLoginResult::UNKNOWN_ERROR};
  uint32_t errorCode{};
};

class PlatformHelper {
public:
  static bool HasNativeLibrary(const std::string &libName);

  static std::vector<std::string> GetAllUsers();
  static std::string GetCurrentUser();
  static bool HasUserPassword(const std::string &userName);

  static PlatformLoginStatus CheckLogin(const std::string &userName, const std::string &password);

  static bool LockScreen();

  static bool Suspend();

#ifdef WINDOWS
  static bool SetDefaultCredProv(const std::string &userName, const std::string &provId);
#endif

#ifdef LINUX
  enum class SeatScreen { GREETER, LOCKED, UNLOCKED, UNKNOWN };
  struct SeatState {
    SeatScreen screen{SeatScreen::UNKNOWN};
    std::string userName{}; // owner of the active session; empty for GREETER/UNKNOWN
  };
  // What seat0 is showing right now, from logind: the login greeter, a locked user
  // session, or an unlocked one (where synthetic key presses would land in user apps).
  static SeatState GetSeatState();

  static std::vector<std::string> GetGreeterUserOrder();

  static bool SelectGreeterUser(int userIndex);

  static bool IsValidMacroKeyName(const std::string &keyName);

  // `waitUntilReady` runs right after the virtual keyboard is created, so the wait overlaps the
  // time the desktop needs to pick that keyboard up; typing starts no sooner than 1s after it.
  // `userName` is whose session LAUNCH_APP steps open the application in.
  static bool PlayMacro(const std::string &macroStepsJson, const std::string &userName, const std::function<void()> &waitUntilReady = {});
#endif
private:
  PlatformHelper() = default;
};

#endif
