#!/usr/bin/env python3
"""check_doc_index.py 的单元测试（docs/ 不入 CI，CI 运行本测试覆盖脚本逻辑）。"""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.ci.check_doc_index import main


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


FRONTMATTER = """---
tags: [ahu-plus, 测试]
audience: agent
status: current
scope: 测试文档。
related: [[01-项目总览]]
---
"""


def make_agents_dir(root: Path, extra: list[str] | None = None) -> Path:
    agents = root / "docs" / "agents"
    write(
        agents / "00-检索导航.md",
        """---
tags: [ahu-plus, 检索导航]
audience: agent
status: current
scope: 路由表。
---

# 检索导航

[[01-项目总览]]
""",
    )
    write(
        agents / "01-项目总览.md",
        FRONTMATTER + "\n# 项目总览\n\n## 何时读\n\n内容。\n",
    )
    for name in extra or []:
        write(
            agents / name,
            FRONTMATTER + f"\n# {name}\n\n## 何时读\n\n内容。\n",
        )
    return agents


def run_check(root: Path, extra_args: list[str] | None = None) -> int:
    args = ["--docs-root", str(root / "docs"), "--repo-root", str(root)]
    if extra_args:
        args.extend(extra_args)
    return main(args)


class CheckDocIndexTest(unittest.TestCase):
    def test_valid_fixture_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_agents_dir(root)
            write(root / "docs" / "user" / "README.md", "---\ntags: [x]\naudience: user\nstatus: current\nscope: s\n---\n\n[[使用指南]]\n")
            write(root / "docs" / "user" / "使用指南.md", FRONTMATTER.replace("audience: agent", "audience: user") + "\n# 使用指南\n")
            self.assertEqual(run_check(root), 0)

    def test_missing_frontmatter_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents = make_agents_dir(root)
            write(agents / "02-导航.md", "# 无 frontmatter\n\n## 何时读\n")
            self.assertNotEqual(run_check(root), 0)

    def test_bad_audience_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents = make_agents_dir(root)
            bad = FRONTMATTER.replace("audience: agent", "audience: robot")
            write(agents / "02-导航.md", bad + "\n# x\n\n## 何时读\n")
            self.assertNotEqual(run_check(root), 0)

    def test_broken_link_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents = make_agents_dir(root)
            write(agents / "02-导航.md", FRONTMATTER + "\n# x\n\n## 何时读\n\n[[不存在的文档]]\n")
            self.assertNotEqual(run_check(root), 0)

    def test_missing_when_to_read_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents = make_agents_dir(root)
            write(agents / "02-导航.md", FRONTMATTER + "\n# x\n\n没有何时读小节。\n")
            self.assertNotEqual(run_check(root), 0)

    def test_duplicate_number_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents = make_agents_dir(root)
            write(agents / "02-a.md", FRONTMATTER + "\n# a\n\n## 何时读\n")
            write(agents / "02-b.md", FRONTMATTER + "\n# b\n\n## 何时读\n")
            self.assertNotEqual(run_check(root), 0)

    def test_number_out_of_range_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents = make_agents_dir(root)
            write(agents / "24-越界.md", FRONTMATTER + "\n# x\n\n## 何时读\n")
            self.assertNotEqual(run_check(root), 0)

    def test_doc_not_in_index_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents = make_agents_dir(root, extra=["07-未登记.md"])
            # 索引只链接 01，07 未登记
            nav = agents / "00-检索导航.md"
            nav.write_text(
                "---\ntags: [x]\naudience: agent\nstatus: current\nscope: s\n---\n\n[[01-项目总览]]\n",
                encoding="utf-8",
            )
            self.assertNotEqual(run_check(root), 0)

    def test_oversize_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents = make_agents_dir(root)
            big = FRONTMATTER + "\n# x\n\n## 何时读\n\n" + ("x" * (12 * 1024 + 10))
            write(agents / "02-大文件.md", big)
            self.assertNotEqual(run_check(root), 0)

    def test_archive_skips_frontmatter_and_size(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_agents_dir(root)
            write(root / "docs" / "archive" / "snapshot.md", "# 无 frontmatter 的归档\n\n" + ("y" * (13 * 1024)))
            self.assertEqual(run_check(root), 0)

    def test_archive_path_link_resolves(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents = make_agents_dir(root)
            write(root / "docs" / "archive" / "old.md", "# old\n")
            nav = agents / "00-检索导航.md"
            nav.write_text(
                "---\ntags: [x]\naudience: agent\nstatus: current\nscope: s\n---\n\n[[01-项目总览]]\n[[02-导航]]\n",
                encoding="utf-8",
            )
            write(
                agents / "02-导航.md",
                FRONTMATTER + "\n# x\n\n## 何时读\n\n见 [[archive/old]]。\n",
            )
            self.assertEqual(run_check(root), 0)

    def test_missing_index_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_agents_dir(root)
            (root / "docs" / "user").mkdir(parents=True, exist_ok=True)
            write(root / "docs" / "user" / "指南.md", FRONTMATTER.replace("audience: agent", "audience: user") + "\n# g\n")
            self.assertNotEqual(run_check(root), 0)


if __name__ == "__main__":
    unittest.main()
