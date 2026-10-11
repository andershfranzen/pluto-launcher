package dev.pluto.launcher.apps

/** Android trim levels, kept numeric so the policy can also be checked on the host JVM. */
internal object AssetMemoryPolicy {
    fun targetBytes(budget: Int, level: Int): Int? = when {
        level >= 40 || level == 15 -> 0 // background or running critical
        level >= 20 || level >= 10 -> budget / 2 // UI hidden or running low
        else -> null
    }
}
