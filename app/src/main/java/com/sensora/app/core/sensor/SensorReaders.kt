package com.sensora.app.core.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sensora.app.core.math.AudioMath
import com.sensora.app.core.math.OrientationMath
import com.sensora.app.core.math.StabilityTracker
import com.sensora.app.core.math.Tilt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max

data class SensorSample(val values: FloatArray, val accuracy: Int, val timestampNs: Long)

enum class CompassMethod { ROTATION_VECTOR, ACCEL_MAG }

data class CompassReading(
    val heading: Double,
    val pitchDeg: Double,
    val fieldMicroTesla: Double,
    val accuracy: Int,
    val method: CompassMethod,
)

data class LevelSample(val gx: Double, val gy: Double, val gz: Double, val tilt: Tilt, val stability: Double)

data class MicFrame(val dbfs: Double, val clipping: Boolean)

/**
 * Cold flows over Android hardware. A listener is registered only while the flow is collected,
 * so collecting with collectAsStateWithLifecycle stops the sensor when the screen is not visible.
 */
object SensorReaders {

    fun samples(sm: SensorManager, type: Int, delay: Int = SensorManager.SENSOR_DELAY_UI): Flow<SensorSample> =
        callbackFlow {
            val sensor = sm.getDefaultSensor(type)
            if (sensor == null) {
                close()
                return@callbackFlow
            }
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    trySend(SensorSample(e.values.copyOf(), e.accuracy, e.timestamp))
                }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(listener, sensor, delay)
            awaitClose { sm.unregisterListener(listener) }
        }

    /** Gravity vector: hardware/virtual gravity sensor if present, otherwise low-passed accelerometer. */
    fun gravity(sm: SensorManager): Flow<FloatArray> {
        if (sm.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) {
            return samples(sm, Sensor.TYPE_GRAVITY).map { it.values }
        }
        var filtered: FloatArray? = null
        return samples(sm, Sensor.TYPE_ACCELEROMETER).map { s ->
            val p = filtered
            val next = if (p == null) s.values.copyOf(3) else FloatArray(3) { i -> p[i] + 0.15f * (s.values[i] - p[i]) }
            filtered = next
            next
        }
    }

    fun levelSamples(sm: SensorManager): Flow<LevelSample> {
        val tracker = StabilityTracker(20)
        return gravity(sm).map { g ->
            val gx = g[0].toDouble(); val gy = g[1].toDouble(); val gz = g[2].toDouble()
            val t = OrientationMath.tilt(gx, gy, gz)
            LevelSample(gx, gy, gz, t, tracker.add(t.rollDeg, t.pitchDeg))
        }
    }

    private fun orientation(r: FloatArray): Pair<Double, Double> {
        val o = FloatArray(3)
        SensorManager.getOrientation(r, o)
        val pitch = Math.toDegrees(o[1].toDouble())
        if (abs(pitch) > 55.0) {
            // Phone held upright: follow the direction the back camera points to.
            val remapped = FloatArray(9)
            SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            SensorManager.getOrientation(remapped, o)
        }
        return OrientationMath.normalizeDegrees(Math.toDegrees(o[0].toDouble())) to pitch
    }

    fun compass(sm: SensorManager): Flow<CompassReading> {
        if (sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null) return emptyFlow()
        val mag = samples(sm, Sensor.TYPE_MAGNETIC_FIELD)
        val hasRotationVector = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
        val method = if (hasRotationVector) CompassMethod.ROTATION_VECTOR else CompassMethod.ACCEL_MAG
        val orient: Flow<Pair<Double, Double>> = if (hasRotationVector) {
            samples(sm, Sensor.TYPE_ROTATION_VECTOR).map { s ->
                val r = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(r, if (s.values.size > 4) s.values.copyOf(4) else s.values)
                orientation(r)
            }
        } else {
            combine(samples(sm, Sensor.TYPE_ACCELEROMETER), mag) { a, m ->
                val r = FloatArray(9)
                if (SensorManager.getRotationMatrix(r, null, a.values, m.values)) orientation(r) else null
            }.filterNotNull()
        }
        var smoothed: Double? = null
        return combine(orient, mag) { o, m ->
            val h = OrientationMath.smoothAngle(smoothed, o.first, 0.2)
            smoothed = h
            CompassReading(
                heading = h,
                pitchDeg = o.second,
                fieldMicroTesla = OrientationMath.magnitude(m.values[0].toDouble(), m.values[1].toDouble(), m.values[2].toDouble()),
                accuracy = m.accuracy,
                method = method,
            )
        }
    }

    /**
     * Device-to-world rotation matrix (row-major 3×3; world = east, magnetic north, up), lightly smoothed.
     * Empty when the device has no way to know its orientation.
     */
    fun rotationMatrix(sm: SensorManager): Flow<FloatArray> {
        val raw: Flow<FloatArray> = when {
            sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ->
                samples(sm, Sensor.TYPE_ROTATION_VECTOR, SensorManager.SENSOR_DELAY_GAME).map { s ->
                    val r = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(r, if (s.values.size > 4) s.values.copyOf(4) else s.values)
                    r
                }
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null && sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null ->
                combine(samples(sm, Sensor.TYPE_ACCELEROMETER), samples(sm, Sensor.TYPE_MAGNETIC_FIELD)) { a, m ->
                    val r = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, null, a.values, m.values)) r else null
                }.filterNotNull()
            else -> emptyFlow()
        }
        var smooth: FloatArray? = null
        return raw.map { r ->
            val p = smooth
            val next = if (p == null) r.copyOf() else FloatArray(9) { i -> p[i] + 0.25f * (r[i] - p[i]) }
            smooth = next
            next
        }
    }

    fun hasOrientation(sm: SensorManager): Boolean =
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
            (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null && sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)

    /** Location updates via Fused provider, falling back to the platform GPS provider (e.g. no Play services). */
    @SuppressLint("MissingPermission")
    fun location(context: Context, intervalMs: Long = 1000): Flow<Location> = callbackFlow {
        val useFused = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        if (useFused) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs / 2)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { trySend(it) }
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { close(it) }
            awaitClose { client.removeLocationUpdates(callback) }
        } else {
            val lm = context.getSystemService(LocationManager::class.java)
            val listener = LocationListener { trySend(it) }
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                close(e)
            }
            awaitClose { lm.removeUpdates(listener) }
        }
    }

    suspend fun currentLocation(context: Context): Location? =
        withTimeoutOrNull(20_000) { location(context).catch { }.firstOrNull() }

    /** Microphone level frames ([frameMs] long). Audio is processed in memory and never stored. */
    @SuppressLint("MissingPermission")
    fun micFrames(context: Context, frameMs: Int = 100): Flow<MicFrame> = flow {
        val rate = 44100
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBuf > 0) { "AudioRecord not supported" }
        val am = context.getSystemService(AudioManager::class.java)
        val source = if (am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true")
            MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val frame = ShortArray(rate * frameMs / 1000)
        val rec = AudioRecord(
            source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            max(minBuf, frame.size * 2) * 2,
        )
        try {
            check(rec.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
            rec.startRecording()
            while (currentCoroutineContext().isActive) {
                var read = 0
                while (read < frame.size) {
                    val n = rec.read(frame, read, frame.size - read)
                    check(n > 0) { "AudioRecord read error $n" }
                    read += n
                }
                emit(MicFrame(AudioMath.dbfs(AudioMath.rms(frame, read)), AudioMath.clippingRatio(frame, read) > 0.01))
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }.flowOn(Dispatchers.IO)
}
