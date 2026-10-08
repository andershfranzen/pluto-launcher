package dev.pluto.launcher.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.pluto.launcher.ui.theme.overWallpaper
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

/**
 * Current time, refreshed on minute boundaries only while the launcher is started
 * (no background polling). Re-reads immediately whenever the launcher comes back.
 */
@Composable
fun rememberMinuteClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000L - now % 60_000L + 20L)
            }
        }
    }
    return now
}

/**
 * Compact time and date drawn over the wallpaper. Respects the system 12/24-hour setting
 * and locale. [compact] puts time and an abbreviated date on one line (landscape / handheld
 * headers). Texts never clip mid-glyph at large font sizes: they ellipsize, the compact
 * date gives way first (it shrinks to nothing before the time does), and the full-size
 * date may wrap to a second line.
 */
@Composable
fun ClockHeader(modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()
    val now = rememberMinuteClock()
    val date = Date(now)
    val time = remember(now, locale) { DateFormat.getTimeFormat(context).format(date) }
    val day = remember(now, locale) {
        DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), date).toString()
    }
    val semanticsModifier = modifier.clearAndSetSemantics { contentDescription = "$time, $day" }
    if (compact) {
        val shortDay = remember(now, locale) {
            DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), date).toString()
        }
        Row(semanticsModifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                time,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold).overWallpaper(),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            // Measured after the time (weight), so the date is what gives way when space is tight.
            Text(
                shortDay,
                style = MaterialTheme.typography.bodyMedium.overWallpaper(),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    } else {
        Column(semanticsModifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                time,
                style = MaterialTheme.typography.displayMedium.overWallpaper(),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(day, style = MaterialTheme.typography.titleMedium.overWallpaper(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
