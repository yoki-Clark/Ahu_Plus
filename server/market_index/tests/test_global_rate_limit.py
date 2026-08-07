import unittest

from server.market_index.api import _GlobalRateLimiter


class GlobalRateLimiterTests(unittest.TestCase):
    def test_allows_requests_under_limit(self):
        clock_time = 0.0

        def clock():
            return clock_time

        limiter = _GlobalRateLimiter(max_qps=10, clock=clock)
        for _ in range(10):
            self.assertTrue(limiter.allow())

    def test_denies_requests_over_limit(self):
        clock_time = 0.0

        def clock():
            return clock_time

        limiter = _GlobalRateLimiter(max_qps=5, clock=clock)
        for _ in range(5):
            self.assertTrue(limiter.allow())
        self.assertFalse(limiter.allow())

    def test_sliding_window_resets_after_one_second(self):
        clock_time = [0.0]

        def clock():
            return clock_time[0]

        limiter = _GlobalRateLimiter(max_qps=3, clock=clock)
        self.assertTrue(limiter.allow())
        self.assertTrue(limiter.allow())
        self.assertTrue(limiter.allow())
        self.assertFalse(limiter.allow())

        clock_time[0] = 1.1
        self.assertTrue(limiter.allow())

    def test_rejects_zero_or_negative_qps(self):
        with self.assertRaises(ValueError):
            _GlobalRateLimiter(max_qps=0)
        with self.assertRaises(ValueError):
            _GlobalRateLimiter(max_qps=-5)


if __name__ == "__main__":
    unittest.main()
