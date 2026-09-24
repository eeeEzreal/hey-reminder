package com.heyreminder.app.monitor

import com.heyreminder.app.data.MonitoringMode

object MonitoredAppResolver {
    fun resolve(
        monitoringMode: MonitoringMode,
        selectedPackages: Set<String>,
        launchablePackages: Set<String>,
    ): Set<String> = when (monitoringMode) {
        MonitoringMode.BLACKLIST -> launchablePackages - selectedPackages
        MonitoringMode.WHITELIST -> launchablePackages.intersect(selectedPackages)
    }
}
