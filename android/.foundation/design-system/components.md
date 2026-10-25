# Composants

> Source de vérité : `android-foundation/design-system/`. Implémentation de référence : DFlow (`ui/theme/Theme.kt`).

Boutons, fondus de scroll, carte de stats, icônes, panneau « Apparence », primitives.

## 4. Button style

No filled `Button` anywhere in the app — every action button is a **tonal-outline pill**:
`containerColor = <Color>Dim, contentColor = <Color>, border = BorderStroke(1.dp, <Color>)`, where
`<Color>` is `Accent` / `Success` / `Red` depending on the action's semantics (neutral CTA / go /
stop). Example (`CurrentDnsCard` in `HomeScreen.kt`):

```kotlin
Button(
    onClick = onToggleVpn,
    colors = ButtonDefaults.buttonColors(containerColor = SuccessDim, contentColor = Success),
    border = BorderStroke(1.dp, Success),
) { Text("Activer") }
```

Same recipe for destructive (`RedDim`/`Red`) and neutral (`AccentDim`/`Accent`) actions. `Switch`
components use `SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = AccentDim)`
everywhere, never the M3 default purple.


## 6. Scroll edge fade (disparition en fondu haut/bas/gauche/droite)

Any scrollable list (`LazyColumn`/`LazyRow`) that can run past its visible bounds gets a soft
fade at its edges instead of a hard visual cutoff — a purely cosmetic hint that there's more
content, **not** tied to actual scroll position or list exhaustion (it fades the same whether
you're at the top/start of the list or not). Two modifiers, each covering **one axis, both
opposite edges at once** — there is no separate top-only/bottom-only variant, one call handles
both:

```kotlin
// Theme.kt — covers top + bottom in one call
fun Modifier.verticalEdgeFade(edgeSize: Dp = 14.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadePx = edgeSize.toPx()
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fadePx),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fadePx, endY = size.height),
            blendMode = BlendMode.DstIn,
        )
    }

// covers left + right in one call, same recipe with horizontalGradient/startX/endX
fun Modifier.horizontalEdgeFade(edgeSize: Dp = 14.dp): Modifier = ...
```

How it works: `CompositingStrategy.Offscreen` renders the content (the list) to an offscreen
layer first, so the two `DstIn` gradient rects that follow mask it **by alpha** (transparent →
opaque black → content fully visible, opaque black → transparent → content fades out) instead of
painting over it. Default `edgeSize = 14.dp` for both axes; pass a bigger value if a list sits on
a busier background and needs a more visible fade.

Usage — pick the modifier that matches the list's scroll axis, applied to the `LazyColumn`/
`LazyRow` itself (not a wrapping `Box`):

| Screen | List | Modifier | Fades |
|---|---|---|---|
| `CatalogScreen` | `LazyRow` of category filter chips | `.horizontalEdgeFade()` | left + right |
| `CatalogScreen` | `LazyColumn` of DNS results | `.verticalEdgeFade()` | top + bottom |
| `HomeScreen` | main vertical content column | `.verticalEdgeFade()` | top + bottom |
| `DnsDetailScreen` | vertical detail content | `.verticalEdgeFade()` | top + bottom |

**Padding rule (mandatory):** the fade masks the list's own edges, *including its content
padding*. So the padding on the faded axis must be **≥ `edgeSize`** (14dp by default → use
16dp), otherwise the first/last item is visibly cut by the fade even when the list is at rest
(bug seen on BioKey: stats card under the top bar with `top = 8.dp`). Same for the horizontal
modifier with `start`/`end`. Put the spacing in `contentPadding`, never in a `.padding()` on the
modifier chain *before* the fade (that padding sits outside the mask and doesn't help):

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize().verticalEdgeFade(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), // ≥ 14dp
)
```

If a larger `edgeSize` is passed, raise the padding accordingly.

Rule of thumb for a new app: every scrollable `Lazy*` that isn't already framed by an opaque
header/footer bar (which would make the cutoff a non-issue) should carry the matching edge-fade
modifier — vertical lists get `verticalEdgeFade()`, horizontal filter rows get
`horizontalEdgeFade()`. Both compose fine with `GlassCard`/liquid-glass content behind them since
they only mask alpha, not color.


## 7. Stats container (`BlockStatsCard`)

The dashboard stat block (`HomeScreen.kt`) is the reference pattern for **any** "card full of
small numeric tiles" — reuse this shape whenever a new app needs a stats/summary card:

```kotlin
GlassCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
        BlockStatsModeSwitch(showAllTime, onModeChange, Modifier.fillMaxWidth().height(32.dp))
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile(DnsCategory.ANTI_AD, "Pubs", stats.ads, Modifier.weight(1f))
            StatTile(DnsCategory.ANTI_TRACKING, "Trackers", stats.trackers, Modifier.weight(1f))
            StatTile(DnsCategory.ANTI_PHISHING, "Phishing", stats.phishing, Modifier.weight(1f))
            StatTile(DnsCategory.ANTI_MALWARE, "Malwares", stats.malware, Modifier.weight(1f))
        }
    }
}
```

Structure, outer → inner:
- **Outer shell**: `GlassCard` (§5 — same card primitive as every other surface in the
  app, `fillMaxWidth`, default 16dp radius), inner `Column` padded 18dp on all sides.
- **Mode switch**: `BlockStatsModeSwitch` — the pill segmented-control from §3 (`percent = 50`
  shape, sliding indicator drawn with `Modifier.glassPill(pillBlurSource())` — see « Pastilles en
  verre » below —, `Border.copy(alpha = 0.4f)` track, no ripple on the tap
  targets since the sliding indicator already communicates selection). Used here to flip between
  "current session" and "all-time" stats without a second card. Height locked to 32dp; a fixed
  14dp `Spacer` separates it from the tile row below.
- **Tile row**: a single `Row` with `Arrangement.SpaceBetween`, each `StatTile` given `weight(1f)`
  so N tiles always split the width evenly regardless of count — adding/removing a tile needs no
  layout changes.
- **`StatTile`** (the atomic unit — reuse standalone for any single-metric display, not just in a
  row): a `Column`, `horizontalAlignment = CenterHorizontally`, three stacked elements —
  1. Icon (26dp, `tint = Accent`) — here `CategoryIcon`, but any icon works.
  2. Count — `headlineSmall` **Bold**, color `Accent`. The number is always the loudest element
     in the tile, always in the brand accent color, never `TextPrimary`.
  3. Label — `labelSmall`, `TextSecondary`, `textAlign = TextAlign.Center` (wraps under a 1-word
     icon-caption without breaking the column's centering).

Rule for new apps: a stat/metric always renders as icon-on-top + big-bold-accent-number-middle +
small-secondary-label-bottom, inside an evenly `weight`-split `Row`, inside a `GlassCard`. Don't
invent a new stat-tile layout per screen — reuse `StatTile` verbatim and just change the icon/
count/label inputs.


## 8. Icon library

Two tiers, no third-party icon pack:

**1. Material Icons Extended (primary tier, ~99% of icons in the app)** — Google's official
Apache-2.0 vector icon set, pulled in as a single Gradle dependency, zero extra assets:
```
implementation 'androidx.compose.material3:material3'
implementation 'androidx.compose.material:material-icons-extended'
```
Referenced as `Icons.Filled.<Name>` (e.g. `Icons.Filled.Dns`, `Icons.Filled.Star`,
`Icons.Filled.ChevronRight`) and drawn with `Icon(imageVector = ..., tint = <semantic color
token>)` — **always** pass one of the semantic tokens (§2) as `tint`, never a raw color, so every
icon re-themes with the rest of the UI automatically. This is the default choice for any new icon
need in a new app — reach for `material-icons-extended` before adding any other icon dependency.

**2. Hand-authored vector drawables (fallback tier, only when Material Icons has no equivalent)**
— plain `res/drawable/*.xml` `<vector>` XML (Android's native SVG-like format, not an actual
`.svg` file), loaded via `painterResource(id)` instead of `Icon(imageVector = ...)`. Used in this
app only for the 2 country flags Material Icons doesn't cover:

```xml
<!-- ic_flag_france.xml — flat 24x24 viewport, 3 solid-color path bands -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#0055A4" android:pathData="M0,0h8v24h-8z" />
    <path android:fillColor="#FFFFFF" android:pathData="M8,0h8v24h-8z" />
    <path android:fillColor="#EF4135" android:pathData="M16,0h8v24h-8z" />
</vector>
```

Dispatch pattern (`CategoryIcon.kt`) — a small per-domain enum-to-icon mapper function that
returns `ImageVector?`, `null` meaning "fall through to the hand-drawn drawable":
```kotlin
private fun DnsCategory.imageVector(): ImageVector? = when (this) {
    DnsCategory.ANTI_AD -> Icons.Filled.Block
    // ...
    DnsCategory.FRANCE, DnsCategory.EUROPE -> null   // no Material icon exists for these
}
private fun DnsCategory.flagDrawableRes(): Int? = when (this) {
    DnsCategory.FRANCE -> R.drawable.ic_flag_france
    else -> null
}
@Composable
fun CategoryIcon(category: DnsCategory, modifier: Modifier = Modifier, tint: Color) {
    val vector = category.imageVector()
    if (vector != null) {
        Icon(imageVector = vector, contentDescription = category.label, tint = tint, modifier = modifier)
    } else {
        // tint intentionally ignored here — a flag's own colors ARE its identity
        Icon(painter = painterResource(category.flagDrawableRes()!!), contentDescription = category.label, tint = Color.Unspecified, modifier = modifier)
    }
}
```
Key rule carried over to any new app: a hand-authored drawable whose colors are semantically fixed
(flags, brand logos) is drawn with `tint = Color.Unspecified` to keep its own colors — every other
icon takes the surrounding semantic tint.

**Bitmap logos (separate from the icon system, not SVG)**: provider/brand logos (`logo_google.png`,
`logo_cloudflare.png`, …) are raster PNGs in `res/drawable-nodpi/`, shown via `ProviderLogo` —
40dp slot, `RoundedCornerShape(8dp)`, `BgElevated` fallback background, falls back to a generic
`Icons.Filled.Dns` glyph tinted `TextSecondary` when a provider has no bundled artwork). Use this
tier only for real third-party brand marks that can't be a vector icon; never rasterize an icon
that Material Icons Extended already provides.


## 9. "Apparence" settings panel (`ConfigDialog.kt`)

One dialog, opened from a header icon button, with this exact row structure — port the whole
pattern, not just the colors:

1. **Thème clair** — `Switch` toggling `AppTheme.isDark`; label under it echoes the current mode
   ("Mode jour/nuit activé").
2. **Couleur d'accent** — row with a live color-dot preview → opens `AccentColorPickerDialog`
   (5-col circle grid, §2).
3. **Fond d'écran** — row showing current wallpaper label → opens `WallpaperPickerDialog`: 3-col
   grid of "Aucun" / bundled wallpapers (with thumb assets, full-res assets loaded separately) /
   custom upload tile (image picker → downscaled to 1600px longest side, JPEG 85, saved to app
   files dir, persisted as a path).
4. **Personnaliser les couleurs** — opens `CustomThemeDialog`: a single horizontal-scroll row
   (« Thèmes prédéfinis ») = an **« Aucun »** tile first (`Icons.Filled.Block`, sets
   `customThemeEnabled = false` → back to the default palette; outlined `Accent` while no custom
   theme is on) followed by the preset swatches (tap = apply all 7 tokens at once, flips
   `customThemeEnabled = true`; a preset shows as selected only while the custom theme is on).
   **Nothing else in this dialog**: no "Ardoise"/"Jour" presets, no « Personnaliser chaque
   couleur » per-token list, no `HexColorPickerDialog`.
5. **Effet verre** — row showing Activé/Désactivé → opens `LiquidGlassDialog`: enable `Switch`
   (auto-closes the dialog if turned off), a horizontal-scroll row of preview-backdrop chips
   ("Grille" synthetic checkerboard + each bundled wallpaper thumb), a **draggable** live preview
   card the user can drag around the chosen backdrop to see the effect against different areas,
   then the blur wheel-slider (see `WheelPicker.kt` for the snap-to-step horizontal picker used
   here and for all percent-style controls).
6. **RÉINITIALISER** button (bottom-left, secondary text color) — confirms via `GlassAlertDialog`,
   then resets every single field above to its documented default in one shot.
7. **FERMER** button (bottom-right, accent color).

Dialog chrome itself: `SliderSafeDialog`/`GlassAlertDialog` are hand-rolled `Dialog { GlassCard {
Column { ... } } }` wrappers, specifically **not** M3's `AlertDialog` — M3 wraps its `text` slot in
a scrollable container that steals a held drag gesture mid-swipe (confirmed on-device), which
breaks any slider/wheel picker living inside. Reuse this wrapper pattern in any new app that needs
a slider inside a dialog.


## 11. Misc reusable primitives (all in `Theme.kt`)

- `HeaderBlurBackground` — lightweight frosted strip for a screen header (native
  `RenderEffect.createBlurEffect`, API 31+ only, silently no-ops below), independent of the full
  liquid-glass engine — use this instead of `GlassCard` when you just want a subtle blurred header
  band with no interactive sliders behind it.
- System nav bar is kept in lockstep with `BgPrimary` and light/dark icon mode via
  `WindowCompat.getInsetsController(...).isAppearanceLightNavigationBars`, set in a `SideEffect`
  inside `DFlowTheme` — always do this so the OS nav bar doesn't clash with the themed background.

## Barre du haut + menu (`components/GlassTopBarMenu.kt`)

Extraits de CallUp, copiés par le script `app` dans `ui/components/` de chaque nouvelle app.

- `AppTopBar(name, menuOpen, onMenuToggle)` : logo texte en majuscules (1re lettre `TextPrimary`,
  2e en `Accent`, reste `TextPrimary`, `headlineSmall` Bold) + bouton ☰ qui devient ✕ quand le
  menu est ouvert. Padding 16.dp horizontal / 8.dp vertical. À passer en `Scaffold(topBar = …)`.
- `GlassMenuPanel(entries, onDismiss)` : le menu est une **page pleine** (pas un petit dropdown)
  affichée à la place de l'onglet courant ; les deux barres restent visibles. Une `GlassCard` avec
  des lignes `MenuEntry(icon, title, subtitle, onClick)` séparées par `HorizontalDivider(Border)`,
  icône en `Accent`, chevron en `TextSecondary`. Choisir une entrée ferme le menu puis lance
  l'action ; Retour ferme le menu. Changer d'onglet dans la barre du bas ferme aussi le menu.

```kotlin
var menuOpen by rememberSaveable { mutableStateOf(false) }
Scaffold(
    topBar = { AppTopBar("MonApp", menuOpen, onMenuToggle = { menuOpen = !menuOpen }) },
    bottomBar = { GlassBottomNavBar(tabs, tab, onSelect = { tab = it; menuOpen = false }) },
) { padding ->
    if (menuOpen) GlassMenuPanel(
        modifier = Modifier.padding(padding),
        onDismiss = { menuOpen = false },
        entries = listOf(
            MenuEntry(Icons.Filled.Palette, "Thèmes et apparence", "Couleurs, fond d'écran, effet verre") { showAppearance = true },
        ),
    ) else { /* onglet courant */ }
}
```

- `AppTopBar(..., actions = { … })` : icônes optionnelles juste avant le bouton menu (ex. cloche de Donova).
- **Verre synchronisé** (1.12.0) : verre activé + fond d'écran actif → la barre est une bande de verre
  pleine largeur (`liquidGlassCard(LocalScreenBackdrop, topRadius = 0, bottomRadius = 0)`) pilotée par
  **les 8 curseurs Apparence, sans aucun override** — exactement comme les cartes. Verre désactivé :
  aucun fond. Calque frère `matchParentSize()`, jamais un modifier sur la `Row` (le contenu reste net).
  `glass = false` quand l'appelant dessine déjà un verre autour (Donova : l'en-tête, qui contient aussi
  la barre de sélection, porte le verre, lui aussi sans override).

## Plein écran + notification permanente (`components/SystemPrompts.kt`)

`SystemPromptsHost(persistentChannelId = null, enabled = true)`, placé une fois dans le thème juste
après `AutoUpdateHost()` (le script `app` l'ajoute) :
- applique `FullscreenMode` à la fenêtre (barres système masquées, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`,
  portrait) et le réapplique quand la fenêtre reprend le focus (dialog, clavier) ;
