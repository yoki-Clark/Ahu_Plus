package com.ahu_plus.data.repository

import android.util.Log
import com.google.gson.Gson
import com.ahu_plus.data.model.AccountPayInfoData
import com.ahu_plus.data.model.AccountPayInfoResponse
import com.ahu_plus.data.model.BathroomBalanceData
import com.ahu_plus.data.model.BathroomPaymentData
import com.ahu_plus.data.model.ElectricityBalanceData
import com.ahu_plus.data.model.ElectricityPaymentData
import com.ahu_plus.data.model.FinalPayResponse
import com.ahu_plus.data.model.InternetBalanceData
import com.ahu_plus.data.model.PayOrderData
import com.ahu_plus.data.model.PayOrderResponse
import com.ahu_plus.util.BladePaySigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

/**
 * 翼支付 / blade-pay 充值仓库 (水电费)。
 *
 * 复用 [YcardRepository] 已登录的 JWT / cookie / OkHttp 客户端,不发重复登录。
 * 流程参考 AHUTong-master `ui/state/ElectricityDepositViewModel.kt:670-919`。
 *
 * 2026-06-29 实测 (testDebugUnitTest, amount=0.01):
 * - Step 1 下单 (paystep=0): 拿到 orderid
 * - Step 2 拉 passwordMap (paystep=2): 拿到 {uuid → mapString}
 * - Step 3 提交密文 (paystep=2, paytype=ACCOUNTTSM): 真扣款,**调用方应二次确认**
 *
 * 调用方负责限流 / 防重复点击 / 二次密码确认;本类只做纯网络。
 */
