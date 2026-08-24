#ifndef PCBU_DESKTOP_FCMUNLOCKSENDER_H
#define PCBU_DESKTOP_FCMUNLOCKSENDER_H

#include <chrono>
#include <cstdint>
#include <mutex>
#include <optional>
#include <string>

class FCMUnlockSender {
public:
  FCMUnlockSender();

  void SendWake(const std::string &cloudToken, const std::string &deviceId, const std::string &pcbuIP, uint16_t pcbuPort, bool isManual,
                const std::string &wakeId);

private:
  std::optional<std::string> LoadServiceAccountKey();
  std::optional<std::string> GetAccessToken();
  std::optional<std::string> SignJWT(const std::string &clientEmail, const std::string &tokenUri, const std::string &privateKeyPem);
  std::optional<std::string> ExchangeJWTForAccessToken(const std::string &jwt, const std::string &tokenUri);

  std::mutex m_Mutex{};
  std::string m_ProjectId{};
  std::string m_ClientEmail{};
  std::string m_TokenUri{};
  std::string m_PrivateKeyPem{};
  bool m_KeyLoaded = false;
  bool m_KeyLoadFailed = false;

  std::string m_CachedAccessToken{};
  std::chrono::steady_clock::time_point m_CachedTokenExpiry{};
};

#endif
