package com.ahu_plus.ui.screen.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SeasonalThemeTest {

    @Test
    fun `anniversary window mid-September returns anniversary mood`() {
        val mood = SeasonalTheme.currentMood(LocalDate.of(2026, 9, 16))

        assertEquals(SeasonalTheme.Kind.Anniversary, mood?.kind)
    }

    @Test
    fun `anniversary takes priority over school start when windows overlap`() {
        // 9.14-9.17 同时落在开学季(9.1-9.21)内,校庆优先
        val mood = SeasonalTheme.currentMood(LocalDate.of(2026, 9, 15))

        assertEquals(SeasonalTheme.Kind.Anniversary, mood?.kind)
    }

    @Test
    fun `early September before anniversary is school start`() {
        val mood = SeasonalTheme.currentMood(LocalDate.of(2026, 9, 5))

        assertEquals(SeasonalTheme.Kind.SchoolStart, mood?.kind)
    }

    @Test
    fun `late February spring semester start is school start`() {
        val mood = SeasonalTheme.currentMood(LocalDate.of(2026, 2, 27))

        assertEquals(SeasonalTheme.Kind.SchoolStart, mood?.kind)
    }

    @Test
    fun `January finals window is exam week`() {
        val mood = SeasonalTheme.currentMood(LocalDate.of(2026, 1, 12))

        assertEquals(SeasonalTheme.Kind.ExamWeek, mood?.kind)
    }

    @Test
    fun `June finals window is exam week`() {
        val mood = SeasonalTheme.currentMood(LocalDate.of(2026, 6, 18))

        assertEquals(SeasonalTheme.Kind.ExamWeek, mood?.kind)
    }

    @Test
    fun `ordinary day outside any window has no mood`() {
        val mood = SeasonalTheme.currentMood(LocalDate.of(2026, 4, 10))

        assertNull(mood)
    }

    @Test
    fun `mood carries non-blank title subtitle and emoji`() {
        val mood = SeasonalTheme.currentMood(LocalDate.of(2026, 9, 16))!!

        assert(mood.title.isNotBlank())
        assert(mood.subtitle.isNotBlank())
        assert(mood.emoji.isNotBlank())
    }
}