class YcardPayRepository(
    private val ycardRepository: YcardRepository,
) {
    companion object {
        private const val TAG = "YcardPayRepo"
        private const val PAY_URL = "https://ycard.ahu.edu.cn/blade-pay/pay"

        /** 浴室 feeitemid (与 YcardRepository.getBathroomBalance 一致) */
        const val FEEITEM_BATHROOM = "430"

        /** 网费 feeitemid (与 YcardRepository.getInternetBalance 一致) */
        const val FEEITEM_INTERNET = "431"

        /**
         * 浴室密码混淆硬编码 (沿用 AHUTong BathroomPayRequest)。
         * 真实查询密码 = 身份证号后 6 位;每位数字 i 替换为 mapString[i] 后提交。
         * 已实测可用;若服务端变更,会从 Step 2 passwordMap 自动取真值覆盖。
         */
        const val BATHROOM_HARDCODED_UUID = "da07e4442e4841cca1655cb29653a023"
        const val BATHROOM_HARDCODED_MAP = "1690457382"
    }

    private val gson = Gson()
    private val mutex = Mutex()  // 防止并发扣款

    /**
     * Step 1: 下单 (paystep=0)。
     * @return 成功返回 orderid;失败返回异常
     */
    suspend fun createElectricityOrder(
        amount: String,
        feeitemid: String,
        room: ElectricityBalanceData,
    ): Result<String> = mutex.withLock {
        runCatching {
            val jwt = ycardRepository.cachedJwt
                ?: throw IOException("未登录 ycard")

            // third_party 整体以服务器回显的 room 字段为准 (对齐 AHUTong PaymentData),
            // 不再掺用户级联选择值,避免码/名不一致导致订单畸形被拒。
            val thirdParty = ElectricityPaymentData(
                area = room.area,
                buildingName = room.buildingName,
                areaName = room.areaName,
                floorName = room.floorName,
                floor = room.floor,
                aid = room.aid,
                account = room.account,
                building = room.building,
                room = room.room,
                roomName = room.roomName,
                myCustomInfo = "房间：${room.areaName} ${room.buildingName} ${room.floorName} ${room.roomName}",
            )
            val thirdPartyJson = gson.toJson(thirdParty)

            val body = BladePaySigner.signedFormBody(
                mapOf(
                    "feeitemid" to feeitemid,
                    "tranamt" to amount,
                    "flag" to "choose",
                    "source" to "app",
                    "paystep" to "0",
                    "abstracts" to "",
                    "redirect_url" to "https://ycard.ahu.edu.cn/plat",
                    "third_party" to thirdPartyJson,
                )
            )
            val req = newSignedRequest(body, jwt)
            executeAndParse(req, "下单", PayOrderResponse::class.java).getOrThrow().let { resp ->
                if (resp.code != 200) throw IOException("下单失败: ${resp.msg}")
                val orderid = resp.data?.orderid
                    ?: throw IOException("下单响应无 orderid: ${resp.msg}")
                Log.i(TAG, "Step 1 成功 orderid=$orderid feeitemid=$feeitemid amount=$amount")
                orderid
            }
        }
    }

    /**
     * Step 1: 浴室下单 (paystep=0)。
     * @param feeitemid 桔园/蕙园 = 430 (Ahu_Plus 仅支持该档;竹园/龙河 409 AHUTong 才支持)
     * @param bathroom 浴室查询响应里的 BathroomData (含 telPhone / accountId / tsmAbstract 等)
     */
    suspend fun createBathroomOrder(
        amount: String,
        feeitemid: String = FEEITEM_BATHROOM,
        bathroom: BathroomBalanceData,
    ): Result<String> = mutex.withLock {
        runCatching {
            val jwt = ycardRepository.cachedJwt
                ?: throw IOException("未登录 ycard")

            val thirdParty = BathroomPaymentData(
                projectId = bathroom.projectId,
                projectName = bathroom.projectName,
                accountId = bathroom.accountId,
                telPhone = bathroom.telPhone,
                identifier = bathroom.identifier,
                sex = bathroom.sex,
                name = bathroom.name,
                statusId = bathroom.statusId,
                accountMoney = bathroom.accountMoney,
                accountGivenMoney = bathroom.accountGivenMoney,
                alias = bathroom.alias,
                tags = bathroom.tags,
                isCard = bathroom.isCard,
                cardStatusId = bathroom.cardStatusId,
                isUseCode = bathroom.isUseCode,
                cardPhysicalId = bathroom.cardPhysicalId,
                tsmAbstract = bathroom.tsmAbstract,
                myCustomInfo = "手机号：${bathroom.telPhone}",
            )
            val thirdPartyJson = gson.toJson(thirdParty)

            val body = BladePaySigner.signedFormBody(
                mapOf(
                    "feeitemid" to feeitemid,
                    "tranamt" to amount,
                    "flag" to "choose",
                    "source" to "app",
                    "paystep" to "0",
                    "abstracts" to "",
                    "third_party" to thirdPartyJson,
                )
            )
            val req = newSignedRequest(body, jwt)
            executeAndParse(req, "浴室下单", PayOrderResponse::class.java).getOrThrow().let { resp ->
                if (resp.code != 200) throw IOException("浴室下单失败: ${resp.msg}")
                val orderid = resp.data?.orderid
                    ?: throw IOException("浴室下单响应无 orderid: ${resp.msg}")
                Log.i(TAG, "浴室 Step 1 成功 orderid=$orderid")
                orderid
            }
        }
    }

    /**
     * Step 1: 网费下单 (paystep=0)。
     * third_party 直接从 [InternetBalanceData] 序列化 (字段名已用 @SerializedName 对齐 API)。
     */
    suspend fun createInternetOrder(
        amount: String,
        internet: InternetBalanceData,
    ): Result<String> = mutex.withLock {
        runCatching {
            val jwt = ycardRepository.cachedJwt
                ?: throw IOException("未登录 ycard")

            val thirdParty = gson.toJson(internet.copy(
                tsmAbstract = "给${internet.account}缴网费"
            ))

            val body = BladePaySigner.signedFormBody(
                mapOf(
                    "feeitemid" to FEEITEM_INTERNET,
                    "tranamt" to amount,
                    "flag" to "choose",
                    "source" to "app",
                    "paystep" to "0",
                    "abstracts" to "",
                    "redirect_url" to "https://ycard.ahu.edu.cn/plat",
                    "third_party" to thirdParty,
                )
            )
            val req = newSignedRequest(body, jwt)
            executeAndParse(req, "网费下单", PayOrderResponse::class.java).getOrThrow().let { resp ->
                if (resp.code != 200) throw IOException("网费下单失败: ${resp.msg}")
                val orderid = resp.data?.orderid
                    ?: throw IOException("网费下单响应无 orderid: ${resp.msg}")
                Log.i(TAG, "网费 Step 1 成功 orderid=$orderid amount=$amount")
                orderid
            }
        }
    }

    /**
     * Step 2 + Step 3: 拉密码映射 → 替换密码 → 提交密文 (真扣款)。
     *
     * @param orderId createOrder 返回的订单号
     * @param plaintext 6 位查询密码 (默认身份证号后 6 位;密码错误时由调用方让用户重输)
     * @return 成功 / 失败
     */
    suspend fun payWithAccount(
        orderId: String,
        plaintext: String,
    ): Result<Unit> = mutex.withLock {
        runCatching {
            val jwt = ycardRepository.cachedJwt
                ?: throw IOException("未登录 ycard")

            // Step 2: 拉 passwordMap
            val step2Body = BladePaySigner.signedFormBody(
                mapOf(
                    "paytypeid" to "64",
                    "paytype" to "ACCOUNTTSM",
                    "paystep" to "2",
                    "orderid" to orderId,
                )
            )
            val step2Resp = executeAndParse(
                newSignedRequest(step2Body, jwt), "Step2", AccountPayInfoResponse::class.java
            ).getOrThrow()
            if (step2Resp.code != 200) {
                throw IOException("Step 2 失败: ${step2Resp.msg}")
            }
            val pwdMap = step2Resp.data?.passwordMap
                ?: throw IOException("Step 2 无 passwordMap: ${step2Resp.msg}")

            val (uuid, mapString) = pwdMap.entries.firstOrNull()
                ?: throw IOException("passwordMap 为空")
            val cipher = encryptPassword(plaintext, mapString)
            Log.d(TAG, "密码已混淆,uuid=$uuid")

            // Step 3: 提交密文 (此步扣款!)
            val step3Body = BladePaySigner.signedFormBody(
                mapOf(
                    "orderid" to orderId,
                    "paystep" to "2",
                    "paytype" to "ACCOUNTTSM",
                    "paytypeid" to "64",
                    "userAgent" to "h5",
                    "ccctype" to "000",
                    "password" to cipher,
                    "uuid" to uuid,
                    "isWX" to "0",
                )
            )
            val step3Resp = executeAndParse(
                newSignedRequest(step3Body, jwt), "Step3", FinalPayResponse::class.java
            ).getOrThrow()
            if (step3Resp.code != 200 || !step3Resp.success) {
                throw IOException("支付失败: ${step3Resp.msg}")
            }
            Log.i(TAG, "支付成功 orderid=$orderId")
            Unit
        }
    }

    /**
     * 浴室 Step 2+3: 硬编码密码映射,免查 passwordMap。
     * AHUTong 经验: 浴室密码映射是固定的,服务端不会返回真 passwordMap。
     */
    suspend fun payBathroomWithAccount(
        orderId: String,
        plaintext: String,
        uuid: String = BATHROOM_HARDCODED_UUID,
        mapString: String = BATHROOM_HARDCODED_MAP,
    ): Result<Unit> = mutex.withLock {
        runCatching {
            val jwt = ycardRepository.cachedJwt
                ?: throw IOException("未登录 ycard")

            val cipher = encryptPassword(plaintext, mapString)
            val body = BladePaySigner.signedFormBody(
                mapOf(
                    "orderid" to orderId,
                    "paystep" to "2",
                    "paytype" to "ACCOUNTTSM",
                    "paytypeid" to "64",
                    "userAgent" to "h5",
                    "ccctype" to "000",
                    "password" to cipher,
                    "uuid" to uuid,
                    "isWX" to "0",
                )
            )
            val resp = executeAndParse(
                newSignedRequest(body, jwt), "浴室 Step3", FinalPayResponse::class.java
            ).getOrThrow()
            if (resp.code != 200 || !resp.success) {
                throw IOException("浴室支付失败: ${resp.msg}")
            }
            Log.i(TAG, "浴室支付成功 orderid=$orderId")
            Unit
        }
    }

    /**
     * 密码混淆: plaintext 每位数字 0-9 → mapString 对应索引字符。
     * 例 mapString="7812430965", plaintext="123456" → "893" (1→8, 2→7, ...)
     */
    private fun encryptPassword(plain: String, mapString: String): String {
        val plainDigits = "0123456789"
        val keymap: Map<String, String> =
            (0..9).associate { i -> mapString[i].toString() to plainDigits[i].toString() }
        val sb = StringBuilder(plain.length)
        for (ch in plain) {
            sb.append(keymap[ch.toString()] ?: ch.toString())
        }
        return sb.toString()
    }

    private fun newSignedRequest(body: FormBody, jwt: String): Request =
        Request.Builder()
            .url(PAY_URL)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/148.0.0.0 Mobile Safari/537.36")
            .header("synjones-auth", "bearer $jwt")
            .header("Referer", "https://ycard.ahu.edu.cn/charge-app/")
            .post(body)
            .build()

    private suspend fun <T> executeAndParse(
        req: Request,
        label: String,
        clazz: Class<T>,
    ): Result<T> = runCatching {
        withContext(Dispatchers.IO) {
            ycardRepository.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code != 200) {
                    throw IOException("$label HTTP ${resp.code}: ${body.take(200)}")
                }
                gson.fromJson(body, clazz)
                    ?: throw IOException("$label 响应解析失败: ${body.take(200)}")
            }
        }
    }
}
