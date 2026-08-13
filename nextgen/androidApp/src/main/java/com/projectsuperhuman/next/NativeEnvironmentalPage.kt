package com.projectsuperhuman.next

import androidx.compose.runtime.Composable

@Composable
internal fun NativeEnvironmentalPage(
    onBack: () -> Unit,
    source: EnvironmentalPresentationSource = EnvironmentalUiRuntime.source()
) {
    NativeEnvironmentalTimelineRoute(onBack = onBack, source = source)
}
