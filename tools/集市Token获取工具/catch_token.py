# -*- coding: utf-8 -*-
"""
集市 Token 捕获脚本（mitmproxy addon）

原理：
  校园集市小程序（api.zxs-bbs.cn）登录后，之后的每个 API 请求都会带上
  请求头 `Authorization: Bearer <JWT>`。本脚本挂在 mitmproxy 上，
  只要用户在电脑微信里打开集市小程序并随便点一下（刷新帖子列表即可），
  就能抓到这个头，解码出学校名 / 有效期，写入文件并复制到剪贴板。

  不依赖“抓登录那一刻”——只要当前是登录状态，点任意页面都能抓到，
  比等登录响应可靠得多。

用法（由 run.ps1 调起，无需手动执行）：
  mitmdump -s catch_token.py
"""
import json
import base64
import os
import secrets
import sys
import time
from urllib.parse import urlencode

# mitmproxy 的 ctx 用于日志；脚本也可在无 mitmproxy 环境下被 import 做单元自检
try:
    from mitmproxy import ctx, http
    _HAS_MITM = True
except Exception:  # pragma: no cover - 仅自检时走到
    ctx = None
    http = None
    _HAS_MITM = False

# ---- 配置 ----
TARGET_HOST = "api.zxs-bbs.cn"        # 集市后端域名
OUT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_FILE = os.path.join(OUT_DIR, "我的集市Token.txt")
QR_FILE = os.path.join(OUT_DIR, "集市身份导入二维码.png")
DONE_FLAG = os.path.join(OUT_DIR, ".captured")   # run.ps1 轮询此文件判断是否抓到
TARGET_SEEN_FLAG = os.path.join(OUT_DIR, ".target_seen")
AUTH_SEEN_FLAG = os.path.join(OUT_DIR, ".auth_seen")
INVALID_AUTH_FLAG = os.path.join(OUT_DIR, ".invalid_auth_seen")
TLS_FAILED_FLAG = os.path.join(OUT_DIR, ".tls_failed")


def _b64url_decode(seg: str) -> bytes:
    seg += "=" * (-len(seg) % 4)
    return base64.urlsafe_b64decode(seg)


def decode_jwt(token: str):
    """解出 JWT payload（dict）；失败返回 None。"""
    raw = token.replace("Bearer", "").strip()
    parts = raw.split(".")
    if len(parts) < 2:
        return None
    try:
        return json.loads(_b64url_decode(parts[1]))
    except Exception:
        return None


def extract_bearer_jwt(value: str):
    """提取目标请求中的 Bearer JWT；返回 (规范化 token, payload)。"""
    if not value or not isinstance(value, str):
        return None
    raw = value.strip()
    if raw.lower().startswith("bearer "):
        raw = raw[7:].strip()
    if len(raw.split(".")) != 3:
        return None
    payload = decode_jwt(raw)
    if not isinstance(payload, dict):
        return None
    return "Bearer " + raw, payload


def _mark(path: str):
    """只落一个无敏感信息的状态标记，供启动脚本区分失败阶段。"""
    try:
        with open(path, "w", encoding="ascii") as f:
            f.write("ok")
    except Exception:
        pass


def _copy_clipboard(text: str) -> bool:
    """尽量把 token 复制到剪贴板（Windows clip）。失败不致命。"""
    try:
        import subprocess
        # clip 读 stdin；用 utf-16le 避免中文乱码（这里是纯 ascii token，稳妥起见仍指定）
        p = subprocess.Popen(["clip"], stdin=subprocess.PIPE)
        p.communicate(text.encode("utf-16le"))
        return p.returncode == 0
    except Exception:
        return False


def build_import_uri(token: str) -> str:
    """生成仅供 Ahu_Plus 导入使用的临时二维码内容。"""
    raw = token.removeprefix("Bearer ").strip()
    query = urlencode({
        "v": "1",
        "token": raw,
        "nonce": secrets.token_urlsafe(18),
    })
    return "ahuplus://market/import?" + query


def _write_qr(token: str) -> bool:
    """完全在本机生成二维码，不把 Bearer token 发送给第三方服务。"""
    try:
        import qrcode

        qr = qrcode.QRCode(
            version=None,
            error_correction=qrcode.constants.ERROR_CORRECT_M,
            box_size=8,
            border=4,
        )
        qr.add_data(build_import_uri(token))
        qr.make(fit=True)
        qr.make_image(fill_color="black", back_color="white").save(QR_FILE)
        return True
    except Exception as e:
        _log("生成二维码失败: %s" % e)
        return False


