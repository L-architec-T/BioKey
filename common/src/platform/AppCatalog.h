#ifndef PCBU_DESKTOP_APPCATALOG_H
#define PCBU_DESKTOP_APPCATALOG_H

#include <cstdint>
#include <filesystem>
#include <optional>
#include <string>
#include <vector>

// The applications a user can launch, read from the freedesktop .desktop entries (system,
// Flatpak, Snap and the user's own) — what the macro editor's "Ouvrir une application" step
// offers. Linux only, like the rest of the macro feature.
class AppCatalog {
public:
  struct App {
    std::string id{};   // desktop file name, e.g. "org.gnome.Nautilus.desktop"
    std::string name{}; // localized display name (French first)
    std::vector<uint8_t> iconPng{}; // empty when no PNG icon was found (SVG-only themes)
  };

  // Sorted alphabetically by name, case-insensitively.
  static std::vector<App> List(const std::string &userName);

  // Where `appId` lives for this user right now, if it's still installed.
  static std::optional<std::filesystem::path> FindDesktopFile(const std::string &userName, const std::string &appId);

  // A bare "*.desktop" file name: no path separators, nothing a shell would interpret.
  static bool IsValidAppId(const std::string &appId);

private:
  AppCatalog() = default;
};

#endif
