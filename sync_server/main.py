"""
河滴答@一键开阀器 — 设备列表云端同步服务 (Render 云部署版)

纯 Python 标准库实现，按 userId 存取设备列表。
数据持久化到 JSON 文件。

部署平台: Render.com (免费 Web Service)

API:
    GET  /              → 健康检查
    GET  /api/health    → 健康检查
    GET  /api/devices/{userId}   → 获取设备列表
    POST /api/devices/{userId}   → 全量替换设备列表
"""

import json
import os
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from typing import Any
from urllib.parse import urlparse, unquote

# ── 配置 ──────────────────────────────────────────────
# Render 自动注入 PORT 环境变量
PORT = int(os.environ.get("PORT", "8000"))
HOST = "0.0.0.0"

DATA_DIR = Path(__file__).parent / "data"
DATA_FILE = DATA_DIR / "devices.json"


# ── 数据层 ────────────────────────────────────────────

def _ensure_data_dir() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)


def _load_all() -> dict[str, list[dict[str, Any]]]:
    _ensure_data_dir()
    if not DATA_FILE.exists():
        return {}
    try:
        with open(DATA_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return {}


def _save_all(data: dict[str, list[dict[str, Any]]]) -> None:
    _ensure_data_dir()
    with open(DATA_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


# ── HTTP 处理器 ───────────────────────────────────────

class DeviceSyncHandler(BaseHTTPRequestHandler):

    def _send_json(self, data: Any, status: int = 200) -> None:
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(length)

    def _parse_path(self) -> tuple[str, str | None]:
        parsed = urlparse(self.path)
        path = unquote(parsed.path).rstrip("/")
        if path == "" or path == "/":
            return ("health", None)
        if path == "/api/health":
            return ("health", None)
        if path.startswith("/api/devices/"):
            user_id = path[len("/api/devices/"):]
            return ("devices", user_id if user_id else None)
        return ("unknown", None)

    def do_OPTIONS(self) -> None:
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self) -> None:
        route, user_id = self._parse_path()

        if route == "health":
            self._send_json({"status": "ok", "service": "device-sync"})
            return

        if route == "devices" and user_id:
            all_data = _load_all()
            self._send_json(all_data.get(user_id, []))
            return

        self._send_json({"error": "Not Found"}, status=404)

    def do_POST(self) -> None:
        route, user_id = self._parse_path()

        if route == "devices" and user_id:
            try:
                body = self._read_body()
                payload = json.loads(body.decode("utf-8"))
                if isinstance(payload, list):
                    devices = payload
                elif isinstance(payload, dict) and "devices" in payload:
                    devices = payload["devices"]
                else:
                    self._send_json({"error": "请求体格式错误，期望设备数组"}, status=400)
                    return

                all_data = _load_all()
                all_data[user_id] = devices
                _save_all(all_data)
                self._send_json({"status": "ok", "count": len(devices)})
                return
            except json.JSONDecodeError:
                self._send_json({"error": "JSON 解析失败"}, status=400)
                return

        self._send_json({"error": "Not Found"}, status=404)

    def log_message(self, format: str, *args: Any) -> None:
        print(f"[{self.log_date_time_string()}] {args[0]}")


# ── 启动入口 ──────────────────────────────────────────

def main() -> None:
    print(f"河滴答 设备同步服务启动 → 端口 {PORT}")
    server = HTTPServer((HOST, PORT), DeviceSyncHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n服务已停止")
        server.server_close()


if __name__ == "__main__":
    main()
