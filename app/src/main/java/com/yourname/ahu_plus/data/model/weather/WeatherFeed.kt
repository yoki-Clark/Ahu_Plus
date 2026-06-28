package com.yourname.ahu_plus.data.model.weather

import com.google.gson.annotations.SerializedName

/**
 * 天气数据 Feed。
 *
 * 数据源:
 *  - https://api.open-meteo.com/v1/forecast  (current / hourly / daily)
 *  - https://air-quality-api.open-meteo.com/v1/air-quality  (airQuality)
 *
 * 两个端点分别拉取后, 各取所需子段拼成此结构 (forecast 端的 current/hourly/daily + air-quality 端的 current)。
 * 全部字段给默认值, 保证部分端点失败时仍能解析。
 */
data class WeatherFeed(
    @SerializedName("version") val version: Int = SCHEMA_VERSION,
    @SerializedName("city") val city: String = "",
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("timezone") val timezone: String = "Asia/Shanghai",
    @SerializedName("fetchedAt") val fetchedAt: Long = 0L,
    @SerializedName("current") val current: WeatherCurrent = WeatherCurrent(),
    @SerializedName("hourly") val hourly: WeatherHourly = WeatherHourly(),
    @SerializedName("daily") val daily: WeatherDaily = WeatherDaily(),
    @SerializedName("airQuality") val airQuality: WeatherAirQuality? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

data class WeatherCurrent(
    @SerializedName("temperature_2m") val temperature: Double = 0.0,
    @SerializedName("apparent_temperature") val apparentTemperature: Double = 0.0,
    @SerializedName("relative_humidity_2m") val humidity: Int = 0,
    @SerializedName("weather_code") val weatherCode: Int = 0,
    @SerializedName("wind_speed_10m") val windSpeed: Double = 0.0,
    @SerializedName("wind_direction_10m") val windDirection: Int = 0,
)

/**
 * hourly data 是数组结构, Open-Meteo 返回 time[] + 各变量 parallel 数组。
 * 这里转成按小时索引的 List, 简化上层取数。
 */
data class WeatherHourly(
    @SerializedName("time") val time: List<String> = emptyList(),
    @SerializedName("temperature_2m") val temperature: List<Double> = emptyList(),
    @SerializedName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerializedName("precipitation_probability") val precipitationProbability: List<Int> = emptyList(),
) {
    /** 索引到 hour (0-23), 找不到返回 -1。 */
    fun indexAtHour(hourOfDay: Int): Int =
        time.indexOfFirst { it.length >= 13 && it.substring(11, 13).toIntOrNull() == hourOfDay }
}

/**
 * daily 也是数组, 按天索引。
 */
data class WeatherDaily(
    @SerializedName("time") val time: List<String> = emptyList(),
    @SerializedName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerializedName("temperature_2m_max") val tempMax: List<Double> = emptyList(),
    @SerializedName("temperature_2m_min") val tempMin: List<Double> = emptyList(),
    @SerializedName("precipitation_sum") val precipitationSum: List<Double> = emptyList(),
)

/** air-quality-api.open-meteo.com 返回结构。 */
data class WeatherAirQuality(
    @SerializedName("current") val current: AirQualityCurrent = AirQualityCurrent(),
)

data class AirQualityCurrent(
    @SerializedName("pm2_5") val pm25: Double = 0.0,
    @SerializedName("pm10") val pm10: Double = 0.0,
    @SerializedName("us_aqi") val usAqi: Int = 0,
    @SerializedName("european_aqi") val euAqi: Int = 0,
)