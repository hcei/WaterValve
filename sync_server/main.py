"""
河滴答@一键开阀器 — 设备列表云端同步服务 (PythonAnywhere 版)

Flask WSGI 应用，适配 PythonAnywhere 免费托管。
按 userId 存取设备列表，JSON 文件持久化。

部署平台: PythonAnywhere.com (免费，无需信用卡)

API:
    GET  /              → 健康检查
    GET  /api/health    → 健康检查
    GET  /api/devices/{userId}   → 获取设备列表
    POST /api/devices/{userId}   → 全量替换设备列表
"""

import json
import os
import time
from functools import lru_cache
from pathlib import Path
from typing import Any

import requests
from flask import Flask, Response, jsonify, request

# ── 配置 ──────────────────────────────────────────────

DATA_DIR = Path(__file__).parent / "data"
DATA_FILE = DATA_DIR / "devices.json"

app = Flask(__name__)


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


# ── CORS 支持 ─────────────────────────────────────────

@app.after_request
def add_cors(response):
    response.headers["Access-Control-Allow-Origin"] = "*"
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type"
    return response


# ── API ───────────────────────────────────────────────

@app.route("/", defaults={"path": ""})
@app.route("/<path:path>", methods=["OPTIONS"])
def handle_options(path):
    return "", 204


@app.route("/")
@app.route("/api/health")
def health():
    return jsonify({"status": "ok", "service": "device-sync"})


@app.route("/api/devices/<user_id>", methods=["GET"])
def get_devices(user_id: str):
    if not user_id.strip():
        return jsonify({"error": "userId 不能为空"}), 400
    all_data = _load_all()
    return jsonify(all_data.get(user_id, []))


@app.route("/api/devices/<user_id>", methods=["POST"])
def save_devices(user_id: str):
    if not user_id.strip():
        return jsonify({"error": "userId 不能为空"}), 400

    try:
        payload = request.get_json(force=True)
    except Exception:
        return jsonify({"error": "JSON 解析失败"}), 400

    if isinstance(payload, list):
        devices = payload
    elif isinstance(payload, dict) and "devices" in payload:
        devices = payload["devices"]
    else:
        return jsonify({"error": "请求体格式错误，期望设备数组"}), 400

    all_data = _load_all()
    all_data[user_id] = devices
    _save_all(all_data)
    return jsonify({"status": "ok", "count": len(devices)})


# ── 应用更新代理 ──────────────────────────────────────

_GITHUB_RELEASE_URL = (
    "https://api.github.com/repos/hcei/WaterValve/releases/latest"
)
_CACHE_TTL_SECONDS = 300  # 5 分钟


def _fetch_github_release():
    """从 GitHub API 获取最新 release（带 5 分钟缓存）。"""
    resp = requests.get(
        _GITHUB_RELEASE_URL,
        headers={"Accept": "application/vnd.github.v3+json"},
        timeout=15,
    )
    resp.raise_for_status()
    return resp.json()


@lru_cache(maxsize=1)
def _cached_release():
    """缓存包装：返回 (data, timestamp) 元组。"""
    return _fetch_github_release(), time.time()


@app.route("/api/release/latest")
def proxy_latest_release():
    """GitHub Release 元数据代理（5 分钟缓存）。"""
    try:
        data, ts = _cached_release()
        if time.time() - ts > _CACHE_TTL_SECONDS:
            _cached_release.cache_clear()
            data, ts = _cached_release()
        return jsonify(data)
    except requests.RequestException as e:
        return jsonify({"error": f"GitHub API 不可用: {str(e)}"}), 502
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/release/apk")
def proxy_apk_download():
    """APK 下载代理（流式转发 GitHub Release asset）。"""
    tag = request.args.get("tag", "")
    if not tag:
        return jsonify({"error": "缺少 tag 参数"}), 400

    github_url = (
        f"https://github.com/hcei/WaterValve/releases/download/"
        f"{tag}/app-debug.apk"
    )

    try:
        resp = requests.get(github_url, stream=True, timeout=120)
        resp.raise_for_status()
    except requests.RequestException as e:
        return jsonify({"error": f"下载失败: {str(e)}"}), 502

    return Response(
        resp.iter_content(chunk_size=8192),
        content_type="application/vnd.android.package-archive",
        headers={
            "Content-Disposition": f"attachment; filename=WaterValve-{tag}.apk",
            "Content-Length": resp.headers.get("Content-Length", ""),
        },
    )


# ── 本地开发启动 ──────────────────────────────────────

if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8000"))
    print(f"河滴答 设备同步服务启动 → 端口 {port}")
    app.run(host="0.0.0.0", port=port, debug=False)
