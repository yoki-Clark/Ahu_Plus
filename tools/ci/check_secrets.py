#!/usr/bin/env python3
"""Reject tracked credential files and high-confidence secret material."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FORBIDDEN_BASENAMES = {"local.properties", ".env"}
FORBIDDEN_SUFFIXES = {
    ".jks",
    ".keystore",
    ".p12",
    ".pfx",
    ".key",
    ".har",
    ".pcap",
    ".pcapng",
}
FORBIDDEN_PARTS = {"captures", "release-snapshots"}
PLACEHOLDER_MARKERS = (
    "example",
    "placeholder",
    "dummy",
    "fake",
    "not-a-",
    "replace_me",
    "replace-me",
    "your_",
    "your-",
    "${",
    "$env:",
    "system.getenv",
    "repeat(",
)
CONTENT_RULES = (
    (
        "private-key",
        re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    ),
    (
        "jwt",
        re.compile(r"\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b"),
    ),
    (
        "authorization-bearer",
        re.compile(
            r"\bauthorization\s*[:=]\s*[\"']?bearer\s+[A-Za-z0-9._~+/=-]{20,}",
            re.IGNORECASE,
        ),
    ),
    (
        "cookie-value",
        re.compile(
            r"\b(?:cookie|set-cookie)\s*[:=]\s*[\"']?[A-Za-z0-9_-]+=[A-Za-z0-9._~+/=-]{20,}",
            re.IGNORECASE,
        ),
    ),
    (
        "credential-assignment",
        re.compile(
            r"\b(?:password|passwd|token|secret|api[_-]?key)\s*[:=]\s*[\"'][A-Za-z0-9._~+/=-]{20,}[\"']",
            re.IGNORECASE,
        ),
    ),
    (
        "environment-credential",
        re.compile(
            r"^\s*[A-Z0-9_]*(?:PASSWORD|PASSWD|TOKEN|SECRET|API_KEY)[A-Z0-9_]*\s*=\s*[A-Za-z0-9._~+/=-]{20,}\s*$"
        ),
    ),
)


@dataclass(frozen=True)
class Finding:
    path: str
    rule: str


def normalized_path(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.as_posix()


def path_finding(path: Path, root: Path) -> Finding | None:
    relative = normalized_path(path, root)
    lower_name = path.name.lower()
    lower_parts = {part.lower() for part in Path(relative).parts}
    if lower_name in FORBIDDEN_BASENAMES or (
        lower_name.startswith(".env.") and lower_name != ".env.example"
    ):
        return Finding(relative, "forbidden-credential-file")
    if path.suffix.lower() in FORBIDDEN_SUFFIXES:
        return Finding(relative, "forbidden-sensitive-artifact")
    if lower_parts & FORBIDDEN_PARTS:
        return Finding(relative, "forbidden-capture-directory")
    if re.search(r"(?:^|[-_.])(?:raw|private)[-_.]?response(?:[-_.]|$)", lower_name):
        return Finding(relative, "forbidden-private-response")
    return None


def line_is_placeholder(line: str, relative: str) -> bool:
    lowered = line.lower()
    if "scanner-fixture" in lowered:
        return relative.startswith("app/src/test/") or relative == "tools/ci/test_check_secrets.py"
    return any(marker in lowered for marker in PLACEHOLDER_MARKERS)


def content_findings_data(relative: str, data: bytes) -> list[Finding]:
    if b"\0" in data:
        return []
    text = data.decode("utf-8", errors="replace")
    findings: list[Finding] = []
    for line in text.splitlines():
        if line_is_placeholder(line, relative):
            continue
        for rule, pattern in CONTENT_RULES:
            if pattern.search(line):
                findings.append(Finding(relative, rule))
    return sorted(set(findings), key=lambda item: (item.path, item.rule))


def content_findings(path: Path, root: Path) -> list[Finding]:
    relative = normalized_path(path, root)
    try:
        data = path.read_bytes()
    except OSError:
        return [Finding(relative, "unreadable-file")]
    return content_findings_data(relative, data)


def scan_paths(paths: list[Path], root: Path = ROOT) -> list[Finding]:
    findings: list[Finding] = []
    for path in paths:
        if not path.is_file():
            continue
        path_issue = path_finding(path, root)
        if path_issue:
            findings.append(path_issue)
            continue
        findings.extend(content_findings(path, root))
    return sorted(set(findings), key=lambda item: (item.path, item.rule))


def scan_git_history(root: Path = ROOT) -> list[Finding]:
    """Scan every reachable Git blob without writing or printing its contents."""
    listing = subprocess.run(
        ["git", "rev-list", "--objects", "--all"],
        cwd=root,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if listing.returncode != 0:
        raise RuntimeError("Unable to enumerate Git history")
    entries = []
    for line in listing.stdout.decode("utf-8", errors="surrogateescape").splitlines():
        parts = line.split(" ", 1)
        if len(parts) == 2:
            entries.append((parts[0], parts[1]))

    findings: set[Finding] = set()
    batch = subprocess.Popen(
        ["git", "cat-file", "--batch"],
        cwd=root,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    assert batch.stdin and batch.stdout
    try:
        for object_id, relative in entries:
            batch.stdin.write(f"{object_id}\n".encode("ascii"))
            batch.stdin.flush()
            header = batch.stdout.readline().decode("ascii", errors="replace").strip()
            header_parts = header.split(" ")
            if len(header_parts) < 3:
                continue
            object_type = header_parts[1]
            if object_type == "missing":
                continue
            size = int(header_parts[2])
            data = batch.stdout.read(size)
            batch.stdout.read(1)
            if object_type != "blob":
                continue
            virtual_path = Path(relative)
            path_issue = path_finding(virtual_path, root)
            if path_issue:
                findings.add(path_issue)
                continue
            findings.update(content_findings_data(relative, data))
    finally:
        batch.stdin.close()
        batch.wait(timeout=30)
    return sorted(findings, key=lambda item: (item.path, item.rule))


def git_paths(base: str | None) -> list[Path]:
    command = ["git"]
    if base:
        command.extend(["diff", "--name-only", "--diff-filter=ACMR", "-z", f"{base}...HEAD"])
    else:
        command.extend(["ls-files", "--cached", "--others", "--exclude-standard", "-z"])
    result = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode != 0:
        raise RuntimeError("Unable to enumerate Git paths")
    return [ROOT / item.decode("utf-8", errors="surrogateescape") for item in result.stdout.split(b"\0") if item]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="Only scan files changed since this merge base")
    parser.add_argument("--history", action="store_true", help="Scan every reachable Git blob")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        findings = scan_git_history() if args.history else scan_paths(git_paths(args.base))
    except RuntimeError as exc:
        print(f"secret scan error: {exc}", file=sys.stderr)
        return 2
    if findings:
        print("Sensitive material check failed:", file=sys.stderr)
        for finding in findings:
            print(f"- {finding.path}: {finding.rule}", file=sys.stderr)
        return 1
    target = "Git history" if args.history else "Tracked files"
    print(f"{target} passed the sensitive material check.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
