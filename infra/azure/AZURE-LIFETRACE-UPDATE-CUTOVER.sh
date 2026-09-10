#!/usr/bin/env bash
set -Eeuo pipefail

# LifeTrace update-site cutover for the existing Azure Docker/Caddy host.
# Run as the DHSP user after copying this file to Azure.
# Pattern: public Caddy TLS -> Docker bridge gateway -> unprivileged user service.

DOMAIN=${DOMAIN:-trace.wmy-cloud.cn}
PORT=${PORT:-18779}
SITE_ROOT=${SITE_ROOT:-"$HOME/.local/share/lifetrace-update-site"}
SERVER_ROOT=${SERVER_ROOT:-"$HOME/.local/lib/lifetrace-update-site"}
UNIT_PATH=${UNIT_PATH:-"$HOME/.config/systemd/user/lifetrace-update-site.service"}
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
TMP_CADDY=$(mktemp)
BACKUP=
CADDYFILE=
CONTAINER=
SERVICE=
PROJECT_DIR=
PROJECT_NAME=
COMPOSE_FILE=
GATEWAY=
TMP_APK=

cleanup() { rm -f "$TMP_CADDY" "${TMP_APK:-}"; }
trap cleanup EXIT
need() { command -v "$1" >/dev/null 2>&1 || { echo "missing command: $1" >&2; exit 2; }; }
need sudo
need python3
need curl
need sha256sum
need systemctl
sudo -n true
DOCKER=(sudo -n docker)

# Discover the exact running Caddy Compose service and host-side Caddyfile mount.
CONTAINER=$(${DOCKER[@]} ps --format '{{.Names}} {{.Image}}' | awk '$1=="wechat-assistant_apple-caddy-1" {print $1; exit}')
if [[ -z "$CONTAINER" ]]; then
  CONTAINER=$(${DOCKER[@]} ps --format '{{.Names}} {{.Image}}' | awk '$2 ~ /^caddy:/ {print $1; exit}')
