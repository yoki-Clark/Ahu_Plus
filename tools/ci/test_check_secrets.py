from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("check_secrets.py")
SPEC = importlib.util.spec_from_file_location("ahu_secret_scan", MODULE_PATH)
assert SPEC and SPEC.loader
scanner = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = scanner
SPEC.loader.exec_module(scanner)


class SecretScannerTest(unittest.TestCase):
    def scan(self, name: str, content: str | bytes) -> list[str]:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(content, bytes):
                path.write_bytes(content)
            else:
                path.write_text(content, encoding="utf-8")
            return [finding.rule for finding in scanner.scan_paths([path], root)]

    def test_forbidden_file_names_and_artifacts(self) -> None:
        self.assertIn("forbidden-credential-file", self.scan("local.properties", "sdk.dir=/tmp"))
        self.assertIn("forbidden-credential-file", self.scan(".env.production", "A=B"))
        self.assertIn("forbidden-sensitive-artifact", self.scan("release.p12", b"binary"))
        self.assertIn("forbidden-sensitive-artifact", self.scan("trace.har", "{}"))

    def test_private_key_and_jwt_are_detected_without_echoing_values(self) -> None:
        key = "-----BEGIN PRIVATE KEY-----\nsecret\n-----END PRIVATE KEY-----\n"  # scanner-fixture
        jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijklmnopqrstuv"  # scanner-fixture
        self.assertIn("private-key", self.scan("notes.txt", key))
        self.assertIn("jwt", self.scan("notes.txt", jwt))

    def test_headers_and_assignments_are_detected(self) -> None:
        self.assertIn(
            "authorization-bearer",
            self.scan("notes.txt", "Authorization: Bearer ABCDEFGHIJKLMNOPQRSTUVWXYZ123456"),  # scanner-fixture
        )
        self.assertIn(
            "cookie-value",
            self.scan("notes.txt", "Cookie: JSESSIONID=ABCDEFGHIJKLMNOPQRSTUVWXYZ123456"),  # scanner-fixture
        )
        self.assertIn(
            "credential-assignment",
            self.scan("notes.txt", 'api_key="ABCDEFGHIJKLMNOPQRSTUVWXYZ123456"'),  # scanner-fixture
        )

    def test_examples_and_source_interpolation_are_allowed(self) -> None:
        self.assertEqual([], self.scan(".env.example", "API_KEY=replace_me\n"))
        self.assertEqual([], self.scan("source.kt", 'header("Authorization", "Bearer $token")'))
        self.assertEqual([], self.scan("test.kt", 'val token = "not-a-real-token-placeholder"'))

    def test_env_example_rejects_non_placeholder_credentials(self) -> None:
        self.assertIn(
            "environment-credential",
            self.scan(".env.example", "API_KEY=ABCDEFGHIJKLMNOPQRSTUVWXYZ123456"),  # scanner-fixture
        )


if __name__ == "__main__":
    unittest.main()
