# Design system commun

Stack : Jetpack Compose + Material3, moteur Liquid Glass `io.github.kyant0:backdrop`.
Implémentation de référence : DFlow (`ui/theme/Theme.kt`, `ui/appearance/ConfigDialog.kt`,
`ui/appearance/WheelPicker.kt`, `data/prefs/AppearancePreferences.kt`).

| Fichier | Contenu |
|---|---|
| [architecture.md](architecture.md) | `object AppTheme` (source unique d'état visuel) + checklist de portage |
| [colors.md](colors.md) | Tokens sémantiques, 15 presets, palette d'accent |
| [typography.md](typography.md) | Échelle typographique et règles |
| [spacing.md](spacing.md) | Grille 4 dp et marges |
| [shapes.md](shapes.md) | Rayons par rôle |
| [components.md](components.md) | Boutons tonal-outline, edge fade, stats, icônes, panneau Apparence, dialogs |
| [liquid-glass.md](liquid-glass.md) | Moteur de verre, 8 réglages, backdrop par écran, tray de navigation |
| [components/](components/) | Composants Kotlin prêts à copier : `GlassBottomNavBar.kt`, `GlassTopBarMenu.kt` (barre du haut + menu), `GlassFab.kt` (bouton + flottant flouté), `GlassPill.kt`, `SystemPrompts.kt` (plein écran + notification permanente) |

Principes non négociables :
1. Jamais de hex en dur dans un écran — toujours un token (`BgPrimary`, `Accent`, …).
2. Aucun `Button` plein : pill tonal-outline (`<Color>Dim` + bordure 1 dp `<Color>`).
3. Texte sur fond accent plein = `OnAccent`, jamais blanc.
4. Chaque écran est enveloppé une fois dans `ScreenWallpaperBackdrop`; toutes les surfaces sont des `GlassCard`.
5. Tout `Lazy*` scrollable non encadré par une barre opaque porte `verticalEdgeFade()` / `horizontalEdgeFade()`,
   avec un `contentPadding` ≥ taille du fondu (14dp → 16dp) sur l'axe fondu, sinon le premier/dernier élément est coupé.
6. Le verre est un bonus : l'UI doit rester correcte avec `liquidGlassEnabled = false`.

Ce qui reste propre à chaque app (à écrire dans son `CLAUDE.md`) : couleur d'accent par défaut,
fonds d'écran fournis, police de marque éventuelle, icône.

Le skill utilisateur `dflow-design-system` (et son script `app`) implémente ce design system ;
en cas de divergence, **ce dossier fait foi** et le skill doit être aligné.
