#include "Login1UnlockWatcher.h"

#include <boost/asio.hpp>
#include <boost/process/v2/process.hpp>
#include <boost/process/v2/stdio.hpp>
#include <chrono>
#include <fstream>
#include <nlohmann/json.hpp>
#include <optional>
#include <spdlog/spdlog.h>
#include <thread>
#include <unordered_map>

#include "platform/PlatformHelper.h"
#include "shell/Shell.h"
#include "storage/PairedDevicesStorage.h"

namespace {

constexpr auto BUSCTL_PATH = "/usr/bin/busctl";
constexpr auto SESSION_PATH_PREFIX = "/org/freedesktop/login1/session/";
constexpr auto RESPAWN_BACKOFF = std::chrono::seconds(5);
constexpr auto REPLAY_COOLDOWN = std::chrono::seconds(5);
// A lock that starts this close after a resume is the "lock on resume" kind: still a sleep unlock.
constexpr auto SLEEP_LOCK_TOLERANCE = std::chrono::seconds(10);
// From the unlock: give up waiting for a ready desktop and play the macro anyway.
constexpr auto READY_MAX_WAIT = std::chrono::seconds(20);
constexpr auto CALM_SAMPLE_PERIOD = std::chrono::milliseconds(250);
constexpr int CALM_SAMPLES_NEEDED = 4; // ~1s of calm in a row
constexpr double CALM_MAX_BUSY_RATIO = 0.30; // iowait counts as busy, so disk-heavy startups aren't "calm"

using Clock = std::chrono::steady_clock;

struct SessionState {
  bool locked{};
  Clock::time_point lockStart{};
  Clock::time_point lastReplayed{};
};

struct WatcherState {
  std::unordered_map<std::string, SessionState> sessions{};
  // Last PrepareForSleep signal (going to sleep or waking up); a lock period that contains
  // one ends with a wake-from-sleep unlock, which must never replay a macro.
  std::optional<Clock::time_point> lastSleepEvent{};
};

std::optional<std::string> ExtractSessionPath(const nlohmann::json &value) {
  if(value.is_string()) {
    auto s = value.get<std::string>();
    if(s.starts_with(SESSION_PATH_PREFIX))
      return s;
    return {};
  }
  if(value.is_object()) {
    for(const auto &[key, child] : value.items()) {
      if(auto found = ExtractSessionPath(child); found.has_value())
        return found;
    }
  } else if(value.is_array()) {
    for(const auto &child : value) {
      if(auto found = ExtractSessionPath(child); found.has_value())
        return found;
    }
  }
  return {};
}

std::optional<bool> IsSessionLocked(const std::string &sessionPath) {
  auto result = Shell::RunCommand(
      fmt::format(R"(busctl --system --json=short get-property org.freedesktop.login1 "{}" org.freedesktop.login1.Session LockedHint)", sessionPath));
  if(result.exitCode != 0)
    return {};
  try {
    return nlohmann::json::parse(result.output)["data"].get<bool>();
  } catch(const std::exception &ex) {
    spdlog::warn("Login1UnlockWatcher: failed to parse LockedHint for '{}': {}", sessionPath, ex.what());
    return {};
  }
}

std::optional<std::string> ResolveSessionUser(const std::string &sessionPath) {
  auto result = Shell::RunCommand(
      fmt::format(R"(busctl --system --json=short get-property org.freedesktop.login1 "{}" org.freedesktop.login1.Session Name)", sessionPath));
  if(result.exitCode != 0)
    return {};
  try {
    return nlohmann::json::parse(result.output)["data"].get<std::string>();
  } catch(const std::exception &ex) {
    spdlog::warn("Login1UnlockWatcher: failed to parse Name for '{}': {}", sessionPath, ex.what());
    return {};
  }
}

bool IsGraphicalSessionUp(const std::string &userName) {
  auto result = Shell::RunCommand(fmt::format(R"(systemctl --user -M "{}@" is-active --quiet graphical-session.target)", userName));
  return result.exitCode == 0;
}

// Busy and total jiffies of the aggregate "cpu" line of /proc/stat.
std::optional<std::pair<uint64_t, uint64_t>> ReadCpuTimes() {
  std::ifstream stat("/proc/stat");
  std::string label{};
  uint64_t user{}, nice{}, system{}, idle{}, iowait{}, irq{}, softirq{}, steal{};
  if(!(stat >> label >> user >> nice >> system >> idle >> iowait >> irq >> softirq >> steal) || label != "cpu")
    return {};
  auto total = user + nice + system + idle + iowait + irq + softirq + steal;
  return std::make_pair(total - idle, total);
}

// "Fully loaded" has no signal on Linux: wait for the user's graphical session to be up, then
// for the machine to stay calm (startup apps done loading) for ~1s. Capped by `deadline`.
void WaitUntilDesktopReady(const std::string &userName, Clock::time_point deadline) {
  while(!IsGraphicalSessionUp(userName)) {
    if(Clock::now() >= deadline) {
      spdlog::warn("Desktop of '{}' still not up after {}s; playing the macro anyway.", userName, READY_MAX_WAIT.count());
      return;
    }
    std::this_thread::sleep_for(CALM_SAMPLE_PERIOD);
  }

  auto previous = ReadCpuTimes();
  auto calmSamples = 0;
  while(calmSamples < CALM_SAMPLES_NEEDED) {
    if(Clock::now() >= deadline) {
      spdlog::warn("PC still busy {}s after unlocking; playing the macro anyway.", READY_MAX_WAIT.count());
      return;
    }
    std::this_thread::sleep_for(CALM_SAMPLE_PERIOD);
    auto current = ReadCpuTimes();
    if(!previous.has_value() || !current.has_value()) {
      spdlog::warn("Can't read /proc/stat; skipping the calm-PC wait.");
      return;
    }
    auto busy = static_cast<double>(current->first - previous->first);
    auto total = static_cast<double>(current->second - previous->second);
    calmSamples = (total > 0 && busy / total <= CALM_MAX_BUSY_RATIO) ? calmSamples + 1 : 0;
    previous = current;
  }
}

void ReplayMacrosFor(const std::string &userName, Clock::time_point unlockedAt) {
  for(const auto &device : PairedDevicesStorage::GetDevicesForUser(userName)) {
    if(device.macroStepsJson.empty())
      continue;
    spdlog::info("Session unlock confirmed for '{}': replaying its post-unlock macro once the PC is ready.", device.deviceName);
    auto waitUntilReady = [&] { WaitUntilDesktopReady(userName, unlockedAt + READY_MAX_WAIT); };
    if(!PlatformHelper::PlayMacro(device.macroStepsJson, userName, waitUntilReady))
      spdlog::error("PlayMacro failed for device '{}'.", device.deviceName);
  }
}

bool IsPrepareForSleepSignal(const nlohmann::json &message) {
  return message.value("member", std::string()) == "PrepareForSleep";
}

void HandleMonitorLine(const std::string &line, WatcherState &state) {
  nlohmann::json parsed;
  try {
    parsed = nlohmann::json::parse(line);
  } catch(const std::exception &) {
    return;
  }

  auto now = Clock::now();
  if(IsPrepareForSleepSignal(parsed)) {
    state.lastSleepEvent = now;
    spdlog::info("Login1UnlockWatcher: sleep/resume seen; the unlock that follows won't replay any macro.");
    return;
  }

  auto sessionPath = ExtractSessionPath(parsed);
  if(!sessionPath.has_value())
    return;

  auto locked = IsSessionLocked(sessionPath.value());
  if(!locked.has_value())
    return;

  // Sessions seen at startup are all in the map, so an unknown one here is a fresh login.
  auto isNewSession = !state.sessions.contains(sessionPath.value());
  auto &session = state.sessions[sessionPath.value()];
  if(locked.value()) {
    if(isNewSession || !session.locked) {
      session.locked = true;
      session.lockStart = now;
    }
    return;
  }

  // Only a locked -> unlocked transition (or a fresh login) replays: other property changes
  // of an already unlocked session (IdleHint, Active...) must not.
  auto wasLocked = session.locked;
  session.locked = false;
  if(!wasLocked && !isNewSession)
    return;
  if(wasLocked && state.lastSleepEvent.has_value() && state.lastSleepEvent.value() + SLEEP_LOCK_TOLERANCE >= session.lockStart) {
    spdlog::info("Login1UnlockWatcher: unlock after waking from sleep; not replaying any macro.");
    return;
  }
  if(session.lastReplayed != Clock::time_point{} && now - session.lastReplayed < REPLAY_COOLDOWN)
    return;

  auto userName = ResolveSessionUser(sessionPath.value());
  if(!userName.has_value())
    return;

  session.lastReplayed = now;
  ReplayMacrosFor(userName.value(), now);
}

// Seeds the lock state of the sessions already open when the daemon starts, so that their
// next property change isn't mistaken for a fresh login.
void SeedExistingSessions(WatcherState &state) {
  auto result = Shell::RunCommand(
      R"(busctl --system --json=short call org.freedesktop.login1 /org/freedesktop/login1 org.freedesktop.login1.Manager ListSessions)");
  if(result.exitCode != 0)
    return;
  try {
    // Kept in a variable: a range-for over a member of the parse() temporary would dangle.
    auto sessions = nlohmann::json::parse(result.output);
    for(const auto &entry : sessions["data"][0]) {
      auto sessionPath = entry[4].get<std::string>();
      auto locked = IsSessionLocked(sessionPath);
      auto &session = state.sessions[sessionPath];
      session.locked = locked.value_or(false);
      session.lockStart = Clock::now();
    }
  } catch(const std::exception &ex) {
    spdlog::warn("Login1UnlockWatcher: failed to parse ListSessions: {}", ex.what());
  }
}

}

