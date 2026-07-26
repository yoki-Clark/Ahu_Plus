package com.ahu_plus.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ahu_plus.ui.screen.grade.Score60
import com.ahu_plus.ui.screen.grade.Score70
import com.ahu_plus.ui.screen.grade.Score80
import com.ahu_plus.ui.screen.grade.Score90
import com.ahu_plus.ui.screen.grade.ScoreNa
import com.ahu_plus.ui.screen.grade.scoreTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守住「固定色在深色主题里读不出来」这类回归。
 *
 * [AhuToneColor.current] 本身是 @Composable(要读 MaterialTheme),JVM 单测碰不到,
 * 所以这里测两件能测的:分档边界正确,以及每个分档的深色档确实比浅色档亮
 * —— 后者就是当初 bug 的形状:深色档忘了填 / 直接抄了浅色值。
 */
class AhuToneColorTest {

    private val bands = mapOf(
        "Score90" to Score90,
        "Score80" to Score80,
        "Score70" to Score70,
        "Score60" to Score60,
        "ScoreNa" to ScoreNa,
    )

    @Test
    fun `每个分档的深色档都比浅色档亮`() {
        bands.forEach { (name, tone) ->
            assertTrue(
                "$name 的 dark(${tone.dark}) 不比 light(${tone.light}) 亮," +
                    "深色主题下会看不清",
                tone.dark.luminance() > tone.light.luminance(),
            )
        }
    }

    @Test
    fun `分档边界按 90 80 70 60 切分`() {
        assertEquals(Score90, scoreTone(100.0))
        assertEquals(Score90, scoreTone(90.0))
        assertEquals(Score80, scoreTone(89.9))
        assertEquals(Score80, scoreTone(80.0))
        assertEquals(Score70, scoreTone(79.9))
        assertEquals(Score70, scoreTone(70.0))
        assertEquals(Score60, scoreTone(69.9))
        assertEquals(Score60, scoreTone(60.0))
        assertEquals(ScoreNa, scoreTone(59.9))
        assertEquals(ScoreNa, scoreTone(0.0))
    }

    @Test
    fun `没有成绩走 ScoreNa`() {
        assertEquals(ScoreNa, scoreTone(null))
    }

    @Test
    fun `AhuToneColor 是值语义`() {
        val a = AhuToneColor(Color(0xFF112233), Color(0xFFAABBCC))
        val b = AhuToneColor(Color(0xFF112233), Color(0xFFAABBCC))
        assertEquals(a, b)
    }
}
