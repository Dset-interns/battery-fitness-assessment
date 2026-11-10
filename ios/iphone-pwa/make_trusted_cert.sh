#!/usr/bin/env bash
# Make an HTTPS certificate that an iPhone can trust, so the Home Screen app works offline.
#
# Safari lets the camera work on a self-signed certificate after "visit this website", but it will not
# register the service worker on it, so the app cannot cache itself or the pose models for offline use.
# This script creates a small local certificate authority (once) and signs the server certificate with it.
# Install the authority on the iPhone once (steps printed at the end); after that the phone trusts the server.
#
# The authority is name-constrained to private network addresses and localhost, so it cannot be used to
# impersonate any public website even if its key were copied.
#
# Re-run when the Mac's Wi-Fi address changes. The authority is kept; only the server certificate is renewed,
# so the iPhone does not need to be set up again.
#
# Usage:  bash ios/iphone-pwa/make_trusted_cert.sh [ip]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
D="$HERE/.certs"            # git-ignored
mkdir -p "$D"
IP="${1:-$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo 127.0.0.1)}"

if [ ! -f "$D/sai_local_ca.key" ]; then
  cat > "$D/ca.cnf" <<'EOF'
[req]
distinguished_name = dn
x509_extensions = v3_ca
prompt = no
[dn]
CN = SAI Battery Fitness Local Test CA
O = DSeT SAI testing (local only)
[v3_ca]
basicConstraints = critical, CA:TRUE, pathlen:0
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
nameConstraints = critical, permitted;IP:192.168.0.0/255.255.0.0, permitted;IP:10.0.0.0/255.0.0.0, permitted;IP:172.16.0.0/255.240.0.0, permitted;IP:127.0.0.0/255.0.0.0, permitted;DNS:localhost
EOF
  openssl req -x509 -new -nodes -newkey rsa:2048 -sha256 -days 825 \
    -keyout "$D/sai_local_ca.key" -out "$D/sai_local_ca.crt" -config "$D/ca.cnf" 2>/dev/null
  chmod 600 "$D/sai_local_ca.key"
  echo "Created the local certificate authority: $D/sai_local_ca.crt"
fi

cat > "$D/leaf.cnf" <<EOF
[req]
distinguished_name = dn
prompt = no
[dn]
CN = $IP
O = SAI Battery Fitness local server
[v3]
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = IP:$IP, IP:127.0.0.1, DNS:localhost
EOF
openssl req -new -nodes -newkey rsa:2048 -keyout "$D/iphone_dev.key" -out "$D/leaf.csr" -config "$D/leaf.cnf" 2>/dev/null
# iOS rejects server certificates valid for more than 825 days.
openssl x509 -req -in "$D/leaf.csr" -CA "$D/sai_local_ca.crt" -CAkey "$D/sai_local_ca.key" -CAcreateserial \
  -days 397 -sha256 -extfile "$D/leaf.cnf" -extensions v3 -out "$D/iphone_dev.crt" 2>/dev/null
chmod 600 "$D/iphone_dev.key"
rm -f "$D/leaf.csr"
openssl verify -CAfile "$D/sai_local_ca.crt" "$D/iphone_dev.crt"

cat <<EOF

Server certificate made for https://$IP:8443 (and https://localhost:8443).

One-time iPhone setup (skip if already done):
  1. AirDrop this file to the iPhone:  $D/sai_local_ca.crt
     (Finder: open the .certs folder with  open "$D" )
  2. iPhone: Settings > General > VPN & Device Management > the downloaded profile > Install.
  3. iPhone: Settings > General > About > Certificate Trust Settings > turn ON "SAI Battery Fitness Local Test CA".

Then start the server:  python3 "$HERE/serve_iphone.py"
and open https://$IP:8443 in Safari. There should be no certificate warning.
EOF
