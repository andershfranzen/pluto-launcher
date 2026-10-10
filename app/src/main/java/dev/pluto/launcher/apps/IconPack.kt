package dev.pluto.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.Log
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

/** An installed icon pack, as offered in settings. */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * A loaded icon pack in the ADW / Nova / Apex format most packs ship: `appfilter.xml` maps
 * activities (`ComponentInfo{package/activity}`) to drawables in the pack, and optional
 * `iconback` / `iconmask` / `iconupon` / `scale` entries theme the apps the pack does not
 * cover. Lookups are thread-safe (icons decode on a background pool).
 */
class IconPack private constructor(
    val packageName: String,
    private val res: Resources,
    private val mapping: Map<String, String>,
    private val backs: List<String>,
    private val mask: String?,
    private val upon: String?,
    private val scale: Float,
) {
    private val ids = ConcurrentHashMap<String, Int>()

    /** True when the pack themes apps it has no icon for (a back plate, mask or overlay). */
    val themesOthers: Boolean get() = backs.isNotEmpty() || mask != null || upon != null

    /** The pack's own icon for [component], or null when the pack does not cover it. */
    fun iconFor(component: ComponentName): Drawable? {
        val name = mapping[component.flattenToString()]
            ?: mapping[component.packageName] // a few packs map whole packages
            ?: return null
        return drawable(name)
    }

    /**
     * [base] (the app's own icon) themed the pack's way: scaled down onto a back plate,
     * cut by the mask, with the overlay on top. The plate is chosen per app, stably.
     */
    fun theme(base: Drawable, component: ComponentName, sizePx: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val inner = (sizePx * scale).toInt().coerceIn(1, sizePx)
        val inset = (sizePx - inner) / 2f
        canvas.drawBitmap(base.toBitmap(inner, inner), inset, inset, paint)
        mask?.let(::drawable)?.let { m ->
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            canvas.drawBitmap(m.toBitmap(sizePx, sizePx), 0f, 0f, paint)
        }
        if (backs.isNotEmpty()) {
            val back = drawable(backs[Math.floorMod(component.flattenToString().hashCode(), backs.size)])
            if (back != null) {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
                canvas.drawBitmap(back.toBitmap(sizePx, sizePx), 0f, 0f, paint)
            }
        }
        upon?.let(::drawable)?.let { u ->
            paint.xfermode = null
            canvas.drawBitmap(u.toBitmap(sizePx, sizePx), 0f, 0f, paint)
        }
        return out
    }

    private fun drawable(name: String): Drawable? {
        val id = ids.getOrPut(name) { res.getIdentifier(name, "drawable", packageName) }
        if (id == 0) return null
        return try {
            res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null) ?: res.getDrawable(id, null)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    companion object {
        private const val TAG = "IconPack"

        /** Intents icon packs declare so launchers can find them. */
        private val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
        )
        private val PACK_CATEGORIES = listOf(
            "com.fede.launcher.THEME_ICONPACK",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
        )

        /** Installed icon packs, by name. Binder and resource work: call off the main thread. */
        fun installed(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            val intents = PACK_ACTIONS.map { Intent(it) } +
                PACK_CATEGORIES.map { Intent(Intent.ACTION_MAIN).addCategory(it) }
            val packages = intents.flatMap { intent ->
                runCatching { pm.queryIntentActivities(intent, PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
            }.map { it.activityInfo.packageName }.distinct()
            return packages.mapNotNull { pkg ->
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    IconPackInfo(pkg, pm.getApplicationLabel(info).toString())
                }.getOrNull()
            }.sortedBy { it.label.lowercase() }
        }

        /** Loads [packageName]'s appfilter; null if the pack is gone or unreadable. Off the main thread. */
        fun load(context: Context, packageName: String): IconPack? = try {
            val res = context.packageManager.getResourcesForApplication(packageName)
            val parser = openAppFilter(context, res, packageName)
            if (parser == null) {
                Log.w(TAG, "$packageName has no appfilter")
                null
            } else {
                parse(packageName, res, parser)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't load icon pack $packageName", e)
            null
        }

        private fun openAppFilter(context: Context, res: Resources, packageName: String): XmlPullParser? {
            val id = res.getIdentifier("appfilter", "xml", packageName)
            if (id != 0) return res.getXml(id)
            // Some packs ship it as a raw asset instead.
            val packContext = context.createPackageContext(packageName, 0)
            val stream = runCatching { packContext.assets.open("appfilter.xml") }.getOrNull() ?: return null
            return Xml.newPullParser().apply { setInput(stream, null) }
        }

        private fun parse(packageName: String, res: Resources, parser: XmlPullParser): IconPack {
            val mapping = HashMap<String, String>()
            val backs = ArrayList<String>()
            var mask: String? = null
            var upon: String? = null
            var scale = 1f
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> {
                            val component = parser.getAttributeValue(null, "component")?.let(::componentKey)
                            val drawable = parser.getAttributeValue(null, "drawable")
                            if (component != null && !drawable.isNullOrBlank()) mapping.putIfAbsent(component, drawable)
                        }
                        "iconback" -> for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeName(i).startsWith("img")) backs += parser.getAttributeValue(i)
                        }
                        "iconmask" -> mask = parser.getAttributeValue(null, "img1")
                        "iconupon" -> upon = parser.getAttributeValue(null, "img1")
                        "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull()?.coerceIn(0.1f, 1f) ?: 1f
                    }
                }
                event = parser.next()
            }
            return IconPack(packageName, res, mapping, backs, mask, upon, scale)
        }

        /**
         * "ComponentInfo{com.app/com.app.Main}" → "com.app/com.app.Main" (relative ".Main"
         * expanded), the form [ComponentName.flattenToString] produces. A bare package is kept.
         */
        internal fun componentKey(raw: String): String? {
            val inner = raw.substringAfter("ComponentInfo{", raw).substringBefore('}').trim()
            if (inner.isEmpty()) return null
            if ('/' !in inner) return inner
            val pkg = inner.substringBefore('/')
            val cls = inner.substringAfter('/')
            if (pkg.isEmpty() || cls.isEmpty()) return null
            return pkg + "/" + if (cls.startsWith('.')) pkg + cls else cls
        }
    }
}
