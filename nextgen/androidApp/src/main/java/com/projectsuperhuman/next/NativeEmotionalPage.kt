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
import com.projectsuperhuman.next.data.EmotionalDataVaultAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun NativeEmotionalPage(onBack: () -> Unit) {
    val domain = remember { NativeDomainData.forDomain(HealthDomain.EMOTIONAL) }
    val vault = remember {
        EmotionalDataVaultAdapter(
            port = NativeDataHub.module(HealthDomain.EMOTIONAL),
            ingestValues = { values -> NativeDataHub.ingestValues(values) }
        )
    }
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<EmotionalPresentationSnapshot?>(null) }
    var recentSavedTimestamp by remember { mutableStateOf<Long?>(null) }
    var editingExisting by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        val rows = domain.latestState()
        current = EmotionalPresentationContract.fromCanonicalValues(rows, rows.latestEmotionalLabel())
    }

    EmotionalModuleScreen(
        current = current,
        onBack = onBack,
        onRecord = { values ->
            scope.launch {
                val recordedAt = System.currentTimeMillis()
                // Capture the old canonical rows before saving the replacement.
                val rowsToReplace = if (editingExisting) {
                    domain.latestState().filter { row ->
                        row.timestampEpochMs == current?.recordedAtEpochMs &&
                            row.source.contains("emotional", ignoreCase = true)
                    }
                } else emptyList()
                val entry = EmotionalPresentationContract.toCanonicalEntry(values, recordedAt)
                vault.save(entry)
                if (rowsToReplace.isNotEmpty()) {
                    NativeDataHub.deleteValues(rowsToReplace)
                }
                recentSavedTimestamp = if (editingExisting) null else recordedAt
                editingExisting = false
                refreshKey++
            }
        },
        editingExisting = editingExisting,
        onEditExisting = if (current != null) ({ editingExisting = true }) else null,
        onUndoRecent = if (recentSavedTimestamp != null) {
            {
                scope.launch {
                    val timestamp = recentSavedTimestamp
                    if (timestamp != null) {
                        val recent = domain.latestState().filter { row ->
                            row.timestampEpochMs == timestamp &&
                                row.source.contains("emotional", ignoreCase = true)
                        }
                        if (recent.isNotEmpty()) {
                            NativeDataHub.deleteValues(recent)
                            recentSavedTimestamp = null
                            refreshKey++
                        }
                    }
                }
            }
        } else null
    )
}

internal fun List<com.projectsuperhuman.next.core.HealthValue>.latestEmotionalLabel(): String? {
    val timestamp = maxOfOrNull { it.timestampEpochMs } ?: return null
    return SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(timestamp))
}
