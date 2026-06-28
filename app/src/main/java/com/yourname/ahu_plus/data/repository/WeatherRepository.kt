package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.yourname.ahu_plus.BuildConfig
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.weather.AirQualityCurrent
import com.yourname.ahu_plus.data.model.weather.WeatherAirQuality
import com.yourname.ahu_plus.data.model.weather.WeatherCurrent
import com.yourname.ahu_plus.data.model.weather.WeatherDaily
import com.yourname.ahu_plus.data.model.weather.WeatherFeed
import com.yourname.ahu_plus.data.model.weather.WeatherHourly
import com.yourname.ahu_plus.data.network.ResilientDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Open-Meteo 公开天气 API 数据源 (零登录)。
 *
 * 数据流 (与 ExamDataRepository / AnnouncementRepository 同构):
 *   api.open-meteo.com / air-quality-api.open-meteo.com
 *     → WeatherRepository.fetchRemote()
 *     → 解析 forecast + air-quality 两端点 raw JSON, 合并成 WeatherFeed
 *     → SessionManager.saveWeatherJson()
 *
 * 网络配置照搬 AnnouncementRepository: HTTP/1.1 + COMPATIBLE_TLS + ResilientDns,
 * 绕开部分国产 ROM (vivo 等) 的 HTTP/2 / TLS 协商问题。
 *
 * 位置固定合肥市蜀山区 (安徽大学本部)。
 */
