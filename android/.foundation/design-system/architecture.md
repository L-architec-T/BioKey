# Architecture du thème + checklist de portage

> Source de vérité : `android-foundation/design-system/`. Implémentation de référence : DFlow (`ui/theme/Theme.kt`).



## 1. Architecture: single mutable source of truth

`object AppTheme` (`Theme.kt`) holds every visual setting as `mutableStateOf` — no ViewModel, no
CompositionLocal. Any composable reads e.g. `AppTheme.accentColor` directly and recomposes on
change. `AppearancePreferences` (DataStore) mirrors each field for persistence across launches;
every setter in `ConfigDialog` writes to both (`AppTheme.x = v` then
`scope.launch(Dispatchers.IO) { prefs.setX(v) }`). Semantic color tokens are top-level
`val X: Color get() = ...` properties computed from `AppTheme` state (see §2) — never hardcode a
hex in a screen, always reference the token.


## 12. Porting checklist for a new app

1. Copy `Theme.kt`, `ConfigDialog.kt`, `WheelPicker.kt`, `AppearancePreferences.kt` verbatim (from
   `/home/larchitect/Mes Apps/DFLOW/src/main/java/com/security/dflow/`).
2. Add the two `kyant0` Gradle dependencies.
3. Change `AppTheme.accentColor`'s default and the reset target in `ConfigDialog` to the new app's
   brand color; swap `AppWallpapers.ALL` for the new app's own wallpaper drawables (or ship none —
   `GlassCard` degrades to flat color automatically).
4. Keep all 15 `ThemePresets`/`ACCENT_PALETTE`/`ThemePresetColors` as-is — they're app-agnostic.
5. Everywhere in the new app's screens: import the semantic tokens (`BgPrimary`, `Accent`, etc.),
   never a raw hex; use the tonal-outline `Button` recipe (§4) for all actions; wrap list rows/
   cards in `GlassCard` instead of a plain `Card`; apply `verticalEdgeFade()`/`horizontalEdgeFade()`
   (§6) to every scrollable `Lazy*` not already framed by an opaque bar.