def _log(msg: str):
    # 直接 print 到控制台：mitmproxy 各版本日志 API 不一（12 改用 logging，
    # 旧版用 ctx.log），print 在所有版本的 mitmdump 控制台都可见，最稳。
    try:
        print(msg, flush=True)
    except Exception:
        # 极端编码环境兜底
        sys.stdout.buffer.write((msg + "\n").encode("utf-8", "replace"))
        sys.stdout.buffer.flush()


class TokenCatcher:
    def __init__(self):
        self.captured = False

    def request(self, flow):
        if self.captured:
            return
        try:
            host = flow.request.pretty_host
        except Exception:
            return
        if host.lower().rstrip(".") != TARGET_HOST:
            return

        _mark(TARGET_SEEN_FLAG)

        # 请求头里找 Authorization（大小写不敏感，mitmproxy 的 Headers 已处理）
        auth = flow.request.headers.get("Authorization")
        if not auth:
            return
        _mark(AUTH_SEEN_FLAG)

        parsed = extract_bearer_jwt(auth)
        if not parsed:
            _mark(INVALID_AUTH_FLAG)
            return
        token, payload = parsed

        self.captured = True
        self._on_captured(token, payload)

    def tls_failed_client(self, _data):
        # allow-hosts 只解密目标域名，因此这里无需记录任何连接详情。
        _mark(TLS_FAILED_FLAG)

    def _on_captured(self, token: str, payload: dict):
        school = (
            payload.get("school")
            or payload.get("schoolName")
            or payload.get("school_name")
            or "未知学校"
        )
        school_id = (
            payload.get("schoolID")
            or payload.get("schoolId")
            or payload.get("school_id")
            or "?"
        )
        exp = payload.get("exp")
        exp_str = ""
        if exp:
            try:
                exp_str = time.strftime("%Y-%m-%d", time.localtime(int(exp)))
            except Exception:
                exp_str = str(exp)

        # 写文件
        try:
            with open(OUT_FILE, "w", encoding="utf-8") as f:
                f.write(token + "\n")
        except Exception as e:
            _log("写入 token 文件失败: %s" % e)

        copied = _copy_clipboard(token)
        qr_created = _write_qr(token)

        # 写完成标志，供 run.ps1 检测后自动收尾
        try:
            with open(DONE_FLAG, "w", encoding="utf-8") as f:
                f.write("ok")
        except Exception:
            pass

        bar = "=" * 56
        _log("\n" + bar)
        _log("  抓到啦！已捕获集市身份")
        _log("  学校：%s （schoolID=%s）" % (school, school_id))
        if exp_str:
            _log("  有效期至：%s" % exp_str)
        _log("  Token 已保存到：%s" % OUT_FILE)
        if qr_created:
            _log("  导入二维码已生成：%s" % QR_FILE)
            _log("  在 Ahu_Plus 集市身份页点「扫描电脑二维码」即可导入")
        _log("  %s" % ("Token 已复制到剪贴板，回 App 直接粘贴即可" if copied
                        else "（剪贴板复制失败，请手动打开上面的 txt 复制）"))
        _log(bar)
        _log("  完成后可关闭本窗口。")


# mitmproxy 通过模块级 addons 列表加载
addons = [TokenCatcher()]


# ---- 无 mitmproxy 时的自检 ----
if __name__ == "__main__":
    sample = ("Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9."
              "eyJ0ZW5hbnRJZCI6Nywic2Nob29sSUQiOjEwNjgyLCJzY2hvb2wiOiJcdTViODlc"
              "dTVmYmRcdTUzM2JcdTc5ZDFcdTU5MjdcdTViNjYiLCJ1dWlkIjoyMTQ2MTgwNTM1"
              "LCJpYXQiOjE3ODE3NTY2MzUsImV4cCI6MTc4NDM0ODYzNX0.sig")
    p = decode_jwt(sample)
    assert p and p.get("schoolID") == 10682, p
    assert p.get("school") == "安徽医科大学", p.get("school")
    parsed = extract_bearer_jwt(sample)
    assert parsed and parsed[0] == sample
    assert extract_bearer_jwt("not-a-jwt") is None

    # 学校字段只用于展示，不应阻止目标域名上的合法 JWT 被捕获。
    generic = ("eyJhbGciOiJSUzI1NiJ9."
               "eyJ0ZW5hbnRJZCI6NywidXVpZCI6MSwiZXhwIjoxNzg0MzQ4NjM1fQ.sig")
    assert extract_bearer_jwt(generic)
    uri = build_import_uri(sample)
    assert uri.startswith("ahuplus://market/import?")
    assert "Bearer" not in uri
    print("自检通过：解码出", p.get("school"), "schoolID=", p.get("schoolID"))
