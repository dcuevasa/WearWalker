package com.example.wearwalker.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

class StepTrackingStore(
    context: Context,
) {
    companion object {
        private const val PREFS_NAME = "step_tracking_store"
        private const val KEY_LAST_SENSOR_TOTAL = "last_sensor_total"
        private const val KEY_PENDING_STEP_DELTA = "pending_step_delta"
        private const val KEY_LAST_ROLLOVER_DATE = "last_rollover_date"
        private const val KEY_WATT_REMAINDER = "watt_remainder"
        private const val KEY_DEFERRED_EXP_STEPS = "deferred_exp_steps"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readLastSensorTotal(): Long? {
        if (!prefs.contains(KEY_LAST_SENSOR_TOTAL)) {
            return null
        }
        return prefs.getLong(KEY_LAST_SENSOR_TOTAL, 0L).coerceAtLeast(0L)
    }

    fun writeLastSensorTotal(total: Long) {
        commit {
            putLong(KEY_LAST_SENSOR_TOTAL, total.coerceAtLeast(0L))
        }
    }

    fun clearLastSensorTotal() {
        commit {
            remove(KEY_LAST_SENSOR_TOTAL)
        }
    }

    fun addPendingStepDelta(delta: Int): Int {
        if (delta <= 0) {
            return readPendingStepDelta()
        }
        val current = readPendingStepDelta()
        val updated = current.toLong().plus(delta.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        commit {
            putInt(KEY_PENDING_STEP_DELTA, updated)
        }
        return updated
    }

    fun consumePendingStepDelta(): Int {
        val pending = readPendingStepDelta()
        if (pending <= 0) {
            return 0
        }
        commit {
            putInt(KEY_PENDING_STEP_DELTA, 0)
        }
        return pending
    }

    fun restorePendingStepDelta(delta: Int) {
        addPendingStepDelta(delta)
    }

    fun readPendingStepDelta(): Int {
        return prefs.getInt(KEY_PENDING_STEP_DELTA, 0).coerceAtLeast(0)
    }

    fun readWattRemainder(): Int {
        return prefs.getInt(KEY_WATT_REMAINDER, 0).coerceIn(0, 19)
    }

    fun writeWattRemainder(remainder: Int) {
        commit {
            putInt(KEY_WATT_REMAINDER, remainder.coerceIn(0, 19))
        }
    }

    fun addDeferredExpSteps(delta: Int): Long {
        if (delta <= 0) {
            return readDeferredExpSteps()
        }
        val current = readDeferredExpSteps()
        val updated = (current + delta.toLong()).coerceAtLeast(0L)
        commit {
            putLong(KEY_DEFERRED_EXP_STEPS, updated)
        }
        return updated
    }

    fun readDeferredExpSteps(): Long {
        return prefs.getLong(KEY_DEFERRED_EXP_STEPS, 0L).coerceAtLeast(0L)
    }

    fun readLastRolloverDate(): LocalDate? {
        val value = prefs.getString(KEY_LAST_ROLLOVER_DATE, null) ?: return null
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }

    fun writeLastRolloverDate(date: LocalDate) {
        commit {
            putString(KEY_LAST_ROLLOVER_DATE, date.toString())
        }
    }

    private fun commit(block: SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
        editor.block()
        editor.commit()
    }
}
