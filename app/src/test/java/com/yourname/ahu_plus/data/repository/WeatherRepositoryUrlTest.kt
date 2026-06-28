package com.yourname.ahu_plus.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定 WeatherRepository 的 endpoint 选择。
 *
 * 2026-06-29 复盘: 默认 best_match ensemble 选 ECMWF 的"最严重码",
 * 导致合肥 0 mm 降水日也被报成 95/96 雷暴/雷暴冰雹。修法是显式指定模型。
 * 改 model 时务必同步改这里, 否则下次换默认又会翻车。
 */
class WeatherRepositoryUrlTest {

    @Test
    fun `forecast url pins a specific model to avoid best_match thunderstorm over-prediction`() {
        val url = WeatherRepository.FORECAST_URL
        assertTrue(
            "FORECAST_URL 必须显式指定 models=, 不能放任 Open-Meteo 默认 best_match (在国内会雷暴过激): $url",
            url.contains("models="),
        )
        // 旧默认: 不带 models= 时是 best_match ensemble。
        assertFalse(
            "FORECAST_URL 不应留 best_match 默认: $url",
            "best_match" in WeatherRepository.FORECAST_MODEL,
        )
    }

    @Test
    fun `forecast url keeps 5 day forecast and Asia Shanghai timezone`() {
        val url = WeatherRepository.FORECAST_URL
        assertTrue("应保持 5 天预报: $url", url.contains("forecast_days=5"))
        assertTrue("应锁定 Asia/Shanghai 时区: $url", url.contains("timezone=Asia/Shanghai"))
    }
}
