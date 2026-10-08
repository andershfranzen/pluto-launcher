package dev.pluto.launcher

import android.app.Application
import dev.pluto.launcher.apps.AppCatalog
import dev.pluto.launcher.apps.IconCache
import dev.pluto.launcher.data.DefaultLayoutSeeder
import dev.pluto.launcher.data.OrganizationRepository
import dev.pluto.launcher.data.db.LauncherDatabase
import dev.pluto.launcher.data.prefs.SettingsRepository
import dev.pluto.launcher.input.ControllerMonitor

/** Manual dependency container; the launcher is small enough not to need a DI framework. */
class AppContainer(app: Application) {
    val database: LauncherDatabase by lazy { LauncherDatabase.build(app) }
    val organization: OrganizationRepository by lazy { OrganizationRepository(database.dao()) }
    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val catalog: AppCatalog by lazy { AppCatalog(app) }
    val icons: IconCache by lazy { IconCache(app) }
    val controllers: ControllerMonitor by lazy { ControllerMonitor(app) }
    val layoutSeeder: DefaultLayoutSeeder by lazy { DefaultLayoutSeeder(app) }
}

class LauncherApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
