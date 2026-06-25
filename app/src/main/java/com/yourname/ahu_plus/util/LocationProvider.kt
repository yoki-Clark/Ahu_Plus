package com.yourname.ahu_plus.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 轻量定位工具(2026-06-24)。
 *
 * 用系统 [LocationManager],不引入 Google Play Services,适配国产 ROM 无 GMS 的情况。
 * 优先返回 lastKnownLocation(秒回),没有则请求单次更新(GPS / 网络双 provider)。
 *
 * 调用方需先确保已授予 [Manifest.permission.ACCESS_FINE_LOCATION] 或 COARSE。
 */
object LocationProvider {

    /** 是否已授予定位权限(fine 或 coarse 任一即可) */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * 获取当前经纬度(lat, lon)。
     *
     * 流程:先取各 provider 的 lastKnownLocation 选最新的;若都没有,
     * 请求单次更新,最多等 [timeoutMs] 毫秒。全程失败返回 failure。
     *
     * **返回 GCJ-02 坐标**:GPS 原始为 WGS-84,超星(高德 SDK)用 GCJ-02,
     * 故统一转换后返回,与预设高德坐标对齐,避免 ~500m 偏移导致签到失败。
     */
    @SuppressLint("MissingPermission") // 调用方保证已授权
    suspend fun getCurrentLocation(
        context: Context,
        timeoutMs: Long = 10_000,
    ): Result<Pair<Double, Double>> {
        if (!hasLocationPermission(context)) {
            return Result.failure(SecurityException("未授予定位权限"))
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return Result.failure(IllegalStateException("无法获取 LocationManager"))

        // 1. 先试 lastKnownLocation(秒回)
        bestLastKnown(lm)?.let { return Result.success(CoordTransform.wgs84ToGcj02(it.latitude, it.longitude)) }

        // 2. 请求单次更新
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            return Result.failure(IllegalStateException("定位服务未开启,请检查系统定位开关"))
        }

        val loc = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }
                    // 旧版本接口需要的空实现
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                val provider = providers.first()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                } else {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            }
        }
        return if (loc != null) {
            Result.success(CoordTransform.wgs84ToGcj02(loc.latitude, loc.longitude))
        } else {
            Result.failure(IllegalStateException("定位超时,请到空旷处重试或改用预设地点"))
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(lm: LocationManager): Location? {
        val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        return candidates.maxByOrNull { it.time }
    }
}
