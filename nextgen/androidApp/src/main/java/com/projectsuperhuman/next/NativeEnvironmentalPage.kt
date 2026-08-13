package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EnvPageNavy = Color(0xFF123D70)
private val EnvPageInk = Color(0xFF0B1F35)
private val EnvPageBlue = Color(0xFF0D6CB4)
private val EnvPageMuted = Color(0xFF748294)
private val EnvPageBg = Color(0xFFF8FBFD)
private val EnvPageWarning = Color(0xFFC77B22)
private val EnvPageError = Color(0xFFCA3A3A)

@Composable
internal fun NativeEnvironmentalPage(
    onBack: () -> Unit,
    source: EnvironmentalPresentationSource = EnvironmentalUiRuntime.source()
) {
    val state = rememberEnvironmentalRenderState(source)

    Column(
        Modifier.fillMaxSize()
            .testTag("environment_screen")
            .background(EnvPageBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        EnvironmentalHeader(onBack)

        when (state) {
            EnvironmentalRenderState.Loading -> EnvironmentalStateCard(
                testTag = "environment_state_loading",
                title = "Reading your environment",
                message = "Getting the latest local context available to Project Superhuman.",
                accent = EnvPageBlue,
                loading = true
            )
            is EnvironmentalRenderState.Ready -> when (val result = state.result) {
                is EnvironmentalLoadResult.Data -> EnvironmentalContent(result.conditions)
                EnvironmentalLoadResult.NoPermission -> EnvironmentalStateCard(
                    testTag = "environment_state_no_permission",
                    title = "Location access needed",
                    message = "The Environmental data source needs a location input before it can provide local conditions.",
                    accent = EnvPageWarning
                )
                EnvironmentalLoadResult.NoData -> EnvironmentalStateCard(
                    testTag = "environment_state_no_data",
                    title = "No environmental reading yet",
                    message = "There is no current local observation available. No values are shown until the data boundary provides them.",
                    accent = EnvPageBlue
                )
                is EnvironmentalLoadResult.Error -> EnvironmentalStateCard(
                    testTag = "environment_state_error",
                    title = "Environmental data unavailable",
                    message = result.message?.takeIf { it.isNotBlank() }
                        ?: "Project Superhuman couldn't load the current environmental context.",
                    accent = EnvPageError
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EnvironmentalHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.superhumanTopButton(onClick = onBack)
                .semantics { contentDescription = "Back to home" },
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = EnvPageNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Environment", color = EnvPageInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Local context for sleep, activity & wellbeing", color = EnvPageMuted, fontSize = 10.sp)
        }
    }
}
