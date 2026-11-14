package dev.arrival300

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign

internal data class WalkingHeading(val degrees: Float? = null, val needsCalibration: Boolean = false)

@Composable
internal fun rememberWalkingHeading(enabled: Boolean, origin: Coordinate): State<WalkingHeading> {
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val heading = remember { mutableStateOf(WalkingHeading()) }
    DisposableEffect(enabled, origin, view, lifecycle) {
        val manager = view.context.getSystemService(SensorManager::class.java)
        val sensor = if (enabled && manager != null) {
            val field = GeomagneticField(origin.latitude.toFloat(), origin.longitude.toFloat(), 0f, System.currentTimeMillis())
            HeadingSensor(manager, field.declination, { view.display?.rotation ?: Surface.ROTATION_0 }) {
                heading.value = it
            }
        } else null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensor?.start()
                Lifecycle.Event.ON_PAUSE -> sensor?.stop()
                else -> Unit
            }
        }
        if (sensor != null) lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sensor?.stop()
        }
    }
    return heading
}

internal class HeadingSensor(
    private val manager: SensorManager,
    private val declination: Float,
    private val rotation: () -> Int,
    private val publish: (WalkingHeading) -> Unit
) : SensorEventListener {
    private val matrix = FloatArray(9)
    private val screen = FloatArray(9)
    private var acceleration: FloatArray? = null
    private var magnetic: FloatArray? = null
    private var magneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var filtered: Float? = null
    private var displayRotation: Int? = null

    fun start() {
        val vectorAvailable = listOf(Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR).any { type ->
            val vector = manager.getDefaultSensor(type)
            vector != null && manager.registerListener(this, vector, 40_000)
        }
        if (vectorAvailable) return
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) ?: return
        if (!manager.registerListener(this, accelerometer, 40_000) ||
            !manager.registerListener(this, magnetometer, 40_000)) stop()
    }

    fun stop() {
        manager.unregisterListener(this)
        acceleration = null
        magnetic = null
        magneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        filtered = null
        displayRotation = null
        publish(WalkingHeading())
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magneticAccuracy = accuracy
    }

    override fun onSensorChanged(event: SensorEvent) {
        val reliable = when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                val estimate = event.values.getOrNull(4)
                event.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM &&
                    (estimate == null || estimate < 0 || estimate <= Math.PI / 4)
            }
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD -> {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    acceleration = event.values.copyOf(3)
                } else {
                    magnetic = event.values.copyOf(3)
                    magneticAccuracy = event.accuracy
                }
                val gravity = acceleration ?: return
                val field = magnetic ?: return
                SensorManager.getRotationMatrix(matrix, null, gravity, field) &&
                    magneticAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            }
            else -> return
        }
        if (!reliable) {
            filtered = null
            publish(WalkingHeading(needsCalibration = true))
            return
        }
        val currentRotation = rotation()
        val (x, y) = when (currentRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        if (!SensorManager.remapCoordinateSystem(matrix, x, y, screen)) return
        val target = mapHeading(screen, declination)
        val previous = filtered.takeIf { currentRotation == displayRotation }
        val next = previous?.let { smoothHeading(it, target) } ?: target
        displayRotation = currentRotation
        filtered = next
        publish(WalkingHeading(next))
    }
}

internal fun mapHeading(screen: FloatArray, declination: Float): Float {
    // The top edge points nearly straight up when the phone is held upright.
    // Blend toward the back camera's forward axis in that position.
    val upright = ((abs(screen[7]) - .80f) / .20f).coerceIn(0f, 1f)
    val forward = -sign(screen[7]) * upright
    val east = screen[1] * (1f - upright) + screen[2] * forward
    val north = screen[4] * (1f - upright) + screen[5] * forward
    return wrapDegrees(Math.toDegrees(atan2(east, north).toDouble()).toFloat() + declination)
}

internal fun smoothHeading(previous: Float, next: Float): Float {
    val delta = wrapDegrees(next - previous + 180f) - 180f
    return wrapDegrees(previous + delta * .25f)
}

private fun wrapDegrees(value: Float) = ((value % 360f) + 360f) % 360f
