package com.sensora.app.sleep

import android.content.Context
import com.sensora.app.core.sleep.SleepEpoch
import com.sensora.app.core.util.MiniJson
import com.sensora.app.core.util.asDouble
import com.sensora.app.core.util.asList
import com.sensora.app.core.util.asMap
import java.io.File

data class SleepSession(val startMs: Long, val endMs: Long, val epochs: List<SleepEpoch>)

/** Overnight sessions as small JSON files in app-private storage (levels and counts only, no audio). */
object SleepStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "sleep").apply { mkdirs() }

    fun save(ctx: Context, s: SleepSession) {
        val sb = StringBuilder("{\"start\":${s.startMs},\"end\":${s.endMs},\"epochs\":[")
        s.epochs.forEachIndexed { i, e ->
            if (i > 0) sb.append(',')
            sb.append("[${e.movement},${"%.1f".format(java.util.Locale.US, e.avgDb)},${"%.1f".format(java.util.Locale.US, e.maxDb)},${if (e.snore) 1 else 0},${e.loudEvents}]")
        }
        sb.append("]}")
        File(dir(ctx), "${s.startMs}.json").writeText(sb.toString())
        // Keep the 30 most recent nights.
        dir(ctx).listFiles()?.sortedByDescending { it.name }?.drop(30)?.forEach { it.delete() }
    }

    fun list(ctx: Context): List<SleepSession> =
        dir(ctx).listFiles()?.sortedByDescending { it.name }?.mapNotNull { f -> runCatching { parse(f.readText()) }.getOrNull() } ?: emptyList()

    fun delete(ctx: Context, startMs: Long) { File(dir(ctx), "$startMs.json").delete() }

    private fun parse(text: String): SleepSession? {
        val m = MiniJson.parse(text).asMap() ?: return null
        val epochs = (m["epochs"].asList() ?: emptyList()).mapNotNull { e ->
            val a = e.asList() ?: return@mapNotNull null
            SleepEpoch(a[0].asDouble()!!.toInt(), a[1].asDouble()!!, a[2].asDouble()!!, a[3].asDouble() == 1.0, a[4].asDouble()!!.toInt())
        }
        return SleepSession(m["start"].asDouble()!!.toLong(), m["end"].asDouble()!!.toLong(), epochs)
    }
}
