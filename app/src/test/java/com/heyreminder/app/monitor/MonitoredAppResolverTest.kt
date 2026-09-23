package com.heyreminder.app.monitor

import com.heyreminder.app.data.MonitoringMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoredAppResolverTest {
    private val launchablePackages = setOf("social", "video", "browser")
    private val selectedPackages = setOf("social", "uninstalled")

    @Test
    fun `blacklist monitors only selected launchable apps`() {
        assertEquals(
            setOf("social"),
            MonitoredAppResolver.resolve(
                monitoringMode = MonitoringMode.BLACKLIST,
                selectedPackages = selectedPackages,
                launchablePackages = launchablePackages,
            ),
        )
    }

    @Test
    fun `whitelist excludes selected apps and ignores stale selections`() {
        assertEquals(
            setOf("video", "browser"),
            MonitoredAppResolver.resolve(
                monitoringMode = MonitoringMode.WHITELIST,
                selectedPackages = selectedPackages,
                launchablePackages = launchablePackages,
            ),
        )
    }
}
