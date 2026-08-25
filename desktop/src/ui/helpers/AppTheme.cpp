#include "AppTheme.h"

#include "storage/AppSettings.h"

AppTheme::AppTheme() {
  auto accentColor = AppSettings::Get().accentColor;
  m_AccentColor = QColor(accentColor.empty() ? QStringLiteral("#F0B400") : QString::fromStdString(accentColor));
}

QColor AppTheme::GetAccentColor() const {
  return m_AccentColor;
}

void AppTheme::SetAccentColor(const QColor &color) {
  if(m_AccentColor == color)
    return;
  m_AccentColor = color;
  emit accentColorChanged();
}
