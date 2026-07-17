package com.ahu_plus.ui

import com.ahu_plus.data.home.AppRegistry
import com.ahu_plus.data.local.resolveThirdPartyServiceFlags
import com.ahu_plus.data.model.MarketTopic
import com.ahu_plus.data.model.FeeItemOption
import com.ahu_plus.data.model.DormHint
import com.ahu_plus.data.model.jw.Grade
import com.ahu_plus.ui.screen.apps.appHubPageForAppKey
import com.ahu_plus.ui.screen.chaoxing.providerChainForTikuType
import com.ahu_plus.ui.screen.grade.GradeUiState
import com.ahu_plus.ui.screen.grade.withSelectedSemester
import com.ahu_plus.ui.screen.home.ElectricityTarget
import com.ahu_plus.ui.screen.home.buildingLoadAvailability
import com.ahu_plus.ui.screen.home.mergeBuildingOptions
import com.ahu_plus.ui.screen.home.matchBuildingOption
import com.ahu_plus.ui.screen.home.matchRoomOption
import com.ahu_plus.ui.screen.home.shouldPersistMatchedRoom
import com.ahu_plus.ui.screen.market.MarketUiState
import com.ahu_plus.ui.screen.market.withSearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLogicRegressionTest {

    @Test
    fun `disabled tiku does not fall back to cache`() {
        assertEquals("DISABLED", providerChainForTikuType("DISABLED"))
        assertEquals("CACHE,YANXI", providerChainForTikuType("YANXI"))
    }

    @Test
    fun `legacy third party accounts remain visible after migration`() {
        val flags = resolveThirdPartyServiceFlags(
            parent = null,
            marketChild = null,
            chaoxingChild = null,
            welearnChild = null,
            legacyMarketEnabled = false,
            hasChaoxingAccount = true,
            hasWelearnAccount = true,
        )

        assertTrue(flags.parentEnabled)
        assertFalse(flags.marketEnabled)
        assertTrue(flags.chaoxingEnabled)
        assertTrue(flags.welearnEnabled)
    }

    @Test
    fun `explicit parent choice wins while migrated child choices are preserved`() {
        val flags = resolveThirdPartyServiceFlags(
            parent = "false",
            marketChild = "true",
            chaoxingChild = null,
            welearnChild = null,
            legacyMarketEnabled = true,
            hasChaoxingAccount = true,
            hasWelearnAccount = false,
        )

        assertFalse(flags.parentEnabled)
        assertTrue(flags.marketEnabled)
        assertTrue(flags.chaoxingEnabled)
        assertFalse(flags.welearnEnabled)
    }

    @Test
    fun `semester selection updates both id and visible name`() {
        val state = GradeUiState(
            gradesBySemester = mapOf(
                "20251" to listOf(Grade(semesterId = 20251, semesterName = "2025-2026 学年第一学期")),
            ),
        ).withSelectedSemester(20251)

        assertEquals(20251, state.selectedSemesterId)
        assertEquals("2025-2026 学年第一学期", state.semesterName)
    }

    @Test
    fun `changing market search query clears stale results and paging`() {
        val state = MarketUiState(
            searchQuery = "旧关键词",
            searchResults = listOf(MarketTopic(id = 1)),
            searchPage = 3,
            hasMoreSearch = false,
            searchLoading = true,
            searchLoadingMore = true,
            searchError = "旧错误",
        ).withSearchQuery("新关键词")

        assertEquals("新关键词", state.searchQuery)
        assertTrue(state.searchResults.isEmpty())
        assertEquals(0, state.searchPage)
        assertTrue(state.hasMoreSearch)
        assertFalse(state.searchLoading)
        assertFalse(state.searchLoadingMore)
        assertEquals(null, state.searchError)
    }

    @Test
    fun `every registered app has an AppHub route in declaration group order`() {
        AppRegistry.all().forEach { spec ->
            assertNotNull("Missing route for ${spec.key}", appHubPageForAppKey(spec.key))
        }
        assertEquals(
            listOf("学习", "通知", "校园卡", "生活", "个人信息"),
            AppRegistry.grouped().keys.toList(),
        )
    }

    @Test
    fun `only automatic room matches are persisted`() {
        val room = FeeItemOption(name = "301", value = "301&301")

        assertTrue(shouldPersistMatchedRoom(autoSubmit = true, room = room))
        assertFalse(shouldPersistMatchedRoom(autoSubmit = false, room = room))
        assertFalse(shouldPersistMatchedRoom(autoSubmit = true, room = null))
    }

    @Test
    fun `building merge keeps naming variants and removes explicit opposite target`() {
        val old = listOf(
            FeeItemOption(name = "梅园一号楼-空调表", value = "1&ac"),
            FeeItemOption(name = "梅园二号楼照明", value = "2&light"),
            FeeItemOption(name = "研究生公寓", value = "3&unknown"),
        )
        val new = listOf(old.first())

        assertEquals(
            listOf("1&ac", "3&unknown"),
            mergeBuildingOptions(old, new, ElectricityTarget.AC).map { it.value },
        )
        assertEquals(
            listOf("2&light", "3&unknown"),
            mergeBuildingOptions(old, new, ElectricityTarget.LIGHTING).map { it.value },
        )
    }

    @Test
    fun `partial building source failure is never reported as complete success`() {
        val partial = buildingLoadAvailability(
            buildingCount = 18,
            failedSources = listOf("新区校区"),
        )
        val totalFailure = buildingLoadAvailability(
            buildingCount = 0,
            failedSources = listOf("老校区空调"),
        )

        assertEquals(null, partial.error)
        assertTrue(partial.warning?.contains("列表可能不完整") == true)
        assertNotNull(totalFailure.error)
        assertEquals(null, totalFailure.warning)
    }

    @Test
    fun `automatic building match handles punctuation but rejects ambiguous park fallback`() {
        val hint = DormHint(
            buildingName = "梅园",
            roomNumber = "2301",
            buildingDigit = 2,
            floorDigit = 3,
        )
        val options = listOf(
            FeeItemOption(name = "梅园一号楼-空调表", value = "1&ac"),
            FeeItemOption(name = "梅园二号楼（空调）", value = "2&ac"),
        )

        assertEquals("2&ac", matchBuildingOption(options, hint, "空调")?.value)
        assertEquals(
            null,
            matchBuildingOption(options, hint.copy(buildingDigit = 9), "空调"),
        )
    }

    @Test
    fun `automatic room fallback only accepts a unique candidate`() {
        val unique = listOf(FeeItemOption(name = "301", value = "301&301"))
        val ambiguous = listOf(
            FeeItemOption(name = "1301", value = "1301&1301"),
            FeeItemOption(name = "2301", value = "2301&2301"),
        )

        assertEquals("301&301", matchRoomOption(unique, "8301")?.value)
        assertEquals(null, matchRoomOption(ambiguous, "301"))
    }
}
