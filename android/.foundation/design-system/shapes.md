# Rayons (shapes)

> Source de vérité : `android-foundation/design-system/`. Implémentation de référence : DFlow (`ui/theme/Theme.kt`).

Échelle de rayons par rôle.

## 3. Border radius scale

No global `Shapes()` is passed to `MaterialTheme` — Material3 defaults apply everywhere except
these explicit overrides, used consistently by role:

| Radius | Where |
|---|---|
| `CircleShape` | avatars/logo fallback dot, switch thumb, color swatches, stat dots |
| `8.dp` | `ProviderLogo` slot, chip borders |
| `9–10.dp` | wallpaper picker thumbnails |
| `12.dp` | grid preview / liquid-glass preview thumbnails, preset swatch |
| `14.dp` | search field, text inputs |
| `16.dp` | default `GlassCard` radius (list rows, standard cards) |
| `20.dp` | category chips, pill switches (or `percent = 50` for a true stadium pill) |
| `28.dp` | dialogs (`SliderSafeDialog`, `GlassAlertDialog`, `LiquidGlassDialog`) |
| `percent = 50` | segmented-control pill (`BlockStatsModeSwitch`), fully-rounded action buttons |

Liquid-glass cards additionally expose a **user-adjustable** corner via the `glassShapeRadius`
slider: `2.dp + (shapeRadiusAmount * 26.dp)`, i.e. 2–28dp, default 0.4 → ~12.4dp.
