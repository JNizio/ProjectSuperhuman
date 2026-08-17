package com.projectsuperhuman.next

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val TrudyNavy get() = superhumanBrandText
private val TrudyBg get() = superhumanBackground
private val TrudyMuted get() = superhumanTextMuted
private val TrudyCyan get() = superhumanAccent
private val TrudyCyanSoft get() = superhumanAccentSoft
private val TrudyBorder get() = superhumanBorder
private val TrudyWarningBg get() = superhumanWarningSurface
private val TrudyWarningText get() = if (SuperhumanAppearance.darkMode) Color(0xFFE8C979) else Color(0xFF7B6430)
private val TrudyErrorBg get() = superhumanErrorSurface
private val TrudyErrorText get() = superhumanRed
private val TrudyUserBubble get() = if (SuperhumanAppearance.darkMode) Color(0xFF174F72) else Color(0xFF123D70)

@Composable
internal fun NativeTrudy(
    state: TrudyConversationState,
    controller: TrudyConversationController,
    voiceController: TrudyVoiceController,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val uiState = state.uiState
    val voiceState by voiceController.state.collectAsState()
    val speechInputController = rememberTrudySpeechInputController()
    val speechInputState by speechInputController.state.collectAsState()
    var voiceModeActive by remember { mutableStateOf(false) }
    var showVoiceSettings by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var voiceContextModules by remember {
        mutableStateOf(emptyList<TrudyVoiceContextModule>())
    }

    fun leaveTrudy() {
        speechInputController.cancel()
        voiceController.onTrudyHidden()
        onBack()
    }

    fun openVoiceMode() {
        showGuide = false
        showVoiceSettings = false
        voiceModeActive = true
    }

    fun closeVoiceMode() {
        speechInputController.cancel()
        voiceController.stop()
        showVoiceSettings = false
        voiceModeActive = false
    }

    BackHandler(enabled = voiceModeActive, onBack = ::closeVoiceMode)
    BackHandler(enabled = !voiceModeActive, onBack = ::leaveTrudy)

    DisposableEffect(voiceController) {
        onDispose { voiceController.onTrudyHidden() }
    }
    LaunchedEffect(voiceController) { voiceController.refresh() }

    fun execute(request: TrudySendRequest?) {
        request ?: return
        scope.launch {
            val result = try {
                controller.respondTo(request.conversationRequest)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                TrudyControllerResult.Failure("Trudy couldn't complete that response. Try again.")
            }
            val accepted = state.complete(request, result)
            if (accepted && result is TrudyControllerResult.Success) {
                voiceContextModules = TrudyVoiceContextMapper.modulesFor(result.reply)
                if (voiceModeActive) {
                    voiceController.speak(request.assistantMessageId, result.reply.text)
                } else {
                    voiceController.maybeAutoSpeak(request.assistantMessageId, result.reply.text)
                }
            }
        }
    }

    fun submit(request: TrudySendRequest?) {
        request ?: return
        voiceController.onUserPromptSubmitted()
        execute(request)
    }

    fun send(textOverride: String? = null) = submit(state.beginSend(textOverride))
    fun retry(messageId: Long) = submit(state.retryFailed(messageId))

    LaunchedEffect(speechInputState.resultId) {
        if (speechInputState.resultId > 0L) {
            val transcript = speechInputState.finalTranscript
            speechInputController.acknowledgeResult()
            if (transcript.isNotBlank()) send(transcript)
        }
    }

    if (voiceModeActive) {
        val visibleContextModules = when (voiceState.status) {
            TrudyVoiceUiStatus.LOADING,
            TrudyVoiceUiStatus.SYNTHESIZING,
            TrudyVoiceUiStatus.SPEAKING -> voiceContextModules
            else -> emptyList()
        }
        TrudyVoiceModeScreen(
            conversationState = uiState,
            voiceState = voiceState,
            speechInputState = speechInputState,
            speechInputController = speechInputController,
            contextModules = visibleContextModules,
            settingsExpanded = showVoiceSettings,
            onToggleSettings = { showVoiceSettings = !showVoiceSettings },
            onKeyboardMode = { partialTranscript ->
                if (partialTranscript.isNotBlank()) state.updateInput(partialTranscript)
                closeVoiceMode()
            },
            onEndSession = ::closeVoiceMode,
            onStopSpeaking = voiceController::stop,
            settingsContent = {
                TrudyVoiceSettingsCard(
                    state = voiceState,
                    onEnabled = voiceController::setEnabled,
                    onAutoSpeak = voiceController::setAutoSpeak,
                    onVoice = voiceController::selectVoice,
                    onSpeed = voiceController::setSpeed,
                    onInstall = voiceController::installModel,
                    onRemove = voiceController::removeModel,
                    onReinstall = voiceController::reinstallModel
                )
            },
            promptContent = {
                TrudyVoicePrompt(
                    state = voiceState,
                    onInstall = voiceController::installModel,
                    onRetry = voiceController::retryLastVoiceAction,
                    onDismiss = voiceController::dismissPrompt
                )
            }
        )
        return
    }

    LaunchedEffect(uiState.messages.size, uiState.isThinking) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    val latestCompleteTrudyMessageId = uiState.messages.lastOrNull {
        it.role == TrudyMessageRole.TRUDY && it.status == TrudyMessageStatus.COMPLETE
    }?.id

    Column(Modifier.fillMaxSize().background(TrudyBg).imePadding()) {
        TrudyHeader(
            onBack = ::leaveTrudy,
            onGuide = { showGuide = true },
            onVoiceMode = ::openVoiceMode,
            voiceEnabled = voiceState.preferences.enabled
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.showWelcome) {
                TrudyDiscoveryWelcome(
                    onPrompt = ::send,
                    onVoiceMode = ::openVoiceMode,
                    onGuide = { showGuide = true }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 17.dp, end = 17.dp, top = 12.dp, bottom = 18.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        TrudyMessageBubble(
                            message = message,
                            evidenceExpanded = message.id in uiState.expandedEvidenceMessageIds,
                            voiceState = voiceState,
                            showFollowUps = message.id == latestCompleteTrudyMessageId,
                            onToggleEvidence = { state.toggleEvidence(message.id) },
                            onRetry = { retry(message.id) },
                            onSpeak = { voiceController.speak(message.id, message.text) },
                            onFollowUp = ::send
                        )
                    }
                }
            }
        }

        if (voiceState.promptVisible) {
            TrudyVoicePrompt(
                state = voiceState,
                onInstall = voiceController::installModel,
                onRetry = voiceController::retryLastVoiceAction,
                onDismiss = voiceController::dismissPrompt
            )
        }

        TrudyComposer(
            value = state.inputText,
            enabled = !uiState.isThinking,
            onValueChange = state::updateInput,
            onSend = { send() }
        )
    }

    if (showGuide) {
        TrudyGuideDialog(
            onDismiss = { showGuide = false },
            onPrompt = ::send,
            onVoiceMode = ::openVoiceMode
        )
    }
}

