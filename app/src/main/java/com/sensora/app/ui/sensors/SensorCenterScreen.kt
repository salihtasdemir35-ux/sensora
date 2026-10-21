package com.sensora.app.ui.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.os.BatteryManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.sensora.app.R
import com.sensora.app.ui.catalog.ToolCatalog
import com.sensora.app.ui.common.*
import com.sensora.app.ui.theme.BadColor
import com.sensora.app.ui.theme.GoodColor

private data class KeySensor(val type: Int, @StringRes val name: Int, val usedBy: List<String>)

private val KEY_SENSORS = listOf(
    KeySensor(Sensor.TYPE_ACCELEROMETER, R.string.s_accelerometer, listOf("level", "incline", "steps")),
    KeySensor(Sensor.TYPE_GYROSCOPE, R.string.s_gyroscope, listOf("compass")),
    KeySensor(Sensor.TYPE_MAGNETIC_FIELD, R.string.s_magnetometer, listOf("compass", "magnet")),
    KeySensor(Sensor.TYPE_LIGHT, R.string.s_light, listOf("light")),
    KeySensor(Sensor.TYPE_PROXIMITY, R.string.s_proximity, emptyList()),
    KeySensor(Sensor.TYPE_PRESSURE, R.string.s_pressure, listOf("baro")),
    KeySensor(Sensor.TYPE_RELATIVE_HUMIDITY, R.string.s_humidity, emptyList()),
    KeySensor(Sensor.TYPE_AMBIENT_TEMPERATURE, R.string.s_ambient_temp, emptyList()),
    KeySensor(Sensor.TYPE_STEP_COUNTER, R.string.s_step_counter, listOf("steps")),
    KeySensor(Sensor.TYPE_STEP_DETECTOR, R.string.s_step_detector, emptyList()),
    KeySensor(Sensor.TYPE_GRAVITY, R.string.s_gravity, listOf("level", "incline")),
    KeySensor(Sensor.TYPE_LINEAR_ACCELERATION, R.string.s_linear_accel, emptyList()),
    KeySensor(Sensor.TYPE_ROTATION_VECTOR, R.string.s_rotation_vector, listOf("compass")),
)

@Composable
fun SensorCenterScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sm = rememberSensorManager()
    val pm = ctx.packageManager
    var showAll by remember { mutableStateOf(false) }
    SimpleScaffold(stringResource(R.string.sensor_center), onBack) { padding ->
        ScreenColumn(padding) {
            Text(stringResource(R.string.sc_key_sensors), style = MaterialTheme.typography.titleMedium)
            KEY_SENSORS.forEach { k ->
                val s = sm.getDefaultSensor(k.type)
                InfoCard {
                    InfoRow(stringResource(k.name), stringResource(if (s != null) R.string.sc_present else R.string.sc_absent))
                    if (s != null) {
                        Text(stringResource(R.string.sc_vendor, "${s.vendor} · ${s.name}"), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.sc_range, fmt(s.maximumRange.toDouble(), 2)) + " · " +
                            stringResource(R.string.sc_resolution, fmt(s.resolution.toDouble(), 4)) + " · " +
                            stringResource(R.string.sc_power, fmt(s.power.toDouble(), 2)), style = MaterialTheme.typography.bodySmall)
                    }
                    if (k.usedBy.isNotEmpty()) {
                        val names = k.usedBy.mapNotNull { ToolCatalog.byId(it) }.map { stringResource(it.title) }.joinToString(", ")
                        Text(stringResource(R.string.sc_used_by, names), style = MaterialTheme.typography.bodySmall,
                            color = if (s != null) GoodColor else BadColor)
                    }
                }
            }
            Text(stringResource(R.string.sc_ambient_note), style = MaterialTheme.typography.bodySmall)

            Text(stringResource(R.string.sc_other_hw), style = MaterialTheme.typography.titleMedium)
            InfoCard {
                listOf(
                    R.string.hw_gps to PackageManager.FEATURE_LOCATION_GPS,
                    R.string.hw_camera to PackageManager.FEATURE_CAMERA_ANY,
                    R.string.hw_microphone to PackageManager.FEATURE_MICROPHONE,
                    R.string.hw_bluetooth to PackageManager.FEATURE_BLUETOOTH_LE,
                    R.string.hw_nfc to PackageManager.FEATURE_NFC,
                ).forEach { (label, feature) ->
                    InfoRow(stringResource(label), stringResource(if (pm.hasSystemFeature(feature)) R.string.sc_present else R.string.sc_absent))
                }
            }

            BatteryCard(ctx)

            val all = remember(sm) { sm.getSensorList(Sensor.TYPE_ALL) }
            TextButton(onClick = { showAll = !showAll }) { Text(stringResource(R.string.sc_all_sensors, all.size)) }
            if (showAll) {
                InfoCard {
                    all.forEach { s -> Text("${s.name} (${s.stringType})", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun BatteryCard(ctx: Context) {
    val intent: Intent? = remember { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    InfoCard {
        Text(stringResource(R.string.sc_battery), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (intent == null) {
            Text("—")
        } else {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (level >= 0 && scale > 0) InfoRow(stringResource(R.string.sc_battery_level), "${level * 100 / scale} %")
            if (temp != Int.MIN_VALUE) InfoRow(stringResource(R.string.sc_battery_temp), "${fmt(temp / 10.0, 1)} °C")
            InfoRow(stringResource(R.string.sc_battery), stringResource(if (charging) R.string.sc_charging else R.string.sc_not_charging))
        }
    }
}
