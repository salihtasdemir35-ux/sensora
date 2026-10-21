package com.sensora.app.sleep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sensora.app.MainActivity
import com.sensora.app.R
import com.sensora.app.core.sleep.SleepEpoch
import com.sensora.app.core.sleep.SleepMath
import com.sensora.app.core.sleep.SoundEventDetector
import com.sensora.app.core.sensor.SensorReaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.sqrt

data class SleepLive(val recording: Boolean = false, val startMs: Long = 0, val minutes: Int = 0, val lastDb: Double = -90.0, val error: Boolean = false)

/**
 * Overnight recording of movement (accelerometer) and sound levels (microphone, levels only).
 * Runs as a foreground service with a visible notification so it survives the screen turning off.
 */
class SleepRecordingService : Service(), SensorEventListener {
    companion object {
        private const val CHANNEL = "sleep_recording"
        private const val NOTIFICATION_ID = 41
        private val _state = MutableStateFlow(SleepLive())
        val state: StateFlow<SleepLive> = _state.asStateFlow()
        private val epochs = ArrayList<SleepEpoch>()

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, SleepRecordingService::class.java))
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, SleepRecordingService::class.java)) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val movement = AtomicInteger(0)
    private var meanMag = -1.0
    private var wakeLock: PowerManager.WakeLock? = null
    private var startMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (_state.value.recording) return START_NOT_STICKY
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sensora:sleep").apply { acquire(14 * 3_600_000L) }
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        synchronized(epochs) { epochs.clear() }
        startMs = System.currentTimeMillis()
        _state.value = SleepLive(recording = true, startMs = startMs)
        scope.launch { recordAudio() }
        return START_NOT_STICKY
    }

    private suspend fun recordAudio() {
        val detector = SoundEventDetector(0.1)
        val minuteEvents = ArrayList<SoundEventDetector.Event>()
        var frames = 0; var sumDb = 0.0; var maxDb = -120.0; var loud = 0
        try {
            SensorReaders.micFrames(this, 100).collect { f ->
                frames++; sumDb += f.dbfs; if (f.dbfs > maxDb) maxDb = f.dbfs
                detector.add(f.dbfs)?.let { e ->
                    minuteEvents.add(e)
                    if (e.peakAboveFloor >= 25 || detector.noiseFloor + e.peakAboveFloor > -20) loud++
                }
                if (frames >= 600) {
                    val epoch = SleepEpoch(movement.getAndSet(0), sumDb / frames, maxDb, SleepMath.isSnoreMinute(minuteEvents), loud)
                    val n = synchronized(epochs) { epochs.add(epoch); epochs.size }
                    if (n % 10 == 0) persist()
                    _state.value = _state.value.copy(minutes = n, lastDb = epoch.avgDb)
                    frames = 0; sumDb = 0.0; maxDb = -120.0; loud = 0; minuteEvents.clear()
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.value = _state.value.copy(error = true)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        val m = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
        if (meanMag < 0) meanMag = m
        meanMag += 0.05 * (m - meanMag)
        if (abs(m - meanMag) > 0.08) movement.incrementAndGet()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun persist() {
        val copy = synchronized(epochs) { epochs.toList() }
        if (copy.isNotEmpty()) SleepStore.save(this, SleepSession(startMs, System.currentTimeMillis(), copy))
    }

    override fun onDestroy() {
        (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        scope.cancel()
        persist()
        wakeLock?.let { if (it.isHeld) it.release() }
        _state.value = SleepLive(recording = false)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.sleep_channel), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sensora)
            .setContentTitle(getString(R.string.sleep_notif_title))
            .setContentText(getString(R.string.sleep_notif_text))
            .setOngoing(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
