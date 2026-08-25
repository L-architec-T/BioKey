#ifndef PCBU_DESKTOP_APPTHEME_H
#define PCBU_DESKTOP_APPTHEME_H

#include <QColor>
#include <QObject>
#include <QtQmlIntegration>

class AppTheme : public QObject {
  Q_OBJECT
  QML_ELEMENT
  QML_SINGLETON
  Q_PROPERTY(QColor accentColor READ GetAccentColor WRITE SetAccentColor NOTIFY accentColorChanged)
public:
  AppTheme();

  [[nodiscard]] QColor GetAccentColor() const;
  void SetAccentColor(const QColor &color);

signals:
  void accentColorChanged();

private:
  QColor m_AccentColor;
};

#endif
