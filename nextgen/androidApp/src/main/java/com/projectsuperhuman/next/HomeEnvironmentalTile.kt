package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EnvTileBlue = Color(0xFF0D6CB4)
private val EnvTileTeal = Color(0xFF168A78)

@Composable
internal fun HomeEnvironmentalTile(
    onClick: () -> Unit,
    source: EnvironmentalPresentationSource = EnvironmentalUiRuntime.source()
) {
    val state = rememberEnvironmentalRenderState(source)
    val shape = RoundedCornerShape(28.dp)
    val palette = superhumanPalette
    val dark = SuperhumanAppearance.darkMode

    Box(
        Modifier.fillMaxWidth()
            .height(174.dp)
            .testTag("environment_home_tile")
            .semantics(mergeDescendants = true) { contentDescription = environmentalTileDescription(state) }
            .background(
                Brush.linearGradient(
                    listOf(
                        if (dark) palette.accentSoft else Color(0xFFE7F5FC),
                        palette.surface,
                        if (dark) palette.surfaceElevated else Color(0xFFEDF8F5)
                    )
                ),
                shape
            )
            .border(1.dp, palette.border, shape)
            .superhumanHomeTileClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (dark) palette.green else EnvTileTeal, CircleShape))
                    Text("  ENVIRONMENT", color = palette.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
                }
                Box(
                    Modifier.size(34.dp).background(palette.surfaceElevated.copy(alpha = .88f), CircleShape).border(1.dp, palette.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("→", color = if (dark) palette.blue else EnvTileBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            when (state) {
                EnvironmentalRenderState.Loading ->
                    EnvironmentalTileStatus("Reading local conditions…", "Updating your environmental context")
                is EnvironmentalRenderState.Ready -> when (val result = state.result) {
                    is EnvironmentalLoadResult.Data -> EnvironmentalTileData(result.conditions)
                    EnvironmentalLoadResult.NoPermission ->
                        EnvironmentalTileStatus("Location access needed", "Open Environment to enable local weather")
                    EnvironmentalLoadResult.NoData ->
                        EnvironmentalTileStatus("No local reading yet", "Open Environment to capture your first reading")
                    is EnvironmentalLoadResult.Error ->
                        EnvironmentalTileStatus("Conditions unavailable", "Open Environment to retry")
                }
            }
        }
    }
}
