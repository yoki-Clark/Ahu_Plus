# -*- coding: utf-8 -*-
"""Read a market JWT from an already-running desktop WeChat process.

The scanner is intentionally narrow: it reads committed, readable memory from
WeChat processes owned by the current Windows user, looks for JWT-shaped ASCII
strings, and only accepts payloads containing a school identity and expiry.
It never dumps process memory or prints a token.
"""
from __future__ import annotations

import argparse
import base64
import ctypes
from ctypes import wintypes
import json
import os
import re
import sys
import time

from catch_token import TokenCatcher


TH32CS_SNAPPROCESS = 0x00000002
PROCESS_VM_READ = 0x0010
PROCESS_QUERY_INFORMATION = 0x0400
PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
MEM_COMMIT = 0x1000
PAGE_NOACCESS = 0x01
PAGE_GUARD = 0x100
READABLE_PROTECTIONS = {0x02, 0x04, 0x08, 0x20, 0x40, 0x80}
CHUNK_SIZE = 2 * 1024 * 1024
CHUNK_OVERLAP = 8192
TARGET_PROCESS_NAMES = {"wechatappex.exe", "weixin.exe", "wechat.exe"}
JWT_PATTERN = re.compile(
    rb"(?<![A-Za-z0-9_-])"
    rb"(eyJ[A-Za-z0-9_-]{8,2048}\.[A-Za-z0-9_-]{8,4096}\."
    rb"[A-Za-z0-9_-]{8,1024})"
    rb"(?![A-Za-z0-9_-])"
)


class PROCESSENTRY32W(ctypes.Structure):
    _fields_ = [
        ("dwSize", wintypes.DWORD),
        ("cntUsage", wintypes.DWORD),
        ("th32ProcessID", wintypes.DWORD),
        ("th32DefaultHeapID", ctypes.c_size_t),
        ("th32ModuleID", wintypes.DWORD),
        ("cntThreads", wintypes.DWORD),
        ("th32ParentProcessID", wintypes.DWORD),
        ("pcPriClassBase", wintypes.LONG),
        ("dwFlags", wintypes.DWORD),
        ("szExeFile", wintypes.WCHAR * 260),
    ]


class MEMORY_BASIC_INFORMATION(ctypes.Structure):
    _fields_ = [
        ("BaseAddress", ctypes.c_void_p),
        ("AllocationBase", ctypes.c_void_p),
        ("AllocationProtect", wintypes.DWORD),
        ("PartitionId", wintypes.WORD),
        ("RegionSize", ctypes.c_size_t),
        ("State", wintypes.DWORD),
        ("Protect", wintypes.DWORD),
        ("Type", wintypes.DWORD),
    ]


kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
kernel32.CreateToolhelp32Snapshot.argtypes = [wintypes.DWORD, wintypes.DWORD]
kernel32.CreateToolhelp32Snapshot.restype = wintypes.HANDLE
kernel32.Process32FirstW.argtypes = [wintypes.HANDLE, ctypes.POINTER(PROCESSENTRY32W)]
kernel32.Process32FirstW.restype = wintypes.BOOL
kernel32.Process32NextW.argtypes = [wintypes.HANDLE, ctypes.POINTER(PROCESSENTRY32W)]
kernel32.Process32NextW.restype = wintypes.BOOL
kernel32.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
kernel32.OpenProcess.restype = wintypes.HANDLE
kernel32.VirtualQueryEx.argtypes = [
    wintypes.HANDLE,
    ctypes.c_void_p,
    ctypes.POINTER(MEMORY_BASIC_INFORMATION),
    ctypes.c_size_t,
]
kernel32.VirtualQueryEx.restype = ctypes.c_size_t
kernel32.ReadProcessMemory.argtypes = [
    wintypes.HANDLE,
    ctypes.c_void_p,
    ctypes.c_void_p,
    ctypes.c_size_t,
    ctypes.POINTER(ctypes.c_size_t),
]
kernel32.ReadProcessMemory.restype = wintypes.BOOL
kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
kernel32.CloseHandle.restype = wintypes.BOOL


def _b64url_json(segment: bytes):
    try:
        padded = segment + b"=" * (-len(segment) % 4)
        return json.loads(base64.urlsafe_b64decode(padded))
    except Exception:
        return None


def _market_payload(token: bytes):
    parts = token.split(b".")
    if len(parts) != 3:
        return None
    header = _b64url_json(parts[0])
    payload = _b64url_json(parts[1])
    if not isinstance(header, dict) or not header.get("alg"):
        return None
    if not isinstance(payload, dict):
        return None
    school_id = payload.get("schoolID") or payload.get("schoolId") or payload.get("school_id")
    school = payload.get("school") or payload.get("schoolName") or payload.get("school_name")
    expires_at = payload.get("exp")
    if not school_id or not school or not expires_at:
        return None
    return payload


