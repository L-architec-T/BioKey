#include "PlatformHelper.h"

#include <chrono>
#include <crypt.h>
#include <cstring>
#include <fcntl.h>
#include <linux/uinput.h>
#include <nlohmann/json.hpp>
#include <optional>
#include <pwd.h>
#include <shadow.h>
#include <thread>
#include <unistd.h>
#include <unordered_map>
#include <utility>

#include "shell/Shell.h"
#include "utils/StringUtils.h"

std::vector<std::string> PlatformHelper::GetAllUsers() {
  auto data = Shell::ReadBytes("/etc/passwd");
  auto passwdStr = std::string(data.begin(), data.end());
  std::vector<std::string> result{};
  for(const auto &entry : StringUtils::Split(passwdStr, "\n")) {
    auto split = StringUtils::Split(entry, ":");
    if(split.size() < 3)
      continue;
    char *end{};
    auto uidStr = split[2].c_str();
    auto uid = std::strtol(uidStr, &end, 10);
    if(end == uidStr || (uid != 0 && (uid < 1000 || uid > 60000)))
      continue;
    auto userName = split[0];
    result.push_back(userName);
  }
  return result;
}

std::string PlatformHelper::GetCurrentUser() {
  std::optional<uid_t> uid{};
  auto sudoUidStr = std::getenv("SUDO_UID");
  if(sudoUidStr != nullptr) {
    char *end{};
    auto sudoUid = std::strtol(sudoUidStr, &end, 10);
    if(end != sudoUidStr)
      uid = sudoUid;
  }
  auto pkExecUidStr = std::getenv("PKEXEC_UID");
  if(pkExecUidStr != nullptr) {
    char *end{};
    auto pkExecUid = std::strtol(pkExecUidStr, &end, 10);
    if(end != pkExecUidStr)
      uid = pkExecUid;
  }

  if(!uid.has_value())
    uid = getuid();
  auto userStruct = getpwuid(uid.value());
  if(userStruct == nullptr) {
    spdlog::error("Failed to find current user.");
    return {};
  }
  return userStruct->pw_name;
}

bool PlatformHelper::HasUserPassword(const std::string &userName) {
  return true;
}

PlatformLoginStatus PlatformHelper::CheckLogin(const std::string &userName, const std::string &password) {
  struct passwd *passwdEntry = getpwnam(userName.c_str());
  if(!passwdEntry) {
    spdlog::error("Failed to read passwd entry for user {}.", userName);
    return {PlatformLoginResult::INVALID_USER};
  }
  if(strcmp(passwdEntry->pw_passwd, "x") != 0) {
    if(strcmp(passwdEntry->pw_passwd, crypt(password.c_str(), passwdEntry->pw_passwd)) == 0)
      return {PlatformLoginResult::SUCCESS};
    return {PlatformLoginResult::INVALID_PASSWORD};
  }

  struct spwd *shadowEntry = getspnam(userName.c_str());
  if(!shadowEntry) {
    spdlog::error("Failed to read shadow entry for user {}." + userName);
    return {PlatformLoginResult::INVALID_USER};
  }
  if(strcmp(shadowEntry->sp_pwdp, crypt(password.c_str(), shadowEntry->sp_pwdp)) == 0)
    return {PlatformLoginResult::SUCCESS};
  return {PlatformLoginResult::INVALID_PASSWORD};
}

bool PlatformHelper::HasNativeLibrary(const std::string &libName) {
  return std::filesystem::exists(std::filesystem::path("/lib64") / libName) || std::filesystem::exists(std::filesystem::path("/lib") / libName) ||
         std::filesystem::exists(std::filesystem::path("/usr/lib/x86_64-linux-gnu") / libName) ||
         std::filesystem::exists(std::filesystem::path("/usr/lib/aarch64-linux-gnu") / libName);
}

bool PlatformHelper::LockScreen() {
  auto result = Shell::RunCommand("loginctl lock-sessions");
  if(result.exitCode != 0) {
    spdlog::error("loginctl lock-sessions failed (exit={}): {}", result.exitCode, result.output);
    return false;
  }
  return true;
}

