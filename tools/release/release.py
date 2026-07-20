#!/usr/bin/env python3
"""Ahu_Plus release state, artifact verification, and local dry-run tooling."""

from __future__ import annotations

import argparse
import copy
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[2]
STATE_PATH = ROOT / "release" / "release-state.json"
PUBLIC_PATHS = (
    ROOT / "version.json",
    ROOT / "version-beta.json",
    ROOT / "website" / "public" / "release.json",
)
CHANNELS = ("stable", "beta")
SHA256_RE = re.compile(r"^[0-9A-F]{64}$")
VERSION_RE = re.compile(r"^[0-9]+(?:\.[0-9]+)+$")
HISTORICAL_APPLICATION_ID = "com.yourname.ahu_plus"


class ReleaseError(RuntimeError):
    pass


def load_state(path: Path = STATE_PATH) -> dict[str, Any]:
    try:
        state = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ReleaseError(f"无法读取 Release 状态: {path}") from exc
    if not isinstance(state, dict):
        raise ReleaseError("Release 状态根节点必须是 object")
    return state


def json_text(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def require_object(parent: dict[str, Any], key: str) -> dict[str, Any]:
    value = parent.get(key)
    if not isinstance(value, dict):
        raise ReleaseError(f"{key} 必须是 object")
    return value


def require_text(parent: dict[str, Any], key: str, label: str) -> str:
    value = parent.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ReleaseError(f"{label}.{key} 必须是非空字符串")
    return value.strip()


def require_int(parent: dict[str, Any], key: str, label: str, minimum: int = 0) -> int:
    value = parent.get(key)
    if not isinstance(value, int) or isinstance(value, bool) or value < minimum:
        raise ReleaseError(f"{label}.{key} 必须是大于等于 {minimum} 的整数")
    return value


def validate_https_url(value: str, label: str) -> None:
    parsed = urlparse(value)
    if parsed.scheme.lower() != "https" or not parsed.netloc:
        raise ReleaseError(f"{label} 必须是 HTTPS URL")


def validate_version_record(record: dict[str, Any], label: str, published: bool) -> None:
    channel = require_text(record, "channel", label)
    if channel not in CHANNELS:
        raise ReleaseError(f"{label}.channel 必须是 stable 或 beta")
    version_name = record.get("versionName")
    if version_name is not None and (
        not isinstance(version_name, str) or not VERSION_RE.fullmatch(version_name)
    ):
        raise ReleaseError(f"{label}.versionName 格式无效")
    if published:
        require_int(record, "versionCode", label, 1)
        if not isinstance(version_name, str):
            raise ReleaseError(f"{label}.versionName 缺失")
    min_supported = require_int(record, "minSupportedVersionCode", label, 0)
    if published and min_supported > int(record["versionCode"]):
        raise ReleaseError(f"{label}.minSupportedVersionCode 不能高于 versionCode")

    download_url = require_text(record, "downloadUrl", label)
    validate_https_url(download_url, f"{label}.downloadUrl")
    mirror = record.get("downloadUrlMirror", "")
    if mirror:
        if not isinstance(mirror, str):
            raise ReleaseError(f"{label}.downloadUrlMirror 必须是字符串")
        validate_https_url(mirror, f"{label}.downloadUrlMirror")

    file_name = require_text(record, "apkFileName", label)
    if Path(file_name).name != file_name or urlparse(download_url).path.rsplit("/", 1)[-1] != file_name:
        raise ReleaseError(f"{label}.apkFileName 必须与主下载 URL 文件名一致")
    notes = record.get("releaseNotes")
    if not isinstance(notes, list) or not notes or not all(isinstance(item, str) and item.strip() for item in notes):
        raise ReleaseError(f"{label}.releaseNotes 必须是非空字符串数组")
    if not isinstance(record.get("forceUpdate"), bool):
        raise ReleaseError(f"{label}.forceUpdate 必须是布尔值")
    validate_https_url(require_text(record, "updateUrl", label), f"{label}.updateUrl")

    if published:
        sha256 = require_text(record, "sha256", label).upper()
        if not SHA256_RE.fullmatch(sha256):
            raise ReleaseError(f"{label}.sha256 必须是 64 位十六进制")
        require_int(record, "fileSize", label, 1)
        published_at = require_text(record, "publishedAt", label)
        try:
            dt.datetime.fromisoformat(published_at)
        except ValueError as exc:
            raise ReleaseError(f"{label}.publishedAt 必须是 ISO-8601 时间") from exc


def validate_state(state: dict[str, Any]) -> None:
    if state.get("schemaVersion") != 1:
        raise ReleaseError("仅支持 schemaVersion 1")
    application = require_object(state, "application")
    app_id = require_text(application, "applicationId", "application")
    if app_id != HISTORICAL_APPLICATION_ID:
        raise ReleaseError(f"applicationId 必须保持为 {HISTORICAL_APPLICATION_ID}")
    require_int(application, "minSdk", "application", 24)
    require_int(application, "targetSdk", "application", 36)
    require_text(application, "primaryAbi", "application")
    fingerprints = application.get("allowedSigningCertificateSha256")
    if not isinstance(fingerprints, list) or not fingerprints:
        raise ReleaseError("签名证书 allowlist 不能为空")
    for fingerprint in fingerprints:
        normalized = str(fingerprint).replace(":", "").upper()
        if not SHA256_RE.fullmatch(normalized):
            raise ReleaseError("签名证书指纹格式无效")

    build = require_object(state, "build")
    version_name = require_text(build, "versionName", "build")
    if not VERSION_RE.fullmatch(version_name):
        raise ReleaseError("build.versionName 格式无效")
    build_code = require_int(build, "versionCode", "build", 1)

    candidate = require_object(state, "candidate")
    validate_version_record(candidate, "candidate", published=False)
    published = require_object(state, "published")
    for channel in CHANNELS:
        record = require_object(published, channel)
        if record.get("channel") != channel:
            raise ReleaseError(f"published.{channel}.channel 不匹配")
        validate_version_record(record, f"published.{channel}", published=True)

    highest_published = max(int(published[channel]["versionCode"]) for channel in CHANNELS)
    if build_code < highest_published:
        raise ReleaseError(
            f"build.versionCode 不能低于已发布渠道: {build_code} < {highest_published}"
        )


def normalized_published_record(state: dict[str, Any], channel: str) -> dict[str, Any]:
    return copy.deepcopy(require_object(require_object(state, "published"), channel))


def render_update_manifest(record: dict[str, Any]) -> dict[str, Any]:
    return {
        "channel": record["channel"],
        "latestVersion": record["versionName"],
        "latestVersionCode": record["versionCode"],
        "minSupportedVersionCode": record["minSupportedVersionCode"],
        "downloadUrl": record["downloadUrl"],
        "downloadUrlMirror": record.get("downloadUrlMirror", ""),
        "apkFileName": record["apkFileName"],
        "releaseNotes": record["releaseNotes"],
        "forceUpdate": record["forceUpdate"],
        "updateTime": record["publishedAt"],
        "updateUrl": record["updateUrl"],
        "sha256": record["sha256"].upper(),
        "fileSize": record["fileSize"],
    }


def render_website_manifest(state: dict[str, Any], record: dict[str, Any]) -> dict[str, Any]:
    min_sdk = int(require_object(state, "application")["minSdk"])
    min_android = "7.0" if min_sdk == 24 else f"API {min_sdk}"
    return {
        "channel": record["channel"],
        "version": record["versionName"],
        "versionCode": record["versionCode"],
        "minAndroid": min_android,
        "fileName": record["apkFileName"],
        "fileSize": record["fileSize"],
        "sha256": record["sha256"].upper(),
        "downloadUrl": record["downloadUrl"],
        "publishedAt": record["publishedAt"],
    }


def rendered_public_files(state: dict[str, Any]) -> dict[Path, str]:
    stable = normalized_published_record(state, "stable")
    beta = normalized_published_record(state, "beta")
    return {
        ROOT / "version.json": json_text(render_update_manifest(stable)),
        ROOT / "version-beta.json": json_text(render_update_manifest(beta)),
        ROOT / "website" / "public" / "release.json": json_text(
            render_website_manifest(state, stable)
        ),
    }


def check_public_files(state: dict[str, Any]) -> None:
    drifted = []
    for path, expected in rendered_public_files(state).items():
        actual = path.read_text(encoding="utf-8") if path.is_file() else ""
        if actual.replace("\r\n", "\n") != expected:
            drifted.append(path.relative_to(ROOT).as_posix())
    if drifted:
        raise ReleaseError("生成的公开清单存在漂移: " + ", ".join(drifted))


def write_public_files(state: dict[str, Any]) -> None:
    for path, content in rendered_public_files(state).items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8", newline="\n")


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def command_output(command: list[str], cwd: Path = ROOT) -> str:
    result = subprocess.run(
        command,
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if result.returncode != 0:
        raise ReleaseError(f"命令执行失败: {Path(command[0]).name}\n{result.stdout.strip()}")
    return result.stdout


def discover_android_sdk() -> Path:
    candidates = [os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME")]
    local_properties = ROOT / "local.properties"
    if local_properties.is_file():
        for line in local_properties.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.startswith("sdk.dir="):
                candidates.append(line.split("=", 1)[1].replace("\\\\", "\\"))
                break
    if os.name == "nt":
        local_app_data = os.environ.get("LOCALAPPDATA")
        if local_app_data:
            candidates.append(str(Path(local_app_data) / "Android" / "Sdk"))
    else:
        candidates.append(str(Path.home() / "Android" / "Sdk"))
    for candidate in candidates:
        if candidate and Path(candidate).is_dir():
            return Path(candidate)
    raise ReleaseError("未找到 Android SDK；请设置 ANDROID_SDK_ROOT")


def sdk_tool(name: str) -> Path:
    sdk = discover_android_sdk()
    build_tools = sdk / "build-tools"
    versions = sorted(
        (path for path in build_tools.iterdir() if path.is_dir()),
        key=lambda path: tuple(int(part) if part.isdigit() else 0 for part in path.name.split(".")),
        reverse=True,
    )
    suffix = ".bat" if os.name == "nt" and name == "apksigner" else ".exe" if os.name == "nt" else ""
    for version in versions:
        candidate = version / f"{name}{suffix}"
        if candidate.is_file():
            return candidate
    raise ReleaseError(f"Android SDK 中缺少 {name}")


def target_record(state: dict[str, Any], channel: str, target: str) -> dict[str, Any]:
    if channel not in CHANNELS:
        raise ReleaseError("channel 必须是 stable 或 beta")
    if target == "published":
        return normalized_published_record(state, channel)
    candidate = copy.deepcopy(require_object(state, "candidate"))
    if candidate.get("channel") != channel:
        raise ReleaseError("候选渠道与请求渠道不匹配")
    build = require_object(state, "build")
    candidate["versionName"] = build["versionName"]
    candidate["versionCode"] = build["versionCode"]
    return candidate


def verify_monotonic_artifact(state: dict[str, Any], version_code: int, sha256: str) -> None:
    published = require_object(state, "published")
    same_code = [record for record in published.values() if record.get("versionCode") == version_code]
    highest_code = max(int(record["versionCode"]) for record in published.values())
    if version_code > highest_code:
        return
    if same_code and all(str(record.get("sha256", "")).upper() == sha256 for record in same_code):
        return
    raise ReleaseError("相同或更低 versionCode 不能发布不同字节内容")


def verify_apk(state: dict[str, Any], apk: Path, channel: str, target: str) -> dict[str, Any]:
    if not apk.is_file():
        raise ReleaseError(f"APK 不存在: {apk}")
    record = target_record(state, channel, target)
    application = require_object(state, "application")
    sha256 = file_sha256(apk)

    apksigner_output = command_output(
        [str(sdk_tool("apksigner")), "verify", "--verbose", "--print-certs", str(apk)]
    )
    digest_match = re.search(
        r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", apksigner_output
    )
    if not digest_match:
        raise ReleaseError("无法读取 APK 签名证书指纹")
    certificate_sha256 = digest_match.group(1).replace(":", "").upper()
    allowed = {
        str(value).replace(":", "").upper()
        for value in application["allowedSigningCertificateSha256"]
    }
    if certificate_sha256 not in allowed:
        raise ReleaseError(f"APK 签名证书不在 allowlist 中: {certificate_sha256}")

    badging = command_output([str(sdk_tool("aapt")), "dump", "badging", str(apk)])
    package_match = re.search(
        r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging
    )
    if not package_match:
        raise ReleaseError("无法读取 APK 包信息")
    app_id, version_code_text, version_name = package_match.groups()
    version_code = int(version_code_text)
    if app_id != application["applicationId"]:
        raise ReleaseError(f"APK applicationId 不匹配: {app_id}")
    if version_code != int(record["versionCode"]) or version_name != record["versionName"]:
        raise ReleaseError(
            f"APK 版本不匹配: {version_name} ({version_code})"
        )
    sdk_match = re.search(r"sdkVersion:'(\d+)'", badging)
    target_match = re.search(r"targetSdkVersion:'(\d+)'", badging)
    if not sdk_match or int(sdk_match.group(1)) != int(application["minSdk"]):
        raise ReleaseError("APK minSdk 不匹配")
    if not target_match or int(target_match.group(1)) != int(application["targetSdk"]):
        raise ReleaseError("APK targetSdk 不匹配")
    abi_match = re.search(r"native-code:\s*(.+)", badging)
    abis = re.findall(r"'([^']+)'", abi_match.group(1)) if abi_match else []
    if application["primaryAbi"] not in abis:
        raise ReleaseError(f"APK 不包含主 ABI: {application['primaryAbi']}")

    command_output(
        [str(sdk_tool("zipalign")), "-c", "-P", "16", "-v", "4", str(apk)]
    )
    if target == "published":
        if sha256 != str(record["sha256"]).upper() or apk.stat().st_size != int(record["fileSize"]):
            raise ReleaseError("APK 大小或 SHA-256 与已发布状态不匹配")
    else:
        verify_monotonic_artifact(state, version_code, sha256)

    return {
        "fileName": apk.name,
        "fileSize": apk.stat().st_size,
        "sha256": sha256,
        "applicationId": app_id,
        "versionName": version_name,
        "versionCode": version_code,
        "certificateSha256": certificate_sha256,
        "abis": abis,
    }


def snapshot_public_files() -> dict[str, str]:
    return {
        path.relative_to(ROOT).as_posix(): file_sha256(path) if path.is_file() else "missing"
        for path in PUBLIC_PATHS
    }


def git_status() -> bytes:
    result = subprocess.run(
        ["git", "status", "--porcelain=v1", "-z"],
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        raise ReleaseError("无法读取 Git 状态")
    return result.stdout


def safe_recreate_directory(path: Path) -> None:
    resolved = path.resolve()
    build_root = (ROOT / "build").resolve()
    if build_root not in resolved.parents:
        raise ReleaseError(f"拒绝清理 build 目录之外的路径: {resolved}")
    if resolved.exists():
        shutil.rmtree(resolved)
    resolved.mkdir(parents=True)


def candidate_record_with_artifact(
    state: dict[str, Any], metadata: dict[str, Any], published_at: str
) -> dict[str, Any]:
    record = target_record(state, str(require_object(state, "candidate")["channel"]), "candidate")
    record["publishedAt"] = published_at
    record["sha256"] = metadata["sha256"]
    record["fileSize"] = metadata["fileSize"]
    validate_version_record(record, "candidate-preview", published=True)
    return record


def dry_run(state: dict[str, Any], channel: str, skip_build: bool = False) -> Path:
    if require_object(state, "candidate").get("channel") != channel:
        raise ReleaseError("dry-run 渠道必须与 candidate.channel 一致")
    before_status = git_status()
    before_public = snapshot_public_files()
    if not skip_build:
        wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
        command_output(
            [str(wrapper), ":app:assembleRelease", "--no-configuration-cache", "--console=plain"]
        )

    apk_dir = ROOT / "app" / "build" / "outputs" / "apk" / "release"
    primary_apk = apk_dir / "app-arm64-v8a-release.apk"
    universal_apk = apk_dir / "app-universal-release.apk"
    primary = verify_apk(state, primary_apk, channel, "candidate")
    universal = verify_apk(state, universal_apk, channel, "candidate")

    output = ROOT / "build" / "release-dry-run"
    safe_recreate_directory(output)
    shutil.copy2(primary_apk, output / primary_apk.name)
    shutil.copy2(universal_apk, output / universal_apk.name)
    published_at = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()
    preview = candidate_record_with_artifact(state, primary, published_at)
    manifest_name = "version.json" if channel == "stable" else "version-beta.json"
    (output / manifest_name).write_text(
        json_text(render_update_manifest(preview)), encoding="utf-8", newline="\n"
    )
    if channel == "stable":
        (output / "website-release.json").write_text(
            json_text(render_website_manifest(state, preview)), encoding="utf-8", newline="\n"
        )
    summary = {
        "dryRun": True,
        "channel": channel,
        "publishedAt": published_at,
        "artifacts": [primary, universal],
        "remoteMutation": False,
    }
    (output / "release-summary.json").write_text(
        json_text(summary), encoding="utf-8", newline="\n"
    )

    if before_public != snapshot_public_files():
        raise ReleaseError("dry-run 修改了公开发布清单")
    if before_status != git_status():
        raise ReleaseError("dry-run 修改了 Git 工作区状态")
    return output


def promote(
    state: dict[str, Any], channel: str, apk: Path, published_at: str, apply: bool
) -> Path:
    metadata = verify_apk(state, apk, channel, "candidate")
    record = candidate_record_with_artifact(state, metadata, published_at)
    proposed = copy.deepcopy(state)
    require_object(proposed, "published")[channel] = record
    output = ROOT / "build" / "release-promote"
    safe_recreate_directory(output)
    (output / "release-state.json").write_text(
        json_text(proposed), encoding="utf-8", newline="\n"
    )
    for path, content in rendered_public_files(proposed).items():
        (output / path.relative_to(ROOT)).parent.mkdir(parents=True, exist_ok=True)
        (output / path.relative_to(ROOT)).write_text(content, encoding="utf-8", newline="\n")
    if apply:
        STATE_PATH.write_text(json_text(proposed), encoding="utf-8", newline="\n")
        write_public_files(proposed)
    return output


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("check", help="Validate release state and generated public files")

    verify = subparsers.add_parser("verify-apk", help="Verify an APK against release state")
    verify.add_argument("--apk", type=Path, required=True)
    verify.add_argument("--channel", choices=CHANNELS, required=True)
    verify.add_argument("--target", choices=("published", "candidate"), default="published")

    dry = subparsers.add_parser("dry-run", help="Build and verify Release APKs locally")
    dry.add_argument("--channel", choices=CHANNELS, default="beta")
    dry.add_argument("--skip-build", action="store_true", help=argparse.SUPPRESS)

    promote_parser = subparsers.add_parser(
        "promote", help="Prepare or apply published state after manual asset upload"
    )
    promote_parser.add_argument("--apk", type=Path, required=True)
    promote_parser.add_argument("--channel", choices=CHANNELS, required=True)
    promote_parser.add_argument("--published-at", required=True)
    promote_parser.add_argument("--apply", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        state = load_state()
        validate_state(state)
        if args.command == "check":
            check_public_files(state)
            print("Release state and generated manifests are consistent.")
        elif args.command == "verify-apk":
            print(json_text(verify_apk(state, args.apk.resolve(), args.channel, args.target)), end="")
        elif args.command == "dry-run":
            output = dry_run(state, args.channel, args.skip_build)
            print(f"Release dry-run completed: {output}")
        elif args.command == "promote":
            output = promote(
                state,
                args.channel,
                args.apk.resolve(),
                args.published_at,
                args.apply,
            )
            print(f"Release promotion {'applied' if args.apply else 'previewed'}: {output}")
    except ReleaseError as exc:
        print(f"release error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
