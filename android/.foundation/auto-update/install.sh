#!/usr/bin/env bash
# android-foundation — installe le système d'auto-update (source : Donova) dans une app.
#
# Usage : install.sh <app_dir> [Titre affiché]
#   repo de release L-architec-T/<pkg> (public, APK seul), asset <pkg>.apk
#
# Idempotent : relancer ne duplique rien. Ce que ça pose dans l'app :
#   src/main/java/com/security/<pkg>/update/UpdateManager.kt   (polling GitHub Releases, DL, install)
#   src/main/java/com/security/<pkg>/update/UpdateDialog.kt    (AutoUpdateHost + popup en verre)
#   src/main/res/xml/update_file_paths.xml + <provider> + permissions dans le manifest
#   publish.gradle + `apply from` dans build.gradle (release GitHub à chaque assembleRelease)
#   AutoUpdateHost() dans le bloc <App>Theme { … } de MainActivity
set -euo pipefail

TPL="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)/templates"
APP="${1:?Usage : install.sh <app_dir> [Titre]}"
APP="$(cd "$APP" && pwd)"
GRADLE="$APP/build.gradle"
MANIFEST="$APP/src/main/AndroidManifest.xml"

[ -f "$GRADLE" ] && [ -f "$MANIFEST" ] || { echo "✘ Pas une app Android mono-module : $APP" >&2; exit 1; }

NAMESPACE=$(grep -oP "namespace\s+['\"]\K[^'\"]+" "$GRADLE" | head -1)
PKG="${NAMESPACE##*.}"
[ "$NAMESPACE" = "com.security.$PKG" ] || { echo "✘ namespace inattendu ($NAMESPACE), attendu com.security.<pkg>" >&2; exit 1; }
TITLE="${2:-$(printf '%s' "$PKG" | tr '[:lower:]' '[:upper:]')}"
REPO="$PKG"
BASE="$PKG"
SRC="$APP/src/main/java/com/security/$PKG"

# 1. Kotlin
mkdir -p "$SRC/update"
for f in UpdateManager.kt UpdateDialog.kt; do
    sed "s/__PKG__/$PKG/g; s/__TITLE__/$TITLE/g; s/__REPO__/$REPO/g" "$TPL/$f" >"$SRC/update/$f"
done
# GlassAlertDialog lives in ui/appearance in DFlow-derived apps, but elsewhere in older ones.
DIALOG_FILE=$(grep -rl "^fun GlassAlertDialog" "$SRC" | head -1 || true)
if [ -n "$DIALOG_FILE" ]; then
    DIALOG_PKG=$(grep -oP '^package \K\S+' "$DIALOG_FILE")
    sed -i "s/^import com\.security\.$PKG\.ui\.appearance\.GlassAlertDialog$/import $DIALOG_PKG.GlassAlertDialog/" "$SRC/update/UpdateDialog.kt"
else
    echo "⚠ GlassAlertDialog introuvable — adapter UpdateDialog.kt à la main" >&2
fi
THEME_FILE=$(grep -rlE "^val (Accent|TextPrimary)\b" "$SRC" | head -1 || true)
if [ -n "$THEME_FILE" ]; then
    THEME_PKG=$(grep -oP '^package \K\S+' "$THEME_FILE")
    sed -i "s/^import com\.security\.$PKG\.ui\.theme\./import $THEME_PKG./" "$SRC/update/UpdateDialog.kt"
fi

# 2. FileProvider paths
mkdir -p "$APP/src/main/res/xml"
cp "$TPL/update_file_paths.xml" "$APP/src/main/res/xml/update_file_paths.xml"

# 3. Manifest
python3 - "$MANIFEST" <<'PYEOF'
import re, sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
perms = ["android.permission.INTERNET", "android.permission.REQUEST_INSTALL_PACKAGES"]
for p in perms:
    if p not in text:
        text = re.sub(r"(<manifest[^>]*>\n)", r'\1    <uses-permission android:name="%s" />\n' % p, text, count=1)
if "update_file_paths" not in text:
    provider = """
        <!-- android-foundation auto-update: hands the downloaded APK to the system installer. -->
        <provider
            android:name=".update.UpdateFileProvider"
            android:authorities="${applicationId}.update.provider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/update_file_paths" />
        </provider>
"""
    text = text.replace("    </application>", provider.lstrip("\n") + "    </application>", 1)
open(path, "w", encoding="utf-8").write(text)
PYEOF

# 4. Gradle : publication + bump de version aussi pour publishGithubRelease
# An app that already registers its own publishGithubRelease (Donova) keeps it.
if ! grep -q "tasks.register('publishGithubRelease')" "$GRADLE"; then
    sed "s/__TITLE__/$TITLE/g; s/__REPO__/$REPO/g; s/__ASSET__/$BASE/g" "$TPL/publish.gradle" >"$APP/publish.gradle"
fi
if ! grep -q "tasks.register('publishGithubRelease')" "$GRADLE" && ! grep -q "apply from: 'publish.gradle'" "$GRADLE"; then
    printf "\n// android-foundation auto-update (release GitHub à chaque assembleRelease)\napply from: 'publish.gradle'\n" >>"$GRADLE"
fi
if grep -q "requestedTasks.contains('bundleRelease')" "$GRADLE" && ! grep -q "requestedTasks.contains('publishGithubRelease')" "$GRADLE"; then
    sed -i "s/requestedTasks.contains('bundleRelease')$/requestedTasks.contains('bundleRelease')   ||\n                      requestedTasks.contains('publishGithubRelease')/" "$GRADLE"
fi
grep -q "version.properties" "$GRADLE" || echo "⚠ Pas d'auto-versioning (version.properties) dans build.gradle — à ajouter (voir auto-update/README.md)" >&2

# 5. MainActivity : AutoUpdateHost() en tête du bloc <App>Theme {
MAIN="$SRC/MainActivity.kt"
if [ -f "$MAIN" ] && ! grep -q "AutoUpdateHost" "$MAIN" && ! grep -q "UpdateManager" "$MAIN"; then
    python3 - "$MAIN" "$PKG" <<'PYEOF'
import re, sys
path, pkg = sys.argv[1], sys.argv[2]
text = open(path, encoding="utf-8").read()
m = re.search(r"^(\s*)\w+Theme \{\n", text, re.M)
if not m:
    print("⚠ Bloc <App>Theme { introuvable dans MainActivity — ajouter AutoUpdateHost() à la main", file=sys.stderr)
    sys.exit(0)
indent = m.group(1) + "    "
text = text[:m.end()] + f"{indent}AutoUpdateHost()\n" + text[m.end():]
text = re.sub(r"^(import [^\n]+\n)", rf"\1import com.security.{pkg}.update.AutoUpdateHost\n", text, count=1, flags=re.M)
open(path, "w", encoding="utf-8").write(text)
PYEOF
fi

echo "✔ Auto-update installé dans $(basename "$APP") (repo de release : L-architec-T/$REPO, asset $BASE.apk)"
