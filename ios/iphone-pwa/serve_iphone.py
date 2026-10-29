#!/usr/bin/env python3
"""
Local Wi-Fi HTTPS + HTTP Server for testing the AthleteIQ™ iOS App on a physical Apple iPhone.

Why HTTPS?
iOS Safari requires a secure context (HTTPS or localhost) to grant access to the
iPhone camera (`navigator.mediaDevices.getUserMedia`). This script automatically
generates a local self-signed TLS certificate (git-ignored) and serves `ios/iphone-pwa/`
over HTTPS on port 8443 so you can open `https://<Mac-WiFi-IP>:8443` in Safari on your
iPhone and test all 10 SAI tasks and the 4 live AI camera analyzers immediately.
"""

from __future__ import annotations

import http.server
import os
import socket
import ssl
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CERT_DIR = os.path.join(HERE, ".certs")
CERT_FILE = os.path.join(CERT_DIR, "iphone_dev.crt")
KEY_FILE = os.path.join(CERT_DIR, "iphone_dev.key")


def get_lan_ip() -> str:
    try:
        out = subprocess.run(
            ["ipconfig", "getifaddr", "en0"],
            capture_output=True, text=True, check=False,
        ).stdout.strip()
        if out:
            return out
    except Exception:
        pass
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


def ensure_cert(lan_ip: str) -> bool:
    os.makedirs(CERT_DIR, exist_ok=True)
    if os.path.exists(CERT_FILE) and os.path.exists(KEY_FILE):
        return True
    cmd = [
        "openssl", "req", "-x509", "-nodes", "-days", "365",
        "-newkey", "rsa:2048",
        "-keyout", KEY_FILE,
        "-out", CERT_FILE,
        "-subj", f"/CN={lan_ip}/O=AthleteIQ-SAI-iOS",
        "-addext", f"subjectAltName=IP:{lan_ip},IP:127.0.0.1,DNS:localhost",
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.returncode == 0


class NoCacheRequestHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self) -> None:
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()


def main() -> int:
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8443
    os.chdir(HERE)
    lan_ip = get_lan_ip()
    has_tls = ensure_cert(lan_ip)

    handler = NoCacheRequestHandler
    httpd = http.server.ThreadingHTTPServer(("0.0.0.0", port), handler)

    scheme = "http"
    if has_tls:
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.load_cert_chain(certfile=CERT_FILE, keyfile=KEY_FILE)
        httpd.socket = ctx.wrap_socket(httpd.socket, server_side=True)
        scheme = "https"

    print("\n" + "=" * 68)
    print("  AthleteIQ™ iOS App — Ready for Apple iPhone Testing")
    print("=" * 68)
    print(f"  1. Connect your iPhone to the same Wi-Fi network as this Mac.")
    print(f"  2. Open Safari on your iPhone and go to:")
    print(f"     👉  {scheme}://{lan_ip}:{port}")
    print(f"     (On Mac browser: {scheme}://localhost:{port})")
    if has_tls:
        print("  3. On iPhone Safari's first visit, tap 'Show Details' -> 'visit this website'")
        print("     to trust the local dev certificate so iOS enables camera access.")
    print("  4. Optional: Tap Share -> 'Add to Home Screen' in Safari to install")
    print("     as a full-screen standalone iOS app icon on your iPhone.")
    print("=" * 68 + "\n", flush=True)

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