def _tokens_in_bytes(data: bytes):
    views = [data]
    if b"e\x00y\x00J\x00" in data:
        views.append(data[::2])
    if b"\x00e\x00y\x00J" in data:
        views.append(data[1::2])
    for view in views:
        for match in JWT_PATTERN.finditer(view):
            token = match.group(1)
            payload = _market_payload(token)
            if payload is not None:
                yield token.decode("ascii"), payload


def _wechat_processes():
    snapshot = kernel32.CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0)
    invalid_handle = ctypes.c_void_p(-1).value
    if not snapshot or snapshot == invalid_handle:
        return []
    processes = []
    try:
        entry = PROCESSENTRY32W()
        entry.dwSize = ctypes.sizeof(entry)
        ok = kernel32.Process32FirstW(snapshot, ctypes.byref(entry))
        while ok:
            name = entry.szExeFile.lower()
            if name in TARGET_PROCESS_NAMES:
                priority = 0 if name == "wechatappex.exe" else 1
                processes.append((priority, int(entry.th32ProcessID), name))
            ok = kernel32.Process32NextW(snapshot, ctypes.byref(entry))
    finally:
        kernel32.CloseHandle(snapshot)
    return [(pid, name) for _, pid, name in sorted(processes)]


def _open_process(pid: int):
    for access in (
        PROCESS_VM_READ | PROCESS_QUERY_INFORMATION,
        PROCESS_VM_READ | PROCESS_QUERY_LIMITED_INFORMATION,
    ):
        handle = kernel32.OpenProcess(access, False, pid)
        if handle:
            return handle
    return None


def scan_process(pid: int, deadline: float):
    handle = _open_process(pid)
    if not handle:
        return None
    try:
        address = 0
        mbi = MEMORY_BASIC_INFORMATION()
        while time.monotonic() < deadline:
            queried = kernel32.VirtualQueryEx(
                handle, ctypes.c_void_p(address), ctypes.byref(mbi), ctypes.sizeof(mbi)
            )
            if not queried:
                break
            base = int(mbi.BaseAddress or 0)
            region_size = int(mbi.RegionSize)
            next_address = base + region_size
            if next_address <= address:
                break
            protection = int(mbi.Protect) & 0xFF
            readable = (
                mbi.State == MEM_COMMIT
                and protection in READABLE_PROTECTIONS
                and not (mbi.Protect & (PAGE_GUARD | PAGE_NOACCESS))
            )
            if readable:
                offset = 0
                carry = b""
                while offset < region_size and time.monotonic() < deadline:
                    size = min(CHUNK_SIZE, region_size - offset)
                    buffer = ctypes.create_string_buffer(size)
                    read = ctypes.c_size_t()
                    ok = kernel32.ReadProcessMemory(
                        handle,
                        ctypes.c_void_p(base + offset),
                        buffer,
                        size,
                        ctypes.byref(read),
                    )
                    if ok and read.value:
                        block = carry + buffer.raw[: read.value]
                        found = next(_tokens_in_bytes(block), None)
                        if found:
                            return found
                        carry = block[-CHUNK_OVERLAP:]
                    else:
                        carry = b""
                    offset += size
            address = next_address
    finally:
        kernel32.CloseHandle(handle)
    return None


def find_market_token(watch_seconds: float, only_pid: int | None = None):
    deadline = time.monotonic() + max(1.0, watch_seconds)
    while time.monotonic() < deadline:
        processes = [(only_pid, "self-test.exe")] if only_pid else _wechat_processes()
        for pid, _name in processes:
            found = scan_process(pid, deadline)
            if found:
                return pid, found[0], found[1]
        if time.monotonic() < deadline:
            time.sleep(1.0)
    return None


def _self_test():
    header = base64.urlsafe_b64encode(b'{"alg":"RS256"}').rstrip(b"=")
    payload = base64.urlsafe_b64encode(
        json.dumps({"schoolID": 10682, "school": "fixture", "exp": 4102444800}).encode()
    ).rstrip(b"=")
    token = header + b"." + payload + b".signature-for-self-test"
    fixture = ctypes.create_string_buffer(b"Bearer " + token + b"\x00")
    found = find_market_token(15, only_pid=os.getpid())
    assert found and found[1].encode() == token
    assert fixture.raw
    print("自检通过：可从当前进程内存识别集市 JWT，且未输出身份内容。")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--watch-seconds", type=float, default=300)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        _self_test()
        return 0

    print("  正在只读检查已运行微信中的集市身份，请在小程序帖子列表下拉刷新…", flush=True)
    found = find_market_token(args.watch_seconds)
    if not found:
        print("  未在微信进程中识别到集市身份。", flush=True)
        return 2

    pid, token, payload = found
    if args.dry_run:
        print("memory-probe-found=true pid=%d payload-keys=%s" % (pid, ",".join(sorted(payload))), flush=True)
        return 0
    TokenCatcher()._on_captured("Bearer " + token, payload)
    return 0


if __name__ == "__main__":
    sys.exit(main())