bool PlatformHelper::Suspend() {
  auto result = Shell::RunCommand("systemctl suspend");
  if(result.exitCode != 0) {
    spdlog::error("systemctl suspend failed (exit={}): {}", result.exitCode, result.output);
    return false;
  }
  return true;
}

std::vector<std::string> PlatformHelper::GetGreeterUserOrder() {
  std::vector<std::string> result{};
  auto listResult = Shell::RunCommand(
      "busctl --system --json=short call org.freedesktop.Accounts /org/freedesktop/Accounts org.freedesktop.Accounts ListCachedUsers");
  if(listResult.exitCode != 0) {
    spdlog::error("ListCachedUsers failed (exit={}): {}", listResult.exitCode, listResult.output);
    return result;
  }

  std::vector<std::string> userPaths{};
  try {
    auto json = nlohmann::json::parse(listResult.output);
    userPaths = json["data"][0].get<std::vector<std::string>>();
  } catch(const std::exception &ex) {
    spdlog::error("Failed to parse ListCachedUsers output: {}", ex.what());
    return result;
  }

  for(const auto &path : userPaths) {
    auto propResult = Shell::RunCommand(fmt::format(
        R"(busctl --system --json=short get-property org.freedesktop.Accounts "{}" org.freedesktop.Accounts.User UserName)", path));
    if(propResult.exitCode != 0) {
      spdlog::warn("Failed to read UserName for '{}' (exit={}).", path, propResult.exitCode);
      continue;
    }
    try {
      auto json = nlohmann::json::parse(propResult.output);
      result.push_back(json["data"].get<std::string>());
    } catch(const std::exception &ex) {
      spdlog::warn("Failed to parse UserName property for '{}': {}", path, ex.what());
    }
  }
  return result;
}

