package com.yourname.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

data class BalanceResponse(
    val code: Int,
    val msg: String,
    @SerializedName("object")
    val balance: Double
)
