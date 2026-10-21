package com.sensora.app.ui.catalog

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.ui.graphics.vector.ImageVector
import com.sensora.app.R

enum class Category(@StringRes val title: Int, val icon: ImageVector) {
    DIRECTION(R.string.cat_direction, Icons.Filled.Explore),
    SPACE(R.string.cat_space, Icons.Filled.NightsStay),
    CAMERA(R.string.cat_camera, Icons.Filled.CameraAlt),
    SOUND(R.string.cat_sound, Icons.Filled.GraphicEq),
    LIGHT(R.string.cat_light, Icons.Filled.LightMode),
    WEATHER(R.string.cat_weather, Icons.Filled.Cloud),
    MOTION(R.string.cat_motion, Icons.Filled.Speed),
    HEALTH(R.string.cat_health, Icons.Filled.Favorite),
    ELECTRONICS(R.string.cat_electronics, Icons.Filled.Memory),
    DAILY(R.string.cat_daily, Icons.Filled.Build),
}

enum class Hardware(@StringRes val label: Int) {
    MAGNETOMETER(R.string.hw_magnetometer),
    ACCELEROMETER(R.string.hw_accelerometer),
    LIGHT(R.string.hw_light),
    BAROMETER(R.string.hw_barometer),
    STEP(R.string.hw_step),
    MICROPHONE(R.string.hw_microphone),
    GPS(R.string.hw_gps),
    CAMERA(R.string.hw_camera),
    ARCORE(R.string.hw_arcore),
    SPEAKER(R.string.hw_speaker),
    NONE(R.string.hw_none),
}

enum class Reliability(@StringRes val label: Int) {
    HIGH(R.string.rel_high), MEDIUM(R.string.rel_medium), LOW(R.string.rel_low)
}

enum class Availability { FULL, FALLBACK, NONE }

data class Tool(
    val id: String,
    val category: Category,
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val example: Int,
    @StringRes val howItWorks: Int,
    val hardware: List<Hardware>,
    val reliability: Reliability,
    val icon: ImageVector,
)

