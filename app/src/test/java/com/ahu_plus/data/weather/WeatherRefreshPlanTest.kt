package com.ahu_plus.data.weather

import com.ahu_plus.data.model.weather.WeatherFeed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRefreshPlanTest {

    @Test
    fun `fresh cache hydrates an empty shared feed without another network request`() {
        val cached = WeatherFeed()

        val plan = planWeatherRefresh(
            currentFeed = null,
            cachedFeed = cached,
            cachedAgeMillis = 5 * 60 * 1000L,
            maxAgeMillis = 30 * 60 * 1000L,
        )

        assertSame(cached, plan.visibleFeed)
        assertFalse(plan.shouldRefreshRemote)
    }

    @Test
    fun `fresh timestamp without a cached payload still requests remote data`() {
        val plan = planWeatherRefresh(
            currentFeed = null,
            cachedFeed = null,
            cachedAgeMillis = 5 * 60 * 1000L,
            maxAgeMillis = 30 * 60 * 1000L,
        )

        assertNull(plan.visibleFeed)
        assertTrue(plan.shouldRefreshRemote)
    }
}
