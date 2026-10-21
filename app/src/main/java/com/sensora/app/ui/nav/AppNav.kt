package com.sensora.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sensora.app.ui.ar.ArMeasureScreen
import com.sensora.app.ui.astro.SkyMapScreen
import com.sensora.app.ui.camera.AngleMeasureScreen
import com.sensora.app.ui.camera.AreaVolumeScreen
import com.sensora.app.ui.camera.LowLightScreen
import com.sensora.app.ui.camera.ReferenceSizeScreen
import com.sensora.app.ui.astro.SunMoonScreen
import com.sensora.app.ui.astro.TonightScreen
import com.sensora.app.ui.catalog.ToolCatalog
import com.sensora.app.ui.health.BreathTestScreen
import com.sensora.app.ui.health.HearingScreen
import com.sensora.app.ui.health.PulseScreen
import com.sensora.app.ui.health.VisionScreen
import com.sensora.app.ui.health.SleepScreen
import com.sensora.app.ui.quake.QuakeScreen
import com.sensora.app.ui.health.ResultsInterpreterScreen
import com.sensora.app.ui.common.sensoraApp
import com.sensora.app.ui.history.HistoryScreen
import com.sensora.app.ui.home.HomeScreen
import com.sensora.app.ui.sensors.SensorCenterScreen
import com.sensora.app.ui.settings.SettingsScreen
import com.sensora.app.ui.tools.BarometerScreen
import com.sensora.app.ui.tools.CompassScreen
import com.sensora.app.ui.tools.ConverterScreen
import com.sensora.app.ui.tools.InclinometerScreen
import com.sensora.app.ui.tools.LevelScreen
import com.sensora.app.ui.tools.LightScreen
import com.sensora.app.ui.tools.MagnetScreen
import com.sensora.app.ui.tools.SoundMeterScreen
import com.sensora.app.ui.tools.SpeedScreen
import com.sensora.app.ui.tools.StepsScreen
import com.sensora.app.ui.tools.ToneScreen

@Composable
fun SensoraNavHost() {
    val nav = rememberNavController()
    val prefs = LocalContext.current.sensoraApp.prefs
    val back: () -> Unit = { nav.navigateUp() }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenTool = { id -> prefs.addRecent(id); nav.navigate("tool/$id") },
                onOpenHistory = { nav.navigate("history") },
                onOpenSensors = { nav.navigate("sensors") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("tool/{id}") { entry ->
            ToolHost(entry.arguments?.getString("id").orEmpty(), back) { id -> prefs.addRecent(id); nav.navigate("tool/$id") }
        }
        composable("history") { HistoryScreen(back) }
        composable("sensors") { SensorCenterScreen(back) }
        composable("settings") { SettingsScreen(back) }
    }
}

@Composable
private fun ToolHost(id: String, onBack: () -> Unit, onOpenTool: (String) -> Unit) {
    val tool = ToolCatalog.byId(id)
    if (tool == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    when (tool.id) {
        "compass" -> CompassScreen(tool, onBack)
        "level" -> LevelScreen(tool, onBack)
        "incline" -> InclinometerScreen(tool, onBack)
        "light" -> LightScreen(tool, onBack)
        "sound" -> SoundMeterScreen(tool, onBack)
        "baro" -> BarometerScreen(tool, onBack)
        "steps" -> StepsScreen(tool, onBack)
        "speed" -> SpeedScreen(tool, onBack)
        "magnet" -> MagnetScreen(tool, onBack)
        "converter" -> ConverterScreen(tool, onBack)
        "sky" -> SkyMapScreen(tool, onBack)
        "tonight" -> TonightScreen(tool, onBack)
        "sunmoon" -> SunMoonScreen(tool, onBack)
        "ar" -> ArMeasureScreen(tool, onBack)
        "angle" -> AngleMeasureScreen(tool, onBack)
        "refsize" -> ReferenceSizeScreen(tool, onBack)
        "areavol" -> AreaVolumeScreen(tool, onBack)
        "lowlight" -> LowLightScreen(tool, onBack)
        "tone" -> ToneScreen(tool, onBack)
        "breath" -> BreathTestScreen(tool, onBack, onOpenTool)
        "results" -> ResultsInterpreterScreen(tool, onBack)
        "pulse" -> PulseScreen(tool, onBack)
        "sleep" -> SleepScreen(tool, onBack)
        "quake" -> QuakeScreen(tool, onBack)
        "hearing" -> HearingScreen(tool, onBack)
        "vision" -> VisionScreen(tool, onBack)
    }
}
