package com.ahu_plus.data.model

data class JwcNotice(
    val title: String,
    val date: String,
    val url: String
)

data class JwcNoticeDetail(
    val title: String,
    val date: String?,
    val content: String,
    val url: String,
    val attachments: List<JwcNoticeAttachment> = emptyList()
)

data class JwcNoticeAttachment(
    val name: String,
    val url: String
)
