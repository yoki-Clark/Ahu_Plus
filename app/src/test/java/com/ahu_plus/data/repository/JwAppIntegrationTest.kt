package com.ahu_plus.data.repository

import com.ahu_plus.data.model.jwapp.JwAppCourse
import com.ahu_plus.data.model.jwapp.JwAppLesson
import com.ahu_plus.data.model.jwapp.JwAppSchedule
import com.ahu_plus.data.model.jwapp.JwAppScheduleRoom
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwAppIntegrationTest {
    @Test
    fun `JWT expiry uses exp claim`() {
        val valid = tokenWithExp(2_000)
        val expired = tokenWithExp(900)

        assertFalse(JwAppAuthRepository.isExpired(valid, nowSeconds = 1_000))
        assertTrue(JwAppAuthRepository.isExpired(expired, nowSeconds = 1_000))
        assertTrue(JwAppAuthRepository.isExpired("not-a-jwt", nowSeconds = 1_000))
    }

    @Test
    fun `password encryption produces a PKCS1 RSA block`() {
        val encrypted = JwAppAuthRepository.encryptPassword("example-password")

        assertNotEquals("example-password", encrypted)
        assertEquals(128, encrypted.decodeBase64()?.size)
    }

    @Test
    fun `room mapping drops other rooms and combines weeks for the same slot`() {
        val selectedRoom = 259L
        val lesson = JwAppLesson(
            id = 197983,
            course = JwAppCourse(code = "ZX34155", nameZh = "食品包装学", credits = 2.0),
            teacherAssignmentList = listOf("黄老师"),
            schedules = listOf(
                schedule(roomId = selectedRoom, week = 12),
                schedule(roomId = selectedRoom, week = 14),
                schedule(roomId = 1065, week = 13),
            ),
        )

        val items = RoomCourseTableRepository.mapLessonsToDisplayItems(
            roomId = selectedRoom,
            roomName = "笃行北楼B101",
            lessons = listOf(lesson),
        )

        assertEquals(1, items.size)
        assertEquals(listOf(12, 14), items.single().weekIndexes)
        assertEquals("食品包装学", items.single().courseName)
        assertEquals("19:00", items.single().startTime)
    }

    private fun schedule(roomId: Long, week: Int) = JwAppSchedule(
        weekday = 1,
        startTime = 1900,
        endTime = 2125,
        teacherName = "黄老师",
        room = JwAppScheduleRoom(id = roomId, nameZh = "教室"),
        startUnit = 11,
        endUnit = 13,
        weekIndex = week,
    )

    private fun tokenWithExp(exp: Long): String {
        val payload = "{\"exp\":$exp}".encodeUtf8().base64Url().trimEnd('=')
        return "header.$payload.signature"
    }
}
