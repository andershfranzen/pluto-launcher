package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppEntry
import java.text.CollationKey
import java.text.Collator
import java.util.Locale

object AppSorting {
    /** Locale-aware, case- and accent-insensitive alphabetical order; ties broken by package then component. */
    fun alphabetical(apps: Collection<AppEntry>, locale: Locale = Locale.getDefault()): List<AppEntry> {
        // Collator instances are not thread-safe, so create one per call.
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        // Collation keys are computed once per entry, far cheaper than collating on every comparison.
        return apps.map { it to collator.getCollationKey(it.label.trim()) }
            .sortedWith(
                compareBy<Pair<AppEntry, CollationKey>> { it.second }
                    .thenBy { it.first.packageName }
                    .thenBy { it.first.key.component }
                    .thenBy { it.first.key.userSerial },
            )
            .map { it.first }
    }
}
