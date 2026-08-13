package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun EnvironmentalContentPreviewFixture() {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF8FBFD)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) { EnvironmentalContent(EnvironmentalUiPreviewFixtures.content) }
}

@Composable
internal fun EnvironmentalNoPermissionPreviewFixture() {
    EnvironmentalStateCard(
        testTag = "environment_preview_no_permission",
        title = "Location access needed",
        message = "A location input is needed before local conditions can be shown.",
        accent = Color(0xFFC77B22)
    )
}