@Composable
private fun TrudyHeader(
    onBack: () -> Unit,
    onGuide: () -> Unit,
    onVoiceMode: () -> Unit,
    voiceEnabled: Boolean
) {
    Row(
        Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.superhumanTopButton(onClick = onBack)
                .semantics { contentDescription = "Back from Trudy" },
            contentAlignment = Alignment.Center
        ) {
            Text("‹", color = TrudyNavy, fontSize = 29.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text("TRUDY", color = TrudyNavy, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.4.sp)
            Text("Reason across your health data", color = TrudyMuted, fontSize = 10.sp)
        }
        Box(
            Modifier.size(42.dp)
                .background(superhumanSurface, RoundedCornerShape(14.dp))
                .border(1.dp, TrudyBorder, RoundedCornerShape(14.dp))
                .superhumanClickable(onClick = onGuide)
                .semantics { contentDescription = "Open guide to Trudy features" },
            contentAlignment = Alignment.Center
        ) {
            Text("?", color = TrudyNavy, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.size(7.dp))
        Box(
            Modifier.defaultMinSize(minWidth = 68.dp, minHeight = 42.dp)
                .background(if (voiceEnabled) TrudyCyanSoft else superhumanSurface, RoundedCornerShape(14.dp))
                .border(1.dp, if (voiceEnabled) TrudyCyan.copy(alpha = .32f) else TrudyBorder, RoundedCornerShape(14.dp))
                .superhumanClickable(onClick = onVoiceMode)
                .semantics { contentDescription = "Open Trudy voice mode, ${if (voiceEnabled) "voice on" else "voice off"}" }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♪", color = if (voiceEnabled) TrudyCyan else TrudyMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(5.dp))
                Text("VOICE", color = TrudyNavy, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .55.sp)
            }
        }
    }
}

