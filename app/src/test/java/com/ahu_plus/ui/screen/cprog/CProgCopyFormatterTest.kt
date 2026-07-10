package com.ahu_plus.ui.screen.cprog

import com.ahu_plus.data.model.CProgOption
import com.ahu_plus.data.model.CProgQuestionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CProgCopyFormatterTest {
    private val question = CProgQuestionItem(
        id = "q1",
        text = "Which option is correct?",
        answer = "B",
        knowledgeCaption = "Basics",
        options = listOf(
            CProgOption("A", "First"),
            CProgOption("B", "Second"),
        ),
        studentAnswer = "A",
        studentGrade = 0.0,
    )

    @Test
    fun `question copy contains prompt and options but no answers`() {
        val text = buildQuestionPromptCopy(index = 2, item = question)

        assertEquals(
            "第 2 题\nWhich option is correct?\n\nA. First\nB. Second",
            text,
        )
        assertFalse(text.contains("我的答案"))
        assertFalse(text.contains("参考答案"))
    }

    @Test
    fun `whole question copy contains submitted and reference answers`() {
        val text = buildWholeQuestionCopy(index = 2, item = question, isCorrection = false)

        assertTrue(text.contains("我的答案：\nA"))
        assertTrue(text.contains("参考答案：\nB"))
        assertFalse(text.contains("得分"))
    }
}
