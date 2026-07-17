package com.ahu_plus.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {

    private fun info(
        channel: String = "",
        latestVersionCode: Int = 30,
        minSupportedVersionCode: Int = 24,
        downloadUrl: String = "https://example.com/app.apk",
        downloadUrlMirror: String = "",
        sha256: String = "a".repeat(64)
    ) = UpdateInfo(
        channel = channel,
        latestVersion = "2.2.2.6",
        latestVersionCode = latestVersionCode,
        minSupportedVersionCode = minSupportedVersionCode,
        downloadUrl = downloadUrl,
        downloadUrlMirror = downloadUrlMirror,
        sha256 = sha256,
        fileSize = 8_000_000
    )

    @Test
    fun `旧清单未声明 channel 仍兼容稳定和内测渠道`() {
        val legacy = Gson().fromJson(
            """{
                "latestVersion":"2.2.2",
                "latestVersionCode":24,
                "minSupportedVersionCode":13,
                "downloadUrl":"https://example.com/app.apk"
            }""".trimIndent(),
            UpdateInfo::class.java
        )

        assertNull(legacy.validationError(UpdateChannel.STABLE))
        assertNull(legacy.validationError(UpdateChannel.BETA))
        assertEquals(listOf("https://example.com/app.apk"), legacy.downloadUrls())
    }

    @Test
    fun `显式渠道不能被另一渠道消费`() {
        val beta = info(channel = "beta")

        assertNull(beta.validationError(UpdateChannel.BETA))
        assertEquals("更新渠道不匹配", beta.validationError(UpdateChannel.STABLE))
    }

    @Test
    fun `下载地址按主源和镜像顺序去重`() {
        val update = info(
            downloadUrl = "https://primary.example/app.apk",
            downloadUrlMirror = "https://mirror.example/app.apk"
        )

        assertEquals(
            listOf(
                "https://primary.example/app.apk",
                "https://mirror.example/app.apk"
            ),
            update.downloadUrls()
        )
    }

    @Test
    fun `最低支持版本不能高于最新版本`() {
        val invalid = info(latestVersionCode = 30, minSupportedVersionCode = 31)

        assertEquals("最低支持版本号无效", invalid.validationError(UpdateChannel.BETA))
    }

    @Test
    fun `下载地址必须使用 https`() {
        val invalid = info(downloadUrl = "http://example.com/app.apk")

        assertEquals("下载地址必须使用 HTTPS", invalid.validationError(UpdateChannel.STABLE))
    }

    @Test
    fun `旧清单可暂时缺省 sha256 但非法摘要会被拒绝`() {
        assertNull(info(sha256 = "").validationError(UpdateChannel.STABLE))
        assertEquals(
            "SHA-256 格式无效",
            info(sha256 = "not-a-sha").validationError(UpdateChannel.STABLE)
        )
    }

    @Test
    fun `强制更新只对更高版本生效`() {
        val update = info(latestVersionCode = 30, minSupportedVersionCode = 28)

        assertTrue(update.isForceUpdate(currentVersionCode = 27))
        assertFalse(update.isForceUpdate(currentVersionCode = 30))
        assertFalse(update.isForceUpdate(currentVersionCode = 31))
    }
}
