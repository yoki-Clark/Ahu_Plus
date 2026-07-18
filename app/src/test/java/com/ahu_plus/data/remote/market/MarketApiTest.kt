package com.ahu_plus.data.remote.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.util.Base64

class MarketApiTest {

    @Test
    fun parseIdentity_readsRequiredClaims() {
        val parsed = MarketApi.parseIdentity("bearer ${jwt(exp = 2_000_000_000L)}").getOrThrow()

        assertTrue(parsed.normalizedToken.startsWith("Bearer "))
        assertEquals("安徽大学", parsed.metadata.school)
        assertEquals(7L, parsed.metadata.schoolId)
        assertEquals(2_000_000_000L, parsed.metadata.expiresAtEpochSeconds)
    }

    @Test
    fun parseIdentity_rejectsMalformedToken() {
        assertTrue(MarketApi.parseIdentity("Bearer not-a-jwt").isFailure)
        assertTrue(MarketApi.parseIdentity(jwt(exp = 0L)).isFailure)
    }

    @Test
    fun parseImportUri_requiresVersionTokenAndNonce() {
        val token = jwt(exp = 2_000_000_000L)
        val encoded = URLEncoder.encode(token, StandardCharsets.UTF_8.name())
        val parsed = MarketApi.parseImportUri(
            "ahuplus://market/import?v=1&token=$encoded&nonce=random-value"
        ).getOrThrow()

        assertEquals("安徽大学", parsed.metadata.school)
        assertTrue(MarketApi.parseImportUri(
            "ahuplus://market/import?v=2&token=$encoded&nonce=random-value"
        ).isFailure)
        assertTrue(MarketApi.parseImportUri(
            "https://market/import?v=1&token=$encoded&nonce=random-value"
        ).isFailure)
    }

    @Test
    fun expiryState_usesExactThreeDayBoundary() {
        val now = 1_000_000L

        assertEquals(MarketIdentityExpiryState.EXPIRED, MarketApi.expiryState(now, now))
        assertEquals(
            MarketIdentityExpiryState.EXPIRING_SOON,
            MarketApi.expiryState(now + MarketApi.EXPIRY_WARNING_SECONDS, now),
        )
        assertEquals(
            MarketIdentityExpiryState.VALID,
            MarketApi.expiryState(now + MarketApi.EXPIRY_WARNING_SECONDS + 1L, now),
        )
    }

    @Test
    fun expiryWarning_prioritizesExpiredIdentity() {
        val now = 1_000_000L
        val warning = MarketApi.expiryWarning(
            listOf(
                jwt(exp = now + 86_400L, school = "即将过期大学", schoolId = 8L),
                jwt(exp = now - 1L, school = "已过期大学", schoolId = 9L),
            ),
            nowEpochSeconds = now,
        )

        assertEquals("已过期大学的集市身份已过期，请重新导入", warning)
    }

    @Test
    fun expiryLabel_usesRequestedTimezone() {
        val parsed = MarketApi.parseIdentity(jwt(exp = 0L + 1_700_000_000L)).getOrThrow()
        assertEquals(
            "有效期至 2023-11-14",
            MarketApi.expiryLabel(parsed.metadata, ZoneId.of("UTC")),
        )
    }

    private fun jwt(
        exp: Long,
        school: String = "安徽大学",
        schoolId: Long = 7L,
    ): String {
        val header = base64Url("""{"alg":"RS256","typ":"JWT"}""")
        val payload = base64Url(
            """{"school":"$school","schoolID":$schoolId,"exp":$exp}"""
        )
        return "$header.$payload.signature"
    }

    private fun base64Url(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}
