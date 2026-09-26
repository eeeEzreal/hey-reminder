package com.heyreminder.app.monitor

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.heyreminder.app.data.ReminderSettings
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.math.max

enum class ReminderVisualLevel {
    FIRST,
    ESCALATED,
}

internal class OverlayReminderPresenter(context: Context) {
    private val applicationContext = context.applicationContext
    private val windowManager = applicationContext.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var isVisible: Boolean = false
        private set

    @Volatile
    var lastFailureReason: String? = null
        private set

    private var overlayView: View? = null

    fun show(
        event: ReminderConditionReachedEvent,
        level: ReminderVisualLevel,
        settings: ReminderSettings,
        onAction: (ReminderActionCommand) -> Unit,
    ): Boolean {
        val canDrawOverlays = Settings.canDrawOverlays(applicationContext)
        lastFailureReason = null
        Log.i(
            TAG,
            "overlay_show_called package=${event.packageName} level=$level " +
                "permission=$canDrawOverlays",
        )
        if (!canDrawOverlays) {
            lastFailureReason = "SYSTEM_ALERT_WINDOW denied while app is in background"
            Log.w(TAG, "overlay_show_rejected reason=permission_missing")
            return false
        }

        return runOnMain {
            dismissOnMain()
            val view = buildOverlayView(event, level, settings, onAction)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
            }
            runCatching {
                windowManager.addView(view, params)
                overlayView = view
                isVisible = true
                isAnyOverlayVisible = true
                vibrate(level)
                true
            }.onSuccess {
                lastFailureReason = null
                Log.i(TAG, "overlay_show_result shown=true package=${event.packageName}")
            }.onFailure { error ->
                lastFailureReason = buildString {
                    append(error.javaClass.simpleName)
                    error.message?.takeIf(String::isNotBlank)?.let { message ->
                        append(": ")
                        append(message)
                    }
                }
                Log.e(TAG, "overlay_show_result shown=false package=${event.packageName}", error)
            }.getOrDefault(false)
        }
    }

    fun dismiss() {
        runOnMain { dismissOnMain() }
    }

    private fun dismissOnMain() {
        overlayView?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        overlayView = null
        isVisible = false
        isAnyOverlayVisible = false
    }

    private fun buildOverlayView(
        event: ReminderConditionReachedEvent,
        level: ReminderVisualLevel,
        settings: ReminderSettings,
        onAction: (ReminderActionCommand) -> Unit,
    ): View {
        val root = FrameLayout(applicationContext).apply {
            setBackgroundColor(
                if (level == ReminderVisualLevel.ESCALATED) {
                    Color.argb(205, 34, 7, 12)
                } else {
                    Color.argb(172, 20, 14, 18)
                },
            )
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val card = LinearLayout(applicationContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (level == ReminderVisualLevel.ESCALATED) {
                    intArrayOf(Color.rgb(195, 49, 67), Color.rgb(235, 91, 76))
                } else {
                    intArrayOf(Color.rgb(238, 132, 100), Color.rgb(226, 94, 116))
                },
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(
                    dp(if (level == ReminderVisualLevel.ESCALATED) 4 else 2),
                    Color.argb(220, 255, 247, 232),
                )
            }
            elevation = dp(18).toFloat()
        }
        val screenHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            applicationContext.resources.displayMetrics.heightPixels
        }
        val cardHeightFraction = if (level == ReminderVisualLevel.ESCALATED) 0.56f else 0.50f
        root.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * cardHeightFraction).toInt(),
                Gravity.CENTER,
            ).apply {
                marginStart = dp(if (level == ReminderVisualLevel.ESCALATED) 16 else 24)
                marginEnd = dp(if (level == ReminderVisualLevel.ESCALATED) 16 else 24)
            },
        )

        card.addView(reminderText("Hey!", 40f, Typeface.BOLD))
        if (level == ReminderVisualLevel.ESCALATED) {
            card.addView(
                reminderText("你已经忽略了第一次提醒", 20f, Typeface.BOLD).withTopMargin(6),
            )
        }
        val durationLabel = if (event.continuousDurationMillis < 60_000L) {
            "${max(1L, event.continuousDurationMillis / 1_000L)} 秒"
        } else {
            "${event.continuousDurationMillis / 60_000L} 分钟"
        }
        card.addView(
            reminderText("你已经连续使用 $durationLabel", 24f, Typeface.BOLD)
                .withTopMargin(10),
        )
        card.addView(
            reminderText(
                if (level == ReminderVisualLevel.ESCALATED) {
                    "已经又过去 5 分钟。现在真的该停一下了。"
                } else {
                    "该停一下了。"
                },
                18f,
                Typeface.NORMAL,
            ).withTopMargin(6),
        )

        val target = ReminderActionTarget(
            packageName = event.packageName,
            sessionStartedAtElapsedMillis = event.sessionStartedAtElapsedMillis,
            reminderDueAtElapsedMillis = event.reminderDueAtElapsedMillis,
        )
        card.addView(
            actionButton("收到", emphasized = true) {
                dismissOnMain()
                onAction(ReminderActionCommand(ReminderActionType.ACKNOWLEDGE, target))
            }.withTopMargin(18),
        )
        card.addView(
            actionButton("再给我 ${settings.snoozeMinutes} 分钟") {
                dismissOnMain()
                onAction(ReminderActionCommand(ReminderActionType.SNOOZE, target))
            }.withTopMargin(8),
        )
        card.addView(
            actionButton("暂停 ${settings.pauseMinutes} 分钟") {
                dismissOnMain()
                onAction(ReminderActionCommand(ReminderActionType.PAUSE, target))
            }.withTopMargin(8),
        )
        return root
    }

    private fun reminderText(text: String, sizeSp: Float, style: Int) =
        TextView(applicationContext).apply {
            this.text = text
            tag = text
            textSize = sizeSp
            setTextColor(Color.rgb(255, 249, 235))
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, style)
        }

    private fun actionButton(
        text: String,
        emphasized: Boolean = false,
        onClick: () -> Unit,
    ) = Button(applicationContext).apply {
        this.text = text
        textSize = 16f
        isAllCaps = false
        setTextColor(if (emphasized) Color.rgb(168, 55, 72) else Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (emphasized) Color.rgb(255, 249, 235) else Color.argb(58, 255, 255, 255))
            if (!emphasized) setStroke(dp(1), Color.argb(160, 255, 255, 255))
        }
        setOnClickListener { onClick() }
        tag = when {
            text == "收到" -> ReminderActionType.ACKNOWLEDGE
            text.startsWith("再给我") -> ReminderActionType.SNOOZE
            else -> ReminderActionType.PAUSE
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(46),
        )
    }

    private fun <T : View> T.withTopMargin(marginDp: Int): T = apply {
        val existing = layoutParams as? LinearLayout.LayoutParams
        layoutParams = (existing ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )).apply {
            topMargin = dp(marginDp)
        }
    }

    private fun vibrate(level: ReminderVisualLevel) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applicationContext.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            applicationContext.getSystemService(Vibrator::class.java)
        }
        val pattern = if (level == ReminderVisualLevel.ESCALATED) {
            longArrayOf(0L, 250L, 120L, 250L, 120L, 500L)
        } else {
            longArrayOf(0L, 220L, 140L, 380L)
        }
        runCatching {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun dp(value: Int): Int =
        (value * applicationContext.resources.displayMetrics.density).toInt()

    private fun <T> runOnMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask(block)
        mainHandler.post(task)
        return task.get(3L, TimeUnit.SECONDS)
    }

    internal fun hasViewForTesting(tag: Any): Boolean = runOnMain {
        overlayView?.findViewWithTag<View>(tag) != null
    }

    internal fun performActionForTesting(action: ReminderActionType): Boolean = runOnMain {
        overlayView?.findViewWithTag<View>(action)?.performClick() == true
    }

    companion object {
        private const val TAG = "HeyOverlayPresenter"

        @Volatile
        internal var isAnyOverlayVisible: Boolean = false
            private set
    }
}
