package com.foldfinance.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Точка входа онбординга доступов (SMS, уведомления, ограниченные настройки, батарея).
 * Заглушка каркаса — реализуется на этапах 3–4. Оболочка показывает онбординг, пока
 * [onFinished] не вызван (или пользователь не пропустил его).
 */
@Composable
fun OnboardingRoute(onFinished: () -> Unit, modifier: Modifier = Modifier) = Unit
