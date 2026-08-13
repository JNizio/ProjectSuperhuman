package com.projectsuperhuman.next

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

internal fun Modifier.padding(horizontal: Dp, bottom: Dp): Modifier =
    padding(start = horizontal, end = horizontal, bottom = bottom)

@Composable
internal fun NativeEnvironmentalPage(onBack: () -> Unit) {
    val context = LocalContext.current
    if (hasEnvironmentalLocationPermission(context)) {
        NativeEnvironmentalPage(onBack = onBack, source = EnvironmentalUiRuntime.source())
    } else {
        NativeEnvironmentalRoute(onBack = onBack)
    }
}
