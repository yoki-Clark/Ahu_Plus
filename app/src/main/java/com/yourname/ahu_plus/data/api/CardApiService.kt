package com.yourname.ahu_plus.data.api

import com.yourname.ahu_plus.data.model.BalanceResponse
import com.yourname.ahu_plus.data.model.QrCodeResponse
import retrofit2.http.GET

interface CardApiService {
    @GET("/xzxcard/yue")
    suspend fun getBalance(): BalanceResponse

    @GET("/xzxcard/qrcode")
    suspend fun getQrCode(): QrCodeResponse
}
