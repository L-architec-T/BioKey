# BioKey — règles spécifiques au projet

> Règles communes (stack, architecture, design system, conventions, Memo) : chargées depuis
> `.claude/rules/foundation/` (généré par android-foundation — ne pas éditer).
> Ce fichier ne contient que ce qui est propre à BioKey et **prime** sur la foundation.

## Rôle de l'app
Client Android libre de PC Bio Unlock (`org.pcbu.libreclient`) : déverrouillage du PC par
biométrie du téléphone. Modules `app/` et `core/`.

## Écarts assumés par rapport à la foundation (importants)
Projet dérivé d'un code amont — stack alignée sur la foundation le 2026-09-24 (Gradle 9.5, AGP 9.3
à Kotlin intégré, Kotlin 2.3.20, KSP 2.3.12, Room 2.8.4, BOM Compose 2026.08, compileSdk 37), mais :
- DSL **Kotlin** (`*.gradle.kts`), multi-module (`app/`, `core/`), plugins `serialization`, KSP,
  google-services.
- targetSdk 34 (limite Robolectric 4.13).
- Namespace `org.pcbu.libreclient` (pas `com.security.*`).

## Design system
Migré le 2026-09-24 : thème/`AppTheme`/verre et panneau Apparence portés de CallUp (`ui/theme`,
`ui/appearance`, `data/prefs/AppearancePreferences`), composants foundation dans `ui/components`,
accent par défaut jaune `#F5C400` (même jaune que Donova). Accueil = `ui/home/HomeShell.kt` (onglets Appareils /
Réglages, menu). Écrans poussés : `SubScreen` + `PillButton` (`ui/components/BioKeyUi.kt`).
`PreferencesStore.themeMode`/`dynamicColor` ne sont plus utilisés (remplacés par Apparence).

## Auto-update (foundation, portage manuel)
`install.sh` ne gère pas ce projet (DSL Kotlin, `org.pcbu`) : l'auto-update est porté à la main —
`app/src/main/kotlin/org/pcbu/libreclient/update/` (dialogue en verre, généré depuis le template),
versioning `app/version.properties`, signature via `~/keystores/biokey/keystore.properties`,
publication `publishGithubRelease` → `L-architec-T/biokey` dans `app/build.gradle.kts`.
