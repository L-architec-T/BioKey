# Couleurs

> Source de vérité : `android-foundation/design-system/`. Implémentation de référence : DFlow (`ui/theme/Theme.kt`).

Tokens sémantiques, presets de thème et palette d'accent. Ne jamais écrire un hex dans un écran.

## 2. Color tokens

Semantic tokens, all derived from `AppTheme` state so every screen re-themes automatically:

| Token | Dark default | Light default | Use |
|---|---|---|---|
| `BgPrimary` | `#1C1C1E` | `#FAF7EA` | Screen background |
| `BgSurface` | `#232325` | `#FFFFFF` | Raised surfaces |
| `BgCard` | `#2A2A2D` | `#FFFFFF` | Cards, sheets |
| `BgElevated` | `#333336` | `#F0EBD9` | Highest elevation, thumbnails' empty state |
| `TextPrimary` | `#F5F5F0` | `#1C1A12` | Primary text |
| `TextSecondary` | `#9A9A94` | `#6B6656` | Secondary/meta text |
| `Border` | `#3A3A3E` | `#E3DCC4` | Hairlines, unselected chip/border |
| `Accent` | `AppTheme.accentColor` (default `#FF8C00`, DFlow's orange) | same | Brand color — CTAs, selection state |
| `AccentDim` | `Accent` @ 10% alpha | | Tonal button fill, selected-chip fill |
| `OnAccent` | `#0A0A0A` (dark) / `#1C1A12` (light) | | Text/icons **on top of a solid accent fill** — never plain white |
| `Success` | `#1ED760` | | Positive/"on" state — deliberately distinct from Accent |
| `SuccessDim` | `Success` @ 10% | | "Activer" pill fill |
| `Red` | `#FF4444` | | Destructive/"off"/error |
| `RedDim` | `Red` @ 10% | | "Désactiver" pill fill |
| `Blue` | `#0078D4` | secondary in colorScheme |
| `Orange` | `#FF7A1A` | tertiary in colorScheme |
| `Yellow` | `#FFC107` | spare accent-adjacent color |

Every token has a **custom-theme override**: when `AppTheme.customThemeEnabled` is true, the 7
"custom" fields (`customBgPrimary/Surface/Card/Elevated/TextPrimary/TextSecondary/Border`) replace
the dark/light computed values, independent of the `isDark` flag. This lets a user pick a themed
palette (see the 15 presets below) that isn't just "dark" or "light."

Rule baked into the code: text/icons drawn on a **solid** accent fill always use `OnAccent`
(near-black), never white — kept white only works on the app's own dark/light backgrounds, not on
a saturated accent block.

### 13 bundled full-palette presets (`ThemePresets`, 7 tokens each)
Nuit, Cyberpunk, Matrix, Forêt, Kawaii, Ocean, Sunset, Perle, Lavande, Menthe, Néon, Tropical,
Arcade. **No "Ardoise" / "Jour"**: they only duplicated the default dark/light palettes — the
"Aucun" tile of `CustomThemeDialog` (custom theme off) is the way back to the defaults. Each preset is a `ThemePreset(label,
bgPrimary, bgSurface, bgCard, bgElevated, textPrimary, textSecondary, border)` — port the list
verbatim, or swap in a brand-appropriate subset for a new app.

### Accent color picker palette (15 swatches, `ACCENT_PALETTE`)
`#F5C400 #00D084 #0078D4 #9B59B6 #E91E63 #00BCD4 #F44336 #FF8C00 #4CAF50 #FF6B6B #CCFF00 #FF00E5
#6200FF #000000 #FFFFFF` — shown as a 5-column circle grid with a checkmark on the selected swatch
(contrast-aware: black check on light swatches, white on dark, via `Color.luminance()`).
