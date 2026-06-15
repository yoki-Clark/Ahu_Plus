package com.yourname.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

/**
 * 一卡通门户余额响应 (one.ahu.edu.cn)
 *
 * 示例 JSON: {"SJTBSJ": 1781438700000,"KHYE": 14.41}
 */
data class PortalBalanceResponse(
    @SerializedName("SJTBSJ")
    val timestamp: Long = 0,

    @SerializedName("KHYE")
    val balance: Double = 0.0
)
