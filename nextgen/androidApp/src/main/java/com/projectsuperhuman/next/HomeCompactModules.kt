package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Two genuinely small modules in one dashboard reorder slot. They use the
 * exact same neutral card surface as the 11.4 Home cards: no gradients,
 * decorative photography or additional elevation.
 */
@Composable
internal fun HomeCompactModulesRow(
    snapshot: NativeHomeSnapshot,
    openHydration: () -> Unit,
    openEnvironment: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SuperhumanLayout.homeSectionGap),
        verticalAlignment = Alignment.Top
    ) {
        HomeCompactHydrationTile(snapshot, onClick = openHydration, modifier = Modifier.weight(1f))
        HomeCompactEnvironmentTile(onClick = openEnvironment, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompactModuleHeader(label: String, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(accent, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = superhumanTextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.tabler_chevron_right),
            contentDescription = null,
            tint = superhumanTextMuted,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
internal fun HomeCompactHydrationTile(
    snapshot: NativeHomeSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = superhumanPalette
    val waterMl = (snapshot.waterLitres.coerceAtLeast(0.0) * 1000.0).roundToInt()
    val goalMl = snapshot.waterGoalMl.coerceAtLeast(1)
    val progress = (waterMl.toFloat() / goalMl).coerceIn(0f, 1f)
    val current = if (waterMl >= 1000) String.format(Locale.US, "%.1f L", waterMl / 1000.0)
        else waterMl.toString() + " ml"
    val goal = if (goalMl >= 1000) String.format(Locale.US, "%.1f L", goalMl / 1000.0)
        else goalMl.toString() + " ml"

    Column(
        modifier
            .height(148.dp)
            .midnightCardSurface(accent = palette.blue)
            .superhumanHomeTileClickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Hydration. " + current + " of " + goal + " today. " +
                    (progress * 100).roundToInt() + " percent of goal."
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        CompactModuleHeader("HYDRATION", palette.blue)
        Spacer(Modifier.height(7.dp))
        Text(
            current,
            fontSize = 23.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1
        )
        Text(
            "of " + goal + " today",
            fontSize = 10.sp,
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(4.dp).background(palette.surfaceSoft, CircleShape)) {
            if (progress > 0f) {
                Box(
                    Modifier.fillMaxWidth(progress).height(4.dp)
                        .background(palette.blue, CircleShape)
                )
            }
        }
    }
}

@Composable
internal fun HomeCompactEnvironmentTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    source: EnvironmentalPresentationSource = EnvironmentalUiRuntime.source()
) {
    val palette = superhumanPalette
    val state = rememberEnvironmentalRenderState(source)
    val (headline, supporting, detail) = when (state) {
        EnvironmentalRenderState.Loading ->
            Triple("—", "Loading conditions", "Updating")
        is EnvironmentalRenderState.Ready -> when (val result = state.result) {
            is EnvironmentalLoadResult.Data -> {
                val conditions = result.conditions
                Triple(
                    conditions.headlineMetric()?.displayValue() ?: "—",
                    conditions.weatherLabel?.takeIf { it.isNotBlank() } ?: "Local conditions",
                    conditions.locationLabel?.takeIf { it.isNotBlank() }
                        ?: conditions.freshnessLabel.orEmpty()
                )
            }
            EnvironmentalLoadResult.NoPermission ->
                Triple("Location off", "Access needed", "Tap to enable")
            EnvironmentalLoadResult.NoData ->
                Triple("No reading", "Local conditions", "Tap for details")
            is EnvironmentalLoadResult.Error ->
                Triple("Unavailable", "Couldn't update", "Tap to retry")
        }
    }

    Column(
        modifier
            .height(148.dp)
            .midnightCardSurface(accent = palette.green)
            .superhumanHomeTileClickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = environmentalTileDescription(state)
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        CompactModuleHeader("ENVIRONMENT", palette.green)
        Spacer(Modifier.height(7.dp))
        Text(
            headline,
            color = palette.textPrimary,
            fontSize = if (headline.length > 7) 15.sp else 23.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            supporting,
            fontSize = 10.sp,
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Keep location in the normal layout flow. A weighted spacer previously
        // pushed its last text line into the bottom of the clipped card.
        Spacer(Modifier.height(9.dp))
        Text(
            detail.ifBlank { "Local conditions" },
            color = palette.textMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
