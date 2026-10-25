#include "UpdaterWindow.h"

#define CPPHTTPLIB_OPENSSL_SUPPORT
#include <httplib.h>
#include <spdlog/spdlog.h>

#include "shell/Shell.h"
#include "utils/AppInfo.h"
#include "utils/RestClient.h"

#ifdef APPLE
#include <mach-o/dyld.h>
#endif

UpdaterWindow::~UpdaterWindow() {
  if(m_CheckThread.joinable())
    m_CheckThread.join();
  if(m_DownloadThread.joinable())
    m_DownloadThread.join();
}

QString UpdaterWindow::GetAppVersion() {
  return QString::fromUtf8(AppInfo::GetVersion());
}

QString UpdaterWindow::GetLatestVersion() {
  std::lock_guard<std::mutex> lock(m_VersionMutex);
  return m_LatestVersion;
}

void UpdaterWindow::CheckForUpdates(QObject *window) {
#ifdef LINUX
  // Only an AppImage can replace itself; a source build or distro package updates its own way.
  if(!std::getenv("APPIMAGE")) {
    spdlog::info("Not running from an AppImage; skipping the update check.");
    return;
  }
#endif
  m_CheckThread = std::thread([this, window]() {
    try {
      auto latestVersion = RestClient::GetLatestVersion();
      m_VersionMutex.lock();
      m_LatestVersion = QString::fromUtf8(latestVersion);
      m_VersionMutex.unlock();
      spdlog::info("Latest version: {}", latestVersion);
      if(AppInfo::CompareVersion(AppInfo::GetVersion(), latestVersion) == 1)
        QMetaObject::invokeMethod(window, "showUpdaterWindow");
    } catch(const std::exception &ex) {
      spdlog::error("Failed checking for updates: {}", ex.what());
      m_VersionMutex.lock();
      m_LatestVersion = {};
      m_VersionMutex.unlock();
    }
  });
}

void UpdaterWindow::OnDownloadClicked(QObject *window) {
  auto latestVersion = GetLatestVersion().toStdString();
  m_DownloadThread = std::thread([window, latestVersion]() {
    std::vector<uint8_t> fileData{};
    auto contentCallback = [&](const char *data, size_t data_length) {
      auto oldSize = fileData.size();
      fileData.resize(oldSize + data_length);
      std::memcpy(fileData.data() + oldSize, data, data_length);
      return true;
    };
    auto progressCallback = [&](uint64_t len, uint64_t total) {
      auto percent = (int)(len * 100 / total);
      auto lenMib = (float)len / 1048576.F;
      auto totalMib = (float)total / 1048576.F;
      auto progressText = fmt::format("{}% ({:.2f}MiB / {:.2f}MiB)", percent, lenMib, totalMib);
      QMetaObject::invokeMethod(window, "updateDownloadProgress", Q_ARG(QVariant, percent), Q_ARG(QVariant, QString::fromUtf8(progressText)));
      return true;
    };

    // GitHub answers with a redirect to its download CDN, hence follow_location.
    httplib::Client client("https://github.com");
    client.set_connection_timeout(5, 0);
    client.set_follow_location(true);
    auto result = client.Get(GetDownloadURL(latestVersion), contentCallback, progressCallback);
    if(!result || fileData.empty()) {
      auto errText = "Error downloading update.";
      spdlog::error(errText);
      QMetaObject::invokeMethod(window, "closeUpdaterWindow", Q_ARG(QVariant, QString::fromUtf8(errText)));
      return;
    }
    auto contentHeader = result->get_header_value("Content-Disposition");
#ifdef WINDOWS
    std::string downloadFileName = "Update-BioKey.exe";
#elif LINUX
    std::string downloadFileName = "Update-BioKey.AppImage";
#elif APPLE
    std::string downloadFileName = "Update-BioKey.dmg";
#endif
    std::regex regex(R"(.*filename=\"(.*)\")");
    std::smatch matches{};
    if(std::regex_search(contentHeader, matches, regex))
      if(matches.size() >= 2)
        downloadFileName = "Update-" + matches[1].str();

    auto dlPath = GetDownloadDirectory() / downloadFileName;
    spdlog::info("Saving update to '{}'...", dlPath.string());
    if(!Shell::WriteBytes(dlPath.string(), fileData)) {
      auto errText = "Error saving update.";
      spdlog::error(errText);
      QMetaObject::invokeMethod(window, "closeUpdaterWindow", Q_ARG(QVariant, QString::fromUtf8(errText)));
      return;
    }
    auto updateCmd = dlPath.string();
#ifdef LINUX
    if(Shell::RunCommand(fmt::format("chmod +x {}", dlPath.string())).exitCode != 0) {
      auto errText = "Error making AppImage executable.";
      spdlog::error(errText);
      QMetaObject::invokeMethod(window, "closeUpdaterWindow", Q_ARG(QVariant, QString::fromUtf8(errText)));
      return;
    }
    auto dstPath = dlPath;
    auto appImagePath = std::getenv("APPIMAGE");
    if(appImagePath)
      dstPath = boost::filesystem::path(appImagePath);
    updateCmd = fmt::format("while ps -p {0} > /dev/null 2>&1; do sleep 1; done && mv {1} {2} && {2}", getpid(), dlPath.string(), dstPath.string());
#endif
    spdlog::info("Starting update...");
    Shell::SpawnCommand(updateCmd);
    QCoreApplication::quit();
  });
}

boost::filesystem::path UpdaterWindow::GetDownloadDirectory() {
#ifdef WINDOWS
  return boost::filesystem::temp_directory_path();
#else
#ifdef LINUX
  auto appImagePath = std::getenv("APPIMAGE");
  if(appImagePath)
    return boost::filesystem::path(appImagePath).parent_path();
#elif APPLE
  char exePath[PATH_MAX];
  uint32_t pathSize = sizeof(exePath);
  if(_NSGetExecutablePath(exePath, &pathSize) == 0) {
    auto resolvedPath = boost::filesystem::canonical(exePath);
    boost::filesystem::path appBundlePath{};
    for(auto it = resolvedPath.begin(); it != resolvedPath.end(); ++it) {
      if(it->string().find(".app") != std::string::npos) {
        appBundlePath = resolvedPath.root_path();
        for(auto jt = resolvedPath.begin(); jt != std::next(it); ++jt)
          appBundlePath /= *jt;
        break;
      }
    }
    if(!appBundlePath.empty())
      return appBundlePath.parent_path();
  }
#endif
  return ".";
#endif
}

// Asset names as published by .github/workflows/release.yml.
std::string UpdaterWindow::GetDownloadURL(const std::string &version) {
  auto arch = AppInfo::GetArchitecture();
#ifdef WINDOWS
  auto asset = fmt::format("BioKey-Setup-{}.exe", arch);
#elif LINUX
  auto asset = fmt::format("BioKey-{}.AppImage", arch);
#elif APPLE
  auto asset = fmt::format("BioKey-{}.dmg", arch);
#endif
  return fmt::format("/{}/releases/download/v{}/{}", RestClient::GITHUB_REPO, version, asset);
}
