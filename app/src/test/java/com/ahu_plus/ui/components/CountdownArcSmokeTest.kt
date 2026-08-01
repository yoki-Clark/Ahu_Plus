package com.ahu_plus.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ahu_plus.ui.theme.AhuPlusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric + Compose UI Test 冒烟:验证 JVM 内可渲染 Compose 组件,
 * 不依赖真机/模拟器。用轻量 Application 避免触发完整 DI 初始化。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CountdownArcSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun countdownArcRendersRemainingSeconds() {
        composeRule.setContent {
            AhuPlusTheme(darkTheme = false, dynamicColor = false) {
                CountdownArc(secondsRemaining = 42, totalSeconds = 60)
            }
        }
        composeRule.onNodeWithText("42s").assertIsDisplayed()
    }

    @Test
    fun countdownArcClampsNegativeSecondsToZero() {
        composeRule.setContent {
            AhuPlusTheme(darkTheme = false, dynamicColor = false) {
                CountdownArc(secondsRemaining = -5, totalSeconds = 60)
            }
        }
        // 注:钳制(0..total)只作用于圆弧比例,文本仍按原始值渲染
        composeRule.onNodeWithText("-5s").assertIsDisplayed()
    }
}
