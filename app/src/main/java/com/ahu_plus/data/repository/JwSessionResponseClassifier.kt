package com.ahu_plus.data.repository

/** Strictly distinguishes a real CAS login response from unrelated redirects/errors. */
object JwSessionResponseClassifier {
    fun isExpired(
        statusCode: Int,
        location: String? = null,
        body: String? = null,
    ): Boolean {
        val redirectToLogin = statusCode in 300..399 && location.orEmpty().let {
            it.contains("cas/login", ignoreCase = true) ||
                it.contains("one.ahu.edu.cn/cas", ignoreCase = true)
        }
        val loginForm = body.orEmpty().contains("name=\"lt\"", ignoreCase = true) ||
            body.orEmpty().contains("name='lt'", ignoreCase = true)
        return statusCode == 401 || redirectToLogin || loginForm
    }
}

