package com.ahu_plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu_plus.data.model.jw.CourseDisplayItem
import com.ahu_plus.data.model.jw.CourseUnit
import com.ahu_plus.data.model.weather.WeatherFeed
import com.ahu_plus.data.weather.WeatherCode
import com.ahu_plus.data.weather.WeatherManager

/**
 * 首页"今日课程"卡片右侧 30% 区域 + 独立天气屏顶部 共用的天气小卡。
 *
 * 始终显示: 天气图标 + 当前温度 + 今日 min~max。
 * 有课时 (currentCourse 或 nextCourse): 多显示一行上下课天气提醒 (带伞 / 戴口罩)。
 *
 * 无数据时显示 "—" 占位, 仍可点击进入 WeatherScreen。
 *
 * @param weather  最新天气数据 (null → 占位)
 * @param classToWarn  用于生成天气提醒的"接下来这门课" (current 或 next)
 * @param unitTimes 课程节次时间表 (用于反查上下课分钟)
 * @param manager   WeatherManager 实例, 提供 getClassWarning(course, unitTimes)
 * @param onClick  点击进入独立 WeatherScreen
 */
@Composable
fun WeatherPanel(
    weather: WeatherFeed?,
    classToWarn: CourseDisplayItem?,
    unitTimes: List<CourseUnit>,
    manager: WeatherManager?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (weather == null) {
            // 占位: "—" + "刷新中" 提示
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = WeatherCode.icon(0),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = "—°",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "天气加载中",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
            )
            return@Column
        }

        val currentCode = weather.current.weatherCode
        val temp = weather.current.temperature.toInt()
        val todayMin = weather.daily.tempMin.firstOrNull()?.toInt()
        val todayMax = weather.daily.tempMax.firstOrNull()?.toInt()
        val rangeText = if (todayMin != null && todayMax != null) "$todayMin°~$todayMax°" else "—"

        // 图标 + 温度行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = WeatherCode.icon(currentCode),
                contentDescription = WeatherCode.describe(currentCode),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "${temp}°",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = rangeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
        )

        // 上下课天气提醒
        if (classToWarn != null && manager != null) {
            val warning = manager.getClassWarning(classToWarn, unitTimes)
            if (!warning.isNullOrBlank()) {
                Text(
                    text = warning,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
            }
        }
    }
}