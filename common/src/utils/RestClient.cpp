#include "RestClient.h"

#define CPPHTTPLIB_OPENSSL_SUPPORT
#include <httplib.h>
#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>

#include "AppInfo.h"

std::string RestClient::GetLatestVersion() {
  httplib::Client client("https://api.github.com");
  client.set_connection_timeout(5, 0);
  client.set_read_timeout(5, 0);
  client.set_write_timeout(5, 0);
  // The GitHub API rejects requests without a User-Agent.
  httplib::Headers headers{{"User-Agent", fmt::format("BioKey/{}", AppInfo::GetVersion())}, {"Accept", "application/vnd.github+json"}};
  auto result = client.Get(fmt::format("/repos/{}/releases/latest", GITHUB_REPO), headers);
  if(!result)
    throw std::runtime_error(to_string(result.error()));
  if(result->status != 200)
    throw std::runtime_error(fmt::format("GitHub API returned HTTP {}", result->status));
  std::string tag = nlohmann::json::parse(result->body)["tag_name"];
  return tag.starts_with('v') ? tag.substr(1) : tag;
}
