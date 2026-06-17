package com.yourname.ahu_plus.data.model

data class XzxxLetter(
    val contentId: String,
    val viewCount: String,
    val title: String,
    val writeDate: String,
    val replyDate: String,
    val url: String,
    val detail: XzxxLetterDetail? = null,
    val detailError: String? = null
) {
    val isReplied: Boolean get() = replyDate.isNotBlank()
    val isDetailLoading: Boolean get() = detail == null && detailError == null
    val isExpanded: Boolean get() = detail != null || detailError != null
}

/**
 * 详情页结构化数据(show.asp 解析产出)。
 *
 * @property title 信件主题(列表里已有,但详情页可能略有差异)
 * @property content 写信内容正文
 * @property replyLabel 回复状态标签(已回复 / 未回复)
 * @property replyContent 回复正文,空 = 无回复
 */
data class XzxxLetterDetail(
    val title: String,
    val content: String,
    val replyLabel: String,
    val replyContent: String
)
