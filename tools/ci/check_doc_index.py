#!/usr/bin/env python3
"""docs 本地知识库质量校验。

检查 docs/（Obsidian 知识库，不入库）的：

- frontmatter：非归档文档必须含 tags/audience/status/scope，枚举值合法
- 链接完整性：wikilink 与相对 markdown 链接必须解析到存在文件
- 编号规则：agents/ 文件名 NN- 唯一，00 仅检索导航、90 仅待修复清单，其余 01-23
- 索引覆盖：agents/user/ops 每个文档出现在对应索引中
- 结构要求：agents 文档（除 00）必须含 “## 何时读”
- 大小限制：非归档文档 >8KB 警告、>12KB 报错

用法：

    python tools/ci/check_doc_index.py [--docs-root PATH] [--repo-root PATH]

docs/ 不入 CI；CI 只运行 tools/ci/test_check_doc_index.py 覆盖本脚本逻辑。
"""
from __future__ import annotations

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path

AUDIENCES = {"agent", "user", "ops"}
STATUSES = {"current", "archived"}
WARN_SIZE = 8 * 1024
ERROR_SIZE = 12 * 1024
NAV_FILE = "00-检索导航.md"
APPENDIX_FILE = "90-待修复问题清单.md"
ALLOWED_NUMBERS = set(range(1, 24)) | {90}

FRONTMATTER_RE = re.compile(r"\A---\n(.*?)\n---", re.S)
FIELD_RE = re.compile(r"^(\w+):\s*(.*)$", re.M)
WIKI_RE = re.compile(r"\[\[([^\]|#]+)(?:[|#][^\]]*)?\]\]")
MD_LINK_RE = re.compile(r"\[[^\]]*?\]\(([^)]+)\)")
WHEN_TO_READ_RE = re.compile(r"^##\s+何时读\s*$", re.M)
EXTERNAL_PREFIXES = ("http://", "https://", "mailto:", "tel:", "#")
EXEMPT_DIRS = {"archive", "adr"}


def _force_utf8_stdio() -> None:
    """Windows GBK 控制台打印中文/emoji 会 UnicodeEncodeError，统一按 UTF-8 输出。"""
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure is not None:
            try:
                reconfigure(encoding="utf-8", errors="replace")
            except (AttributeError, ValueError, OSError):
                pass


def parse_frontmatter(text: str) -> dict[str, str]:
    """返回 frontmatter 的 key -> value（无 frontmatter 时为空 dict）。"""
    m = FRONTMATTER_RE.match(text)
    if not m:
        return {}
    return {k: v.strip() for k, v in FIELD_RE.findall(m.group(1))}


def iter_links(text: str) -> list[str]:
    links: list[str] = []
    for m in WIKI_RE.finditer(text):
        links.append(m.group(1).strip())
    for m in MD_LINK_RE.finditer(text):
        links.append(m.group(1).strip())
    return links


class LinkResolver:
    """把 wikilink / 相对 markdown 链接解析为存在的文件路径。

    - 无路径分隔符：按文件名解析（docs 内唯一 stem 优先，仓库根 *.md 兜底）
    - 以 ./ 或 ../ 开头：相对当前文件所在目录
    - 含 / 的其他形式：相对 docs 根（Obsidian 库根）
    """

    def __init__(self, docs_root: Path, repo_root: Path) -> None:
        self.docs_root = docs_root
        self.repo_root = repo_root
        self.docs_by_stem: dict[str, list[Path]] = defaultdict(list)
        self.repo_by_stem: dict[str, list[Path]] = defaultdict(list)
        for p in docs_root.rglob("*.md"):
            self.docs_by_stem[p.stem].append(p)
        for p in repo_root.glob("*.md"):
            self.repo_by_stem[p.stem].append(p)

    def resolve(self, link: str, from_file: Path) -> Path | None:
        link = link.strip().split("#", 1)[0].strip()
        if not link:
            return None
        if link.startswith(("http://", "https://", "mailto:")):
            return None
        candidates: list[Path] = []
        if link.startswith(("./", "../")) or link.startswith((".\\", "..\\")):
            base = (from_file.parent / link).resolve()
            candidates = [base]
        elif "/" in link or "\\" in link:
            base = (self.docs_root / link).resolve()
            candidates = [base]
        else:
            stem = Path(link).stem
            candidates = list(self.docs_by_stem.get(stem, []))
            if not candidates:
                candidates = list(self.repo_by_stem.get(stem, []))
        for cand in candidates:
            if cand.is_file():
                return cand
            if cand.suffix == "":
                with_md = cand.with_suffix(".md")
                if with_md.is_file():
                    return with_md
        return None


def file_links_resolve(path: Path, resolver: LinkResolver) -> list[str]:
    text = path.read_text(encoding="utf-8", errors="replace")
    broken: list[str] = []
    for link in iter_links(text):
        if link.startswith(EXTERNAL_PREFIXES):
            continue
        if resolver.resolve(link, path) is None:
            broken.append(link)
    return broken


def index_targets(index_path: Path, resolver: LinkResolver) -> set[Path]:
    """索引文件里所有能解析到的链接目标集合。"""
    text = index_path.read_text(encoding="utf-8", errors="replace")
    targets: set[Path] = set()
    for link in iter_links(text):
        target = resolver.resolve(link, index_path)
        if target is not None:
            targets.add(target)
    return targets


