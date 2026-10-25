#ifndef PCBU_DESKTOP_RESTCLIENT_H
#define PCBU_DESKTOP_RESTCLIENT_H

#include <cstdint>
#include <string>

class RestClient {
public:
  // Releases (desktop + Android) are published together on this repo by .github/workflows/release.yml.
  static constexpr const char *GITHUB_REPO = "L-architec-T/BioKey";

  // Version of the latest GitHub release ("v0.2.0" -> "0.2.0").
  static std::string GetLatestVersion();

private:
  RestClient() = default;
};

#endif
