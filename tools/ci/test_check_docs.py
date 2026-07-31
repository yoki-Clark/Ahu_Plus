import tempfile
import unittest
from pathlib import Path

import tools.ci.check_docs as check_docs


class CheckDocsTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self._root = Path(self._tmp.name)
        self._agents = self._root / "AGENTS.md"
        self._claude = self._root / "CLAUDE.md"
        self._old_agents = check_docs.AGENTS
        self._old_claude = check_docs.CLAUDE
        check_docs.AGENTS = self._agents
        check_docs.CLAUDE = self._claude

    def tearDown(self):
        check_docs.AGENTS = self._old_agents
        check_docs.CLAUDE = self._old_claude
        self._tmp.cleanup()

    def test_missing_files_skip(self):
        self.assertTrue(check_docs.files_in_sync())

    def test_check_reports_mismatch(self):
        self._agents.write_bytes(b"a")
        self._claude.write_bytes(b"b")
        self.assertFalse(check_docs.files_in_sync())

    def test_check_passes_when_identical(self):
        self._agents.write_bytes(b"same\n")
        self._claude.write_bytes(b"same\n")
        self.assertTrue(check_docs.files_in_sync())

    def test_sync_makes_files_identical(self):
        self._agents.write_bytes(b"# rules\nsecond line\n")
        self._claude.write_bytes(b"# stale\n")
        check_docs.sync()
        self.assertEqual(self._agents.read_bytes(), self._claude.read_bytes())

    def test_version_matches_release_state(self):
        self._agents.write_text(
            "- 当前版本：`2.2.2.11`，versionCode 35。\n",
            encoding="utf-8",
        )
        state = self._root / "release-state.json"
        state.write_text(
            '{"build": {"versionName": "2.2.2.11", "versionCode": 35}}',
            encoding="utf-8",
        )
        old_state = check_docs.RELEASE_STATE
        check_docs.RELEASE_STATE = state
        try:
            self.assertTrue(check_docs.version_matches_release_state())
        finally:
            check_docs.RELEASE_STATE = old_state

    def test_version_mismatch_reported(self):
        self._agents.write_text(
            "- 当前版本：`2.2.2.10`，versionCode 34。\n",
            encoding="utf-8",
        )
        state = self._root / "release-state.json"
        state.write_text(
            '{"build": {"versionName": "2.2.2.11", "versionCode": 35}}',
            encoding="utf-8",
        )
        old_state = check_docs.RELEASE_STATE
        check_docs.RELEASE_STATE = state
        try:
            self.assertFalse(check_docs.version_matches_release_state())
        finally:
            check_docs.RELEASE_STATE = old_state


if __name__ == "__main__":
    unittest.main()
