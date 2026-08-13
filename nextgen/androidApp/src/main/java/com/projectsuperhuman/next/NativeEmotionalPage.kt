package com.projectsuperhuman.next

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.emotional.DefaultEmotionalHealthValueMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun NativeEmotionalPage(onBack: () -> Unit) {
    val domain = remember { NativeDomainData.forDomain(HealthDomain.EMOTIONAL) }
    val mapper = remember { DefaultEmotionalHealthValueMapper() }
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<EmotionalPresentationSnapshot?>(null) }

    LaunchedEffect(refreshKey) {
        val rows = domain.latestState()
        current = EmotionalPresentationContract.fromCanonicalValues(rows, rows.latestEmotionalLabel())
    }

    EmotionalModuleScreen(
        current = current,
        onBack = onBack,
        onRecord = { values ->
            scope.launch {
                val entry = EmotionalPresentationContract.toCanonicalEntry(values, System.currentTimeMillis())
                NativeDataHub.ingestValues(mapper.map(entry))
                refreshKey++
            }
        }
    )
}

internal fun List<com.projectsuperhuman.next.core.HealthValue>.latestEmotionalLabel(): String? {
    val timestamp = maxOfOrNull { it.timestampEpochMs } ?: return null
    return SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(timestamp))
}