class WeatherRepository(
    private val sessionManager: SessionManager,
    private val latitude: Double = DEFAULT_LATITUDE,
    private val longitude: Double = DEFAULT_LONGITUDE,
) {
    private val gson = GsonProvider.instance

    /** 策略 A: HTTP/1.1 + COMPATIBLE_TLS + 手动 302。 */
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionSpecs(listOf(ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
        .dns(ResilientDns)
        .build()

    /** 策略 B: OkHttp 默认 (HTTP/2 + MODERN_TLS) 兜底。 */
    private val fallbackClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dns(ResilientDns)
        .build()

    private val userAgent = "Ahu_Plus/${BuildConfig.VERSION_NAME} (Android)"

    companion object {
        private const val TAG = "WeatherRepo"

        /** 合肥市蜀山区 (安徽大学本部) */
        const val DEFAULT_LATITUDE = 31.82
        const val DEFAULT_LONGITUDE = 117.39
        const val CITY_LABEL = "合肥蜀山区"

        /**
         * 固定用 GFS Seamless 模型。
         *
         * 默认的 best_match ensemble 会把 ECMWF IFS025 (对国内 0 mm 降水日仍报 code 99
         * 雷暴冰雹) 的"最严重码"选出来, 导致合肥天天雷暴/雷暴冰雹, 严重失真。
         * GFS Seamless 在国内 5 日预报更克制, 与中国天气/彩云等平台一致得多。
         * cma_grapes_global 虽然是中国气象局原生, 但只支持 3-4 天。
         */
        const val FORECAST_MODEL = "gfs_seamless"

        /** forecast 端点 — 当前 + 小时预报 + 5 天预报 */
        val FORECAST_URL: String = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=$DEFAULT_LATITUDE&longitude=$DEFAULT_LONGITUDE")
            append("&models=$FORECAST_MODEL")
            append("&current=temperature_2m,apparent_temperature,relative_humidity_2m,")
            append("weather_code,wind_speed_10m,wind_direction_10m")
            append("&hourly=temperature_2m,weather_code,precipitation_probability")
            append("&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum")
            append("&timezone=Asia/Shanghai&forecast_days=5")
        }

        /** air-quality 端点 — 当前空气质量 */
        val AIR_QUALITY_URL: String = buildString {
            append("https://air-quality-api.open-meteo.com/v1/air-quality")
            append("?latitude=$DEFAULT_LATITUDE&longitude=$DEFAULT_LONGITUDE")
            append("&current=pm2_5,pm10,us_aqi,european_aqi")
            append("&timezone=Asia/Shanghai")
        }
    }

    // ─────────────────────────────────────────────────────────
    // 拉取与缓存
    // ─────────────────────────────────────────────────────────

    /**
     * 从 Open-Meteo 拉取 forecast + air-quality, 合并后写入缓存。
     * 失败时返回 Result.failure, 不修改已有缓存。
     */
    suspend fun fetchRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        // 串行: 先 forecast (主), 再 air-quality (辅)。
        // 任一失败不破坏另一的成功缓存。
        val forecastBody = fetchOne(FORECAST_URL) ?: return@withContext Result.failure(
            Exception("forecast 端点失败")
        )
        val airQualityBody = fetchOne(AIR_QUALITY_URL)

        try {
            persist(forecastBody, airQualityBody)
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "合并/落盘失败: ${t.message}", t)
            Result.failure(t)
        }
    }

    /** 策略 A → 策略 B, 任一成功即返回 body; 都失败返回 null。 */
    private fun fetchOne(url: String): String? {
        // 策略 A
        runCatching { fetchWithManualRedirect(client, url) }.onSuccess { return it }
            .onFailure { Log.w(TAG, "策略 A 失败 ($url): ${it.message}") }
        // 策略 B
        return runCatching { fetchWithAutoRedirect(fallbackClient, url) }
            .onFailure { Log.w(TAG, "策略 B 失败 ($url): ${it.message}") }
            .getOrNull()
    }

    private fun fetchWithManualRedirect(client: OkHttpClient, startUrl: String): String {
        var currentUrl = startUrl.toHttpUrl()
        var redirectCount = 0
        val maxRedirects = 5

        while (true) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, */*;q=0.8")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isRedirect) {
                val location = response.header("Location")
                    ?: error("HTTP ${response.code} 但无 Location 头")
                response.close()
                currentUrl = currentUrl.resolve(location) ?: error("无法解析 Location: $location")
                redirectCount++
                check(redirectCount <= maxRedirects) { "重定向超过 $maxRedirects 次" }
                continue
            }

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                error("HTTP $code (final=$currentUrl)")
            }

            return try {
                response.body?.string() ?: error("empty body")
            } finally {
                response.close()
            }
        }
    }

    private fun fetchWithAutoRedirect(client: OkHttpClient, url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, */*;q=0.8")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            error("HTTP $code (final=${response.request.url})")
        }
        return try {
            response.body?.string() ?: error("empty body")
        } finally {
            response.close()
        }
    }

    /** 合并两端点 raw JSON, 序列化成 WeatherFeed 字符串落盘。 */
    private suspend fun persist(forecastBody: String, airQualityBody: String?) {
        val forecast = gson.fromJson(forecastBody, ForecastRaw::class.java)
            ?: error("forecast JSON 无效")

        val airQuality: WeatherAirQuality? = airQualityBody?.let {
            runCatching {
                val raw = gson.fromJson(it, AirQualityRaw::class.java)
                WeatherAirQuality(
                    current = AirQualityCurrent(
                        pm25 = raw.current?.pm25 ?: 0.0,
                        pm10 = raw.current?.pm10 ?: 0.0,
                        usAqi = raw.current?.usAqi ?: 0,
                        euAqi = raw.current?.euAqi ?: 0,
                    )
                )
            }.getOrNull()
        }

        val feed = WeatherFeed(
            city = CITY_LABEL,
            latitude = forecast.latitude ?: DEFAULT_LATITUDE,
            longitude = forecast.longitude ?: DEFAULT_LONGITUDE,
            timezone = forecast.timezone ?: "Asia/Shanghai",
            fetchedAt = System.currentTimeMillis(),
            current = WeatherCurrent(
                temperature = forecast.current?.temperature ?: 0.0,
                apparentTemperature = forecast.current?.apparentTemperature ?: 0.0,
                humidity = forecast.current?.humidity ?: 0,
                weatherCode = forecast.current?.weatherCode ?: 0,
                windSpeed = forecast.current?.windSpeed ?: 0.0,
                windDirection = forecast.current?.windDirection ?: 0,
            ),
            hourly = WeatherHourly(
                time = forecast.hourly?.time ?: emptyList(),
                temperature = forecast.hourly?.temperature ?: emptyList(),
                weatherCode = forecast.hourly?.weatherCode ?: emptyList(),
                precipitationProbability = forecast.hourly?.precipProbability ?: emptyList(),
            ),
            daily = WeatherDaily(
                time = forecast.daily?.time ?: emptyList(),
                weatherCode = forecast.daily?.weatherCode ?: emptyList(),
                tempMax = forecast.daily?.tempMax ?: emptyList(),
                tempMin = forecast.daily?.tempMin ?: emptyList(),
                precipitationSum = forecast.daily?.precipSum ?: emptyList(),
            ),
            airQuality = airQuality,
        )

        sessionManager.saveWeatherJson(gson.toJson(feed))
        Log.i(TAG, "✓ 天气更新: ${feed.current.temperature}°C, code=${feed.current.weatherCode}, " +
                "PM2.5=${airQuality?.current?.pm25}")
    }

    // ─────────────────────────────────────────────────────────
    // 缓存读取
    // ─────────────────────────────────────────────────────────

    /** 同步读取缓存; 无缓存或解析失败返回 null。 */
    fun getCached(): WeatherFeed? {
        val json = sessionManager.getWeatherJson() ?: return null
        return runCatching { gson.fromJson(json, WeatherFeed::class.java) }.getOrNull()
    }

    fun getCachedAgeMillis(): Long {
        val ts = sessionManager.getWeatherFetchedAt()
        return if (ts == 0L) Long.MAX_VALUE else System.currentTimeMillis() - ts
    }
}

