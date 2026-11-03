package com.projectsuperhuman.next

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** One scheduled dose; pillCount is the number of individual capsules/tablets due each day. */
internal data class SupplementRoutineItem(
    val id: String,
    val name: String,
    val pillCount: Int = 1,
    val time: String = "Anytime"
)

internal data class SupplementDailyState(
    val date: String,
    val routine: List<SupplementRoutineItem> = emptyList(),
    val taken: Set<String> = emptySet()
) {
    val totalPills: Int get() = routine.sumOf { it.pillCount }
    val takenPills: Int get() = routine.sumOf { item ->
        (0 until item.pillCount).count { dose -> key(item.id, dose) in taken }
    }
    companion object {
        fun key(id: String, index: Int): String = "$id:$index"
    }
}

/**
 * On-device, offline-only supplement checklist. A new day starts with unticked pills;
 * prior days stay recorded under their own date key. Never auto-mark anything taken.
 */
internal object SupplementRoutineStore {
    private const val PREFS = "project_superhuman_supplements"
    private const val ROUTINE_KEY = "routine_v1"
    private const val DAILY_PREFIX = "taken_v1_"
    private val allowedTimes = setOf("Morning", "Afternoon", "Evening", "Anytime")

    fun read(context: Context, date: LocalDate = LocalDate.now()): SupplementDailyState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val routine = runCatching {
            val list = JSONArray(prefs.getString(ROUTINE_KEY, "[]") ?: "[]")
            buildList {
                for (i in 0 until list.length()) {
                    val obj = list.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val name = obj.optString("name").trim()
                    if (id.isEmpty() || name.isEmpty()) continue
                    add(SupplementRoutineItem(
                        id = id,
                        name = name,
                        pillCount = obj.optInt("count", 1).coerceIn(1, 24),
                        time = obj.optString("time").takeIf(allowedTimes::contains) ?: "Anytime"
                    ))
                }
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
        val today = date.toString()
        val taken = runCatching {
            val array = JSONArray(prefs.getString(DAILY_PREFIX + today, "[]") ?: "[]")
            buildSet {
                for (i in 0 until array.length()) {
                    val value = array.optString(i)
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptySet())
        return SupplementDailyState(today, routine, taken)
    }

    fun saveRoutine(context: Context, items: List<SupplementRoutineItem>): SupplementDailyState {
        val valid = items.distinctBy { it.id }.take(80).mapNotNull {
            val name = it.name.trim().take(60)
            if (name.isBlank()) null else it.copy(
                name = name,
                pillCount = it.pillCount.coerceIn(1, 24),
                time = if (it.time in allowedTimes) it.time else "Anytime"
            )
        }
        val array = JSONArray()
        valid.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("count", item.pillCount)
                put("time", item.time)
            })
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(ROUTINE_KEY, array.toString()).apply()
        return read(context)
    }

    fun toggle(context: Context, itemId: String, pillIndex: Int): SupplementDailyState {
        val current = read(context)
        val item = current.routine.firstOrNull { it.id == itemId } ?: return current
        if (pillIndex !in 0 until item.pillCount) return current
        val key = SupplementDailyState.key(itemId, pillIndex)
        val updated = current.taken.toMutableSet().also {
            if (!it.add(key)) it.remove(key)
        }
        val array = JSONArray()
        updated.sorted().forEach { array.put(it) }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(DAILY_PREFIX + current.date, array.toString()).apply()
        return current.copy(taken = updated)
    }
}

private val SupplementMutedPill = Color(0xFF61646B)
private val SupplementTakenPill = Color(0xFFF5F5F6)

/** A capsule-shaped progress mark. The enclosing 30dp touch area stays tappable. */
@Composable
private fun SupplementPill(
    checked: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(width = 29.dp, height = 29.dp)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 20.dp, height = 9.dp)
                .background(if (checked) {
                    if (SuperhumanAppearance.darkMode) SupplementTakenPill else Color(0xFF202329)
                } else SupplementMutedPill, CircleShape)
                .border(0.6.dp,
                    if (checked) {
                        if (SuperhumanAppearance.darkMode) Color.White else Color(0xFF202329)
                    } else Color(0xFF74777D).copy(alpha = 0.5f),
                    CircleShape)
        )
    }
}

