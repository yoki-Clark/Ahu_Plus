import gzip
import unittest

from server.market_index.store import _safe_decompress, MAX_DECOMPRESSED_SIZE


class SafeDecompressTests(unittest.TestCase):
    def test_decompresses_normal_payload(self):
        original = b"Hello, World!" * 100
        compressed = gzip.compress(original, compresslevel=9, mtime=0)
        decompressed = _safe_decompress(compressed)
        self.assertEqual(decompressed, original)

    def test_rejects_zip_bomb_exceeding_limit(self):
        # Create a 1MB payload that compresses to ~1KB
        original = b"A" * (1024 * 1024)
        compressed = gzip.compress(original, compresslevel=9, mtime=0)

        # Should pass with default 10MB limit
        decompressed = _safe_decompress(compressed)
        self.assertEqual(len(decompressed), 1024 * 1024)

        # Should fail with 100KB limit
        with self.assertRaises(ValueError) as ctx:
            _safe_decompress(compressed, max_size=100 * 1024)
        self.assertIn("exceeds", str(ctx.exception).lower())

    def test_rejects_malformed_gzip(self):
        with self.assertRaises(ValueError):
            _safe_decompress(b"not a valid gzip stream")

    def test_respects_custom_size_limit(self):
        original = b"X" * 2000
        compressed = gzip.compress(original, compresslevel=9, mtime=0)

        # Should pass
        _safe_decompress(compressed, max_size=3000)

        # Should fail
        with self.assertRaises(ValueError):
            _safe_decompress(compressed, max_size=1000)

    def test_default_limit_is_10mb(self):
        # Verify MAX_DECOMPRESSED_SIZE constant
        self.assertEqual(MAX_DECOMPRESSED_SIZE, 10 * 1024 * 1024)


if __name__ == "__main__":
    unittest.main()
