package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.Organization

/**
 * Turns the device's default handlers into a first-run layout. Pure, so it is unit-testable;
 * the Android side ([dev.pluto.launcher.data.DefaultLayoutSeeder]) only resolves intents.
 *
 * Each slot lists [Handler]s in preference order (e.g. the CATEGORY_APP_BROWSER handler,
 * then whatever opens http links). A handler maps to a launchable catalog entry of its
 * package: the resolved activity itself when it is a launcher activity, else the package's
 * first launcher activity by label. Slots with no launchable match are skipped, and a
 * package is used at most once (dock first). Dock entries are packed from the first slot.
 */
object DefaultLayout {
    /** A resolved default handler: its package and, when known, its activity (flattened ComponentName). */
    data class Handler(val packageName: String, val component: String? = null)

    data class Plan(val dock: List<AppKey?>, val home: List<AppKey>) {
        val isEmpty: Boolean get() = dock.all { it == null } && home.isEmpty()
    }

    fun choose(dockSlots: List<List<Handler>>, homeSlots: List<List<Handler>>, catalog: List<AppEntry>): Plan {
        val launchable = catalog.filter { it.isEnabled }
        val byComponent = launchable.associateBy { it.key.component }
        val byPackage = AppSorting.alphabetical(launchable).groupBy { it.packageName }
        val usedPackages = HashSet<String>()

        fun pick(candidates: List<Handler>): AppKey? {
            for (handler in candidates) {
                // One tile per app: a package already placed (e.g. as the dialer) is not reused.
                if (handler.packageName in usedPackages) continue
                val exact = handler.component?.let(byComponent::get)?.takeIf { it.packageName == handler.packageName }
                val entry = exact ?: byPackage[handler.packageName]?.firstOrNull() ?: continue
                usedPackages += entry.packageName
                return entry.key
            }
            return null
        }

        val dock = dockSlots.take(Organization.DOCK_SLOTS).mapNotNull(::pick)
        val home = homeSlots.mapNotNull(::pick)
        return Plan(dock = List(Organization.DOCK_SLOTS) { dock.getOrNull(it) }, home = home)
    }
}
