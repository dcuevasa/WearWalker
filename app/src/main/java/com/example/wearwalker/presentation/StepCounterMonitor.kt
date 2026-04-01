package com.example.wearwalker.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class StepCounterMonitor(
    context: Context,
    private val onStepCounterSample: (Long) -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    var isListening: Boolean = false
        private set

    val isAvailable: Boolean
        get() = sensorManager != null && stepCounterSensor != null

    fun start() {
        if (!isAvailable || isListening) {
            return
        }

        val started = sensorManager?.registerListener(
            this,
            stepCounterSensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        ) == true
        isListening = started
    }

    fun stop() {
        if (!isListening) {
            return
        }
        sensorManager?.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) {
            return
        }
        val totalSteps = event.values.firstOrNull()?.toLong()?.coerceAtLeast(0L) ?: return
        onStepCounterSample(totalSteps)
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {
        // Accuracy updates are not required for step-counter reconciliation.
    }
}
