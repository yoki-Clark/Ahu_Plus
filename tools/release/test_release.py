from __future__ import annotations

import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("release.py")
SPEC = importlib.util.spec_from_file_location("ahu_release", MODULE_PATH)
assert SPEC and SPEC.loader
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class ReleaseStateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.state = release.load_state()

    def test_repository_state_is_valid(self) -> None:
        release.validate_state(self.state)

    def test_stable_and_beta_manifests_map_from_one_state(self) -> None:
        rendered = release.rendered_public_files(self.state)
        stable = json.loads(rendered[release.ROOT / "version.json"])
        beta = json.loads(rendered[release.ROOT / "version-beta.json"])
        website = json.loads(rendered[release.ROOT / "website" / "public" / "release.json"])

        self.assertEqual("stable", stable["channel"])
        self.assertEqual("beta", beta["channel"])
        self.assertEqual(stable["latestVersionCode"], website["versionCode"])
        self.assertEqual(stable["sha256"], website["sha256"])
        self.assertEqual(stable["downloadUrl"], website["downloadUrl"])

    def test_http_download_is_rejected(self) -> None:
        state = copy.deepcopy(self.state)
        state["published"]["stable"]["downloadUrl"] = "http://example.com/app.apk"
        with self.assertRaisesRegex(release.ReleaseError, "HTTPS"):
            release.validate_state(state)

    def test_missing_published_sha_is_rejected(self) -> None:
        state = copy.deepcopy(self.state)
        state["published"]["stable"]["sha256"] = ""
        with self.assertRaisesRegex(release.ReleaseError, "sha256"):
            release.validate_state(state)

    def test_published_channel_mismatch_is_rejected(self) -> None:
        state = copy.deepcopy(self.state)
        state["published"]["beta"]["channel"] = "stable"
        with self.assertRaisesRegex(release.ReleaseError, "channel"):
            release.validate_state(state)

    def test_build_version_must_not_be_below_published_channels(self) -> None:
        state = copy.deepcopy(self.state)
        state["build"]["versionCode"] = 30
        with self.assertRaisesRegex(release.ReleaseError, "不能低于已发布渠道"):
            release.validate_state(state)

    def test_build_version_may_equal_identical_published_release(self) -> None:
        state = copy.deepcopy(self.state)
        state["build"]["versionCode"] = 31
        release.validate_state(state)

    def test_same_version_allows_only_identical_artifact(self) -> None:
        beta = self.state["published"]["beta"]
        release.verify_monotonic_artifact(self.state, beta["versionCode"], beta["sha256"])
        with self.assertRaisesRegex(release.ReleaseError, "不同字节内容"):
            release.verify_monotonic_artifact(self.state, beta["versionCode"], "F" * 64)

    def test_manifest_drift_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            original_root = release.ROOT
            try:
                release.ROOT = Path(temp_dir)
                with self.assertRaisesRegex(release.ReleaseError, "清单存在漂移"):
                    release.check_public_files(self.state)
            finally:
                release.ROOT = original_root


if __name__ == "__main__":
    unittest.main()