// ── 内部 raw 类型 (Open-Meteo 真实响应形状) ─────────────────────

private data class ForecastRaw(
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("timezone") val timezone: String? = null,
    @SerializedName("current") val current: ForecastCurrentRaw? = null,
    @SerializedName("hourly") val hourly: ForecastHourlyRaw? = null,
    @SerializedName("daily") val daily: ForecastDailyRaw? = null,
)

private data class ForecastCurrentRaw(
    @SerializedName("time") val time: String? = null,
    @SerializedName("temperature_2m") val temperature: Double? = null,
    @SerializedName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerializedName("relative_humidity_2m") val humidity: Int? = null,
    @SerializedName("weather_code") val weatherCode: Int? = null,
    @SerializedName("wind_speed_10m") val windSpeed: Double? = null,
    @SerializedName("wind_direction_10m") val windDirection: Int? = null,
)

private data class ForecastHourlyRaw(
    @SerializedName("time") val time: List<String> = emptyList(),
    @SerializedName("temperature_2m") val temperature: List<Double> = emptyList(),
    @SerializedName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerializedName("precipitation_probability") val precipProbability: List<Int> = emptyList(),
)

private data class ForecastDailyRaw(
    @SerializedName("time") val time: List<String> = emptyList(),
    @SerializedName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerializedName("temperature_2m_max") val tempMax: List<Double> = emptyList(),
    @SerializedName("temperature_2m_min") val tempMin: List<Double> = emptyList(),
    @SerializedName("precipitation_sum") val precipSum: List<Double> = emptyList(),
)

private data class AirQualityRaw(
    @SerializedName("current") val current: AirQualityCurrentRaw? = null,
)

private data class AirQualityCurrentRaw(
    @SerializedName("pm2_5") val pm25: Double? = null,
    @SerializedName("pm10") val pm10: Double? = null,
    @SerializedName("us_aqi") val usAqi: Int? = null,
    @SerializedName("european_aqi") val euAqi: Int? = null,
)