void Login1UnlockWatcher::Run() {
  WatcherState state{};
  SeedExistingSessions(state);

  while(true) {
    try {
      boost::asio::io_context ctx{};
      boost::asio::readable_pipe outPipe{ctx};
      std::vector<std::string> args{
          "--system",
          "--json=short",
          "monitor",
          "--match=type='signal',interface='org.freedesktop.DBus.Properties',member='PropertiesChanged',"
          "path_namespace='/org/freedesktop/login1/session'",
          "--match=type='signal',interface='org.freedesktop.login1.Manager',member='PrepareForSleep'",
      };
      boost::process::v2::process proc(ctx, BUSCTL_PATH, args, boost::process::v2::process_stdio{{}, outPipe, {}});

      boost::system::error_code ec;
      std::vector<char> charBuffer(4096);
      std::string lineBuffer{};
      while(true) {
        auto n = outPipe.read_some(boost::asio::buffer(charBuffer), ec);
        if(ec)
          break;
        lineBuffer.append(charBuffer.data(), n);
        std::size_t pos{};
        while((pos = lineBuffer.find('\n')) != std::string::npos) {
          std::string line = lineBuffer.substr(0, pos);
          lineBuffer.erase(0, pos + 1);
          if(!line.empty())
            HandleMonitorLine(line, state);
        }
      }
      proc.wait();
      spdlog::warn("Login1UnlockWatcher: busctl monitor exited; respawning in {}s.", RESPAWN_BACKOFF.count());
    } catch(const std::exception &ex) {
      spdlog::error("Login1UnlockWatcher: failed to spawn busctl monitor: {}", ex.what());
    }
    std::this_thread::sleep_for(RESPAWN_BACKOFF);
  }
}