- 1,5 s après le lancement, **propose une seule fois** : « Plein écran » (ACTIVER / NON MERCI), puis,
  si `persistentChannelId` est donné et que cette notification est visible, « Notification
  permanente » (MASQUER → réglages système du canal : le service tourne toujours, seule la
  notification disparaît). Réponses stockées dans les prefs `foundation_system`.
- **Marges du contenu racine** : `Modifier.appContentPadding()` (même fichier) à la place de
  `safeDrawingPadding()` sur la `Surface` racine de `MainActivity`. Hors plein écran = zone sûre
  complète ; en plein écran = clavier seulement, et la fenêtre passe en
  `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` : le contenu remonte jusqu'en haut de l'écran au lieu de
  laisser une bande vide à la hauteur de l'encoche / du trou de caméra (bug corrigé en 1.14.0).
- Menu : `fullscreenMenuEntry(context)` (bascule) et `persistentNotificationMenuEntry(context, channelId)`
  à ajouter à la liste de `GlassMenuPanel` pour pouvoir changer d'avis.
- Apps avec service au premier plan : CallUp (`KeepAliveService.CHANNEL_ID`), DFLOW
  (`DFlowVpnService.CHANNEL_ID`), BioKey (`UnlockForegroundService.CHANNEL_ID`). Le canal est l'unique
  canal du service : ne jamais y publier d'autres notifications (elles seraient masquées avec).