/** Compact full-width tile, approximately half the height of the normal dashboard modules. */
@Composable
internal fun HomeSupplementsTile(onOpen: () -> Unit) {
    val context = LocalContext.current
    var day by remember { mutableStateOf(SupplementRoutineStore.read(context)) }

    // Only a date rollover triggers a refresh; this does not poll storage while scrolling.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            if (day.date != LocalDate.now().toString()) {
                day = SupplementRoutineStore.read(context)
            }
        }
    }

    val dark = SuperhumanAppearance.darkMode
    val ink = superhumanTextPrimary
    val muted = superhumanTextMuted
    val pillItems = remember(day.routine) {
        day.routine.flatMap { item -> (0 until item.pillCount).map { item to it } }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .height(118.dp)
            .midnightCardSurface(accent = if (dark) Color(0xFF888B92) else Color(0xFF6E727B), radius = 25.dp)
            .superhumanHomeTileClickable(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Supplements", color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                if (day.totalPills == 0) "Set routine"
                else "${day.takenPills}/${day.totalPills} taken",
                color = muted, fontSize = 11.sp
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                painter = painterResource(R.drawable.tabler_chevron_right),
                contentDescription = "Open supplement routine",
                tint = muted, modifier = Modifier.size(17.dp)
            )
        }
        if (pillItems.isEmpty()) {
            Text("Add supplements to build your daily checklist",
                color = muted, fontSize = 11.sp, maxLines = 2)
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    items = pillItems,
                    key = { (item, index) -> SupplementDailyState.key(item.id, index) }
                ) { (item, index) ->
                    val checked = SupplementDailyState.key(item.id, index) in day.taken
                    SupplementPill(
                        checked = checked,
                        description = "${item.name} pill ${index + 1}: " +
                            if (checked) "taken, tap to undo" else "not taken, tap to mark taken",
                        onClick = { day = SupplementRoutineStore.toggle(context, item.id, index) }
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(if (day.totalPills == 0) "Your routine, your choice"
             else if (day.takenPills == day.totalPills) "All scheduled pills logged"
             else "Tap a capsule to log it",
            color = muted, fontSize = 9.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun NativeSupplementsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(SupplementRoutineStore.read(context)) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SupplementRoutineItem?>(null) }
    var pendingRemoval by remember { mutableStateOf<SupplementRoutineItem?>(null) }
    val times = listOf("Morning", "Afternoon", "Evening", "Anytime")

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            if (state.date != LocalDate.now().toString()) {
                state = SupplementRoutineStore.read(context)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(superhumanBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SuperhumanLayout.pageHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SuperhumanModuleBackButton(onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Supplements", color = superhumanTextPrimary,
                    fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Daily routine & intake", color = superhumanTextMuted, fontSize = 11.sp)
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .background(superhumanSurface, RoundedCornerShape(25.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("TODAY", color = superhumanTextMuted,
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text("${state.takenPills} / ${state.totalPills} pills logged",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = superhumanTextPrimary)
            if (state.totalPills > 0) {
                Box(Modifier.fillMaxWidth().height(4.dp)
                    .background(superhumanSurfaceSoft, CircleShape)) {
                    Box(Modifier.fillMaxWidth(state.takenPills.toFloat() / state.totalPills)
                        .height(4.dp).background(Color(0xFFF5F5F6), CircleShape))
                }
            } else {
                Text("No supplements scheduled. Create your daily routine below.",
                    color = superhumanTextMuted, fontSize = 12.sp)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Your daily routine", color = superhumanTextPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("+ Add supplement", color = superhumanTextPrimary,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.superhumanClickable(onClick = {
                    editing = null
                    editorOpen = true
                }).padding(10.dp))
        }

        if (state.routine.isEmpty()) {
            Text("Add a supplement, set how many pills you take each day, " +
                "and tap the capsules to log the ones you've actually taken.",
                fontSize = 12.sp, lineHeight = 18.sp, color = superhumanTextMuted)
        } else {
            times.forEach { time ->
                val entries = state.routine.filter { it.time == time }
                if (entries.isNotEmpty()) {
                    Text(time.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Medium,
                        color = superhumanTextMuted)
                    entries.forEach { item ->
                        Column(
                            Modifier.fillMaxWidth()
                                .background(superhumanSurface, RoundedCornerShape(22.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = superhumanTextPrimary)
                                    Text("${item.pillCount} pill" +
                                        if (item.pillCount == 1) " daily" else "s daily",
                                        fontSize = 11.sp, color = superhumanTextMuted)
                                }
                                Text("Edit", color = superhumanTextMuted, fontSize = 12.sp,
                                    modifier = Modifier.superhumanClickable(onClick = {
                                        editing = item
                                        editorOpen = true
                                    }).padding(10.dp))
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                (0 until item.pillCount).forEach { index ->
                                    val checked = SupplementDailyState.key(item.id, index) in state.taken
                                    SupplementPill(checked, "${item.name}, pill ${index + 1}, " +
                                        if (checked) "taken" else "not taken",
                                        onClick = {
                                            state = SupplementRoutineStore.toggle(context, item.id, index)
                                        })
                                }
                            }
                        }
                    }
                }
            }
        }

        Text("A white capsule means you've logged that pill as taken. " +
            "A grey capsule means it hasn't been logged. Logs reset for each new day; " +
            "existing history stays on your device.",
            fontSize = 11.sp, lineHeight = 16.sp, color = superhumanTextMuted)
        Spacer(Modifier.height(112.dp))
    }

    if (editorOpen) {
        SupplementRoutineEditor(
            initial = editing,
            onDismiss = { editorOpen = false },
            onSave = { saved ->
                val updated = if (editing == null) state.routine + saved else
                    state.routine.map { if (it.id == saved.id) saved else it }
                state = SupplementRoutineStore.saveRoutine(context, updated)
                editorOpen = false
            },
            onRemove = if (editing == null) null else {
                {
                    pendingRemoval = editing
                    editorOpen = false
                }
            }
        )
    }
    pendingRemoval?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove ${item.name}?") },
            text = { Text("This removes it from your current routine. Previous day logs remain saved.") },
            confirmButton = {
                TextButton(onClick = {
                    state = SupplementRoutineStore.saveRoutine(
                        context, state.routine.filterNot { it.id == item.id })
                    pendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SupplementRoutineEditor(
    initial: SupplementRoutineItem?,
    onDismiss: () -> Unit,
    onSave: (SupplementRoutineItem) -> Unit,
    onRemove: (() -> Unit)?
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var pills by remember(initial?.id) {
        mutableStateOf(initial?.pillCount?.toString() ?: "1")
    }
    var time by remember(initial?.id) {
        mutableStateOf(initial?.time ?: "Anytime")
    }
    val count = pills.toIntOrNull()
    val valid = name.trim().isNotEmpty() && count != null && count in 1..24
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add supplement" else "Edit supplement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Supplement name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pills,
                    onValueChange = { pills = it.filter(Char::isDigit).take(2) },
                    label = { Text("Pills per day (1–24)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("When do you usually take it?",
                    color = superhumanTextMuted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Morning", "Afternoon", "Evening", "Anytime").forEach { option ->
                        Text(
                            option,
                            fontSize = 11.sp,
                            color = if (time == option) superhumanTextPrimary else superhumanTextMuted,
                            modifier = Modifier
                                .background(if (time == option) superhumanSurfaceSoft
                                    else superhumanSurface, RoundedCornerShape(12.dp))
                                .border(1.dp, if (time == option) superhumanBorder
                                    else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable(interactionSource = null, indication = null) { time = option }
                                .padding(horizontal = 10.dp, vertical = 11.dp)
                        )
                    }
                }
                if (onRemove != null) {
                    Text("Remove from routine", color = superhumanRed,
                        fontSize = 12.sp,
                        modifier = Modifier.superhumanClickable(onClick = onRemove)
                            .padding(vertical = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    if (valid) onSave(SupplementRoutineItem(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(), pillCount = count!!, time = time
                    ))
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
