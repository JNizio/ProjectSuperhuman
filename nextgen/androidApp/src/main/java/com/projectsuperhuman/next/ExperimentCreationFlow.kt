package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ExperimentCreationFlow(
    onBack: () -> Unit,
    onPreviewProtocol: (ExperimentDraft) -> Unit
) {
    val steps = ExperimentCreationStep.entries
    var stepIndex by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf(ExperimentDraft()) }
    val step = steps[stepIndex]

    Column(Modifier.fillMaxSize().background(ExperimentBackground)) {
        ExperimentsHeader("Create experiment", "Step ${step.number} of ${steps.size}", onBack)
        CreationProgress(steps, stepIndex)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Column {
                Text("STEP ${step.number}", color = ExperimentViolet, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(4.dp))
                Text(step.title, color = ExperimentInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(stepSubtitle(step), color = ExperimentMuted, fontSize = 10.sp, lineHeight = 15.sp)
            }

            when (step) {
                ExperimentCreationStep.TESTING -> TestingStep(
                    draft = draft,
                    onSelect = { draft = draft.copy(testingTarget = it) },
                    onCustom = { draft = draft.copy(customTestingTarget = it) }
                )
                ExperimentCreationStep.CHANGING -> ChangingStep(
                    draft = draft,
                    onIntervention = { draft = draft.copy(intervention = it, device = if (it == "Device / sensor") draft.device else null) },
                    onCustom = { draft = draft.copy(customIntervention = it) },
                    onDevice = { draft = draft.copy(device = it) }
                )
                ExperimentCreationStep.MEASURING -> MeasuringStep(
                    draft = draft,
                    onToggle = { outcome ->
                        val updated = if (outcome in draft.outcomes) draft.outcomes - outcome else draft.outcomes + outcome
                        val primary = when {
                            updated.isEmpty() -> null
                            draft.primaryOutcome in updated -> draft.primaryOutcome
                            else -> updated.first()
                        }
                        draft = draft.copy(outcomes = updated, primaryOutcome = primary)
                    },
                    onPrimary = { draft = draft.copy(primaryOutcome = it) }
                )
                ExperimentCreationStep.SCHEDULE -> ScheduleStep(draft) { draft = it }
                ExperimentCreationStep.REVIEW -> ReviewStep(draft) { draft = draft.copy(customTitle = it) }
            }
            Spacer(Modifier.height(8.dp))
        }

        CreationFooter(
            canContinue = draft.canContinue(step),
            isFirst = stepIndex == 0,
            isLast = stepIndex == steps.lastIndex,
            onPrevious = { if (stepIndex > 0) stepIndex-- else onBack() },
            onContinue = {
                if (stepIndex == steps.lastIndex) onPreviewProtocol(draft) else stepIndex++
            }
        )
    }
}

private fun stepSubtitle(step: ExperimentCreationStep): String = when (step) {
    ExperimentCreationStep.TESTING -> "Start with the question. Pick the part of your health or performance you want to understand."
    ExperimentCreationStep.CHANGING -> "Choose one main intervention so the protocol stays understandable."
    ExperimentCreationStep.MEASURING -> "Select the signals you will watch, then mark one as the primary outcome."
    ExperimentCreationStep.SCHEDULE -> "Give the experiment enough structure to compare normal days with intervention days."
    ExperimentCreationStep.REVIEW -> "Check the protocol before previewing it. Nothing is saved or started on this UI branch."
}

@Composable
private fun CreationProgress(steps: List<ExperimentCreationStep>, currentIndex: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { index, _ ->
            Box(
                Modifier.size(if (index == currentIndex) 12.dp else 8.dp)
                    .background(if (index <= currentIndex) ExperimentViolet else Color(0xFFDCE3E9), CircleShape)
            )
            if (index != steps.lastIndex) Box(Modifier.weight(1f).height(2.dp).background(if (index < currentIndex) ExperimentViolet.copy(alpha = .6f) else Color(0xFFE1E6EB)))
        }
    }
}

@Composable
private fun TestingStep(draft: ExperimentDraft, onSelect: (String) -> Unit, onCustom: (String) -> Unit) {
    OptionGrid(ExperimentOptions.testingTargets, draft.testingTarget, onSelect)
    if (draft.testingTarget == "Custom") CustomEntryField("What do you want to understand?", draft.customTestingTarget, onCustom)
}

