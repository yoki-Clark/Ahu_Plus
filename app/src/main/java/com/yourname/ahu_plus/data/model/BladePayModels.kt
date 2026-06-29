package com.yourname.ahu_plus.data.model

/**
 * blade-pay 充值 API 数据模型。
 *
 * 2026-06-29 实测端点: ycard.ahu.edu.cn/blade-pay/pay
 * 流程: Step1 下单 (paystep=0) → Step2 拉 passwordMap (paystep=2) → Step3 提交密文 (paystep=2)
 */

/** Step1 响应: 下单结果 */
data class PayOrderResponse(
    val code: Int = 0,
    val success: Boolean = false,
    val msg: String = "",
    val data: PayOrderData? = null,
)

data class PayOrderData(
    val orderid: String? = null,
    val paystep: Int? = null,
    val payExpDate: String? = null,
    val payList: List<PayMethodItem>? = null,
)

/** 支付方式条目 (Step1 响应 payList) */
data class PayMethodItem(
    val id: Int? = null,
    val pay_type_code: String? = null,
    val pay_type_name: String? = null,
    val icon: String? = null,
    val nopassword: Int? = null,
    val partner_key: String? = null,
    val flag: String? = null,
)

/** Step2 响应: 拉密码混淆表 */
data class AccountPayInfoResponse(
    val code: Int = 0,
    val success: Boolean = false,
    val msg: String = "",
    val data: AccountPayInfoData? = null,
)

data class AccountPayInfoData(
    /** 账户号 (返回的银行卡/账户标识) */
    val accountno: String? = null,
    /** 账户类型 + 余额 [{ ccctype: "000", balance: 107.95 }] */
    val ccctype: List<CcctypeBalance>? = null,
    val uuid: String? = null,
    /** 密码混淆表 {uuid -> mapString(10 位)} - 0-9 替换为 mapString[i] */
    val passwordMap: Map<String, String>? = null,
    val orderid: String? = null,
)

data class CcctypeBalance(
    val ccctype: String? = null,
    val balance: Double? = null,
)

/** Step3 响应: 提交密文结果 */
data class FinalPayResponse(
    val code: Int = 0,
    val success: Boolean = false,
    val msg: String = "",
    val data: String? = null,
)

/** 电费 third_party 业务字段 (Step1 参数) */
data class ElectricityPaymentData(
    val area: String,
    val buildingName: String,
    val areaName: String,
    val extdata: String = "",
    val floorName: String,
    val floor: String,
    val aid: String,
    val account: String,
    val building: String,
    val room: String,
    val roomName: String,
    val myCustomInfo: String,
)

/** 浴室 third_party 业务字段 (Step1 参数) - 沿用 AHUTong 字段 */
data class BathroomPaymentData(
    val projectId: Int? = null,
    val projectName: String? = null,
    val accountId: Int? = null,
    val telPhone: String,
    val identifier: String? = null,
    val sex: String? = null,
    val name: String? = null,
    val statusId: Int? = null,
    val accountMoney: Int? = null,
    val accountGivenMoney: Int? = null,
    val alias: String? = null,
    val tags: String? = null,
    val isCard: Int? = null,
    val cardStatusId: Int? = null,
    val isUseCode: Int? = null,
    val cardPhysicalId: String? = null,
    val tsmAbstract: String? = null,
    val myCustomInfo: String,
)
