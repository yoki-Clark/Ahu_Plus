#!/usr/bin/env python3
"""AGENTS.md / CLAUDE.md 同步校验与版本一致性检查。

AGENTS.md 是仓库协作规则的权威来源；CLAUDE.md 必须与其逐字节一致。
修改 AGENTS.md 后运行：

    python tools/ci/check_docs.py --sync

本脚本还校验规则文件中的“当前版本 / versionCode”与
release/release-state.json 一致，防止手工维护版本号造成漂移。

注意：AGENTS.md / CLAUDE.md 是本地规则文件（.gitignore 排除），
CI 只运行本脚本的单元测试，不直接执行同步检查。
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AGENTS = ROOT / "AGENTS.md"
CLAUDE = ROOT / "CLAUDE.md"
RELEASE_STATE = ROOT / "release" / "release-state.json"

VERSION_RE = re.compile(r"当前版本：`(?P<version>[\d.]+)`，versionCode (?P<code>\d+)。")


def files_in_sync() -> bool:
    """检查 AGENTS.md 与 CLAUDE.md 是否逐字节一致。"""
    if not AGENTS.exists() or not CLAUDE.exists():
        print("SKIP: AGENTS.md/CLAUDE.md 不存在（本地规则文件未检出），跳过同步检查。")
        return True
    if AGENTS.read_bytes() == CLAUDE.read_bytes():
        print("OK: AGENTS.md 与 CLAUDE.md 完全一致。")
        return True
    print("MISMATCH: AGENTS.md 与 CLAUDE.md 不一致。", file=sys.stderr)
    print("以 AGENTS.md 为权威，运行: python tools/ci/check_docs.py --sync", file=sys.stderr)
    return False


def version_matches_release_state() -> bool:
    """检查 AGENTS.md 的版本行与 release/release-state.json 是否一致。"""
    if not AGENTS.exists() or not RELEASE_STATE.exists():
        return True
    text = AGENTS.read_text(encoding="utf-8")
    match = VERSION_RE.search(text)
    if match is None:
        print("SKIP: AGENTS.md 中未找到“当前版本”行，跳过版本一致性检查。")
        return True
    state = json.loads(RELEASE_STATE.read_text(encoding="utf-8"))
    expected_version = state["build"]["versionName"]
    expected_code = str(state["build"]["versionCode"])
    ok = match.group("version") == expected_version and match.group("code") == expected_code
    if ok:
        print(f"OK: 规则文件版本 {expected_version}/{expected_code} 与 release-state.json 一致。")
    else:
        print(
            f"MISMATCH: AGENTS.md 写的是 {match.group('version')}/{match.group('code')}，"
            f"release-state.json 是 {expected_version}/{expected_code}。",
            file=sys.stderr,
        )
    return ok


def sync() -> None:
    """把 AGENTS.md 原样复制到 CLAUDE.md。"""
    if not AGENTS.exists():
        print(f"错误: {AGENTS} 不存在。", file=sys.stderr)
        raise SystemExit(1)
    CLAUDE.write_bytes(AGENTS.read_bytes())
    print(f"已把 {AGENTS.name} 同步到 {CLAUDE.name}。")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sync", action="store_true", help="把 AGENTS.md 原样复制到 CLAUDE.md")
    args = parser.parse_args(argv)
    if args.sync:
        sync()
        return 0
    ok = files_in_sync()
    ok = version_matches_release_state() and ok
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
