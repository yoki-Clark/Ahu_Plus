package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.model.jw.TrainingPlanResponse
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import okhttp3.OkHttpClient
import okhttp3.Request

/** 培养方案仓库。端点: GET .../get-all-course-module?programId=3000 */
class TrainingPlanRepository(private val jwAuthRepository: JwAuthRepository) {
    private val gson = GsonProvider.instance
    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwAuthRepository.jwCookieJar, followRedirects = false, disableGzip = false,
        extraInterceptors = listOf(okhttp3.Interceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", UA).header("x-requested-with", "XMLHttpRequest").build())
        })
    )

    suspend fun getTrainingPlan(): Result<TrainingPlanResponse> = try {
        val url = "$JW_BASE/student/for-std/credit-certification-apply/other_apply/get-all-course-module?programId=$DEFAULT_PROGRAM_ID"
        Log.i(TAG, "请求培养方案: programId=$DEFAULT_PROGRAM_ID")
        val request = Request.Builder().url(url).get()
            .header("Accept", "application/json, text/javascript, */*; q=0.01").build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (response.code == 302) throw SessionExpiredException()
            if (!response.isSuccessful) throw Exception("培养方案查询失败: HTTP ${response.code}")
            if (body.isBlank() || body[0] != '{') throw Exception("培养方案接口返回非 JSON: ${body.take(200)}")
            val parsed = gson.fromJson(body, TrainingPlanResponse::class.java)
            Log.i(TAG, "培养方案: ${parsed.children?.size ?: 0} 一级模块, 总要求=${parsed.sumChildrenRequiredCreditsOrZero}")
            Result.success(parsed)
        }
    } catch (e: Exception) { Log.e(TAG, "培养方案失败", e); Result.failure(e) }

    companion object {
        private const val TAG = "TrainingPlanRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"
        const val DEFAULT_PROGRAM_ID = 3000
    }
}
