package dev.pluto.launcher.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.pluto.launcher.R
import dev.pluto.launcher.domain.ExtensionCount
import dev.pluto.launcher.domain.RomAge
import dev.pluto.launcher.domain.RomSystems

/** Words for the ROM domain's data: ages, system keys and skipped file types. */

/** "today", "3 days ago", "a year ago": a [RomAge] in words, to fill a sentence such as "Played %1$s". */
@Composable
internal fun romAgeText(age: RomAge): String = when (age) {
    RomAge.Today -> stringResource(R.string.rom_age_today)
    RomAge.Yesterday -> stringResource(R.string.rom_age_yesterday)
    is RomAge.Days -> pluralStringResource(R.plurals.rom_age_days, age.days, age.days)
    RomAge.AWeek -> stringResource(R.string.rom_age_a_week)
    is RomAge.Weeks -> pluralStringResource(R.plurals.rom_age_weeks, age.weeks, age.weeks)
    is RomAge.Months -> pluralStringResource(R.plurals.rom_age_months, age.months, age.months)
    RomAge.AYear -> stringResource(R.string.rom_age_a_year)
}

/** A system key from [RomSystems] as shown: the generic keys are translated, system names are proper nouns. */
@Composable
internal fun romSystemLabel(system: String): String = when (system) {
    RomSystems.OTHER -> stringResource(R.string.rom_system_other)
    RomSystems.DISC -> stringResource(R.string.rom_system_disc)
    else -> system
}

/** ".sav×12, .txt×3, no extension×1" from a scan's skipped file types. */
@Composable
internal fun romExtensionsText(extensions: List<ExtensionCount>): String {
    val parts = extensions.map {
        if (it.extension.isEmpty()) stringResource(R.string.rom_extension_none_count, it.count)
        else stringResource(R.string.rom_extension_count, it.extension, it.count)
    }
    return parts.joinToString(stringResource(R.string.console_list_separator))
}