@Composable
private fun ChangingStep(
    draft: ExperimentDraft,
    onIntervention: (String) -> Unit,
    onCustom: (String) -> Unit,
    onDevice: (ExperimentDevice) -> Unit
) {
    OptionGrid(ExperimentOptions.interventions, draft.intervention, onIntervention)
    if (draft.intervention == "Custom intervention") CustomEntryField("Describe the change", draft.customIntervention, onCustom)
    if (draft.intervention == "Device / sensor") {
        Column(Modifier.experimentCard()) {
            CardEyebrow("MOCK DEVICE SOURCE")
            Spacer(Modifier.height(5.dp))
            Text("Choose how this experiment could receive input", color = ExperimentInk, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            MockExperimentData.devices.forEach { device ->
                DeviceSelectionRow(device, selected = draft.device?.id == device.id) { onDevice(device) }
                Spacer(Modifier.height(7.dp))
            }
            Text("Selection is visual only. No connection or permission request will run.", color = ExperimentMuted, fontSize = 8.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun MeasuringStep(
    draft: ExperimentDraft,
    onToggle: (String) -> Unit,
    onPrimary: (String) -> Unit
) {
    Column(Modifier.experimentCard()) {
        CardEyebrow("OUTCOMES")
        Spacer(Modifier.height(8.dp))
        ExperimentOptions.outcomes.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { option ->
                    MultiSelectOption(option, option in draft.outcomes, Modifier.weight(1f)) { onToggle(option) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (draft.outcomes.isNotEmpty()) {
        Column(Modifier.experimentCard()) {
            CardEyebrow("PRIMARY OUTCOME")
            Spacer(Modifier.height(5.dp))
            Text("Which result answers your main question?", color = ExperimentInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            draft.outcomes.forEach { outcome ->
                PrimaryOutcomeRow(outcome, selected = outcome == draft.primaryOutcome) { onPrimary(outcome) }
            }
        }
    }
}

@Composable
private fun ScheduleStep(draft: ExperimentDraft, onChange: (ExperimentDraft) -> Unit) {
    Column(Modifier.experimentCard()) {
        CardEyebrow("START")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today", "Tomorrow", "Next Monday").forEach { option ->
                SelectablePill(option, draft.startDateLabel == option, Modifier.weight(1f)) { onChange(draft.copy(startDateLabel = option)) }
            }
        }
    }
    Column(Modifier.experimentCard()) {
        CardEyebrow("PROTOCOL LENGTH")
        Spacer(Modifier.height(9.dp))
        ScheduleStepper("Total duration", "days", draft.durationDays, 3, 30) { onChange(draft.copy(durationDays = it)) }
        ScheduleDivider()
        ScheduleStepper("Baseline period", "days", draft.baselineDays, 0, 14) { onChange(draft.copy(baselineDays = it)) }
        ScheduleDivider()
        ScheduleStepper("Intervention period", "days", draft.interventionDays, 1, 28) { onChange(draft.copy(interventionDays = it)) }
        ScheduleDivider()
        ScheduleStepper("Daily frequency", "check-ins", draft.dailyFrequency, 1, 4) { onChange(draft.copy(dailyFrequency = it)) }
    }
    if (!draft.canContinue(ExperimentCreationStep.SCHEDULE)) {
        Text(
            "Baseline + intervention must fit inside the total duration.",
            color = ExperimentAmber,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF7E8), RoundedCornerShape(15.dp)).padding(11.dp)
        )
    }
}

@Composable
private fun ReviewStep(draft: ExperimentDraft, onTitleChange: (String) -> Unit) {
    OutlinedTextField(
        value = draft.customTitle.orEmpty(),
        onValueChange = onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Experiment name (optional)") },
        placeholder = { Text(draft.protocolTitle()) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = ExperimentViolet,
            unfocusedIndicatorColor = ExperimentBorder
        )
    )
    Column(Modifier.experimentCard()) {
        CardEyebrow("PROTOCOL SUMMARY")
        Spacer(Modifier.height(9.dp))
        ReviewLine("QUESTION", draft.resolvedTestingTarget().ifBlank { "Not selected" })
        ReviewLine("CHANGE", draft.resolvedIntervention().ifBlank { "Not selected" })
        ReviewLine("PRIMARY", draft.primaryOutcome ?: "Not selected")
        ReviewLine("SECONDARY", draft.outcomes.filterNot { it == draft.primaryOutcome }.joinToString(" · ").ifBlank { "None" })
        ReviewLine("START", draft.startDateLabel)
        ReviewLine("DURATION", "${draft.durationDays} days")
        ReviewLine("PHASES", "${draft.baselineDays} baseline · ${draft.interventionDays} intervention")
        ReviewLine("CHECK-INS", "${draft.dailyFrequency}× daily")
        draft.device?.let { ReviewLine("INPUT", "${it.title} · ${it.status.label}") }
    }
    Column(Modifier.fillMaxWidth().background(Color(0xFFEDF8F6), RoundedCornerShape(20.dp)).padding(14.dp)) {
        Text("UI PREVIEW", color = ExperimentTeal, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        Spacer(Modifier.height(4.dp))
        Text("Previewing creates no Data Vault record and starts no device connection.", color = ExperimentInk, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun CustomEntryField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = ExperimentViolet,
            unfocusedIndicatorColor = ExperimentBorder
        )
    )
}

@Composable
private fun OptionGrid(options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Column(Modifier.experimentCard()) {
        options.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { option ->
                    SingleSelectOption(option, selected == option, Modifier.weight(1f)) { onSelect(option) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SingleSelectOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(50.dp).background(if (selected) ExperimentViolet.copy(alpha = .12f) else Color(0xFFF5F8FA), RoundedCornerShape(16.dp))
            .border(1.dp, if (selected) ExperimentViolet.copy(alpha = .5f) else ExperimentBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).semantics { role = Role.RadioButton; contentDescription = label },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (selected) ExperimentViolet else Color(0xFFD7DEE4), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, color = if (selected) ExperimentNavy else ExperimentInk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MultiSelectOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).background(if (selected) ExperimentTeal.copy(alpha = .11f) else Color(0xFFF5F8FA), RoundedCornerShape(15.dp))
            .border(1.dp, if (selected) ExperimentTeal.copy(alpha = .45f) else ExperimentBorder, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick).semantics { role = Role.Checkbox; contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(if (selected) "✓  $label" else label, color = if (selected) ExperimentTeal else ExperimentInk, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrimaryOutcomeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp).semantics { role = Role.RadioButton },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(18.dp).border(2.dp, if (selected) ExperimentViolet else ExperimentBorder, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(9.dp).background(ExperimentViolet, CircleShape))
        }
        Spacer(Modifier.width(9.dp))
        Text(label, color = ExperimentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeviceSelectionRow(device: ExperimentDevice, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) ExperimentViolet.copy(alpha = .09f) else Color(0xFFF5F8FA), RoundedCornerShape(15.dp))
            .border(1.dp, if (selected) ExperimentViolet.copy(alpha = .4f) else ExperimentBorder, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).background(if (selected) ExperimentViolet else ExperimentMuted, CircleShape))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(device.title, color = ExperimentInk, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(device.description, color = ExperimentMuted, fontSize = 7.sp)
        }
        Text(device.status.label, color = if (device.status == ExperimentDeviceStatus.CONNECTED) ExperimentTeal else ExperimentMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelectablePill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(39.dp).background(if (selected) ExperimentViolet else Color(0xFFF1F4F7), RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else ExperimentMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScheduleStepper(label: String, suffix: String, value: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = ExperimentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("$value $suffix", color = ExperimentMuted, fontSize = 8.sp)
        }
        StepButton("−", enabled = value > min) { onValue((value - 1).coerceAtLeast(min)) }
        Text(value.toString(), color = ExperimentNavy, fontSize = 14.sp, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(38.dp))
        StepButton("+", enabled = value < max) { onValue((value + 1).coerceAtMost(max)) }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).background(if (enabled) Color(0xFFF0EDF8) else Color(0xFFF2F4F6), RoundedCornerShape(11.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) ExperimentViolet else ExperimentBorder, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScheduleDivider() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEDF1F4)))
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = ExperimentMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .65.sp, modifier = Modifier.width(78.dp))
        Text(value, color = ExperimentInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CreationFooter(
    canContinue: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).border(1.dp, ExperimentBorder).padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.weight(.42f).height(46.dp).background(Color(0xFFF0F4F7), RoundedCornerShape(16.dp)).clickable(onClick = onPrevious),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isFirst) "CANCEL" else "BACK", color = ExperimentMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.weight(1f)) {
            ExperimentPrimaryButton(if (isLast) "PREVIEW PROTOCOL" else "CONTINUE", onContinue, canContinue)
        }
    }
}

@Preview(name = "Experiments · Create", showBackground = true, backgroundColor = 0xFFF8FBFD)
@Composable
private fun ExperimentCreationPreview() {
    ExperimentCreationFlow(onBack = {}, onPreviewProtocol = {})
}
