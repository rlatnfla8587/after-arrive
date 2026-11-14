package dev.arrival300

import android.car.Car
import android.car.VehicleAreaSeat
import android.car.VehicleGear
import android.car.VehicleIgnitionState
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech

/** Public AAOS properties are subscribed independently so a missing capability stays unknown. */
@Suppress("DEPRECATION")
class AaosSafety(private val context: Context, private val onStatus: (ArrivalVehicle) -> Unit) {
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var propertyManager: CarPropertyManager? = null
    private var listener: CarPropertyManager.CarPropertyEventCallback? = null
    private var generation = 0
    private var restricted: Boolean? = null
    private var parkedGear: Boolean? = null
    private var stationary: Boolean? = null
    private var ignitionOff: Boolean? = null
    private var driverSeated: Boolean? = null

    fun connect() {
        if (Build.VERSION.SDK_INT < 29 || car != null) return
        val session = ++generation
        car = runCatching {
            Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
                if (session != generation) return@createCar
                detach()
                if (ready) runCatching { observe(connectedCar, session) }.onFailure { detach() }
            }
        }.getOrNull()
    }

    private fun observe(connectedCar: Car, session: Int) {
        val ux = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
        val properties = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
        uxManager = ux
        propertyManager = properties
        ux.registerListener {
            if (session == generation && uxManager === ux) {
                restricted = it.isRequiresDistractionOptimization
                publish()
            }
        }
        restricted = ux.currentCarUxRestrictions?.isRequiresDistractionOptimization

        val driverSeat = runCatching {
            val value = properties.getProperty<Int>(VehiclePropertyIds.INFO_DRIVER_SEAT, 0)
            value?.takeIf { it.status == CarPropertyValue.STATUS_AVAILABLE }?.value
                ?.takeUnless { it == VehicleAreaSeat.SEAT_UNKNOWN }
        }.getOrNull()
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (session != generation || propertyManager !== properties) return
                if (value.propertyId == VehiclePropertyIds.SEAT_OCCUPANCY && value.areaId != driverSeat) return
                update(value.propertyId, value.value.takeIf { value.status == CarPropertyValue.STATUS_AVAILABLE })
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                if (session == generation && propertyManager === properties &&
                    (propertyId != VehiclePropertyIds.SEAT_OCCUPANCY || areaId == driverSeat)) {
                    update(propertyId, null)
                }
            }
        }
        listener = callback
        val onChange = CarPropertyManager.SENSOR_RATE_ONCHANGE
        runCatching { properties.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, onChange) }
        runCatching { properties.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) }
        runCatching { properties.registerCallback(callback, VehiclePropertyIds.IGNITION_STATE, onChange) }
        if (driverSeat != null) runCatching {
            properties.registerCallback(callback, VehiclePropertyIds.SEAT_OCCUPANCY, onChange)
        }
        publish()
    }

    private fun update(property: Int, value: Any?) {
        when (property) {
            VehiclePropertyIds.GEAR_SELECTION ->
                parkedGear = (value as? Int)?.takeUnless { it == VehicleGear.GEAR_UNKNOWN }?.let { it == VehicleGear.GEAR_PARK }
            VehiclePropertyIds.PERF_VEHICLE_SPEED ->
                stationary = (value as? Float)?.takeIf { it.isFinite() }?.let { kotlin.math.abs(it) <= .01f }
            VehiclePropertyIds.IGNITION_STATE -> ignitionOff = when (value) {
                VehicleIgnitionState.LOCK, VehicleIgnitionState.OFF -> true
                VehicleIgnitionState.ACC, VehicleIgnitionState.ON, VehicleIgnitionState.START -> false
                else -> null
            }
            VehiclePropertyIds.SEAT_OCCUPANCY -> driverSeated = when (value) {
                SEAT_VACANT -> false
                SEAT_OCCUPIED -> true
                else -> null
            }
        }
        publish()
    }

    private fun publish() = onStatus(ArrivalVehicle(aaosStatus(restricted, parkedGear), stationary, ignitionOff, driverSeated))

    private fun detach() {
        val ux = uxManager
        val properties = propertyManager
        val callback = listener
        uxManager = null
        propertyManager = null
        listener = null
        restricted = null
        parkedGear = null
        stationary = null
        ignitionOff = null
        driverSeated = null
        publish()
        runCatching { ux?.unregisterListener() }
        if (callback != null) runCatching { properties?.unregisterCallback(callback) }
    }

    fun close() {
        generation++
        detach()
        runCatching { car?.disconnect() }
        car = null
    }

    private companion object {
        // Values defined by AOSP VehicleSeatOccupancyState for the public SEAT_OCCUPANCY property.
        const val SEAT_VACANT = 1
        const val SEAT_OCCUPIED = 2
    }
}

class KoreanSpeech(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var closed = false
    private var ready = false
    private var clip: MediaPlayer? = null
    private val engine: TextToSpeech = TextToSpeech(app) { result ->
        main.post {
            if (!closed && result == TextToSpeech.SUCCESS) configureVoice()
        }
    }

    private fun configureVoice() {
        val voice = engine.voices.orEmpty().firstOrNull {
            it.locale.language == "ko" && !it.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
        } ?: return
        engine.setAudioAttributes(attributes)
        ready = engine.setVoice(voice) == TextToSpeech.SUCCESS
    }

    fun speak(text: String) {
        if (closed) return
        stop()
        if (ready && engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "garage-approach") == TextToSpeech.SUCCESS) return
        val resource = when (text) {
            RIGHT_CUE -> R.raw.entry_right
            LEFT_CUE -> R.raw.entry_left
            else -> return
        }
        val player = runCatching {
            MediaPlayer.create(app, resource, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE)
        }.getOrNull() ?: return
        clip = player
        runCatching {
            player.setOnCompletionListener {
                if (clip === it) {
                    it.release()
                    clip = null
                }
            }
            player.start()
        }.onFailure {
            player.release()
            clip = null
        }
    }

    fun stop() {
        engine.stop()
        clip?.release()
        clip = null
    }

    fun close() {
        closed = true
        stop()
        engine.shutdown()
    }
}