## Bouton + flottant (`components/GlassFab.kt`)

Repris de CallUp **en corrigeant son défaut** : dans CallUp le bouton reposait sur un disque opaque
`BgCard`, il n'était donc jamais transparent. `GlassFab` a un fond **transparent et flouté** : il
affiche une copie floutée (12.dp) de ce qui passe derrière lui — le contenu de l'écran, plus le fond
d'écran quand le verre est activé — sous un voile `Accent` 12 % et un contour `Accent` 50 %,
icône `Accent`, 56.dp, marge 16.dp.

Le contenu qui défile sous le bouton doit être enregistré avec `rememberGlassFabBackdrop()` :

```kotlin
val fabBackdrop = rememberGlassFabBackdrop()
Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().layerBackdrop(fabBackdrop)) { /* … */ }   // le contenu, PAS le parent du bouton
    GlassFab(
        onClick = onAdd,
        icon = Icons.Filled.Add,
        contentDescription = "Ajouter",
        backdrop = fabBackdrop,
        modifier = Modifier.align(Alignment.BottomEnd),
    )
}
```

Sans fond d'écran, l'enregistrement commence par `BgPrimary`, pour que le flou soit opaque et
qu'aucun contenu net ne transparaisse sur les bords du bouton. Flou réel à partir d'Android 12
(API 31) ; en dessous, la bibliothèque n'applique pas de flou.

