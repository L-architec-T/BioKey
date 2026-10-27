#include "AppCatalog.h"

#include <algorithm>
#include <fstream>
#include <map>
#include <pwd.h>
#include <spdlog/spdlog.h>

#include "shell/Shell.h"
#include "utils/StringUtils.h"

namespace {

// Larger icons are skipped rather than resized: no image library here, and a 128px PNG is
// already plenty for a phone list row.
constexpr auto MAX_ICON_BYTES = 96 * 1024;

std::filesystem::path HomeOf(const std::string &userName) {
  auto *pw = getpwnam(userName.c_str());
  if(pw == nullptr || pw->pw_dir == nullptr)
    return {};
  return pw->pw_dir;
}

// Lowest priority first: a later directory's entry overrides an earlier one with the same id,
// so the user's own ~/.local/share/applications wins, as in the desktop's own menus.
std::vector<std::filesystem::path> ApplicationDirs(const std::filesystem::path &home) {
  std::vector<std::filesystem::path> dirs{
      "/usr/share/applications",
      "/usr/local/share/applications",
      "/var/lib/snapd/desktop/applications",
      "/var/lib/flatpak/exports/share/applications",
  };
  if(!home.empty()) {
    dirs.push_back(home / ".local/share/flatpak/exports/share/applications");
    dirs.push_back(home / ".local/share/applications");
  }
  return dirs;
}

std::vector<std::filesystem::path> IconRoots(const std::filesystem::path &home) {
  std::vector<std::filesystem::path> roots{};
  if(!home.empty()) {
    roots.push_back(home / ".local/share/icons");
    roots.push_back(home / ".local/share/flatpak/exports/share/icons");
  }
  roots.emplace_back("/usr/share/icons");
  roots.emplace_back("/usr/local/share/icons");
  roots.emplace_back("/var/lib/flatpak/exports/share/icons");
  roots.emplace_back("/var/lib/snapd/desktop/icons");
  return roots;
}

struct DesktopEntry {
  std::string name{};
  std::string icon{};
  bool visible{};
};

// Only the [Desktop Entry] group matters; actions ([Desktop Action ...]) have their own Name keys.
DesktopEntry ParseDesktopFile(const std::filesystem::path &path) {
  std::ifstream file(path);
  std::string line{};
  bool inMainGroup{};
  std::string name{}, nameFr{}, nameFrFr{}, icon{}, type{};
  bool noDisplay{}, hidden{};
  while(std::getline(file, line)) {
    line = StringUtils::Trim(line);
    if(line.empty() || line.starts_with('#'))
      continue;
    if(line.starts_with('[')) {
      inMainGroup = line == "[Desktop Entry]";
      continue;
    }
    if(!inMainGroup)
      continue;
    auto eq = line.find('=');
    if(eq == std::string::npos)
      continue;
    auto key = StringUtils::Trim(line.substr(0, eq));
    auto value = StringUtils::Trim(line.substr(eq + 1));
    if(key == "Name")
      name = value;
    else if(key == "Name[fr]")
      nameFr = value;
    else if(key == "Name[fr_FR]")
      nameFrFr = value;
    else if(key == "Icon")
      icon = value;
    else if(key == "Type")
      type = value;
    else if(key == "NoDisplay")
      noDisplay = value == "true";
    else if(key == "Hidden")
      hidden = value == "true";
  }
  DesktopEntry entry{};
  entry.name = !nameFrFr.empty() ? nameFrFr : !nameFr.empty() ? nameFr : name;
  entry.icon = icon;
  entry.visible = type == "Application" && !noDisplay && !hidden && !entry.name.empty();
  return entry;
}

std::vector<uint8_t> ReadIcon(const std::filesystem::path &path) {
  std::error_code ec;
  if(!std::filesystem::is_regular_file(path, ec))
    return {};
  auto size = std::filesystem::file_size(path, ec);
  if(ec || size == 0 || size > MAX_ICON_BYTES)
    return {};
  return Shell::ReadBytes(path);
}

std::vector<uint8_t> FindIconPng(const std::string &icon, const std::vector<std::filesystem::path> &roots) {
  if(icon.empty())
    return {};
  if(icon.starts_with('/'))
    return icon.ends_with(".png") ? ReadIcon(icon) : std::vector<uint8_t>{};
  if(icon.find('/') != std::string::npos)
    return {};

  // Closest to a phone list row first; the bigger sizes only as a fallback.
  static const std::vector<std::string> sizes{"96x96", "128x128", "64x64", "72x72", "48x48", "256x256"};
  static const std::vector<std::string> themes{"hicolor", "Adwaita"};
  for(const auto &size : sizes) {
    for(const auto &root : roots) {
      for(const auto &theme : themes) {
        auto bytes = ReadIcon(root / theme / size / "apps" / (icon + ".png"));
        if(!bytes.empty())
          return bytes;
      }
    }
  }
  return ReadIcon(std::filesystem::path("/usr/share/pixmaps") / (icon + ".png"));
}

}

bool AppCatalog::IsValidAppId(const std::string &appId) {
  if(appId.size() <= 8 || appId.size() > 255 || !appId.ends_with(".desktop") || appId.starts_with('.'))
    return false;
  return std::ranges::all_of(appId, [](unsigned char c) { return std::isalnum(c) || c == '.' || c == '-' || c == '_' || c == '+'; });
}

std::optional<std::filesystem::path> AppCatalog::FindDesktopFile(const std::string &userName, const std::string &appId) {
  if(!IsValidAppId(appId))
    return {};
  auto dirs = ApplicationDirs(HomeOf(userName));
  std::error_code ec;
  for(auto it = dirs.rbegin(); it != dirs.rend(); ++it) {
    auto path = *it / appId;
    if(std::filesystem::is_regular_file(path, ec))
      return path;
  }
  return {};
}

std::vector<AppCatalog::App> AppCatalog::List(const std::string &userName) {
  auto home = HomeOf(userName);
  std::map<std::string, std::filesystem::path> byId{};
  for(const auto &dir : ApplicationDirs(home)) {
    std::error_code ec;
    for(const auto &entry : std::filesystem::directory_iterator(dir, ec)) {
      auto fileName = entry.path().filename().string();
      if(entry.is_regular_file(ec) && IsValidAppId(fileName))
        byId[fileName] = entry.path();
    }
  }

  auto iconRoots = IconRoots(home);
  std::vector<App> apps{};
  for(const auto &[id, path] : byId) {
    auto desktop = ParseDesktopFile(path);
    if(!desktop.visible)
      continue;
    apps.push_back({id, desktop.name, FindIconPng(desktop.icon, iconRoots)});
  }
  std::ranges::sort(apps, [](const App &a, const App &b) {
    auto lowerA = StringUtils::ToLower(a.name), lowerB = StringUtils::ToLower(b.name);
    return lowerA != lowerB ? lowerA < lowerB : a.id < b.id;
  });
  spdlog::info("App catalog for '{}': {} applications.", userName, apps.size());
  return apps;
}
