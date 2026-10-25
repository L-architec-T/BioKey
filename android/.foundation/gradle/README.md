# Gradle — conventions et versions communes

## Catalogue partagé

`libs.versions.toml` est copié dans chaque app sous `.foundation/gradle/libs.versions.toml` par
`scripts/foundation.sh`, et déclaré dans `settings.gradle` sous le nom **`foundation`** (bloc
ajouté automatiquement, entre marqueurs `// >>> android-foundation` / `// <<< android-foundation`) :

```groovy
dependencyResolutionManagement {
    versionCatalogs {
        create('foundation') { from(files('.foundation/gradle/libs.versions.toml')) }
    }
}
```

Usage dans `build.gradle` (Groovy) :

```groovy
implementation platform(foundation.compose.bom)
implementation foundation.bundles.compose.core
implementation foundation.bundles.liquid.glass
implementation foundation.androidx.core.ktx
```

Le nom `foundation` (et non `libs`) évite tout conflit avec un `gradle/libs.versions.toml` propre
à une app. Une dépendance spécifique à une seule app (zxing, guava…) reste déclarée en dur dans
l'app ; elle entre dans le catalogue quand une deuxième app l'utilise.

Migration : les apps existantes déclarent encore leurs versions en dur. Migrer ligne par ligne
vers `foundation.*` lors d'une modification du `build.gradle` (pas de big-bang), en vérifiant
`./gradlew assembleDebug` après chaque migration.

## Conventions de build (toutes apps)

| Réglage | Valeur |
|---|---|
| Gradle wrapper | 9.5.0 |
| AGP | 9.3.0 — Kotlin **intégré à AGP 9** (pas de plugin `kotlin-android`, pas de `jvmTarget` : il suit `targetCompatibility`) |
| Plugin Compose | `org.jetbrains.kotlin.plugin.compose` 2.3.20 |
| compileSdk / targetSdk | 37 / 37 |
| minSdk | 26 (29 si une API l'exige — le justifier dans le CLAUDE.md de l'app) |
| Java | 17 (`org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64`) |
| DSL | Groovy `build.gradle`, projet mono-module (`src/` à la racine) |
| Versioning | `version.properties` (versionCode/versionName), bump auto uniquement sur `assembleRelease` |
| Signature | `signing.properties` + keystore **propres à chaque app**, jamais commités, jamais partagés |
| Release | `minifyEnabled true` + `proguard-rules.pro` ; debug : `applicationIdSuffix ".debug"` |
| Namespace | `com.security.<app>` |

Build local : `./gradlew assembleDebug` (ajouter `--offline` si le cache est chaud).