fi
[[ -n "$CONTAINER" ]] || { echo 'Caddy container not found' >&2; exit 3; }
SERVICE=$(${DOCKER[@]} inspect -f '{{ index .Config.Labels "com.docker.compose.service" }}' "$CONTAINER")
PROJECT_DIR=$(${DOCKER[@]} inspect -f '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "$CONTAINER")
PROJECT_NAME=$(${DOCKER[@]} inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$CONTAINER")
COMPOSE_FILE=$(${DOCKER[@]} inspect -f '{{ index .Config.Labels "com.docker.compose.project.config_files" }}' "$CONTAINER" | cut -d, -f1)
CADDYFILE=$(${DOCKER[@]} inspect -f '{{range .Mounts}}{{if eq .Destination "/etc/caddy/Caddyfile"}}{{.Source}}{{end}}{{end}}' "$CONTAINER")
GATEWAY=$(${DOCKER[@]} inspect -f '{{range .NetworkSettings.Networks}}{{.Gateway}}{{"\n"}}{{end}}' "$CONTAINER" | awk 'NF {print; exit}')
[[ -n "$SERVICE" && -n "$PROJECT_DIR" && -n "$PROJECT_NAME" && -n "$COMPOSE_FILE" && -n "$CADDYFILE" && -n "$GATEWAY" ]] || {
  echo 'Caddy discovery incomplete' >&2; exit 4;
}
if [[ "$COMPOSE_FILE" != /* ]]; then COMPOSE_FILE="$PROJECT_DIR/$COMPOSE_FILE"; fi
python3 - "$GATEWAY" <<'PY'
import ipaddress, sys
ip = ipaddress.ip_address(sys.argv[1])
if ip.version != 4 or ip.is_loopback or ip.is_unspecified:
    raise SystemExit(f'unsafe Docker gateway: {ip}')
PY

echo "DISCOVERED_CONTAINER=$CONTAINER"
echo "DISCOVERED_SERVICE=$SERVICE"
echo "DISCOVERED_PROJECT_DIR=$PROJECT_DIR"
echo "DISCOVERED_PROJECT_NAME=$PROJECT_NAME"
echo "DISCOVERED_COMPOSE_FILE=$COMPOSE_FILE"
echo "DISCOVERED_CADDYFILE=$CADDYFILE"
echo "DISCOVERED_DOCKER_GATEWAY=$GATEWAY"

mkdir -p "$SITE_ROOT/releases" "$SERVER_ROOT" "$(dirname "$UNIT_PATH")"

# Small static backend. It binds only to the Docker bridge gateway, never 0.0.0.0.
cat > "$SERVER_ROOT/server.py" <<'PY'
#!/usr/bin/env python3
import os
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.environ["LIFETRACE_SITE_ROOT"]
HOST = os.environ["LIFETRACE_BIND_ADDRESS"]
PORT = int(os.environ.get("LIFETRACE_BIND_PORT", "18779"))

class Handler(SimpleHTTPRequestHandler):
    extensions_map = {
        **SimpleHTTPRequestHandler.extensions_map,
        ".apk": "application/vnd.android.package-archive",
        ".json": "application/json; charset=utf-8",
    }

    def do_GET(self):
        if self.path == "/healthz":
            body = b"ok\n"
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)
            return
        return super().do_GET()

    def do_HEAD(self):
        if self.path == "/healthz":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            return
        return super().do_HEAD()

    def end_headers(self):
        if self.path == "/update.json":
            self.send_header("Cache-Control", "no-store, max-age=0")
        elif self.path.startswith("/releases/"):
            self.send_header("Cache-Control", "public, max-age=31536000, immutable")
            self.send_header("X-Content-Type-Options", "nosniff")
        super().end_headers()

server = ThreadingHTTPServer((HOST, PORT), partial(Handler, directory=ROOT))
server.serve_forever()
PY
chmod 755 "$SERVER_ROOT/server.py"

cat > "$UNIT_PATH" <<UNIT
[Unit]
Description=LifeTrace private update backend for Azure Caddy
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
Environment=LIFETRACE_SITE_ROOT=$SITE_ROOT
Environment=LIFETRACE_BIND_ADDRESS=$GATEWAY
Environment=LIFETRACE_BIND_PORT=$PORT
ExecStart=/usr/bin/python3 $SERVER_ROOT/server.py
Restart=always
RestartSec=2
NoNewPrivileges=true
PrivateTmp=true
RestrictSUIDSGID=true

[Install]
WantedBy=default.target
UNIT
systemctl --user daemon-reload
systemctl --user enable --now lifetrace-update-site.service

for _ in $(seq 1 30); do
  if curl -fsS --connect-timeout 2 --max-time 5 "http://${GATEWAY}:${PORT}/healthz" | grep -qx ok; then break; fi
  sleep 1
done
curl -fsS "http://${GATEWAY}:${PORT}/healthz" | grep -qx ok
echo 'PRIVATE_UPDATE_BACKEND=PASS'

# Optional initial release publication. If one SOURCE_* variable is supplied, require all.
if [[ -n "${SOURCE_APK_URL:-}" || -n "${SOURCE_APK_SHA256:-}" || -n "${RELEASE_VERSION:-}" || -n "${RELEASE_VERSION_CODE:-}" ]]; then
  : "${SOURCE_APK_URL:?SOURCE_APK_URL is required}"
  : "${SOURCE_APK_SHA256:?SOURCE_APK_SHA256 is required}"
  : "${RELEASE_VERSION:?RELEASE_VERSION is required}"
  : "${RELEASE_VERSION_CODE:?RELEASE_VERSION_CODE is required}"
  SOURCE_APK_SHA256=${SOURCE_APK_SHA256,,}
  [[ "$SOURCE_APK_SHA256" =~ ^[0-9a-f]{64}$ ]] || { echo 'SOURCE_APK_SHA256 must be 64 hex chars' >&2; exit 5; }
  [[ "$RELEASE_VERSION_CODE" =~ ^[0-9]+$ ]] || { echo 'RELEASE_VERSION_CODE must be an integer' >&2; exit 5; }
  TMP_APK=$(mktemp --suffix=.apk)
  curl -fL --retry 4 --connect-timeout 15 -o "$TMP_APK" "$SOURCE_APK_URL"
  printf '%s  %s\n' "$SOURCE_APK_SHA256" "$TMP_APK" | sha256sum -c -
  RELEASE_NAME="LifeTrace-${RELEASE_VERSION}.apk"
  install -m 0644 "$TMP_APK" "$SITE_ROOT/releases/${RELEASE_NAME}.new"
  mv -f "$SITE_ROOT/releases/${RELEASE_NAME}.new" "$SITE_ROOT/releases/$RELEASE_NAME"
  RELEASE_NOTES=${RELEASE_NOTES:-"LifeTrace ${RELEASE_VERSION}"} \
  DOMAIN="$DOMAIN" RELEASE_NAME="$RELEASE_NAME" SOURCE_APK_SHA256="$SOURCE_APK_SHA256" \
  RELEASE_VERSION="$RELEASE_VERSION" RELEASE_VERSION_CODE="$RELEASE_VERSION_CODE" SITE_ROOT="$SITE_ROOT" \
  python3 - <<'PY'
import json, os
from pathlib import Path
root = Path(os.environ["SITE_ROOT"])
payload = {
    "versionCode": int(os.environ["RELEASE_VERSION_CODE"]),
    "versionName": os.environ["RELEASE_VERSION"],
    "apkUrl": f'https://{os.environ["DOMAIN"]}/releases/{os.environ["RELEASE_NAME"]}',
    "sha256": os.environ["SOURCE_APK_SHA256"].lower(),
    "notes": os.environ["RELEASE_NOTES"],
}
tmp = root / "update.json.new"
tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
tmp.replace(root / "update.json")
PY
  rm -f "$TMP_APK"
  TMP_APK=
  echo "INITIAL_RELEASE_PUBLISHED=$RELEASE_VERSION"
fi

# Patch Caddy with explicit managed markers, making reruns idempotent.
sudo -n cat "$CADDYFILE" > "$TMP_CADDY"
BACKUP="${CADDYFILE}.pre-lifetrace-update-${STAMP}.bak"
sudo -n cp -a "$CADDYFILE" "$BACKUP"

python3 - "$TMP_CADDY" "$DOMAIN" "$GATEWAY" "$PORT" <<'PY'
from pathlib import Path
import re, sys
p = Path(sys.argv[1]); domain=sys.argv[2]; gateway=sys.argv[3]; port=sys.argv[4]
s = p.read_text()
begin = '# BEGIN LIFETRACE_UPDATE_SITE_MANAGED'
end = '# END LIFETRACE_UPDATE_SITE_MANAGED'
block = f'''{begin}\n{domain} {{\n\tencode zstd gzip\n\treverse_proxy {gateway}:{port}\n}}\n{end}'''
pattern = re.compile(re.escape(begin) + r'.*?' + re.escape(end), re.S)
if pattern.search(s):
    s = pattern.sub(block, s, count=1)
else:
    if domain in s:
        raise SystemExit(f'{domain} already exists outside the LifeTrace managed block; refusing blind edit')
    if not s.endswith('\n'):
        s += '\n'
    s += '\n' + block + '\n'
p.write_text(s)
print('CADDY_LIFETRACE_PATCH=ready')
PY

# Validate using the exact running Caddy image before production mutation.
${DOCKER[@]} cp "$TMP_CADDY" "$CONTAINER:/tmp/Caddyfile.lifetrace-update"
${DOCKER[@]} exec "$CONTAINER" caddy validate --config /tmp/Caddyfile.lifetrace-update --adapter caddyfile
echo 'CADDY_VALIDATE=PASS'

rollback() {
  echo 'ROLLBACK=begin' >&2
  if [[ -n "${BACKUP:-}" && -n "${CADDYFILE:-}" ]]; then sudo -n cp -a "$BACKUP" "$CADDYFILE" || true; fi
  if [[ -n "${PROJECT_DIR:-}" && -n "${COMPOSE_FILE:-}" && -n "${SERVICE:-}" ]]; then
    sudo -n docker compose --project-directory "$PROJECT_DIR" -f "$COMPOSE_FILE" up -d --force-recreate "$SERVICE" || true
  fi
  echo "ROLLBACK=restored backup=${BACKUP:-none}" >&2
}

mode=$(sudo -n stat -c '%a' "$CADDYFILE")
uid=$(sudo -n stat -c '%u' "$CADDYFILE")
gid=$(sudo -n stat -c '%g' "$CADDYFILE")
NEW="${CADDYFILE}.lifetrace-update-${STAMP}.new"
sudo -n install -m "$mode" -o "$uid" -g "$gid" "$TMP_CADDY" "$NEW"
sudo -n mv -f "$NEW" "$CADDYFILE"

# The existing deployment bind-mounts Caddyfile as a single file. Recreate only Caddy
# so Docker rebinds the new inode; a reload alone can keep the stale file.
if ! sudo -n docker compose --project-directory "$PROJECT_DIR" -f "$COMPOSE_FILE" up -d --force-recreate "$SERVICE"; then
  rollback; exit 10
fi
sleep 3
NEW_CONTAINER=$(${DOCKER[@]} ps --filter "label=com.docker.compose.project=$PROJECT_NAME" --filter "label=com.docker.compose.service=$SERVICE" --format '{{.Names}}' | head -1)
if [[ -z "$NEW_CONTAINER" ]]; then rollback; echo 'recreated Caddy container not found' >&2; exit 11; fi
if ! ${DOCKER[@]} exec "$NEW_CONTAINER" grep -q 'LIFETRACE_UPDATE_SITE_MANAGED' /etc/caddy/Caddyfile; then
  rollback; echo 'new Caddyfile not visible in container' >&2; exit 12
fi

# Allow time for automatic ACME issuance, then prove public HTTPS.
PUBLIC_OK=0
for _ in $(seq 1 40); do
  if curl -fsS --connect-timeout 5 --max-time 10 "https://${DOMAIN}/healthz" | grep -qx ok; then
    PUBLIC_OK=1; break
  fi
  sleep 3
done
if [[ "$PUBLIC_OK" != 1 ]]; then rollback; echo 'public HTTPS health check failed' >&2; exit 13; fi

if [[ -f "$SITE_ROOT/update.json" ]]; then
  curl -fsS --connect-timeout 5 --max-time 10 "https://${DOMAIN}/update.json" | python3 -m json.tool >/dev/null || {
    rollback; echo 'public update.json validation failed' >&2; exit 14;
  }
fi

echo 'AZURE_LIFETRACE_UPDATE_CUTOVER=PASS'
echo "Public health: https://${DOMAIN}/healthz"
echo "Public manifest: https://${DOMAIN}/update.json"
echo "Backup: $BACKUP"
