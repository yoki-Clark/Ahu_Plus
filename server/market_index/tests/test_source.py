import unittest
import json
from datetime import datetime

from server.market_index.source import parse_source_page


class SourceParserTests(unittest.TestCase):
    def test_filters_to_ahu_circle_and_deduplicates_ids(self):
        body = """
        {
          "status": "success",
          "data": {
            "rows": [
              {"id": 3, "createTime": "2026-08-05 12:00:00", "schoolInfo": {"id": 11327}},
              {"id": 2, "createTime": "2026-08-05 11:00:00", "schoolInfo": {"schoolId": 10681}},
              {"id": 2, "createTime": "2026-08-05 11:01:00", "schoolInfo": {"id": 10681}},
              {"id": 1, "createTime": "2026-08-05 10:00:00", "schoolInfo": {"id": 10681}}
            ],
            "hasMore": true
          }
        }
        """

        page = parse_source_page(body, school_id=10681)

        self.assertTrue(page.has_more)
        self.assertEqual([2, 1], [record.topic_id for record in page.records])
        self.assertEqual(datetime(2026, 8, 5, 11, 1), page.records[0].create_time)

    def test_accepts_top_level_data_array_and_falls_back_to_discovery_time(self):
        body = '{"status":"success","data":[{"id":9,"schoolInfo":{"id":10681}}]}'
        discovered_at = datetime(2026, 8, 5, 13, 0, 0)

        page = parse_source_page(body, school_id=10681, discovered_at=discovered_at)

        self.assertTrue(page.has_more)
        self.assertEqual(discovered_at, page.records[0].create_time)

    def test_keeps_compact_source_row_for_archive_including_comment_preview(self):
        body = json.dumps({
            "status": "success",
            "data": {
                "rows": [{
                    "id": 99,
                    "title": "保留标题",
                    "content": "保留正文",
                    "comments": [{"id": 7, "content": "预览评论"}],
                    "schoolInfo": {"id": 10681},
                    "createTime": "2026-08-05 14:22:06",
                }],
            },
        }, ensure_ascii=False)

        page = parse_source_page(body, school_id=10681)

        self.assertEqual(
            {
                "id": 99,
                "title": "保留标题",
                "content": "保留正文",
                "comments": [{"id": 7, "content": "预览评论"}],
                "schoolInfo": {"id": 10681},
                "createTime": "2026-08-05 14:22:06",
            },
            json.loads(page.records[0].payload_json),
        )

    def test_authenticated_source_can_use_configured_school_when_row_omits_school_fields(self):
        body = '{"status":"success","data":{"rows":[{"id":10,"content":"接口已按 token 限定学校"}]}}'

        page = parse_source_page(
            body,
            school_id=10681,
            assume_configured_school_when_missing=True,
        )

        self.assertEqual([10], [record.topic_id for record in page.records])


if __name__ == "__main__":
    unittest.main()
