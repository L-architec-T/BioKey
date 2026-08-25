#include <QGuiApplication>
#include <QIcon>
#include <QQmlApplicationEngine>
#include <exception>

#include "storage/AppSettings.h"
#include "storage/LoggingSystem.h"

int main(int argc, char *argv[]) {
  std::set_terminate([]() {
    spdlog::critical("Uncaught exception - application is terminating.");
    if(auto ex = std::current_exception()) {
      try {
        std::rethrow_exception(ex);
      } catch(const std::exception &e) {
        spdlog::critical("Reason: {}", e.what());
      } catch(...) {
        spdlog::critical("Reason: unknown exception type.");
      }
    }
    LoggingSystem::Destroy();
    std::abort();
  });

  auto accentColor = AppSettings::Get().accentColor;
  if(accentColor.empty())
    accentColor = "#F0B400";

  qputenv("QT_QUICK_CONTROLS_STYLE", QByteArray("Material"));
  qputenv("QT_QUICK_CONTROLS_MATERIAL_THEME", QByteArray("Dark"));
  qputenv("QT_QUICK_CONTROLS_MATERIAL_VARIANT", QByteArray("Dense"));
  qputenv("QT_QUICK_CONTROLS_MATERIAL_PRIMARY", QByteArray("Amber"));
  qputenv("QT_QUICK_CONTROLS_MATERIAL_ACCENT", QByteArray::fromStdString(accentColor));
  LoggingSystem::Init("desktop");

  QGuiApplication app(argc, argv);
  QGuiApplication::setWindowIcon(QIcon(":/res/icons/icon.png"));

  auto url = QUrl("qrc:/ui/MainWindow.qml");
  QQmlApplicationEngine engine{};
  QObject::connect(
      &engine, &QQmlApplicationEngine::objectCreated, &app,
      [url](QObject *obj, const QUrl &objUrl) {
        if(!obj && url == objUrl) {
          QCoreApplication::exit(-1);
        }
      },
      Qt::QueuedConnection);
  engine.load(url);

  auto result = QGuiApplication::exec();
  LoggingSystem::Destroy();
  return result;
}
