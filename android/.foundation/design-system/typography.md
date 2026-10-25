# Typographie

> Source de vérité : `android-foundation/design-system/`. Implémentation de référence : DFlow (`ui/theme/Theme.kt`).



## 10. Typography

`Typography()` defaults are used as-is except three weight bumps applied globally in `DFlowTheme`:
`titleLarge` → Bold, `titleMedium`/`titleSmall` → SemiBold. No custom font family.

## Règles communes

- Pas de `fontSize` en dur dans les écrans : utiliser `MaterialTheme.typography.*` (le seul écart toléré aujourd'hui dans DFlow est un `15.sp` isolé — ne pas le reproduire).
- Titres d'écran : `titleLarge` (Bold). Titres de carte / section : `titleMedium` (SemiBold). Méta : `bodySmall` + `TextSecondary`.
- Les polices custom (titres de marque) sont une spécificité d'app : à documenter dans le `CLAUDE.md` du projet, jamais dans la foundation.
- Champs hexadécimaux / valeurs techniques : `FontFamily.Monospace`.
