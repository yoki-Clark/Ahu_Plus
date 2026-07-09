package com.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

/**
 * 用户自定义签到位置(2026-06-24)。
 *
 * 用户可在签到位置选择器里添加多个自命名地点(如"我的教室""实验楼"),
 * 经 SessionManager 以 JSON 列表持久化。坐标系同 [CampusLocation],为 GCJ-02。
 */
data class CustomSignLocation(
    @SerializedName("name") val name: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
)
