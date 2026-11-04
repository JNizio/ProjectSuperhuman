package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthValue

/**
 * Continuous strength logging: each exercise owns its own editable draft.
 * Only tapping Complete persists a set; editing completed sets is explicit.
 * Prior values are taken from real recorded rows, never invented.
 */
@Composable
internal fun StrengthInlineExerciseLogger(
    exercise: NativeExercise,
    number: Int,
    completed: List<NativeWorkoutSet>,
    previous: HealthValue?,
    target: RoutineExerciseTarget?,
    startedAt: Long,
    deletePendingTimestamp: Long?,
    onLog: (Int, Double, String, Int?, Double?) -> Unit,
    onEdit: (NativeWorkoutSet, Int, Double) -> Unit,
    onDelete: (NativeWorkoutSet) -> Unit,
    onRemoveExercise: () -> Unit
) {
    val ink = superhumanTextPrimary
    val muted = superhumanTextMuted
    val accent = superhumanBlue
    val priorReps = previous?.metadata?.get("reps")?.toIntOrNull()
    val priorKg = previous?.metadata?.get("loadKg")?.toDoubleOrNull()
    var repsText by rememberSaveable(startedAt, exercise.id) {
        mutableStateOf((priorReps ?: target?.reps ?: 10).toString())
    }
    var kgText by rememberSaveable(startedAt, exercise.id) {
        mutableStateOf(priorKg?.let(::strengthInlineNumber) ?: "0")
    }
    var inputError by rememberSaveable(startedAt, exercise.id) { mutableStateOf(false) }
    var advanced by rememberSaveable(startedAt, exercise.id) { mutableStateOf(false) }
    var setKind by rememberSaveable(startedAt, exercise.id) { mutableStateOf("Work") }
    var rirText by rememberSaveable(startedAt, exercise.id) { mutableStateOf("") }
    var rpeText by rememberSaveable(startedAt, exercise.id) { mutableStateOf("") }
    var lastSubmittedAt by rememberSaveable(startedAt, exercise.id) { mutableStateOf(0L) }
    val previousLabel = if (priorReps != null && priorKg != null) {
        strengthInlineNumber(priorKg) + " kg × " + priorReps
    } else "—"
    Column(
        Modifier.fillMaxWidth()
            .background(superhumanSurface, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(number.toString() + ". " + exercise.name, color = ink,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(exercise.group + " · " + exercise.equipment, color = muted, fontSize = 11.sp)
            }
            Text("REMOVE", color = muted, fontSize = 10.sp,
                modifier = Modifier.clickable(onClick = onRemoveExercise).padding(8.dp))
        }
        if (target != null) {
            Text("Plan: " + target.sets + " sets × " + target.reps + " reps",
                color = accent, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SET", Modifier.width(30.dp), color = muted, fontSize = 10.sp)
            Text("PREVIOUS", Modifier.weight(1.35f), color = muted, fontSize = 10.sp)
            Text("WEIGHT", Modifier.weight(.95f), color = muted, fontSize = 10.sp)
            Text("REPS", Modifier.weight(.55f), color = muted, fontSize = 10.sp)
            Text("DONE", Modifier.width(42.dp), color = muted, fontSize = 10.sp)
        }
        completed.forEachIndexed { index, set ->
            key(set.timestamp) {
                var editing by rememberSaveable(set.timestamp) { mutableStateOf(false) }
                var editedKg by rememberSaveable(set.timestamp) { mutableStateOf(strengthInlineNumber(set.loadKg)) }
                var editedReps by rememberSaveable(set.timestamp) { mutableStateOf(set.reps.toString()) }
                Column(
                    Modifier.fillMaxWidth().background(superhumanSurfaceSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    if (editing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                editedKg, { editedKg = strengthInlineDecimal(it) },
                                Modifier.weight(1f), singleLine = true,
                                label = { Text("kg") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            OutlinedTextField(
                                editedReps, { editedReps = it.filter(Char::isDigit).take(3) },
                                Modifier.weight(1f), singleLine = true,
                                label = { Text("Reps") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("SAVE", color = superhumanGreen, fontSize = 11.sp,
                                modifier = Modifier.clickable {
                                    val reps = editedReps.toIntOrNull()
                                    val kg = editedKg.toDoubleOrNull()
                                    if (reps != null && reps > 0 && kg != null && kg >= 0.0 && kg.isFinite()) {
                                        onEdit(set, reps, kg)
                                        editing = false
                                    }
                                }.padding(8.dp))
                            Text("CANCEL", color = muted, fontSize = 11.sp,
                                modifier = Modifier.clickable { editing = false }.padding(8.dp))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text((index + 1).toString(), Modifier.width(24.dp), color = muted, fontSize = 11.sp)
                            Text("✓", Modifier.width(26.dp), color = superhumanGreen)
                            Text(strengthInlineNumber(set.loadKg) + " kg", Modifier.weight(1f), color = ink, fontSize = 11.sp)
                            Text(set.reps.toString(), Modifier.width(35.dp), color = ink, fontSize = 11.sp)
                            Text("EDIT", color = accent, fontSize = 10.sp,
                                modifier = Modifier.clickable { editing = true }.padding(7.dp))
                        }
                        Text(
                            if (deletePendingTimestamp == set.timestamp) "CONFIRM DELETE" else "Delete set",
                            color = if (deletePendingTimestamp == set.timestamp) superhumanRed else muted,
                            fontSize = 10.sp,
                            modifier = Modifier.clickable { onDelete(set) }.padding(vertical = 5.dp)
                        )
                    }
                }
            }
        }
        Text("Previous: " + previousLabel, color = muted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                kgText, { kgText = strengthInlineDecimal(it); inputError = false },
                Modifier.weight(1f), singleLine = true,
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                repsText, { repsText = it.filter(Char::isDigit).take(3); inputError = false },
                Modifier.weight(1f), singleLine = true,
                label = { Text("Reps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Text(
            if (advanced) "HIDE SET OPTIONS" else "SET OPTIONS (WARM-UP / EFFORT)",
            color = muted, fontSize = 10.sp,
            modifier = Modifier.clickable { advanced = !advanced }.padding(vertical = 4.dp)
        )
        if (advanced) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("WORKING", color = if (setKind == "Work") accent else muted,
                    modifier = Modifier.clickable { setKind = "Work" }.padding(7.dp), fontSize = 11.sp)
                Text("WARM-UP", color = if (setKind == "Warmup") accent else muted,
                    modifier = Modifier.clickable { setKind = "Warmup" }.padding(7.dp), fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    rirText, { rirText = it.filter(Char::isDigit).take(2) },
                    Modifier.weight(1f), label = { Text("RIR (optional)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    rpeText, { rpeText = strengthInlineDecimal(it) },
                    Modifier.weight(1f), label = { Text("RPE (optional)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SET " + (completed.size + 1), Modifier.weight(1f), color = muted, fontSize = 11.sp)
            Text("COMPLETE SET", color = superhumanGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(superhumanGreen.copy(alpha = .12f), RoundedCornerShape(12.dp))
                    .clickable {
                        val reps = repsText.toIntOrNull()
                        val kg = kgText.toDoubleOrNull()
                        if (reps != null && reps > 0 && kg != null && kg >= 0.0 && kg.isFinite()) {
                            val rir = rirText.toIntOrNull()?.takeIf { it in 0..20 }
                            val rpe = rpeText.toDoubleOrNull()?.takeIf { it in 0.0..10.0 }
                            if ((rirText.isNotBlank() && rir == null) || (rpeText.isNotBlank() && rpe == null)) {
                                inputError = true
                            } else {
                                val now = System.currentTimeMillis()
                                if (now - lastSubmittedAt >= 350L) {
                                    lastSubmittedAt = now
                                    onLog(reps, kg, setKind, rir, rpe)
                                }
                                inputError = false
                            }
                        } else inputError = true
                    }.padding(horizontal = 16.dp, vertical = 13.dp))
        }
        if (inputError) Text("Check reps, non-negative weight, and optional effort values.", color = superhumanRed, fontSize = 11.sp)
    }
}

private fun strengthInlineDecimal(text: String): String {
    val clean = text.filter { it.isDigit() || it == '.' }.take(8)
    val firstDot = clean.indexOf('.')
    return if (firstDot < 0) clean else clean.take(firstDot + 1) + clean.drop(firstDot + 1).replace(".", "")
}

private fun strengthInlineNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
