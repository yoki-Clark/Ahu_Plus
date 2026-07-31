package com.ahu_plus.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.ui.theme.AhuGradient
import com.ahu_plus.ui.theme.AhuShapes
import java.time.LocalDate
import java.time.MonthDay

/**
 * 季节/节日彩蛋(批次四·10)。
 *
 * 决策 D6:仅 3 个触发节点 -- 开学季 / 考试周 / 校庆(9 月),不做全节日。
 * 纯氛围,不改功能:在首页以 banner 形式呈现对应文案 + 品牌渐变底。
 *
 * 日期判断走 [SeasonalTheme](纯逻辑,不依赖 Compose,可单测);UI 渲染见 [SeasonalBanner]。
 *
 * 注:校庆日取安大校庆(9 月)附近窗口;考试周为大致期末窗口,非精确校历,
 * 后续可接教务学期数据精化。
 */
object SeasonalTheme {

    /** 彩蛋类型。 */
    sealed class Kind(val key: String) {
        object SchoolStart : Kind("school_start")
        object ExamWeek : Kind("exam_week")
        object Anniversary : Kind("anniversary")
    }

    /** 当前命中的彩蛋氛围。 */
    data class Mood(
        val kind: Kind,
        val title: String,
        val subtitle: String,
        val emoji: String,
    )

    /**
     * 返回 [date] 当前的彩蛋氛围;不在任何窗口内返回 null。
     *
     * 优先级:校庆 > 开学季 > 考试周(9 月校庆窗口可能与开学季重叠,校庆优先)。
     */
    fun currentMood(date: LocalDate = DebugClock.todayDate()): Mood? {
        val md = MonthDay.of(date.monthValue, date.dayOfMonth)
        return when {
            md in range(9, 14, 9, 17) -> Mood(
                Kind.Anniversary, "安大校庆", "祝安大生日快乐", "🎂",
            )
            md in range(9, 1, 9, 21) || md in range(2, 25, 3, 10) -> Mood(
                Kind.SchoolStart, "新学期加油", "新学期 · 新起点", "🎒",
            )
            md in range(1, 6, 1, 19) || md in range(6, 10, 6, 25) -> Mood(
                Kind.ExamWeek, "期末考试周", "逢考必过 · 加油", "✍️",
            )
            else -> null
        }
    }

    /** [MonthDay] 闭区间,支持跨月(如 2.25..3.10)。 */
    private fun range(startMonth: Int, startDay: Int, endMonth: Int, endDay: Int): ClosedRange<MonthDay> =
        MonthDay.of(startMonth, startDay)..MonthDay.of(endMonth, endDay)
}

/**
 * 季节彩蛋 banner。无氛围时由调用方决定不渲染(避免空白 item 占间距)。
 */
@Composable
fun SeasonalBanner(
    mood: SeasonalTheme.Mood,
    modifier: Modifier = Modifier,
) {
    val gradient = when (mood.kind) {
        SeasonalTheme.Kind.SchoolStart -> AhuGradient.Blue.brush
        SeasonalTheme.Kind.ExamWeek -> AhuGradient.Orange.brush
        SeasonalTheme.Kind.Anniversary -> AhuGradient.Violet.brush
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AhuShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .background(gradient)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = mood.emoji,
                style = MaterialTheme.typography.headlineMedium,
            )
            Column {
                Text(
                    text = mood.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = mood.subtitle,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
