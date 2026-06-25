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
import sys
import time

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
DONE_FLAG = os.path.join(OUT_DIR, ".captured")   # run.ps1 轮询此文件判断是否抓到


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
        if TARGET_HOST not in host:
            return

        # 请求头里找 Authorization（大小写不敏感，mitmproxy 的 Headers 已处理）
        auth = flow.request.headers.get("Authorization")
        if not auth or "ey" not in auth:
            return

        token = auth.strip()
        if not token.lower().startswith("bearer"):
            token = "Bearer " + token

        payload = decode_jwt(token)
        if not payload or "schoolID" not in payload:
            # 不是我们要的集市 JWT（可能是别的鉴权头），跳过
            return

        self.captured = True
        self._on_captured(token, payload)

    def _on_captured(self, token: str, payload: dict):
        school = payload.get("school", "未知学校")
        school_id = payload.get("schoolID", "?")
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
        _log("  %s" % ("Token 已复制到剪贴板，回 App 直接粘贴即可" if copied
                        else "（剪贴板复制失败，请手动打开上面的 txt 复制）"))
        _log(bar)
        _log("  完成后可关闭本窗口。")


# mitmproxy 通过模块级 addons 列表加载
addons = [TokenCatcher()]


# ---- 无 mitmproxy 时的自检 ----
if __name__ == "__main__" and not _HAS_MITM:
    sample = ("Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9."
              "eyJ0ZW5hbnRJZCI6Nywic2Nob29sSUQiOjEwNjgyLCJzY2hvb2wiOiJcdTViODlc"
              "dTVmYmRcdTUzM2JcdTc5ZDFcdTU5MjdcdTViNjYiLCJ1dWlkIjoyMTQ2MTgwNTM1"
              "LCJpYXQiOjE3ODE3NTY2MzUsImV4cCI6MTc4NDM0ODYzNX0.sig")
    p = decode_jwt(sample)
    assert p and p.get("schoolID") == 10682, p
    assert p.get("school") == "安徽医科大学", p.get("school")
    print("自检通过：解码出", p.get("school"), "schoolID=", p.get("schoolID"))
