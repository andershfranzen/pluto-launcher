package dev.pluto.launcher.ui.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionAndDensityTest {
    @Test
    fun reducedMotionDisablesAnimationRegardlessOfSystemScale() {
        assertEquals(0f, effectiveMotionScale(reducedMotion = true, systemScale = 1f))
        assertEquals(0f, effectiveMotionScale(reducedMotion = true, systemScale = 5f))
    }

    @Test
    fun systemScaleAppliesWhenNotReduced() {
        assertEquals(1f, effectiveMotionScale(reducedMotion = false, systemScale = 1f))
        assertEquals(0f, effectiveMotionScale(reducedMotion = false, systemScale = 0f))
        assertEquals(0.5f, effectiveMotionScale(reducedMotion = false, systemScale = 0.5f))
    }

    @Test
    fun motionDurationScaleFollowsSetting() {
        val scale = LauncherMotionDurationScale()
        assertEquals(1f, scale.scaleFactor)
        scale.reducedMotion = true
        assertEquals(0f, scale.scaleFactor)
    }

    /** Stand-in for Android 14+'s non-linear converter: large sizes grow less than small ones. */
    private class NonLinearDensity : Density {
        override val density = 1f
        override val fontScale = 2f
        override fun TextUnit.toDp(): Dp = if (value <= 14f) (value * 2f).dp else (28f + (value - 14f) * 1.2f).dp
        override fun Dp.toSp(): TextUnit = if (value <= 28f) (value / 2f).sp else (14f + (value - 28f) / 1.2f).sp
    }

    @Test
    fun textScaleIsAppliedOnTopOfTheNonLinearCurve() {
        val base = NonLinearDensity()
        val scaled = TextScaledDensity(base, 1.5f)
        // 45sp (displayMedium): base curve gives 65.2dp, Pluto's 1.5x makes 97.8dp (not 45 * 2 * 1.5 = 135dp).
        assertEquals(97.8f, with(scaled) { 45.sp.toDp() }.value, 0.01f)
        assertEquals(42f, with(scaled) { 14.sp.toDp() }.value, 0.01f)
        assertEquals(45f, with(scaled) { 97.8.dp.toSp() }.value, 0.01f)
    }

    @Test
    fun linearBaseMatchesPlainMultiplication() {
        val scaled = TextScaledDensity(Density(2f, 1.3f), 1.2f)
        assertEquals(10f * 1.3f * 1.2f * 2f, with(scaled) { 10.sp.toPx() }, 0.01f)
    }
}
