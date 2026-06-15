package com.yourname.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

data class QrCodeResponse(
    val code: Int,
    val msg: String,
    @SerializedName("object")
    val qrCodeData: String
)
