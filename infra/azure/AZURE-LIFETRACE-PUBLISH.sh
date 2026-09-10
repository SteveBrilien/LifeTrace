#!/usr/bin/env bash
set -Eeuo pipefail

DOMAIN=${DOMAIN:-trace.wmy-cloud.cn}
SITE_ROOT=${SITE_ROOT:-"$HOME/.local/share/lifetrace-update-site"}

usage() {
  cat >&2 <<'EOF'
Usage:
  AZURE-LIFETRACE-PUBLISH.sh <apk-path> <version-code> <version-name> [release-notes]

Example:
  ./AZURE-LIFETRACE-PUBLISH.sh /tmp/LifeTrace-0.3.4.apk 7 0.3.4 'Bug fixes'
EOF
  exit 2
}

[[ $# -ge 3 ]] || usage
APK=$1
VERSION_CODE=$2
VERSION_NAME=$3
NOTES=${4:-"LifeTrace ${VERSION_NAME}"}
[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 3; }
[[ "$VERSION_CODE" =~ ^[0-9]+$ ]] || { echo 'version-code must be an integer' >&2; exit 3; }
command -v sha256sum >/dev/null
command -v curl >/dev/null
command -v python3 >/dev/null

mkdir -p "$SITE_ROOT/releases"
SHA=$(sha256sum "$APK" | awk '{print $1}')
RELEASE_NAME="LifeTrace-${VERSION_NAME}.apk"
TMP_RELEASE="$SITE_ROOT/releases/${RELEASE_NAME}.new"
install -m 0644 "$APK" "$TMP_RELEASE"
printf '%s  %s\n' "$SHA" "$TMP_RELEASE" | sha256sum -c -
mv -f "$TMP_RELEASE" "$SITE_ROOT/releases/$RELEASE_NAME"

DOMAIN="$DOMAIN" SITE_ROOT="$SITE_ROOT" VERSION_CODE="$VERSION_CODE" VERSION_NAME="$VERSION_NAME" \
RELEASE_NAME="$RELEASE_NAME" SHA="$SHA" NOTES="$NOTES" python3 - <<'PY'
import json, os
from pathlib import Path
root = Path(os.environ['SITE_ROOT'])
payload = {
    'versionCode': int(os.environ['VERSION_CODE']),
    'versionName': os.environ['VERSION_NAME'],
    'apkUrl': f"https://{os.environ['DOMAIN']}/releases/{os.environ['RELEASE_NAME']}",
    'sha256': os.environ['SHA'],
    'notes': os.environ['NOTES'],
}
tmp = root / 'update.json.new'
tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
tmp.replace(root / 'update.json')
PY

# Public end-to-end verification: manifest first, then the exact APK bytes.
TMP_DOWNLOAD=$(mktemp --suffix=.apk)
trap 'rm -f "$TMP_DOWNLOAD"' EXIT
curl -fsS --connect-timeout 10 --max-time 30 "https://${DOMAIN}/update.json" | python3 -m json.tool >/dev/null
curl -fL --retry 3 --connect-timeout 15 -o "$TMP_DOWNLOAD" "https://${DOMAIN}/releases/${RELEASE_NAME}"
printf '%s  %s\n' "$SHA" "$TMP_DOWNLOAD" | sha256sum -c -

echo 'LIFETRACE_UPDATE_PUBLISH=PASS'
echo "VERSION_CODE=$VERSION_CODE"
echo "VERSION_NAME=$VERSION_NAME"
echo "SHA256=$SHA"
echo "MANIFEST=https://${DOMAIN}/update.json"
echo "APK=https://${DOMAIN}/releases/${RELEASE_NAME}"
