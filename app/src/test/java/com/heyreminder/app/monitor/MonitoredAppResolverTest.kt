package com.heyreminder.app.monitor

import com.heyreminder.app.data.MonitoringMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoredAppResolverTest {
    private val launchablePackages = setOf("social", "video", "browser")
    private val selectedPackages = setOf("social", "uninstalled")

    @Test
    fun `blacklist excludes selected apps and monitors the rest`() {
        assertEquals(
            setOf("video", "browser"),
            MonitoredAppResolver.resolve(
                monitoringMode = MonitoringMode.BLACKLIST,
                selectedPackages = selectedPackages,
                launchablePackages = launchablePackages,
            ),
        )
    }

    @Test
    fun `whitelist monitors only selected launchable apps`() {
        assertEquals(
            setOf("social"),
            MonitoredAppResolver.resolve(
                monitoringMode = MonitoringMode.WHITELIST,
                selectedPackages = selectedPackages,
                launchablePackages = launchablePackages,
            ),
        )
    }

    @Test
    fun `empty default blacklist monitors every launchable app`() {
        assertEquals(
            launchablePackages,
            MonitoredAppResolver.resolve(
                monitoringMode = MonitoringMode.BLACKLIST,
                selectedPackages = emptySet(),
                launchablePackages = launchablePackages,
            ),
        )
    }
}
