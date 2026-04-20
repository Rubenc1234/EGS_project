#!/usr/bin/env bash
set -euo pipefail

HOST_IP="${1:-127.0.0.1}"
HOSTS_FILE="/etc/hosts"
MARKER_BEGIN="# EGS Project hosts"
MARKER_END="# EGS Project hosts end"

if [[ $EUID -ne 0 ]]; then
  exec sudo "$0" "$HOST_IP"
fi

if [[ ! "$HOST_IP" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
  echo "Invalid IP address: $HOST_IP" >&2
  exit 1
fi

TMP_FILE="$(mktemp)"
trap 'rm -f "$TMP_FILE"' EXIT

if grep -qF "$MARKER_BEGIN" "$HOSTS_FILE"; then
  awk -v begin="$MARKER_BEGIN" -v end="$MARKER_END" 'BEGIN { skip = 0 }
    $0 == begin { skip = 1; next }
    $0 == end { skip = 0; next }
    skip == 0 { print }
  ' "$HOSTS_FILE" > "$TMP_FILE"
else
  cp "$HOSTS_FILE" "$TMP_FILE"
fi

cat >> "$TMP_FILE" <<EOF
$MARKER_BEGIN
$HOST_IP iam.pt
$HOST_IP app.pt
$HOST_IP payment.pt
$HOST_IP payment-api.pt
$HOST_IP notifications.pt
$HOST_IP transactions.pt
$HOST_IP keycloak.pt
$HOST_IP payment-keycloak.pt
$MARKER_END
EOF

cp "$TMP_FILE" "$HOSTS_FILE"
echo "Updated $HOSTS_FILE with EGS host mappings for $HOST_IP"
