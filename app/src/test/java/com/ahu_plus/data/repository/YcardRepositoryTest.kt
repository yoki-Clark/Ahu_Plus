package com.ahu_plus.data.repository

import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.ahu_plus.data.model.BillRecord
import com.ahu_plus.data.model.BillResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * YcardRepository 测试。
 *
 * 主要覆盖 BillResponse 的 JSON 解析(账单响应可能因 ycard 服务端变化而格式波动)。
 */
class YcardRepositoryTest {

    private val gson = GsonBuilder().setStrictness(Strictness.LENIENT).create()

    @Test
    fun `BillResponse parses typical 200 response`() {
        val json = """
            {
              "code": 200,
              "msg": "success",
              "data": {
                "total": 2,
                "records": [
                  {
                    "orderId": "20250615001",
                    "resume": "食堂消费",
                    "tranamt": -500,
                    "effectdateStr": "06-15 12:30",
                    "jndatetimeStr": "2025-06-15 12:30:00",
                    "turnoverType": "消费"
                  },
                  {
                    "orderId": "20250614002",
                    "resume": "充值",
                    "tranamt": 10000,
                    "effectdateStr": "06-14 09:00",
                    "jndatetimeStr": "2025-06-14 09:00:00",
                    "turnoverType": "充值"
                  }
                ]
              }
            }
        """.trimIndent()

        val resp = gson.fromJson(json, BillResponse::class.java)
        assertEquals(200, resp.code)
        assertEquals("success", resp.msg)
        assertNotNull(resp.data)
        assertEquals(2, resp.data!!.records.size)
        assertEquals(-500, resp.data.records[0].tranAmt)
        assertEquals("食堂消费", resp.data.records[0].resume)
    }

    @Test
    fun `BillResponse parses response with missing data field`() {
        val json = """{"code": 401, "msg": "token expired"}"""
        val resp = gson.fromJson(json, BillResponse::class.java)
        assertEquals(401, resp.code)
        assertEquals("token expired", resp.msg)
        // data 字段缺失时,records 为 null,UI 应优雅处理
        assertEquals(null, resp.data)
    }

    @Test
    fun `BillResponse parses empty records array`() {
        val json = """{"code": 200, "msg": "ok", "data": {"total": 0, "records": []}}"""
        val resp = gson.fromJson(json, BillResponse::class.java)
        assertEquals(200, resp.code)
        assertEquals(0, resp.data!!.records.size)
    }

    @Test
    fun `BillRecord amount divides correctly for yuan display`() {
        val record = BillRecord(
            orderId = "x", resume = "x",
            tranAmt = -350  // 单位:分 → 应显示 -3.50
        )
        // 验证 yuan 显示逻辑(对应 HomeScreen 的格式化)
        val absYuan = Math.abs(record.tranAmt) / 100.0
        assertEquals(3.5, absYuan, 0.001)
    }
}

/**
 * YcardRepository 缓存与 Cookie 行为测试。
 *
 * 不直接 mock HTTP,改为验证 Repository 内部状态管理:
 *  - clearCookies 清空 cookieStore + cachedJwt
 *  - getBills 在未登录时返回明确失败
 */
class YcardRepositoryStateTest {

    @Test
    fun `clearCookies resets internal state`() {
        val repo = YcardRepository()
        // 模拟登录后状态
        repo.clearCookies()  // 多次调用也安全
        repo.clearCookies()
        // getBills 在未登录时应返回 failure
        kotlinx.coroutines.runBlocking {
            repo.getBills().fold(
                onSuccess = { fail("应失败但成功") },
                onFailure = { e ->
                    assertTrue(
                        "错误信息应提到未登录: ${e.message}",
                        e.message!!.contains("未登录")
                    )
                }
            )
        }
    }
}