package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EnvTileBlue = Color(0xFF0D6CB4)
private val EnvTileTeal = Color(0xFF168A78)
private val EnvTileMuted = Color(0xFF748294)
private val EnvTileBorder = Color(0xFFE1E9EF)

@Composable
internal fun HomeEnvironmentalTile(
    onClick: () -> Unit,
    source: EnvironmentalPresentationSource = EnvironmentalUiRuntime.source()
) {
    val state = rememberEnvironmentalRenderState(source)
    val shape = RoundedCornerShape(27.dp)
    Box(
        Modifier.fillMaxWidth()
            .height(136.dp)
            .testTag("environment_home_tile")
            .semantics(mergeDescendants = true) {
                contentDescription = environmentalTileDescription(state)
            }
            .background(
                Brush.horizontalGradient(
                    listOf(Color.White, Color(0xFFF4FAFD), Color(0xFFEAF5FB))
                ),
                shape
            )
            .border(1.dp, EnvTileBorder, shape)
            .superhumanHomeTileClickable(onClick = onClick)
            .padding(horizontal = 19.dp, vertical = 16.dp)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val centre = Offset(size.width * .93f, size.height * .72f)
            drawCircle(
                EnvTileBlue.copy(alpha = .08f),
                size.width * .24f,
                centre,
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                EnvTileTeal.copy(alpha = .07f),
                size.width * .15f,
                centre,
                style = Stroke(width = 1.2f)
            )
            drawCircle(
                EnvTileBlue.copy(alpha = .13f),
                3.2f,
                Offset(size.width * .82f, size.height * .28f)
            )
        }

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "ENVIRONMENT",
                    color = EnvTileMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.15.sp
                )
                Text("→", color = EnvTileBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }

            when (state) {
                EnvironmentalRenderState.Loading ->
                    EnvironmentalTileStatus("Reading local context…", "Current environmental conditions")
                is EnvironmentalRenderState.Ready -> when (val result = state.result) {
                    is EnvironmentalLoadResult.Data -> EnvironmentalTileData(result.conditions)
                    EnvironmentalLoadResult.NoPermission ->
                        EnvironmentalTileStatus("Location access needed", "Open Environment to continue")
                    EnvironmentalLoadResult.NoData ->
                        EnvironmentalTileStatus("No local reading yet", "Open Environment for details")
                    is EnvironmentalLoadResult.Error ->
                        EnvironmentalTileStatus("Conditions unavailable", "Open Environment to retry")
                }
            }
        }
    }
}