namespace {
bool EmitUinputEvent(int fd, uint16_t type, uint16_t code, int32_t value) {
  struct input_event ev {};
  ev.type = type;
  ev.code = code;
  ev.value = value;
  return write(fd, &ev, sizeof(ev)) == sizeof(ev);
}

bool TapKey(int fd, uint16_t code) {
  return EmitUinputEvent(fd, EV_KEY, code, 1) && EmitUinputEvent(fd, EV_SYN, SYN_REPORT, 0) && EmitUinputEvent(fd, EV_KEY, code, 0) &&
         EmitUinputEvent(fd, EV_SYN, SYN_REPORT, 0);
}

bool TapCombo(int fd, const std::vector<uint16_t> &keycodes) {
  for(auto code : keycodes)
    if(!EmitUinputEvent(fd, EV_KEY, code, 1))
      return false;
  if(!EmitUinputEvent(fd, EV_SYN, SYN_REPORT, 0))
    return false;
  for(auto it = keycodes.rbegin(); it != keycodes.rend(); ++it)
    if(!EmitUinputEvent(fd, EV_KEY, *it, 0))
      return false;
  return EmitUinputEvent(fd, EV_SYN, SYN_REPORT, 0);
}

const std::unordered_map<std::string, uint16_t> &KeyNameToKeycode() {
  static const std::unordered_map<std::string, uint16_t> table = {
      {"CTRL", KEY_LEFTCTRL}, {"ALT", KEY_LEFTALT}, {"SHIFT", KEY_LEFTSHIFT}, {"WIN", KEY_LEFTMETA},
      {"ENTER", KEY_ENTER}, {"TAB", KEY_TAB}, {"ESC", KEY_ESC}, {"SPACE", KEY_SPACE},
      {"BACKSPACE", KEY_BACKSPACE}, {"DELETE", KEY_DELETE}, {"HOME", KEY_HOME}, {"END", KEY_END},
      {"UP", KEY_UP}, {"DOWN", KEY_DOWN}, {"LEFT", KEY_LEFT}, {"RIGHT", KEY_RIGHT},
      {"A", KEY_A}, {"B", KEY_B}, {"C", KEY_C}, {"D", KEY_D}, {"E", KEY_E}, {"F", KEY_F},
      {"G", KEY_G}, {"H", KEY_H}, {"I", KEY_I}, {"J", KEY_J}, {"K", KEY_K}, {"L", KEY_L},
      {"M", KEY_M}, {"N", KEY_N}, {"O", KEY_O}, {"P", KEY_P}, {"Q", KEY_Q}, {"R", KEY_R},
      {"S", KEY_S}, {"T", KEY_T}, {"U", KEY_U}, {"V", KEY_V}, {"W", KEY_W}, {"X", KEY_X},
      {"Y", KEY_Y}, {"Z", KEY_Z},
      {"0", KEY_0}, {"1", KEY_1}, {"2", KEY_2}, {"3", KEY_3}, {"4", KEY_4},
      {"5", KEY_5}, {"6", KEY_6}, {"7", KEY_7}, {"8", KEY_8}, {"9", KEY_9},
      {"F1", KEY_F1}, {"F2", KEY_F2}, {"F3", KEY_F3}, {"F4", KEY_F4}, {"F5", KEY_F5}, {"F6", KEY_F6},
      {"F7", KEY_F7}, {"F8", KEY_F8}, {"F9", KEY_F9}, {"F10", KEY_F10}, {"F11", KEY_F11}, {"F12", KEY_F12},
  };
  return table;
}

const std::unordered_map<char, std::pair<uint16_t, bool>> &AsciiToKeycode() {
  static const std::unordered_map<char, std::pair<uint16_t, bool>> table = [] {
    std::unordered_map<char, std::pair<uint16_t, bool>> t;
    t[' '] = {KEY_SPACE, false};
    for(char c = 'a'; c <= 'z'; ++c) t[c] = {KeyNameToKeycode().at(std::string(1, static_cast<char>(std::toupper(c)))), false};
    for(char c = 'A'; c <= 'Z'; ++c) t[c] = {KeyNameToKeycode().at(std::string(1, c)), true};
    const char *digitsUnshifted = "0123456789";
    const char *digitsShifted = ")!@#$%^&*(";
    for(int i = 0; i < 10; ++i) {
      t[digitsUnshifted[i]] = {KeyNameToKeycode().at(std::string(1, digitsUnshifted[i])), false};
      t[digitsShifted[i]] = {KeyNameToKeycode().at(std::string(1, digitsUnshifted[i])), true};
    }
    t['`'] = {KEY_GRAVE, false};
    t['~'] = {KEY_GRAVE, true};
    t['-'] = {KEY_MINUS, false};
    t['_'] = {KEY_MINUS, true};
    t['='] = {KEY_EQUAL, false};
    t['+'] = {KEY_EQUAL, true};
    t['['] = {KEY_LEFTBRACE, false};
    t['{'] = {KEY_LEFTBRACE, true};
    t[']'] = {KEY_RIGHTBRACE, false};
    t['}'] = {KEY_RIGHTBRACE, true};
    t['\\'] = {KEY_BACKSLASH, false};
    t['|'] = {KEY_BACKSLASH, true};
    t[';'] = {KEY_SEMICOLON, false};
    t[':'] = {KEY_SEMICOLON, true};
    t['\''] = {KEY_APOSTROPHE, false};
    t['"'] = {KEY_APOSTROPHE, true};
    t[','] = {KEY_COMMA, false};
    t['<'] = {KEY_COMMA, true};
    t['.'] = {KEY_DOT, false};
    t['>'] = {KEY_DOT, true};
    t['/'] = {KEY_SLASH, false};
    t['?'] = {KEY_SLASH, true};
    return t;
  }();
  return table;
}

const std::unordered_map<char, std::pair<uint16_t, bool>> &AsciiToKeycodeFr() {
  static const std::unordered_map<char, std::pair<uint16_t, bool>> table = [] {
    std::unordered_map<char, std::pair<uint16_t, bool>> t;
    t[' '] = {KEY_SPACE, false};
    for(char c : std::string("ertyuiopsdfghjklxcvbn")) {
      auto code = KeyNameToKeycode().at(std::string(1, static_cast<char>(std::toupper(c))));
      t[c] = {code, false};
      t[static_cast<char>(std::toupper(c))] = {code, true};
    }
    t['a'] = {KEY_Q, false};
    t['A'] = {KEY_Q, true};
    t['q'] = {KEY_A, false};
    t['Q'] = {KEY_A, true};
    t['w'] = {KEY_Z, false};
    t['W'] = {KEY_Z, true};
    t['z'] = {KEY_W, false};
    t['Z'] = {KEY_W, true};
    t['m'] = {KEY_SEMICOLON, false};
    t['M'] = {KEY_SEMICOLON, true};
    const std::unordered_map<char, uint16_t> digitKeys = {
        {'1', KEY_1}, {'2', KEY_2}, {'3', KEY_3}, {'4', KEY_4}, {'5', KEY_5},
        {'6', KEY_6}, {'7', KEY_7}, {'8', KEY_8}, {'9', KEY_9}, {'0', KEY_0},
    };
    for(const auto &[digit, code] : digitKeys)
      t[digit] = {code, true};
    t['&'] = {KEY_1, false};
    t['"'] = {KEY_3, false};
    t['\''] = {KEY_4, false};
    t['('] = {KEY_5, false};
    t['-'] = {KEY_6, false};
    t['_'] = {KEY_8, false};
    t[')'] = {KEY_MINUS, false};
    t['='] = {KEY_EQUAL, false};
    t['+'] = {KEY_EQUAL, true};
    t[','] = {KEY_M, false};
    t['?'] = {KEY_M, true};
    t[';'] = {KEY_COMMA, false};
    t['.'] = {KEY_COMMA, true};
    t[':'] = {KEY_DOT, false};
    t['/'] = {KEY_DOT, true};
    t['!'] = {KEY_SLASH, false};
    return t;
  }();
  return table;
}

std::string DetectKeyboardLayout() {
  auto data = Shell::ReadBytes("/etc/default/keyboard");
  auto contents = std::string(data.begin(), data.end());
  for(const auto &line : StringUtils::Split(contents, "\n")) {
    static const std::string prefix = "XKBLAYOUT=";
    if(line.rfind(prefix, 0) != 0)
      continue;
    auto value = line.substr(prefix.size());
    if(!value.empty() && value.front() == '"')
      value.erase(0, 1);
    auto quote = value.find('"');
    if(quote != std::string::npos)
      value.erase(quote);
    auto comma = value.find(',');
    if(comma != std::string::npos)
      value.erase(comma);
    return value.empty() ? "us" : value;
  }
  return "us";
}

const std::unordered_map<char, std::pair<uint16_t, bool>> &ActiveAsciiToKeycode() {
  static const std::unordered_map<char, std::pair<uint16_t, bool>> &table =
      DetectKeyboardLayout() == "fr" ? AsciiToKeycodeFr() : AsciiToKeycode();
  return table;
}

void RegisterMacroKeybits(int fd) {
  ioctl(fd, UI_SET_EVBIT, EV_KEY);
  for(const auto &[name, code] : KeyNameToKeycode())
    ioctl(fd, UI_SET_KEYBIT, code);
  for(const auto &[ch, keycodeAndShift] : AsciiToKeycode())
    ioctl(fd, UI_SET_KEYBIT, keycodeAndShift.first);
}

int OpenUinputKeyboard() {
  int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
  if(fd < 0) {
    spdlog::error("Failed to open /dev/uinput (errno={}).", errno);
    return -1;
  }

  RegisterMacroKeybits(fd);

  struct uinput_setup usetup {};
  usetup.id.bustype = BUS_USB;
  usetup.id.vendor = 0x1209;
  usetup.id.product = 0xB10C;
  strncpy(usetup.name, "pcbu-virtual-keyboard", sizeof(usetup.name) - 1);

  if(ioctl(fd, UI_DEV_SETUP, &usetup) < 0 || ioctl(fd, UI_DEV_CREATE) < 0) {
    spdlog::error("Failed to create uinput device (errno={}).", errno);
    close(fd);
    return -1;
  }
  std::this_thread::sleep_for(std::chrono::milliseconds(3000));
  return fd;
}

void CloseUinputKeyboard(int fd) {
  std::this_thread::sleep_for(std::chrono::milliseconds(300));
  ioctl(fd, UI_DEV_DESTROY);
  close(fd);
}
}