@Composable
private fun TrudyMessageBubble(
    message: TrudyMessage,
    evidenceExpanded: Boolean,
    voiceState: TrudyVoiceUiState,
    showFollowUps: Boolean,
    onToggleEvidence: () -> Unit,
    onRetry: () -> Unit,
    onSpeak: () -> Unit,
    onFollowUp: (String) -> Unit
) {
    val isUser = message.role == TrudyMessageRole.USER
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Text(
                "TRUDY",
                color = TrudyCyan,
                fontSize = 7.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = .8.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
        }
        Box(
            Modifier.widthIn(max = 350.dp)
                .background(if (isUser) TrudyUserBubble else superhumanSurfaceElevated, RoundedCornerShape(19.dp))
                .border(1.dp, if (isUser) TrudyUserBubble else TrudyBorder, RoundedCornerShape(19.dp))
                .padding(horizontal = 15.dp, vertical = 12.dp)
        ) {
            when (message.status) {
                TrudyMessageStatus.SENDING -> TrudyThinkingContent(message.activity)
                TrudyMessageStatus.ERROR -> TrudyErrorContent(message, onRetry)
                TrudyMessageStatus.COMPLETE -> Text(
                    message.text,
                    color = if (isUser) Color.White else superhumanTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        if (message.supportsVoicePlayback()) {
            TrudySpeakControl(
                active = voiceState.isSpeakingMessage(message.id),
                voiceEnabled = voiceState.preferences.enabled,
                status = if (voiceState.activeMessageId == message.id) voiceState.status else null,
                onClick = onSpeak
            )
        }

        if (!isUser && message.status == TrudyMessageStatus.COMPLETE) {
            message.activity?.let { activity ->
                Text(activity.label, color = TrudyMuted, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp, top = 5.dp))
            }
            message.notices.forEach { notice -> TrudyNoticeRow(notice) }
            if (message.evidence.isNotEmpty()) {
                TrudyEvidenceBlock(message.evidence, evidenceExpanded, onToggleEvidence)
            }
            if (showFollowUps) {
                Box(Modifier.widthIn(max = 350.dp)) {
                    TrudyFollowUpStrip(onPrompt = onFollowUp)
                }
            }
        }
    }
}

@Composable
private fun TrudySpeakControl(
    active: Boolean,
    voiceEnabled: Boolean,
    status: TrudyVoiceUiStatus?,
    onClick: () -> Unit
) {
    val detail = when (status) {
        TrudyVoiceUiStatus.LOADING -> "Preparing voice"
        TrudyVoiceUiStatus.SYNTHESIZING -> "Synthesizing"
        TrudyVoiceUiStatus.SPEAKING -> "Speaking"
        else -> null
    }
    Row(
        Modifier.padding(start = 5.dp, top = 4.dp)
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            .superhumanClickable(onClick = onClick)
            .semantics {
                contentDescription = when {
                    active -> "Stop speaking this Trudy answer"
                    !voiceEnabled -> "Speak this Trudy answer. Voice is currently off"
                    else -> "Speak this Trudy answer"
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (active) "■ Stop" else "▶ Speak", color = if (active) TrudyCyan else TrudyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        detail?.let {
            Spacer(Modifier.size(7.dp))
            Text(it, color = TrudyMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun TrudyVoiceSettingsCard(
    state: TrudyVoiceUiState,
    onEnabled: (Boolean) -> Unit,
    onAutoSpeak: (Boolean) -> Unit,
    onVoice: (String) -> Unit,
    onSpeed: (Float) -> Unit,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    onReinstall: () -> Unit
) {
    var voiceMenu by remember { mutableStateOf(false) }
    val voices = state.modelInfo.availableVoices
    val currentVoice = voices.firstOrNull { it.id == state.preferences.selectedVoiceId }?.label
        ?: state.preferences.selectedVoiceId

    Surface(
        color = superhumanSurface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            SettingsSwitchRow("Local voice", state.preferences.enabled, "Enable local Trudy voice", onEnabled)
            SettingsSwitchRow("Auto-speak replies", state.preferences.autoSpeak, "Automatically speak completed Trudy replies", onAutoSpeak)

            if (voices.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Box {
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                            .superhumanClickable(enabled = voices.size > 1) { voiceMenu = true }
                            .semantics {
                                contentDescription = if (voices.size > 1) "Selected Trudy voice: $currentVoice. Choose voice" else "Trudy voice: $currentVoice"
                                if (voices.size <= 1) disabled()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Voice", color = TrudyNavy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(currentVoice, color = TrudyMuted, fontSize = 11.sp)
                    }
                    if (voices.size > 1) {
                        DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                            voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice.label) },
                                    onClick = { voiceMenu = false; onVoice(voice.id) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", color = TrudyNavy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 54.dp))
                Slider(
                    value = state.preferences.speed.coerceIn(0.75f, 1.5f),
                    onValueChange = onSpeed,
                    valueRange = 0.75f..1.5f,
                    steps = 14,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Trudy speech speed" }
                )
                Text(String.format("%.2fx", state.preferences.speed), color = TrudyMuted, fontSize = 10.sp)
            }

            val modelStatus = when (state.modelInfo.installation) {
                TrudyVoiceModelInstallation.INSTALLED -> "Installed${state.modelInfo.modelVersion?.let { " · $it" }.orEmpty()}"
                TrudyVoiceModelInstallation.NOT_INSTALLED -> "Not installed"
                TrudyVoiceModelInstallation.UNAVAILABLE -> "Unavailable"
            }
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Voice model", color = TrudyNavy, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(modelStatus, color = TrudyMuted, fontSize = 10.sp)
                }
                when {
                    state.modelInfo.installation == TrudyVoiceModelInstallation.NOT_INSTALLED && state.modelInfo.canInstall ->
                        SmallVoiceAction("Install", "Install local Trudy voice model", onInstall)
                    state.modelInfo.canRemove && state.modelInfo.canInstall -> {
                        SmallVoiceAction("Reinstall", "Reinstall local Trudy voice model", onReinstall)
                        Spacer(Modifier.size(5.dp))
                        SmallVoiceAction("Remove", "Remove local Trudy voice model", onRemove)
                    }
                    state.modelInfo.canRemove -> SmallVoiceAction("Remove", "Remove local Trudy voice model", onRemove)
                }
            }
            Text("Voice stays on-device. Auto-speak is off by default and voice assets are never downloaded silently.", color = TrudyMuted, fontSize = 9.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, description: String, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TrudyNavy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked, modifier = Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun SmallVoiceAction(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
            .background(TrudyCyanSoft, RoundedCornerShape(10.dp))
            .superhumanClickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = TrudyNavy, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun TrudyVoicePrompt(
    state: TrudyVoiceUiState,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val isError = state.status == TrudyVoiceUiStatus.ERROR || state.status == TrudyVoiceUiStatus.UNAVAILABLE
    val title = when (state.status) {
        TrudyVoiceUiStatus.MODEL_NOT_INSTALLED -> "Local Trudy voice needs a one-time voice model download."
        TrudyVoiceUiStatus.DOWNLOADING -> "Installing local Trudy voice…"
        TrudyVoiceUiStatus.ERROR -> "Voice unavailable — retry"
        TrudyVoiceUiStatus.UNAVAILABLE -> "Local voice is unavailable in this build."
        else -> state.errorMessage ?: "Voice status"
    }
    val sizeText = state.modelInfo.approximateDownloadBytes?.let(::formatApproximateBytes)

    Surface(
        color = if (isError) TrudyErrorBg else TrudyCyanSoft,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 5.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = if (isError) TrudyErrorText else TrudyNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    when (state.status) {
                        TrudyVoiceUiStatus.MODEL_NOT_INSTALLED -> Text(
                            buildString {
                                append("The model stays on your device for offline playback.")
                                sizeText?.let { append(" Approximate download: ").append(it).append('.') }
                            }, color = TrudyMuted, fontSize = 9.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 3.dp)
                        )
                        TrudyVoiceUiStatus.ERROR, TrudyVoiceUiStatus.UNAVAILABLE -> state.errorMessage?.let {
                            Text(it, color = TrudyMuted, fontSize = 9.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                        else -> Unit
                    }
                }
                Box(
                    Modifier.size(40.dp).superhumanClickable(onClick = onDismiss)
                        .semantics { contentDescription = "Dismiss voice status" },
                    contentAlignment = Alignment.Center
                ) { Text("×", color = TrudyMuted, fontSize = 18.sp) }
            }
            if (state.status == TrudyVoiceUiStatus.DOWNLOADING) {
                Spacer(Modifier.height(7.dp))
                state.installProgressPercent?.let { percent ->
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Voice model installation $percent percent" }
                    )
                    Text("$percent%", color = TrudyMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
                } ?: Text("Downloading…", color = TrudyMuted, fontSize = 9.sp)
            } else {
                Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (state.status == TrudyVoiceUiStatus.MODEL_NOT_INSTALLED && state.modelInfo.canInstall) {
                        SmallVoiceAction("Install", "Install local Trudy voice model", onInstall)
                    }
                    if (state.status == TrudyVoiceUiStatus.ERROR) {
                        SmallVoiceAction("Retry", "Retry Trudy voice action", onRetry)
                    }
                }
            }
        }
    }
}

private fun formatApproximateBytes(bytes: Long): String {
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mib >= 100.0) "${mib.toInt()} MB" else String.format("%.1f MB", mib)
}

@Composable
private fun TrudyThinkingContent(activity: TrudyActivityStatus?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(TrudyCyan, CircleShape))
        Spacer(Modifier.size(8.dp))
        Text(activity?.label ?: "Trudy is thinking…", color = TrudyMuted, fontSize = 12.sp)
    }
}

@Composable
private fun TrudyErrorContent(message: TrudyMessage, onRetry: () -> Unit) {
    Column {
        Text(message.text, color = TrudyErrorText, fontSize = 12.sp, lineHeight = 17.sp)
        if (message.retryable) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.background(TrudyErrorBg, RoundedCornerShape(10.dp))
                    .border(1.dp, TrudyErrorText.copy(alpha = .28f), RoundedCornerShape(10.dp))
                    .superhumanClickable(onClick = onRetry)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) { Text("Retry", color = TrudyErrorText, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TrudyNoticeRow(notice: TrudyNotice) {
    val caution = notice.level == TrudyNoticeLevel.CAUTION
    Box(
        Modifier.widthIn(max = 350.dp).padding(top = 6.dp)
            .background(if (caution) TrudyWarningBg else superhumanSurfaceSoft, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            notice.text,
            color = if (caution) TrudyWarningText else TrudyMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun TrudyEvidenceBlock(
    evidence: List<TrudyEvidenceItem>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val usedEvidence = evidence.distinctBy(TrudyEvidenceItem::id)
    val groups = usedEvidence.groupBy { it.groupLabel ?: "Other" }
    Column(Modifier.widthIn(max = 350.dp).padding(top = 6.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .background(TrudyCyanSoft, RoundedCornerShape(13.dp))
                .border(1.dp, TrudyCyan.copy(alpha = .18f), RoundedCornerShape(13.dp))
                .superhumanClickable(onClick = onToggle)
                .semantics {
                    contentDescription = if (expanded) "Hide evidence used for this Trudy answer" else "View evidence used for this Trudy answer"
                }
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("EVIDENCE", color = TrudyCyan, fontSize = 6.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
                Text(
                    if (usedEvidence.size == 1) "1 record used" else "${usedEvidence.size} records used",
                    color = TrudyNavy,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(if (expanded) "HIDE  ↑" else "VIEW  ↓", color = TrudyNavy, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .45.sp)
        }
        if (expanded) {
            Spacer(Modifier.height(7.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                groups.forEach { (group, records) ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (groups.size > 1 || group != "Other") {
                            Text(
                                group,
                                color = TrudyMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 3.dp, top = 2.dp)
                            )
                        }
                        records.forEach { item ->
                            Box(
                                Modifier.fillMaxWidth().background(superhumanSurface, RoundedCornerShape(10.dp))
                                    .border(1.dp, TrudyBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Column {
                                    Text(item.label, color = TrudyNavy, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    item.detail?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = TrudyMuted, fontSize = 9.sp, lineHeight = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrudyComposer(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(color = superhumanSurface, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (enabled) "Ask a reading, trend, pattern, or why…" else "Waiting for Trudy…",
                        color = TrudyMuted,
                        fontSize = 12.sp
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Spacer(Modifier.size(10.dp))
            val canSend = enabled && value.isNotBlank()
            Box(
                Modifier.size(52.dp).background(if (canSend) TrudyCyan else superhumanSurfaceSoft, CircleShape)
                    .superhumanClickable(enabled = canSend, onClick = onSend)
                    .semantics {
                        contentDescription = "Send message to Trudy"
                        if (!canSend) disabled()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("↑", color = if (canSend) Color.White else TrudyMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
