package com.yourname.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

/**
 * 一卡通账单列表响应 (ycard.ahu.edu.cn)
 */
data class BillResponse(
    val code: Int = 0,
    val success: Boolean = false,
    val data: BillData? = null,
    val msg: String? = null
)

data class BillData(
    val records: List<BillRecord> = emptyList(),
    val total: Int = 0,
    val size: Int = 0,
    val current: Int = 0,
    val pages: Int = 0
)

data class BillRecord(
    val resume: String = "",           // 商户/描述 "北二区食堂一楼-扫码支付"
    @SerializedName("tranamt")
    val tranAmt: Int = 0,              // 交易金额(分)
    @SerializedName("cardBalance")
    val cardBalance: Int = 0,          // 卡余额(分)
    @SerializedName("effectdateStr")
    val effectDateStr: String = "",    // 交易时间 "2026-06-15 13:27:46"
    @SerializedName("jndatetimeStr")
    val jndatetimeStr: String = "",    // 流水时间
    val orderId: String = "",          // 订单号
    @SerializedName("turnoverType")
    val turnoverType: String = "",     // 交易类型 "二维码支付"/"充值"
    val icon: String = "",             // 图标标识
    @SerializedName("payName")
    val payName: String = "",          // 支付方式
    @SerializedName("consumeTypeName")
    val consumeTypeName: String? = null, // 消费类型
    @SerializedName("locationName")
    val locationName: String = "",     // 位置编码
    @SerializedName("toMerchant")
    val toMerchant: String = ""        // 商户名
)
