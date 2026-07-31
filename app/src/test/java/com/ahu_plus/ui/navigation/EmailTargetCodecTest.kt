package com.ahu_plus.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [EmailTarget] 的 NavigationTargetCodec round-trip 测试。
 *
 * AGENTS.md 要求:新增 NavigationTarget 子类时必须同步补对应 round-trip 测试。
 * 此测试覆盖 toRecord/fromRecord 双向序列化,验证 EMAIL Tab 的栈持久化。
 */
class EmailTargetCodecTest {

    @Test
    fun `EmailTarget ROOT round trips through NavigationTargetCodec`() {
        val original = EmailTarget(EmailRoute.ROOT)
        val record = NavigationTargetCodec.toRecord(original)
        val restored = NavigationTargetCodec.fromRecord(record)
        assertEquals(original, restored)
    }

    @Test
    fun `EmailTarget INBOX with folderId round trips`() {
        val original = EmailTarget(
            route = EmailRoute.INBOX,
            folderId = "1",
        )
        val record = NavigationTargetCodec.toRecord(original)
        val restored = NavigationTargetCodec.fromRecord(record)
        assertEquals(original, restored)
    }

    @Test
    fun `EmailTarget DETAIL with mailId round trips`() {
        val original = EmailTarget(
            route = EmailRoute.DETAIL,
            mailId = "AAsAZABbKp06pUdXI5mjbKpz",
        )
        val record = NavigationTargetCodec.toRecord(original)
        val restored = NavigationTargetCodec.fromRecord(record)
        assertEquals(original, restored)
    }

    @Test
    fun `EmailTarget with both folderId and mailId round trips`() {
        val original = EmailTarget(
            route = EmailRoute.DETAIL,
            folderId = "1",
            mailId = "AAsAZABbKp06pUdXI5mjbKpz",
        )
        val record = NavigationTargetCodec.toRecord(original)
        val restored = NavigationTargetCodec.fromRecord(record)
        assertEquals(original, restored)
    }

    @Test
    fun `EmailTarget null args survive round trip`() {
        val original = EmailTarget(EmailRoute.COMPOSE, null, null)
        val record = NavigationTargetCodec.toRecord(original)
        val restored = NavigationTargetCodec.fromRecord(record)
        assertEquals(original, restored)
        // 进一步验证 cast 后的属性为 null
        val emailTarget = restored as? EmailTarget
        assertEquals(EmailRoute.COMPOSE, emailTarget?.route)
        assertNull(emailTarget?.folderId)
        assertNull(emailTarget?.mailId)
    }

    @Test
    fun `EmailTarget topLevel is EMAIL`() {
        val target = EmailTarget()
        assertEquals(TopLevelDestination.EMAIL, target.topLevel)
    }

    @Test
    fun `rootTarget for EMAIL returns EmailTarget ROOT`() {
        val root = rootTarget(TopLevelDestination.EMAIL)
        assertEquals(EmailTarget(EmailRoute.ROOT), root)
    }

    @Test
    fun `EMAIL Tab has initial stack with root EmailTarget`() {
        val state = MainNavigationState.initial()
        val emailStack = state.stacks[TopLevelDestination.EMAIL]
        assertEquals(listOf(EmailTarget(EmailRoute.ROOT)), emailStack)
    }

    @Test
    fun `EmailTarget survives snapshot round trip`() {
        val expected = MainNavigationState.initial()
            .navigate(NavigationRequest(EmailTarget(EmailRoute.INBOX, folderId = "1")))
        val restored = MainNavigationSnapshotCodec.decode(MainNavigationSnapshotCodec.encode(expected))
        assertEquals(expected, restored)
    }
}
