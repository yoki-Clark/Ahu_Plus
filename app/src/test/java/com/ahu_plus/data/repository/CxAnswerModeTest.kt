package com.ahu_plus.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CxAnswerModeTest {
    @Test
    fun `skip mode never queries or submits answers`() {
        assertFalse(CxAnswerMode.SKIP.shouldQueryAnswers)
        assertFalse(CxAnswerMode.SKIP.shouldSubmit)
    }

    @Test
    fun `stored values map to explicit modes and unknown values fail closed`() {
        assertTrue(CxAnswerMode.fromSetting("auto") === CxAnswerMode.AUTO)
        assertTrue(CxAnswerMode.fromSetting("SAVE") === CxAnswerMode.SAVE)
        assertTrue(CxAnswerMode.fromSetting("skip") === CxAnswerMode.SKIP)
        assertTrue(CxAnswerMode.fromSetting("unexpected") === CxAnswerMode.SKIP)
        assertTrue(CxAnswerMode.fromSetting(null) === CxAnswerMode.SKIP)
    }
}
