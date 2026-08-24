#include "Login1UnlockWatcher.h"

#include <boost/asio.hpp>
#include <boost/process/v2/process.hpp>
#include <boost/process/v2/stdio.hpp>
#include <chrono>
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

bool IsSessionUnlocked(const std::string &sessionPath) {
  auto result = Shell::RunCommand(
      fmt::format(R"(busctl --system --json=short get-property org.freedesktop.login1 "{}" org.freedesktop.login1.Session LockedHint)", sessionPath));
  if(result.exitCode != 0)
    return false;
  try {
    return !nlohmann::json::parse(result.output)["data"].get<bool>();
  } catch(const std::exception &ex) {
    spdlog::warn("Login1UnlockWatcher: failed to parse LockedHint for '{}': {}", sessionPath, ex.what());
    return false;
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

void ReplayMacrosFor(const std::string &userName) {
  for(const auto &device : PairedDevicesStorage::GetDevicesForUser(userName)) {
    if(device.macroStepsJson.empty())
      continue;
    spdlog::info("Session unlock confirmed for '{}': replaying its post-unlock macro.", device.deviceName);
    if(!PlatformHelper::PlayMacro(device.macroStepsJson))
      spdlog::error("PlayMacro failed for device '{}'.", device.deviceName);
  }
}

void HandleMonitorLine(const std::string &line, std::unordered_map<std::string, std::chrono::steady_clock::time_point> &lastReplayed) {
  nlohmann::json parsed;
  try {
    parsed = nlohmann::json::parse(line);
  } catch(const std::exception &) {
    return;
  }

  auto sessionPath = ExtractSessionPath(parsed);
  if(!sessionPath.has_value())
    return;

  auto now = std::chrono::steady_clock::now();
  auto it = lastReplayed.find(sessionPath.value());
  if(it != lastReplayed.end() && now - it->second < REPLAY_COOLDOWN)
    return;

  if(!IsSessionUnlocked(sessionPath.value()))
    return;

  auto userName = ResolveSessionUser(sessionPath.value());
  if(!userName.has_value())
    return;

  lastReplayed[sessionPath.value()] = now;
  ReplayMacrosFor(userName.value());
}

}

void Login1UnlockWatcher::Run() {
  std::unordered_map<std::string, std::chrono::steady_clock::time_point> lastReplayed{};

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
            HandleMonitorLine(line, lastReplayed);
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
