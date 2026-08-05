import unittest
from datetime import datetime

from server.market_index.cursor import CursorPosition, decode_cursor, encode_cursor


class CursorTests(unittest.TestCase):
    def test_signed_cursor_round_trips_position(self):
        position = CursorPosition(
            school_id=10681,
            create_time=datetime(2026, 8, 5, 12, 30, 0),
            topic_id=39350265,
        )

        token = encode_cursor(position, "test-secret")

        self.assertEqual(position, decode_cursor(token, "test-secret"))

    def test_cursor_rejects_tampering(self):
        position = CursorPosition(10681, datetime(2026, 8, 5, 12, 30, 0), 39350265)
        token = encode_cursor(position, "test-secret")

        with self.assertRaises(ValueError):
            decode_cursor(token + "x", "test-secret")


if __name__ == "__main__":
    unittest.main()
