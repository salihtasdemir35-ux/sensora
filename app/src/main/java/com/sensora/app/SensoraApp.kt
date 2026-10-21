package com.sensora.app

import android.app.Application
import androidx.room.Room
import com.sensora.app.core.astro.CatalogParser
import com.sensora.app.core.astro.SkyCatalog
import com.sensora.app.data.AppDatabase
import com.sensora.app.data.AppPrefs
import com.sensora.app.data.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Minimal service locator; keeps the app free of a DI framework at this stage. */
class SensoraApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "sensora.db").build()
    }
    val history: HistoryRepository by lazy { HistoryRepository(database.measurementDao()) }
    val prefs: AppPrefs by lazy { AppPrefs(this) }

    /** Star, constellation and deep-sky catalogue bundled in assets/sky; parsed once, off the main thread. */
    val skyCatalog: SkyCatalog by lazy {
        fun read(name: String) = assets.open("sky/$name").bufferedReader().use { it.readText() }
        SkyCatalog(
            CatalogParser.stars(read("stars.tsv")),
            CatalogParser.constellations(read("constellations.tsv")),
            CatalogParser.deepSky(read("dso.tsv")),
        )
    }
}