object ToolCatalog {
    val tools: List<Tool> = listOf(
        Tool("compass", Category.DIRECTION, R.string.tool_compass, R.string.tool_compass_desc, R.string.tool_compass_ex,
            R.string.tool_compass_how, listOf(Hardware.MAGNETOMETER, Hardware.ACCELEROMETER), Reliability.MEDIUM, Icons.Filled.Explore),
        Tool("level", Category.DIRECTION, R.string.tool_level, R.string.tool_level_desc, R.string.tool_level_ex,
            R.string.tool_level_how, listOf(Hardware.ACCELEROMETER), Reliability.HIGH, Icons.Filled.Straighten),
        Tool("incline", Category.DIRECTION, R.string.tool_incline, R.string.tool_incline_desc, R.string.tool_incline_ex,
            R.string.tool_incline_how, listOf(Hardware.ACCELEROMETER), Reliability.HIGH, Icons.Filled.SquareFoot),
        Tool("sky", Category.SPACE, R.string.tool_sky, R.string.tool_sky_desc, R.string.tool_sky_ex,
            R.string.tool_sky_how, listOf(Hardware.MAGNETOMETER, Hardware.ACCELEROMETER, Hardware.GPS), Reliability.MEDIUM, Icons.Filled.AutoAwesome),
        Tool("tonight", Category.SPACE, R.string.tool_tonight, R.string.tool_tonight_desc, R.string.tool_tonight_ex,
            R.string.tool_tonight_how, listOf(Hardware.GPS), Reliability.HIGH, Icons.Filled.Public),
        Tool("sunmoon", Category.SPACE, R.string.tool_sunmoon, R.string.tool_sunmoon_desc, R.string.tool_sunmoon_ex,
            R.string.tool_sunmoon_how, listOf(Hardware.GPS), Reliability.HIGH, Icons.Filled.Brightness4),
        Tool("ar", Category.CAMERA, R.string.tool_ar, R.string.tool_ar_desc, R.string.tool_ar_ex,
            R.string.tool_ar_how, listOf(Hardware.CAMERA, Hardware.ARCORE), Reliability.MEDIUM, Icons.Filled.ViewInAr),
        Tool("angle", Category.CAMERA, R.string.tool_angle, R.string.tool_angle_desc, R.string.tool_angle_ex,
            R.string.tool_angle_how, listOf(Hardware.ACCELEROMETER, Hardware.CAMERA), Reliability.MEDIUM, Icons.Filled.Height),
        Tool("refsize", Category.CAMERA, R.string.tool_refsize, R.string.tool_refsize_desc, R.string.tool_refsize_ex,
            R.string.tool_refsize_how, listOf(Hardware.CAMERA), Reliability.LOW, Icons.Filled.Crop),
        Tool("areavol", Category.CAMERA, R.string.tool_areavol, R.string.tool_areavol_desc, R.string.tool_areavol_ex,
            R.string.tool_areavol_how, listOf(Hardware.NONE), Reliability.HIGH, Icons.Filled.CropSquare),
        Tool("lowlight", Category.CAMERA, R.string.tool_lowlight, R.string.tool_lowlight_desc, R.string.tool_lowlight_ex,
            R.string.tool_lowlight_how, listOf(Hardware.CAMERA), Reliability.LOW, Icons.Filled.Visibility),
        Tool("breath", Category.HEALTH, R.string.tool_breath, R.string.tool_breath_desc, R.string.tool_breath_ex,
            R.string.tool_breath_how, listOf(Hardware.MICROPHONE), Reliability.LOW, Icons.Filled.Air),
        Tool("pulse", Category.HEALTH, R.string.tool_pulse, R.string.tool_pulse_desc, R.string.tool_pulse_ex,
            R.string.tool_pulse_how, listOf(Hardware.CAMERA), Reliability.MEDIUM, Icons.Filled.MonitorHeart),
        Tool("sleep", Category.HEALTH, R.string.tool_sleep, R.string.tool_sleep_desc, R.string.tool_sleep_ex,
            R.string.tool_sleep_how, listOf(Hardware.MICROPHONE, Hardware.ACCELEROMETER), Reliability.LOW, Icons.Filled.Bedtime),
        Tool("quake", Category.ELECTRONICS, R.string.tool_quake, R.string.tool_quake_desc, R.string.tool_quake_ex,
            R.string.tool_quake_how, listOf(Hardware.ACCELEROMETER), Reliability.MEDIUM, Icons.Filled.Vibration),
        Tool("hearing", Category.SOUND, R.string.tool_hearing, R.string.tool_hearing_desc, R.string.tool_hearing_ex,
            R.string.tool_hearing_how, listOf(Hardware.SPEAKER), Reliability.LOW, Icons.Filled.Hearing),
        Tool("vision", Category.HEALTH, R.string.tool_vision, R.string.tool_vision_desc, R.string.tool_vision_ex,
            R.string.tool_vision_how, listOf(Hardware.NONE), Reliability.LOW, Icons.Filled.RemoveRedEye),
        Tool("results", Category.HEALTH, R.string.tool_results, R.string.tool_results_desc, R.string.tool_results_ex,
            R.string.tool_results_how, listOf(Hardware.NONE), Reliability.HIGH, Icons.Filled.Assignment),
        Tool("light", Category.LIGHT, R.string.tool_light, R.string.tool_light_desc, R.string.tool_light_ex,
            R.string.tool_light_how, listOf(Hardware.LIGHT), Reliability.MEDIUM, Icons.Filled.LightMode),
        Tool("sound", Category.SOUND, R.string.tool_sound, R.string.tool_sound_desc, R.string.tool_sound_ex,
            R.string.tool_sound_how, listOf(Hardware.MICROPHONE), Reliability.LOW, Icons.Filled.GraphicEq),
        Tool("tone", Category.SOUND, R.string.tool_tone, R.string.tool_tone_desc, R.string.tool_tone_ex,
            R.string.tool_tone_how, listOf(Hardware.SPEAKER), Reliability.HIGH, Icons.Filled.MusicNote),
        Tool("baro", Category.WEATHER, R.string.tool_baro, R.string.tool_baro_desc, R.string.tool_baro_ex,
            R.string.tool_baro_how, listOf(Hardware.BAROMETER, Hardware.GPS), Reliability.MEDIUM, Icons.Filled.Terrain),
        Tool("steps", Category.MOTION, R.string.tool_steps, R.string.tool_steps_desc, R.string.tool_steps_ex,
            R.string.tool_steps_how, listOf(Hardware.STEP), Reliability.MEDIUM, Icons.AutoMirrored.Filled.DirectionsWalk),
        Tool("speed", Category.MOTION, R.string.tool_speed, R.string.tool_speed_desc, R.string.tool_speed_ex,
            R.string.tool_speed_how, listOf(Hardware.GPS), Reliability.MEDIUM, Icons.Filled.Speed),
        Tool("magnet", Category.ELECTRONICS, R.string.tool_magnet, R.string.tool_magnet_desc, R.string.tool_magnet_ex,
            R.string.tool_magnet_how, listOf(Hardware.MAGNETOMETER), Reliability.MEDIUM, Icons.Filled.Adjust),
        Tool("converter", Category.DAILY, R.string.tool_converter, R.string.tool_converter_desc, R.string.tool_converter_ex,
            R.string.tool_converter_how, listOf(Hardware.NONE), Reliability.HIGH, Icons.Filled.SwapHoriz),
    )

