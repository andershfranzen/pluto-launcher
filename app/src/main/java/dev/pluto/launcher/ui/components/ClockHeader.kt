package dev.pluto.launcher.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.pluto.launcher.ui.motion.PlutoMotion
import dev.pluto.launcher.ui.theme.overWallpaper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
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
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(lifecycleOwner, appContext) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // The delay loop runs on uptime, which stalls while the device sleeps; the
            // system's own minute tick and clock/time-zone changes keep it in step with the
            // status bar. Each one re-aligns the loop to the next minute boundary.
            val wake = Channel<Unit>(Channel.CONFLATED)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    wake.trySend(Unit)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            try {
                while (true) {
                    now = System.currentTimeMillis()
                    withTimeoutOrNull(60_000L - now % 60_000L + 20L) { wake.receive() }
                }
            } finally {
                appContext.unregisterReceiver(receiver)
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
            AnimatedClockText(
                time,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold).overWallpaper(),
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
            AnimatedClockText(time, style = MaterialTheme.typography.displayMedium.overWallpaper())
            Text(day, style = MaterialTheme.typography.titleMedium.overWallpaper(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The time, with each character that changes on a minute tick sliding up into place while
 * the old one slides away (one-shot, subtle; nothing loops). Characters are matched from the
 * end so "9:59" -> "10:00" keeps the colon steady. Falls back to a single ellipsizing Text
 * when the time does not fit (very large font sizes), so it never clips mid-glyph.
 */
@Composable
private fun AnimatedClockText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val fits = remember(text, style, maxWidth) {
            maxWidth == Constraints.Infinity ||
                measurer.measure(text, style, maxLines = 1, softWrap = false).size.width <= maxWidth * FitTolerance
        }
        if (!fits) {
            Text(text, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        } else {
            Row {
                val length = text.length
                for (index in 0 until length) {
                    // Keyed by position from the end, so digits keep their slot when the length changes.
                    key(length - index) {
                        AnimatedContent(
                            targetState = text[index],
                            transitionSpec = {
                                (slideInVertically(PlutoMotion.slideSpring) { it / 2 } + fadeIn(PlutoMotion.fadeIn()))
                                    .togetherWith(slideOutVertically(PlutoMotion.slideSpring) { -it / 2 } + fadeOut(PlutoMotion.fadeOut()))
                                    .using(SizeTransform(clip = false) { _, _ -> PlutoMotion.spatialFast() })
                            },
                            label = "clockChar",
                        ) { char ->
                            Text(char.toString(), style = style, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

/** Per-character layout loses kerning; keep a little slack before choosing it. */
private const val FitTolerance = 0.96f
