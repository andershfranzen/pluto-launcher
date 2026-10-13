package dev.pluto.launcher.ui.components

import dev.pluto.launcher.R
import dev.pluto.launcher.ui.UiText
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.system.NotificationApp

val LocalNotificationApps = compositionLocalOf<Set<NotificationApp>> { emptySet() }
fun Set<NotificationApp>.hasDot(app: AppEntry) = NotificationApp(app.packageName, app.key.userSerial) in this

/** A quiet iOS-red dot with a narrow dark rim, inside the icon bounds to avoid clipping. */
internal fun DrawScope.drawNotificationDot() {
    val radius = (size.minDimension * 0.095f).coerceIn(3.dp.toPx(), 7.dp.toPx())
    val center = Offset(size.width - radius - 1.dp.toPx(), radius + 1.dp.toPx())
    drawCircle(Color(0xFF252529), radius + 1.dp.toPx(), center)
    drawCircle(Color(0xFFFF453A), radius, center)
}
internal fun Modifier.notificationDot(visible: Boolean): Modifier = if (!visible) this else
    semantics { stateDescription = UiText.get(R.string.common_has_notifications) }.drawWithContent { drawContent(); drawNotificationDot() }