bool PlatformHelper::SelectGreeterUser(int userIndex) {
  if(userIndex < 0) {
    spdlog::error("SelectGreeterUser called with invalid index {}.", userIndex);
    return false;
  }

  int fd = OpenUinputKeyboard();
  if(fd < 0)
    return false;

  auto ok = true;
  for(auto i = 0; i < userIndex && ok; i++)
    ok = TapKey(fd, KEY_DOWN);
  if(ok)
    ok = TapKey(fd, KEY_ENTER);

  CloseUinputKeyboard(fd);

  if(!ok)
    spdlog::error("Failed to write synthetic key events to uinput device.");
  return ok;
}

bool PlatformHelper::IsValidMacroKeyName(const std::string &keyName) {
  return KeyNameToKeycode().contains(keyName);
}

bool PlatformHelper::PlayMacro(const std::string &macroStepsJson) {
  nlohmann::json macroJson;
  try {
    macroJson = nlohmann::json::parse(macroStepsJson);
  } catch(const std::exception &ex) {
    spdlog::error("PlayMacro: failed to parse macro JSON: {}", ex.what());
    return false;
  }
  if(!macroJson.contains("steps") || !macroJson["steps"].is_array()) {
    spdlog::error("PlayMacro: macro JSON has no 'steps' array.");
    return false;
  }

  int fd = OpenUinputKeyboard();
  if(fd < 0)
    return false;

  auto ok = true;
  try {
    for(const auto &step : macroJson["steps"]) {
      if(!ok)
        break;
      auto type = step.value("type", std::string());
      if(type == "KEY_COMBO") {
        std::vector<uint16_t> keycodes{};
        for(const auto &key : step.value("keys", nlohmann::json::array())) {
          auto it = KeyNameToKeycode().find(key.get<std::string>());
          if(it == KeyNameToKeycode().end()) {
            spdlog::error("PlayMacro: unrecognized key name '{}'.", key.get<std::string>());
            ok = false;
            break;
          }
          keycodes.push_back(it->second);
        }
        if(ok)
          ok = TapCombo(fd, keycodes);
      } else if(type == "TIMEOUT") {
        std::this_thread::sleep_for(std::chrono::milliseconds(step.value("ms", 0)));
      } else if(type == "TYPE_TEXT") {
        for(char c : step.value("text", std::string())) {
          auto it = ActiveAsciiToKeycode().find(c);
          if(it == ActiveAsciiToKeycode().end()) {
            spdlog::warn("PlayMacro: skipping unsupported character in TYPE_TEXT step.");
            continue;
          }
          auto [keycode, needsShift] = it->second;
          if(needsShift)
            EmitUinputEvent(fd, EV_KEY, KEY_LEFTSHIFT, 1);
          ok = TapKey(fd, keycode);
          if(needsShift) {
            EmitUinputEvent(fd, EV_KEY, KEY_LEFTSHIFT, 0);
            EmitUinputEvent(fd, EV_SYN, SYN_REPORT, 0);
          }
          if(!ok)
            break;
        }
      } else {
        spdlog::error("PlayMacro: unrecognized step type '{}'.", type);
        ok = false;
      }
    }
  } catch(const std::exception &ex) {
    spdlog::error("PlayMacro: malformed step data: {}", ex.what());
    ok = false;
  }

  CloseUinputKeyboard(fd);

  if(!ok)
    spdlog::error("PlayMacro: failed partway through the macro; see prior log lines.");
  return ok;
}
