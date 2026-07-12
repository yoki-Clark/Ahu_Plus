package com.ahu_plus.data.repository

import java.net.URI

/** Strictly distinguishes a real CAS login response from unrelated redirects/errors. */
object JwSessionResponseClassifier {
    fun isExpired(
        statusCode: Int,
        location: String? = null,
        body: String? = null,
    ): Boolean {
        val redirectToLogin = statusCode in 300..399 && location.orEmpty().let {
            it.contains("cas/login", ignoreCase = true) ||
                it.contains("one.ahu.edu.cn/cas", ignoreCase = true) ||
                isJwLoginLocation(it)
        }
        val loginForm = body.orEmpty().contains("name=\"lt\"", ignoreCase = true) ||
            body.orEmpty().contains("name='lt'", ignoreCase = true)
        return statusCode == 401 || redirectToLogin || loginForm
    }

    private fun isJwLoginLocation(location: String): Boolean {
        val uri = runCatching { URI(location.trim()) }.getOrNull() ?: return false
        val isJwHost = uri.host == null || uri.host.equals("jw.ahu.edu.cn", ignoreCase = true)
        return isJwHost && uri.path.equals("/student/login", ignoreCase = true)
    }
}
