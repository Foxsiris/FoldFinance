package com.foldfinance.app.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion answers an action (docs/design.md, principle 3). Two spring families:
 *
 * - **spatial** — things that move or resize: damping 0.8, stiffness 380 (a slight settle);
 * - **effects** — colour, opacity, elevation: no bounce, fast.
 *
 * `MotionScheme` is internal in Material 3 1.4.0, so these are our own tokens. Use [FoldMotion.spatial]
 * / [FoldMotion.effects] instead of building springs by hand: they return `snap()` when the
 * user turned animations off in system settings («Убрать анимацию» sets the animator scale to 0).
 * No background or looping animation anywhere in the app.
 */
object FoldMotion {
    const val SpatialDamping = 0.8f
    const val SpatialStiffness = 380f
    const val EffectsDamping = Spring.DampingRatioNoBouncy
    const val EffectsStiffness = 1600f

    /** Spring for position/size changes, or an instant snap under reduced motion. */
    @Composable
    @ReadOnlyComposable
    fun <T> spatial(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else spring(SpatialDamping, SpatialStiffness)

    /** Spring for colour/alpha changes, or an instant snap under reduced motion. */
    @Composable
    @ReadOnlyComposable
    fun <T> effects(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else spring(EffectsDamping, EffectsStiffness)

    /** Non-composable variant for places that already know the reduced-motion flag. */
    fun <T> spatial(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(SpatialDamping, SpatialStiffness)

    /** Non-composable variant for places that already know the reduced-motion flag. */
    fun <T> effects(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(EffectsDamping, EffectsStiffness)
}

/** `true` when the system animator duration scale is 0 (animations removed). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Reads the system animator scale; `true` means animations are off. */
fun isReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Observes the system animator scale so toggling «Убрать анимацию» applies without restart. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember(context) { mutableStateOf(isReducedMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = isReducedMotion(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}
