package com.heyreminder.app.monitor

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.heyreminder.app.data.OverlayPermissionRepository
import com.heyreminder.app.data.ReminderSettings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayReminderPresenterInstrumentedTest {
    @Test
    fun overlayPermissionCreationAndActionWorkOverAnotherApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val uiAutomation = instrumentation.uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow",
        )).use { stream -> stream.readBytes() }
        assertTrue(OverlayPermissionRepository(context).hasOverlayPermission())

        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Thread.sleep(500L)
        val presenter = OverlayReminderPresenter(context)

        try {
            ReminderActionType.entries.forEach { expectedAction ->
                val handled = CountDownLatch(1)
                var handledAction: ReminderActionType? = null
                assertTrue(
                    presenter.show(
                        event = testEvent(),
                        level = ReminderVisualLevel.FIRST,
                        settings = ReminderSettings(),
                        onAction = { command ->
                            handledAction = command.action
                            handled.countDown()
                        },
                    ),
                )
                assertTrue(presenter.isVisible)
                assertTrue(presenter.hasViewForTesting("你已经连续使用 10 分钟"))
                assertTrue(presenter.performActionForTesting(expectedAction))
                assertTrue(handled.await(2L, TimeUnit.SECONDS))
                assertEquals(expectedAction, handledAction)
                assertFalse(presenter.isVisible)
            }

            assertTrue(
                presenter.show(
                    event = testEvent().copy(continuousDurationMillis = 15L * 60_000L),
                    level = ReminderVisualLevel.ESCALATED,
                    settings = ReminderSettings(),
                    onAction = {},
                ),
            )
            assertTrue(presenter.hasViewForTesting("你已经忽略了第一次提醒"))
        } finally {
            presenter.dismiss()
        }
    }

    private fun testEvent() = ReminderConditionReachedEvent(
        packageName = "com.android.settings",
        sessionStartedAtElapsedMillis = 1_000L,
        reminderDueAtElapsedMillis = 601_000L,
        continuousDurationMillis = 10L * 60_000L,
    )
}