def check_frontmatter(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8", errors="replace")
    fm = parse_frontmatter(text)
    rel = path.as_posix()
    errors: list[str] = []
    for field in ("tags", "audience", "status", "scope"):
        if field not in fm or not fm[field]:
            errors.append(f"{rel}: frontmatter 缺少 {field}")
    if "audience" in fm and fm["audience"] not in AUDIENCES:
        errors.append(f"{rel}: audience 非法 {fm['audience']!r}（应为 {sorted(AUDIENCES)}）")
    if "status" in fm and fm["status"] not in STATUSES:
        errors.append(f"{rel}: status 非法 {fm['status']!r}（应为 {sorted(STATUSES)}）")
    return errors


def check_numbering(agents_dir: Path) -> list[str]:
    errors: list[str] = []
    numbers: set[int] = set()
    for p in sorted(agents_dir.glob("*.md")):
        rel = p.relative_to(agents_dir).as_posix()
        m = re.match(r"^(\d{2})-", p.name)
        if not m:
            errors.append(f"agents/{rel}: 文件名必须以 NN- 开头")
            continue
        num = int(m.group(1))
        if num in numbers:
            errors.append(f"agents/{rel}: 编号 {num:02d} 重复")
        numbers.add(num)
        if num == 0 and p.name != NAV_FILE:
            errors.append(f"agents/{rel}: 00 仅允许 {NAV_FILE}")
        if num == 90 and p.name != APPENDIX_FILE:
            errors.append(f"agents/{rel}: 90 仅允许 {APPENDIX_FILE}")
        if num not in ALLOWED_NUMBERS and num != 0:
            errors.append(f"agents/{rel}: 编号 {num:02d} 超出 01-23/90 范围")
    if not numbers:
        errors.append("agents/: 目录为空")
    return errors


def main(argv: list[str] | None = None) -> int:
    _force_utf8_stdio()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--docs-root", type=Path, default=None, help="docs 根目录（默认仓库 docs/）")
    parser.add_argument("--repo-root", type=Path, default=None, help="仓库根目录（默认由脚本位置推断）")
    args = parser.parse_args(argv)

    script_dir = Path(__file__).resolve()
    repo_root = args.repo_root or script_dir.parents[2]
    docs_root = args.docs_root or (repo_root / "docs")
    if not docs_root.is_dir():
        print(f"ERROR: docs 根目录不存在: {docs_root}", file=sys.stderr)
        return 1

    errors: list[str] = []
    warnings: list[str] = []
    resolver = LinkResolver(docs_root, repo_root)
    agents_dir = docs_root / "agents"
    nav_file = agents_dir / NAV_FILE

    if not nav_file.is_file():
        errors.append(f"agents/{NAV_FILE}: 检索导航文件缺失")

    for p in sorted(docs_root.rglob("*.md")):
        if p.name == "README.md" and p.parent == docs_root:
            pass  # docs/README.md 也参与常规检查
        rel = p.relative_to(docs_root).as_posix()
        top_dir = p.relative_to(docs_root).parts[0]
        is_exempt = top_dir in EXEMPT_DIRS

        if is_exempt:
            continue

        # 链接完整性：非归档/非 ADR 文档
        broken = file_links_resolve(p, resolver)
        for link in broken:
            errors.append(f"{rel}: 链接无法解析: [[{link}]]")

        # frontmatter
        errors.extend(check_frontmatter(p))

        # 大小限制
        size = p.stat().st_size
        if size > ERROR_SIZE:
            errors.append(f"{rel}: 大小 {size}B 超过 {ERROR_SIZE}B 上限，请拆分")
        elif size > WARN_SIZE:
            warnings.append(f"{rel}: 大小 {size}B 超过 {WARN_SIZE}B，建议拆分")

    # agents 编号规则
    if agents_dir.is_dir():
        errors.extend(check_numbering(agents_dir))

        # 结构要求：agents 文档（除 00）必须含 何时读
        for p in sorted(agents_dir.glob("*.md")):
            if p.name == NAV_FILE:
                continue
            text = p.read_text(encoding="utf-8", errors="replace")
            if not WHEN_TO_READ_RE.search(text):
                errors.append(f"agents/{p.name}: 缺少 “## 何时读” 小节")

    # 索引覆盖：agents/user/ops 每个文档出现在对应索引
    index_checks = [
        (agents_dir, nav_file, "agents"),
        (docs_root / "user", docs_root / "user" / "README.md", "user"),
        (docs_root / "ops", docs_root / "ops" / "README.md", "ops"),
    ]
    for dir_path, index_path, label in index_checks:
        if not dir_path.is_dir():
            continue
        if not index_path.is_file():
            errors.append(f"{label}/README.md: 索引缺失")
            continue
        targets = index_targets(index_path, resolver)
        for p in sorted(dir_path.glob("*.md")):
            if p == index_path:
                continue
            if p not in targets:
                errors.append(f"{label}/{p.name}: 未登记进 {index_path.relative_to(docs_root).as_posix()}")

    for w in sorted(warnings):
        print(f"WARN: {w}")
    for e in sorted(errors):
        print(f"ERROR: {e}")
    if errors:
        print(f"检查失败：{len(errors)} 个错误。", file=sys.stderr)
        return 1
    print("OK: docs 校验通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
