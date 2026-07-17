package com.ahu_plus.ui.screen.chaoxing

import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.model.CxWorkData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChaoxingHomeworkDetailCacheTest {

    @Test
    fun `cache is returned only for matching work id`() {
        val workData = CxWorkData(
            answerwqbid = "answer-1",
            formActionUrl = "https://example.test/submit",
        )
        val json = encodeCxHomeworkDetailCache("work-1", workData)

        assertEquals(workData, decodeCxHomeworkDetailCache(json, "work-1"))
        assertNull(decodeCxHomeworkDetailCache(json, "work-2"))
    }

    @Test
    fun `legacy unscoped cache and malformed json are rejected`() {
        val legacyJson = GsonProvider.instance.toJson(CxWorkData(answerwqbid = "legacy"))

        assertNull(decodeCxHomeworkDetailCache(legacyJson, "work-1"))
        assertNull(decodeCxHomeworkDetailCache("not-json", "work-1"))
    }
}
