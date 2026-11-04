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
    val conditions = (state as? EnvironmentalRenderState.Ready)
        ?.result.let { it as? EnvironmentalLoadResult.Data }?.conditions

    val headline = when {
        conditions != null -> conditions.headlineMetric()?.displayValue() ?: "—"
        state is EnvironmentalRenderState.Loading -> "—"
        state is EnvironmentalRenderState.Ready -> when (state.result) {
            EnvironmentalLoadResult.NoPermission -> "Location off"
            EnvironmentalLoadResult.NoData -> "No reading"
            is EnvironmentalLoadResult.Error -> "Unavailable"
            else -> "—"
        }
        else -> "—"
    }
    val supporting = conditions?.weatherLabel?.takeIf { it.isNotBlank() }
        ?: when (state) {
            EnvironmentalRenderState.Loading -> "Loading conditions"
            is EnvironmentalRenderState.Ready -> when (state.result) {
                EnvironmentalLoadResult.NoPermission -> "Tap to enable"
                EnvironmentalLoadResult.NoData -> "Local conditions"
                is EnvironmentalLoadResult.Error -> "Tap to retry"
                else -> "Local conditions"
            }
        }
    val humidity = conditions?.metric(EnvironmentalMetricKind.HUMIDITY)
    val wind = conditions?.metric(EnvironmentalMetricKind.WIND)
    val airQuality = conditions?.metric(EnvironmentalMetricKind.AIR_QUALITY)
    // Give air quality priority when it is available; otherwise retain wind.
    val secondary = airQuality ?: wind
    val secondaryLabel = if (airQuality != null) "Air quality" else "Wind"

    Column(
        modifier
            .height(148.dp)
            .midnightCardSurface(accent = palette.green)
            .superhumanHomeTileClickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = environmentalTileDescription(state) +
                    ". Humidity " + (humidity?.displayValue() ?: "unavailable") +
                    ". " + secondaryLabel + " " + (secondary?.displayValue() ?: "unavailable")
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        CompactModuleHeader("ENVIRONMENT", palette.green)
        Spacer(Modifier.height(4.dp))
        Text(
            headline,
            color = palette.textPrimary,
            fontSize = if (headline.length > 7) 14.sp else 23.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            supporting,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        // A quiet divider keeps the mini metrics distinct without a harsh card outline.
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.surfaceSoft))
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactEnvironmentMetric("Humidity", humidity?.displayValue(), Modifier.weight(1f))
            CompactEnvironmentMetric(secondaryLabel, secondary?.displayValue(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactEnvironmentMetric(label: String, value: String?, modifier: Modifier = Modifier) {
    val palette = superhumanPalette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = palette.textMuted, fontSize = 9.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Text(value ?: "—", color = palette.textPrimary, fontSize = 12.sp,
            lineHeight = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}
