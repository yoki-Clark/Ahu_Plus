package com.ahu_plus.data.repository

import com.ahu_plus.data.model.MarketUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 通知列表解析：匿名用户/缺省字段的行必须存活（集市是匿名论坛，
 * senderUserInfo 为 null 或全 null 字段是常态，丢行会导致
 * “总条数 132 只显示 2 条”）。
 */
class MarketNoticeParserTest {

    @Test
    fun `anonymous sender notice survives parsing`() {
        val page = parseNotices(
            """{"status":"success","data":{
                "count": 132,
                "page": 1,
                "rows": [
                    {
                        "id": 1001,
                        "actionType": 4,
                        "createTime": "2026-07-30 12:00:00",
                        "senderUserInfo": null,
                        "topic": {"id": 1, "title": "匿名帖子", "data": {"imgs": []}}
                    },
                    {
                        "id": 1002,
                        "actionType": 2,
                        "actionTypeText": "评论了你的帖子",
                        "createTime": "2026-07-31 12:00:00",
                        "senderUserInfo": {"uuid": null, "nickname": null, "avatar": null},
                        "topic": {"id": 2, "title": "帖子2", "data": null},
                        "sendContent": {"id": 5, "content": "你好"}
                    },
                    {
                        "id": 1003,
                        "actionType": 3,
                        "actionTypeText": "回复了你",
                        "createTime": "2026-08-01 10:00:00",
                        "senderUserInfo": {"uuid": 123, "nickname": "同学A", "avatar": "https://x/a.png"},
                        "topic": {"id": 3, "title": "帖子3", "data": {"imgs": ["https://x/b.png"]}}
                    }
                ]
            }}"""
        )

        assertEquals(132, page.count)
        assertEquals(3, page.rows.size)
        assertNotNull(page.rows.find { it.id == 1001L })
        assertNotNull(page.rows.find { it.id == 1002L })
        // senderUserInfo 为 JSON null → null；为“字段全 null 的对象”→ 空 MarketUser，
        // UI 均有“匿名同学”兜底（nickname.isNotBlank() 判断）
        assertNull(page.rows.find { it.id == 1001L }?.senderUserInfo)
        assertEquals(
            MarketUser(uuid = 0L, nickname = "", avatar = ""),
            page.rows.find { it.id == 1002L }?.senderUserInfo
        )
    }

    @Test
    fun `count and page fall back to response values`() {
        val page = parseNotices(
            """{"status":"success","data":{"count":7,"page":2,"rows":[{"id":9,"actionType":6,"createTime":"2026-07-29 09:00:00"}]}}""",
            fallbackPage = 1
        )
        assertEquals(7, page.count)
        assertEquals(2, page.page)
        assertEquals(1, page.rows.size)
    }
}
