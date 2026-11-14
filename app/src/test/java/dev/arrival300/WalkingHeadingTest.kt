package dev.arrival300

import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WalkingHeadingTest {
    private val manager get() = RuntimeEnvironment.getApplication().getSystemService(SensorManager::class.java)

    @Test fun rotationVectorRespectsDisplayRotationAndShowsLowAccuracy() {
        val hardware = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR)
        val simulated = shadowOf(manager)
        simulated.addSensor(hardware)
        var rotation = Surface.ROTATION_0
        var state = WalkingHeading()
        val listener = HeadingSensor(manager, 0f, { rotation }) { state = it }
        listener.start()
        val event = ShadowSensorManager.createSensorEvent(5, Sensor.TYPE_ROTATION_VECTOR)
        event.values[3] = 1f
        event.values[4] = .2f
        event.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        simulated.sendSensorEventToListeners(event, hardware)
        assertEquals(0f, requireNotNull(state.degrees), .01f)

        rotation = Surface.ROTATION_90
        simulated.sendSensorEventToListeners(event, hardware)
        assertEquals(90f, requireNotNull(state.degrees), .01f)
        event.accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        simulated.sendSensorEventToListeners(event, hardware)
        assertNull(state.degrees)
        assertTrue(state.needsCalibration)

        listener.stop()
        assertTrue(simulated.listeners.isEmpty())
    }

    @Test fun noSensorsLeavesTheHeadingEmptyAndAccelerometerAndMagnetometerCanProvideIt() {
        val simulated = shadowOf(manager)
        var state = WalkingHeading()
        val listener = HeadingSensor(manager, 0f, { Surface.ROTATION_0 }) { state = it }
        listener.start()
        assertTrue(simulated.listeners.isEmpty())
        assertEquals(WalkingHeading(), state)

        val accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = ShadowSensor.newInstance(Sensor.TYPE_MAGNETIC_FIELD)
        simulated.addSensor(accelerometer)
        simulated.addSensor(magnetometer)
        listener.start()
        val gravity = ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_ACCELEROMETER)
        gravity.values[2] = 9.81f
        simulated.sendSensorEventToListeners(gravity, accelerometer)
        val magnetic = ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_MAGNETIC_FIELD)
        magnetic.values[1] = 35f
        magnetic.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        simulated.sendSensorEventToListeners(magnetic, magnetometer)
        assertEquals(0f, requireNotNull(state.degrees), .01f)
        listener.stop()
    }
}
