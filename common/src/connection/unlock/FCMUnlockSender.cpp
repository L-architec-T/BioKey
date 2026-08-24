#include "FCMUnlockSender.h"

#include <chrono>

#include <nlohmann/json.hpp>
#include <openssl/bio.h>
#include <openssl/evp.h>
#include <openssl/pem.h>
#include <spdlog/spdlog.h>

#define CPPHTTPLIB_OPENSSL_SUPPORT
#include <httplib.h>

#include "shell/Shell.h"
#include "storage/AppSettings.h"

namespace {

constexpr std::string_view SERVICE_ACCOUNT_FILE_NAME = "fcm-service-account.json";
constexpr std::string_view FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
constexpr auto JWT_LIFETIME = std::chrono::seconds(3600);

std::string Base64UrlEncode(const uint8_t *data, size_t len) {
  static constexpr char TABLE[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
  std::string out;
  out.reserve(((len + 2) / 3) * 4);
  size_t i = 0;
  while(i + 2 < len) {
    uint32_t chunk = (data[i] << 16) | (data[i + 1] << 8) | data[i + 2];
    out += TABLE[(chunk >> 18) & 0x3F];
    out += TABLE[(chunk >> 12) & 0x3F];
    out += TABLE[(chunk >> 6) & 0x3F];
    out += TABLE[chunk & 0x3F];
    i += 3;
  }
  if(len - i == 1) {
    uint32_t chunk = data[i] << 16;
    out += TABLE[(chunk >> 18) & 0x3F];
    out += TABLE[(chunk >> 12) & 0x3F];
  } else if(len - i == 2) {
    uint32_t chunk = (data[i] << 16) | (data[i + 1] << 8);
    out += TABLE[(chunk >> 18) & 0x3F];
    out += TABLE[(chunk >> 12) & 0x3F];
    out += TABLE[(chunk >> 6) & 0x3F];
  }
  return out;
}

std::string Base64UrlEncode(const std::string &str) {
  return Base64UrlEncode(reinterpret_cast<const uint8_t *>(str.data()), str.size());
}

}

FCMUnlockSender::FCMUnlockSender() = default;

std::optional<std::string> FCMUnlockSender::LoadServiceAccountKey() {
  if(m_KeyLoaded)
    return m_ClientEmail;
  if(m_KeyLoadFailed)
    return std::nullopt;

  auto path = AppSettings::GetBaseDir() / SERVICE_ACCOUNT_FILE_NAME;
  if(!std::filesystem::exists(path)) {
    spdlog::warn("FCMUnlockSender: no service account key at {}, FCM wake disabled.", path.string());
    m_KeyLoadFailed = true;
    return std::nullopt;
  }

  try {
    auto bytes = Shell::ReadBytes(path);
    auto json = nlohmann::json::parse(std::string(bytes.begin(), bytes.end()));
    m_ProjectId = json.at("project_id").get<std::string>();
    m_ClientEmail = json.at("client_email").get<std::string>();
    m_TokenUri = json.at("token_uri").get<std::string>();
    m_PrivateKeyPem = json.at("private_key").get<std::string>();
  } catch(const std::exception &ex) {
    spdlog::error("FCMUnlockSender: failed to parse service account key: {}", ex.what());
    m_KeyLoadFailed = true;
    return std::nullopt;
  }

  m_KeyLoaded = true;
  return m_ClientEmail;
}

std::optional<std::string> FCMUnlockSender::SignJWT(const std::string &clientEmail, const std::string &tokenUri, const std::string &privateKeyPem) {
  auto now = std::chrono::duration_cast<std::chrono::seconds>(std::chrono::system_clock::now().time_since_epoch()).count();

  nlohmann::json header = {{"alg", "RS256"}, {"typ", "JWT"}};
  nlohmann::json claims = {
      {"iss", clientEmail}, {"scope", FCM_SCOPE}, {"aud", tokenUri}, {"iat", now}, {"exp", now + JWT_LIFETIME.count()},
  };
  auto signingInput = Base64UrlEncode(header.dump()) + "." + Base64UrlEncode(claims.dump());

  BIO *bio = BIO_new_mem_buf(privateKeyPem.data(), static_cast<int>(privateKeyPem.size()));
  if(!bio)
    return std::nullopt;
  EVP_PKEY *pkey = PEM_read_bio_PrivateKey(bio, nullptr, nullptr, nullptr);
  BIO_free(bio);
  if(!pkey) {
    spdlog::error("FCMUnlockSender: failed to parse service account private key.");
    return std::nullopt;
  }

  EVP_MD_CTX *ctx = EVP_MD_CTX_new();
  std::optional<std::string> result;
  if(ctx && EVP_DigestSignInit(ctx, nullptr, EVP_sha256(), nullptr, pkey) == 1) {
    size_t sigLen = 0;
    if(EVP_DigestSign(ctx, nullptr, &sigLen, reinterpret_cast<const uint8_t *>(signingInput.data()), signingInput.size()) == 1) {
      std::vector<uint8_t> sig(sigLen);
      if(EVP_DigestSign(ctx, sig.data(), &sigLen, reinterpret_cast<const uint8_t *>(signingInput.data()), signingInput.size()) == 1) {
        result = signingInput + "." + Base64UrlEncode(sig.data(), sigLen);
      }
    }
  }
  if(ctx)
    EVP_MD_CTX_free(ctx);
  EVP_PKEY_free(pkey);
  return result;
}

std::optional<std::string> FCMUnlockSender::ExchangeJWTForAccessToken(const std::string &jwt, const std::string &tokenUri) {
  httplib::Client client("https://oauth2.googleapis.com");
  client.set_connection_timeout(10, 0);
  client.set_read_timeout(10, 0);
  httplib::Params params{
      {"grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer"},
      {"assertion", jwt},
  };
  auto result = client.Post("/token", params);
  if(!result) {
    spdlog::warn("FCMUnlockSender: token exchange request failed: {}", httplib::to_string(result.error()));
    return std::nullopt;
  }
  if(result->status != 200) {
    spdlog::warn("FCMUnlockSender: token exchange rejected ({}): {}", result->status, result->body);
    return std::nullopt;
  }
  try {
    auto json = nlohmann::json::parse(result->body);
    auto expiresIn = json.value("expires_in", 3600);
    m_CachedTokenExpiry = std::chrono::steady_clock::now() + std::chrono::seconds(expiresIn) - std::chrono::seconds(60);
    return json.at("access_token").get<std::string>();
  } catch(const std::exception &ex) {
    spdlog::error("FCMUnlockSender: malformed token response: {}", ex.what());
    return std::nullopt;
  }
  (void)tokenUri;
}

std::optional<std::string> FCMUnlockSender::GetAccessToken() {
  if(!m_CachedAccessToken.empty() && std::chrono::steady_clock::now() < m_CachedTokenExpiry)
    return m_CachedAccessToken;

  auto jwt = SignJWT(m_ClientEmail, m_TokenUri, m_PrivateKeyPem);
  if(!jwt)
    return std::nullopt;
  auto token = ExchangeJWTForAccessToken(*jwt, m_TokenUri);
  if(!token)
    return std::nullopt;
  m_CachedAccessToken = *token;
  return m_CachedAccessToken;
}

void FCMUnlockSender::SendWake(const std::string &cloudToken, const std::string &deviceId, const std::string &pcbuIP, uint16_t pcbuPort, bool isManual,
                                const std::string &wakeId) {
  if(cloudToken.empty())
    return;

  std::lock_guard lock(m_Mutex);
  if(!LoadServiceAccountKey())
    return;
  auto accessToken = GetAccessToken();
  if(!accessToken) {
    spdlog::warn("FCMUnlockSender: could not obtain an access token, skipping FCM wake for device {}.", deviceId);
    return;
  }

  nlohmann::json message = {
      {"message",
       {{"token", cloudToken},
        {"data",
         {{"deviceId", deviceId},
          {"pcbuIP", pcbuIP},
          {"pcbuPort", std::to_string(pcbuPort)},
          {"isManual", isManual ? "true" : "false"},
          {"wakeId", wakeId}}},
        {"android", {{"priority", "high"}}}}},
  };

  httplib::Client client("https://fcm.googleapis.com");
  client.set_connection_timeout(10, 0);
  client.set_write_timeout(10, 0);
  client.set_read_timeout(10, 0);
  httplib::Headers headers{{"Authorization", "Bearer " + *accessToken}};
  auto result = client.Post(fmt::format("/v1/projects/{}/messages:send", m_ProjectId), headers, message.dump(), "application/json");
  if(!result) {
    spdlog::warn("FCMUnlockSender: send request failed: {}", httplib::to_string(result.error()));
    return;
  }
  if(result->status != 200) {
    spdlog::warn("FCMUnlockSender: send rejected ({}): {}", result->status, result->body);
    return;
  }
  spdlog::info("FCMUnlockSender: wake sent for device {}.", deviceId);
}
