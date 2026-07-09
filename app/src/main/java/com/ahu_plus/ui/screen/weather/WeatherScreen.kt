package com.ahu_plus.ui.screen.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.weather.WeatherFeed
import com.ahu_plus.data.repository.WeatherRepository
import com.ahu_plus.data.weather.WeatherCode
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.components.CenteredError
import com.ahu_plus.ui.components.CenteredLoader
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val feed by viewModel.feed.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("天气 · 合肥蜀山区") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isLoading && feed == null -> CenteredLoader()
                uiState.error != null && feed == null ->
                    CenteredError(message = uiState.error!!, onRetry = viewModel::refresh)
                feed == null ->
                    CenteredError(message = "暂无天气数据", onRetry = viewModel::refresh)
                else -> WeatherContent(feed = feed!!)
            }
        }
    }
}

@Composable
private fun WeatherContent(feed: WeatherFeed) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CurrentWeatherCard(feed = feed) }
        item { AirQualityCard(feed = feed) }
        item { HourlyForecastCard(feed = feed) }
        item { DailyForecastCard(feed = feed) }
        item {
            Text(
                text = "数据来源: Open-Meteo · ${WeatherRepository.FORECAST_MODEL} · 更新于 ${formatAge(feed.fetchedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CurrentWeatherCard(feed: WeatherFeed) {
    val current = feed.current
    val code = current.weatherCode
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = WeatherCode.icon(code),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column {
                    Text(
                        text = "${current.temperature.toInt()}°",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = WeatherCode.describe(code) +
                                " · 体感 ${current.apparentTemperature.toInt()}°",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WeatherMetric(
                    icon = Icons.Filled.WaterDrop,
                    label = "湿度",
                    value = "${current.humidity}%",
                )
                WeatherMetric(
                    icon = Icons.Filled.Air,
                    label = "风速",
                    value = "%.1f m/s".format(current.windSpeed),
                )
            }
        }
    }
}

@Composable
private fun WeatherMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
        )
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun AirQualityCard(feed: WeatherFeed) {
    val a = feed.airQuality?.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "空气质量",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (a == null) {
                Text(
                    text = "数据获取失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "PM2.5 ${"%.0f".format(a.pm25)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "PM10 ${"%.0f".format(a.pm10)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "AQI ${a.euAqi}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val (status, color) = when {
                a.pm25 > 75 -> "污染较高,建议戴口罩" to MaterialTheme.colorScheme.error
                a.pm25 > 35 -> "轻度污染" to Color(0xFFFFA726)
                else -> "空气良好" to Color(0xFF66BB6A)
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HourlyForecastCard(feed: WeatherFeed) {
    val timeFormatter = DateTimeFormatter.ofPattern("H:00")
    var showPopInfo by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "未来 24 小时",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val nowHour = java.time.LocalTime.now().hour
            val hours = feed.hourly.time.indices.filter { it >= nowHour && it < nowHour + 24 }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                hours.forEach { idx ->
                    val t = feed.hourly.time.getOrNull(idx) ?: return@forEach
                    val hour = if (t.length >= 13) t.substring(11, 13).toIntOrNull() ?: idx else idx
                    val code = feed.hourly.weatherCode.getOrNull(idx) ?: 0
                    val temp = feed.hourly.temperature.getOrNull(idx)?.toInt() ?: 0
                    val pop = feed.hourly.precipitationProbability.getOrNull(idx) ?: 0
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.width(56.dp),
                    ) {
                        Text(
                            text = "${hour}时",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Icon(
                            imageVector = WeatherCode.icon(code),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${temp}°",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        if (pop > 0) {
                            // 点击弹出说明: 这个蓝色数字是降雨概率 (PoP),不是湿度。
                            Text(
                                text = "$pop%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1E88E5),
                                modifier = Modifier.clickable { showPopInfo = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPopInfo) {
        AlertDialog(
            onDismissRequest = { showPopInfo = false },
            title = { Text("蓝色数字是什么", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "降雨概率 (PoP, Probability of Precipitation) — 当前小时 GFS 模型预测发生可观测降水的概率。\n\n" +
                            "数字越高,该小时越可能下雨;和具体下多大、下多久无关。\n\n" +
                            "数据来源: NOAA GFS,经 Open-Meteo 聚合。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showPopInfo = false }) {
                    Text("知道了")
                }
            },
        )
    }
}



@Composable
private fun DailyForecastCard(feed: WeatherFeed) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "未来 ${feed.daily.time.size} 天",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            feed.daily.time.forEachIndexed { idx, dateStr ->
                val code = feed.daily.weatherCode.getOrNull(idx) ?: 0
                val min = feed.daily.tempMin.getOrNull(idx)?.toInt() ?: 0
                val max = feed.daily.tempMax.getOrNull(idx)?.toInt() ?: 0
                val label = if (dateStr.length >= 10) "${dateStr.substring(5, 7)}/${dateStr.substring(8, 10)}" else dateStr
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(48.dp),
                    )
                    Icon(
                        imageVector = WeatherCode.icon(code),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = WeatherCode.describe(code),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$min° / $max°",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun formatAge(epochMs: Long): String {
    if (epochMs == 0L) return "—"
    val diffMin = (System.currentTimeMillis() - epochMs) / 60_000
    return when {
        diffMin < 1 -> "刚刚"
        diffMin < 60 -> "${diffMin} 分钟前"
        diffMin < 24 * 60 -> "${diffMin / 60} 小时前"
        else -> "${diffMin / (24 * 60)} 天前"
    }
}