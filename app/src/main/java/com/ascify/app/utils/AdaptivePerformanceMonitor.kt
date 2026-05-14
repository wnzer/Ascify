package com.ascify.app.utils

import android.os.SystemClock
import com.ascify.app.settings.AsciiDensity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AdaptivePerformanceMonitor
 *
 * Watches the rendering FPS and automatically adjusts ASCII density to maintain
 * smooth performance on mid-range devices. This prevents overheating and frame drops.
 *
 * Algorithm:
 *  - If FPS drops below LOW_FPS_THRESHOLD for WINDOW_MS consecutive ms → reduce density
 *  - If FPS exceeds HIGH_FPS_THRESHOLD for WINDOW_MS consecutive ms → increase density
 *  - Cooldown period prevents oscillation
 */
@Singleton
class AdaptivePerformanceMonitor @Inject constructor() {

    companion object {
        private const val LOW_FPS_THRESHOLD = 12f     // below this → drop quality
        private const val HIGH_FPS_THRESHOLD = 25f    // above this → raise quality
        private const val WINDOW_MS = 3000L           // evaluation window
        private const val COOLDOWN_MS = 5000L         // cooldown after adjustment
    }

    private val _suggestedDensity = MutableStateFlow<AsciiDensity?>(null)
    val suggestedDensity: StateFlow<AsciiDensity?> = _suggestedDensity.asStateFlow()

    private var enabled = true
    private var lastAdjustmentMs = 0L
    private var lowFpsStartMs = 0L
    private var highFpsStartMs = 0L

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun onFpsUpdate(fps: Float, currentDensity: AsciiDensity) {
        if (!enabled) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAdjustmentMs < COOLDOWN_MS) return

        when {
            fps < LOW_FPS_THRESHOLD -> {
                if (lowFpsStartMs == 0L) lowFpsStartMs = now
                highFpsStartMs = 0L
                if (now - lowFpsStartMs >= WINDOW_MS) {
                    val reduced = reduceDensity(currentDensity)
                    if (reduced != currentDensity) {
                        _suggestedDensity.value = reduced
                        lastAdjustmentMs = now
                        lowFpsStartMs = 0L
                    }
                }
            }
            fps > HIGH_FPS_THRESHOLD -> {
                if (highFpsStartMs == 0L) highFpsStartMs = now
                lowFpsStartMs = 0L
                if (now - highFpsStartMs >= WINDOW_MS) {
                    val raised = raiseDensity(currentDensity)
                    if (raised != currentDensity) {
                        _suggestedDensity.value = raised
                        lastAdjustmentMs = now
                        highFpsStartMs = 0L
                    }
                }
            }
            else -> {
                lowFpsStartMs = 0L
                highFpsStartMs = 0L
            }
        }
    }

    fun clearSuggestion() {
        _suggestedDensity.value = null
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    private fun reduceDensity(current: AsciiDensity): AsciiDensity {
        val values = AsciiDensity.values()
        val idx = current.ordinal
        return if (idx > 0) values[idx - 1] else current
    }

    private fun raiseDensity(current: AsciiDensity): AsciiDensity {
        val values = AsciiDensity.values()
        val idx = current.ordinal
        return if (idx < values.size - 1) values[idx + 1] else current
    }
}
