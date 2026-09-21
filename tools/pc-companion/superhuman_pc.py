"""Project Superhuman PC Companion v0.1
Local-only Windows telemetry bridge. Standard library only.
Tracks Windows boot/uptime and exposes it to devices on the same LAN.
"""
from __future__ import annotations
import ctypes
import json
import os
import socket
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HOST = "0.0.0.0"
PORT = int(os.environ.get("SUPERHUMAN_PC_PORT", "8765"))
DATA_DIR = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "ProjectSuperhuman" / "pc-companion"
DATA_DIR.mkdir(parents=True, exist_ok=True)
STATE_FILE = DATA_DIR / "state.json"
TOKEN_FILE = DATA_DIR / "token.txt"

def now_iso():
    return datetime.now(timezone.utc).isoformat()

def uptime_seconds():
    # GetTickCount64 is monotonic milliseconds since Windows boot.
    return int(ctypes.windll.kernel32.GetTickCount64() // 1000)

def boot_iso():
    return datetime.fromtimestamp(time.time() - uptime_seconds(), timezone.utc).isoformat()

def load_or_create_token():
    if TOKEN_FILE.exists():
        return TOKEN_FILE.read_text(encoding="utf-8").strip()
    import secrets
    token = secrets.token_urlsafe(24)
    TOKEN_FILE.write_text(token, encoding="utf-8")
    return token

TOKEN = load_or_create_token()

def load_state():
    try:
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    except Exception:
        return {}

def save_state():
    state = {
        "last_seen_utc": now_iso(),
        "boot_utc": boot_iso(),
        "uptime_seconds": uptime_seconds(),
        "hostname": socket.gethostname(),
    }
    tmp = STATE_FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(state, indent=2), encoding="utf-8")
    tmp.replace(STATE_FILE)

def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("192.0.2.1", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()

class Handler(BaseHTTPRequestHandler):
    server_version = "SuperhumanPC/0.1"

    def _send(self, code, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            return self._send(200, {"ok": True, "service": "project-superhuman-pc"})
        if self.path.startswith("/status"):
            auth = self.headers.get("Authorization", "")
            if auth != "Bearer " + TOKEN:
                return self._send(401, {"error": "unauthorized"})
            seconds = uptime_seconds()
            return self._send(200, {
                "schema": 1,
                "device_type": "windows_pc",
                "hostname": socket.gethostname(),
                "observed_at_utc": now_iso(),
                "boot_utc": boot_iso(),
                "uptime_seconds": seconds,
                "uptime_minutes": round(seconds / 60, 1),
                "uptime_hours": round(seconds / 3600, 2),
                "source": "windows.GetTickCount64"
            })
        return self._send(404, {"error": "not_found"})

    def log_message(self, fmt, *args):
        pass

def state_loop():
    while True:
        save_state()
        time.sleep(60)

if __name__ == "__main__":
    save_state()
    threading.Thread(target=state_loop, daemon=True).start()
    ip = local_ip()
    print("Project Superhuman PC Companion")
    print(f"Phone endpoint: http://{ip}:{PORT}/status")
    print(f"Pairing token: {TOKEN}")
    print("Keep the PC and phone on the same Wi-Fi.")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