    fun byId(id: String): Tool? = tools.firstOrNull { it.id == id }
}

object DeviceCapabilities {
    val KEY_SENSOR_TYPES = listOf(
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_LIGHT,
        Sensor.TYPE_PRESSURE, Sensor.TYPE_PROXIMITY, Sensor.TYPE_GRAVITY, Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_STEP_COUNTER,
    )

    private fun sm(ctx: Context) = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    fun hasSensor(ctx: Context, type: Int) = sm(ctx).getDefaultSensor(type) != null
    fun hasFeature(ctx: Context, feature: String) = ctx.packageManager.hasSystemFeature(feature)

    fun availability(ctx: Context, tool: Tool): Availability = when (tool.id) {
        "compass", "magnet" -> if (hasSensor(ctx, Sensor.TYPE_MAGNETIC_FIELD)) Availability.FULL else Availability.NONE
        "level", "incline" -> if (hasSensor(ctx, Sensor.TYPE_ACCELEROMETER)) Availability.FULL else Availability.NONE
        "light" -> if (hasSensor(ctx, Sensor.TYPE_LIGHT)) Availability.FULL else Availability.NONE
        "sound" -> if (hasFeature(ctx, PackageManager.FEATURE_MICROPHONE)) Availability.FULL else Availability.NONE
        "baro" -> when {
            hasSensor(ctx, Sensor.TYPE_PRESSURE) -> Availability.FULL
            hasFeature(ctx, PackageManager.FEATURE_LOCATION) -> Availability.FALLBACK
            else -> Availability.NONE
        }
        "steps" -> when {
            hasSensor(ctx, Sensor.TYPE_STEP_COUNTER) -> Availability.FULL
            hasSensor(ctx, Sensor.TYPE_ACCELEROMETER) -> Availability.FALLBACK
            else -> Availability.NONE
        }
        "sky" -> if (hasSensor(ctx, Sensor.TYPE_MAGNETIC_FIELD) && hasSensor(ctx, Sensor.TYPE_ACCELEROMETER)) Availability.FULL else Availability.FALLBACK
        "pulse" -> if (hasFeature(ctx, PackageManager.FEATURE_CAMERA_FLASH)) Availability.FULL
            else if (hasFeature(ctx, PackageManager.FEATURE_CAMERA_ANY)) Availability.FALLBACK else Availability.NONE
        "sleep" -> if (hasFeature(ctx, PackageManager.FEATURE_MICROPHONE)) Availability.FULL else Availability.NONE
        "quake" -> if (hasSensor(ctx, Sensor.TYPE_ACCELEROMETER)) Availability.FULL else Availability.FALLBACK
        "breath" -> if (hasFeature(ctx, PackageManager.FEATURE_MICROPHONE)) Availability.FULL else Availability.NONE
        "ar", "refsize", "lowlight" -> if (hasFeature(ctx, PackageManager.FEATURE_CAMERA_ANY)) Availability.FULL else Availability.NONE
        "angle" -> when {
            !hasSensor(ctx, Sensor.TYPE_ACCELEROMETER) -> Availability.NONE
            hasFeature(ctx, PackageManager.FEATURE_CAMERA_ANY) -> Availability.FULL
            else -> Availability.FALLBACK
        }
        "speed" -> if (hasFeature(ctx, PackageManager.FEATURE_LOCATION)) Availability.FULL else Availability.NONE
        else -> Availability.FULL
    }

    fun keySensorSummary(ctx: Context): Pair<Int, Int> =
        KEY_SENSOR_TYPES.count { hasSensor(ctx, it) } to KEY_SENSOR_TYPES.size
}
