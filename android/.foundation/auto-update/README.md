# Auto-update — système commun à toutes les apps

Repris de **Donova** (`~/Mes Apps/Donova` : `UpdateManager.kt`, `ui/UpdateDialog.kt`, bloc
`publishGithubRelease` de `build.gradle`). **Obligatoire dans chaque app**, existante ou future.

## Fonctionnement

```
assembleRelease ──► version.properties : patch+1, versionCode+1   (bloc auto-versioning de build.gradle)
       │
       └─finalizedBy─► publishGithubRelease (publish.gradle)
                          gh repo create L-architec-T/<pkg> --public  (1re fois seulement)
                          gh release create v<versionName> <apk> --notes release_notes.txt
                          → APK seul, jamais de code source poussé

Lancement de l'app ──► AutoUpdateHost()  (dans <App>Theme { … } de MainActivity)
       GET api.github.com/repos/L-architec-T/<pkg>/releases/latest   (timeout 6 s, sans auth)
       version distante > version installée ?  ──► popup en verre « MISE À JOUR DISPONIBLE »
       INSTALLER ──► téléchargement dans cacheDir/update.apk ──► installeur système (FileProvider)
       PLUS TARD ──► reproposé au prochain lancement
```

## Fichiers

| Template (`templates/`) | Destination dans l'app |
|---|---|
| `UpdateManager.kt` | `src/main/java/com/security/<pkg>/update/UpdateManager.kt` |
| `UpdateDialog.kt` (`AutoUpdateHost` + `UpdateDialog`) | `src/main/java/com/security/<pkg>/update/UpdateDialog.kt` |
| `update_file_paths.xml` | `src/main/res/xml/update_file_paths.xml` |
| `publish.gradle` | `publish.gradle` (+ `apply from: 'publish.gradle'` en fin de `build.gradle`) |

Manifest : permissions `INTERNET` + `REQUEST_INSTALL_PACKAGES`, `<provider>` `.update.UpdateFileProvider`
(sous-classe de FileProvider, cohabite avec un provider existant) d'autorité `${applicationId}.update.provider`.

Placeholders : `__PKG__` (segment de package, ex. `donova`), `__TITLE__` (nom affiché, majuscules).

## Installer

- **Nouvelle app** : automatique via le script `app` (skill dflow-design-system).
- **App existante** : `"~/Mes Apps/android-foundation/auto-update/install.sh" <app_dir> "<Nom>"`
  (idempotent), puis `./gradlew assembleDebug`. Si l'app avait déjà son propre système
  (Donova), ne pas l'installer par-dessus.

Pré-requis : `gh` authentifié sur le compte `L-architec-T` ; bloc auto-versioning
(`version.properties`) dans `build.gradle` — présent dans toute app issue de DFlow, et
`publishGithubRelease` doit y compter comme tâche de release.

## Règles

- Repo de release = segment de package (`com.security.<pkg>` → `L-architec-T/<pkg>`), **public**, seul repo de l'app (jamais de `-android`), asset à nom fixe `<pkg>.apk`
  (l'app interroge l'API sans jeton). Jamais de source dedans.
- Ne jamais baisser `version.properties` : l'app ne propose que des versions **strictement
  supérieures** (comparaison numérique `x.y.z`).
- La même clé de signature pour toutes les releases d'une app (sinon l'installation échoue) :
  `~/keystores/<pkg>/`.
- Notes de version : `release_notes.txt` à la racine de l'app (`-` ou vide = pas de notes affichées).
- Écarts assumés vs Donova : comparaison de version stricte (Donova proposait aussi un downgrade),
  popup maintenue ouverte pendant le téléchargement (le spinner de Donova n'était jamais visible),
  autorité FileProvider dédiée `.update.provider` (évite les collisions avec un provider existant).