Le rendu est exposé seul par `Modifier.glassFabSurface(backdrop, shape, borderColor)` (même fichier),
utilisé par le bouton lui-même (contour `Accent` 50 %, par défaut) et par les cards quand un fond
d'écran est affiché avec le verre désactivé (sans contour : `Color.Transparent`, voir `liquid-glass.md` §5.4). À poser sur un calque de fond (`Modifier.matchParentSize()` derrière
le contenu), jamais sur le nœud qui porte le texte : le flou s'appliquerait aussi au texte.

## Pastilles en verre (`components/GlassPill.kt`)

Toute petite pastille « chiffre » ou indicateur (badge de compte d'une section, badge d'activité
« +N », indicateur glissant d'un sélecteur segmenté) a **le même rendu que `GlassFab`** : copie
floutée (12.dp) de ce qui est derrière, voile `tint` 12 %, liseré lumineux de `drawBackdrop`,
contour `tint` 50 %. Un seul modifier :

```kotlin
Box(Modifier.glassPill(pillBlurSource())) { Text("$count", color = Accent, …) }          // Accent
Box(Modifier.glassPill(pillBlurSource(), tint = Yellow)) { Text("+$n", color = Yellow, …) } // autre teinte
```

- Toujours dessiné via `drawBackdrop` (`emptyBackdrop()` quand `blurSource` est `null`) : toutes
  les pastilles ont le même liseré, qu'elles aient quelque chose à flouter ou non. Ne jamais
  retomber sur un simple `background` + `border` pour « le cas sans verre » : le liseré
  apparaîtrait/disparaîtrait d'une pastille à l'autre.
- `pillBlurSource()` = le fond d'écran s'il est actif, sinon `null`. C'est la source par défaut.
- **Interdit** : passer comme `blurSource` un backdrop qui enregistre la pastille elle-même (ex. le
  `fabBackdrop` d'une `LazyColumn` pour un badge situé dans cette liste) → le calque se dessine
  dans lui-même. Une pastille **hors** du contenu enregistré (ex. copie épinglée ci-dessous) peut
  flouter la liste, comme `GlassFab`.

## En-tête de section collant à fondu

Référence : Donova, `ui/HomeScreen.kt` (`PinnedHeaderOverlay` + `fadeBelowPinnedHeader`). Pour un
`stickyHeader` dont le contenu défile dessous (1.12.0 : **fondu**, plus de flou) :

- L'en-tête reste **transparent** dans la liste (aucun fond : un fond plein ou verre se lit comme
  un rectangle collé).
- La **liste elle-même est masquée** (`graphicsLayer(Offscreen)` + `drawWithContent` + dégradé
  `DstIn`) : transparente du haut jusqu'au bas de l'en-tête épinglé, puis retour à l'opaque sur
  **14.dp**. Les lignes disparaissent avant d'atteindre le texte de l'en-tête ; le fond d'écran (ou
  `BgPrimary`) derrière la liste reste intact. Le masque est placé **après** `layerBackdrop(fabBackdrop)`
  (le `GlassFab` échantillonne la liste déjà fondue) et **avant** le `padding` de la liste (y = 0 du
  masque = haut du nœud, d'où `padding + offset + hauteur` de l'en-tête).
- Au-dessus, une copie non interactive de l'en-tête (`interactive = false` : aucun `pointerInput`,
  les gestes passent à la liste), badge en `glassPill(pillBlurSource())`.
- En-tête épinglé = celui des `listState.layoutInfo.visibleItemsInfo` (clés `section_*`) d'index
  ≤ `firstVisibleItemIndex`, avec son `offset` (négatif quand l'en-tête suivant le pousse) et sa
  hauteur ; rien tant que la liste n'a pas défilé. La copie dans la liste passe à `alpha(0f)`
  pendant ce temps (elle reste cliquable).
- La copie est **découpée au viewport de la liste** (`matchParentSize().padding(<même padding que
  la LazyColumn>).clipToBounds()`) : poussée vers le haut, elle glisse sous le bord de la liste et ne
  passe **jamais** par-dessus la carte de stats (ou tout autre élément) au-dessus.
