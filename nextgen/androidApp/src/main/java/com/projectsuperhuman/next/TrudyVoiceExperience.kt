package com.projectsuperhuman.next

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val VoiceNavy get() = superhumanBrandText
private val VoiceMuted get() = superhumanTextMuted
private val VoiceCyan get() = superhumanAccent
private val VoiceAqua = Color(0xFF82EBE3)
private val VoiceBlue = Color(0xFF4DAFE8)
private val VoiceBorder get() = superhumanBorder

internal enum class TrudyVoiceExperienceState(
    val label: String,
    val guidance: String,
    val energy: Float,
    val tension: Float
) {
    IDLE("Ready", "Tap the microphone when you want to talk", .12f, .04f),
    LISTENING("Listening…", "Speak naturally", .58f, .08f),
    THINKING("Thinking…", "Trudy is preparing a response", .34f, .42f),
    SPEAKING("Speaking", "Trudy is responding", .66f, .16f),
    NO_INPUT("No input heard", "Tap the microphone and try again", .10f, .10f),
    ERROR("Voice needs attention", "You can continue in text chat", .08f, .22f)
}

internal enum class TrudyVoiceContextModule(val label: String, val keywords: Set<String>) {
    SLEEP("Sleep", setOf("sleep", "asleep", "bedtime")),
    VITALS("Vitals", setOf("vitals", "heart rate", "blood pressure", "oxygen saturation", "spo2")),
    ENVIRONMENT("Environment", setOf("environment", "weather", "air quality", "humidity", "uv index")),
    EMOTIONAL("Emotional", setOf("emotional", "mood", "stress", "anxiety", "calmness")),
    EXERCISE("Exercise", setOf("exercise", "workout", "training", "step count", "active minutes")),
    NUTRITION("Nutrition", setOf("nutrition", "meal", "food", "protein", "calorie intake")),
    HYDRATION("Hydration", setOf("hydration", "water intake"))
}

internal object TrudyVoiceContextMapper {
    fun modulesFor(reply: TrudyReply): List<TrudyVoiceContextModule> {
        val explicitRuntimeContext = buildString {
            reply.activity?.label?.let { append(it).append(' ') }
            reply.evidence.forEach { item ->
                append(item.id).append(' ')
                append(item.label).append(' ')
                item.detail?.let { append(it).append(' ') }
            }
        }.lowercase()
        if (explicitRuntimeContext.isBlank()) return emptyList()
        return TrudyVoiceContextModule.entries
            .filter { module -> module.keywords.any(explicitRuntimeContext::contains) }
            .take(MAX_CONTEXT_LABELS)
    }

    private const val MAX_CONTEXT_LABELS = 4
}

internal fun resolveTrudyVoiceExperienceState(
    inputStatus: TrudySpeechInputStatus,
    conversationThinking: Boolean,
    conversationError: Boolean,
    outputStatus: TrudyVoiceUiStatus
): TrudyVoiceExperienceState = when {
    outputStatus == TrudyVoiceUiStatus.SPEAKING -> TrudyVoiceExperienceState.SPEAKING
    conversationThinking || inputStatus == TrudySpeechInputStatus.PROCESSING ||
        outputStatus == TrudyVoiceUiStatus.LOADING || outputStatus == TrudyVoiceUiStatus.SYNTHESIZING ->
        TrudyVoiceExperienceState.THINKING
    inputStatus == TrudySpeechInputStatus.LISTENING -> TrudyVoiceExperienceState.LISTENING
    inputStatus == TrudySpeechInputStatus.NO_INPUT -> TrudyVoiceExperienceState.NO_INPUT
    conversationError || inputStatus.isVoiceInputFailure() || outputStatus.isVoiceOutputFailure() ->
        TrudyVoiceExperienceState.ERROR
    else -> TrudyVoiceExperienceState.IDLE
}

private fun TrudySpeechInputStatus.isVoiceInputFailure(): Boolean = when (this) {
    TrudySpeechInputStatus.PERMISSION_REQUIRED,
    TrudySpeechInputStatus.UNAVAILABLE,
    TrudySpeechInputStatus.ERROR -> true
    else -> false
}

private fun TrudyVoiceUiStatus.isVoiceOutputFailure(): Boolean = when (this) {
    TrudyVoiceUiStatus.UNAVAILABLE,
    TrudyVoiceUiStatus.MODEL_NOT_INSTALLED,
    TrudyVoiceUiStatus.ERROR -> true
    else -> false
}

@Composable
internal fun TrudyVoiceModeScreen(
    conversationState: TrudyUiState,
    voiceState: TrudyVoiceUiState,
    speechInputState: TrudySpeechInputState,
    speechInputController: TrudySpeechInputController,
    contextModules: List<TrudyVoiceContextModule>,
    settingsExpanded: Boolean,
    onToggleSettings: () -> Unit,
    onKeyboardMode: (String) -> Unit,
    onEndSession: () -> Unit,
    onStopSpeaking: () -> Unit,
    settingsContent: @Composable () -> Unit,
    promptContent: @Composable () -> Unit
) {
    val experienceState = resolveTrudyVoiceExperienceState(
        inputStatus = speechInputState.status,
        conversationThinking = conversationState.isThinking,
        conversationError = conversationState.errorMessage != null,
        outputStatus = voiceState.status
    )
    var microphoneEnabled by remember { mutableStateOf(true) }
    var previousOutputStatus by remember { mutableStateOf(voiceState.status) }
    val background = if (SuperhumanAppearance.darkMode) {
        Brush.verticalGradient(listOf(Color(0xFF07111B), Color(0xFF0B1D27), Color(0xFF07111B)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF8FDFE), Color(0xFFECF9FB), Color(0xFFF8FBFD)))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) speechInputController.startListening()
        else speechInputController.onPermissionDenied()
    }

    fun beginListening() {
        if (speechInputController.hasRecordAudioPermission()) {
            speechInputController.startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (microphoneEnabled) beginListening()
    }
    LaunchedEffect(voiceState.status) {
        val speechFinished = previousOutputStatus == TrudyVoiceUiStatus.SPEAKING &&
            voiceState.status == TrudyVoiceUiStatus.READY
        previousOutputStatus = voiceState.status
        if (speechFinished && microphoneEnabled) beginListening()
    }

    Box(Modifier.fillMaxSize().background(background)) {
        Column(Modifier.fillMaxSize()) {
            VoiceModeHeader(
                state = experienceState,
                voiceEnabled = voiceState.preferences.enabled,
                onBackToChat = { onKeyboardMode(speechInputState.partialTranscript) },
                onSettings = {
                    if (!settingsExpanded) {
                        microphoneEnabled = false
                        speechInputController.cancel()
                    }
                    onToggleSettings()
                }
            )

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TrudyOrganicOrb(
                    modifier = Modifier.weight(1f),
                    state = experienceState,
                    inputAmplitude = speechInputState.amplitude,
                    modules = contextModules
                )
                Spacer(Modifier.height(18.dp))
                VoiceTranscriptCard(
                    text = currentVoiceTranscript(conversationState, voiceState, speechInputState, experienceState),
                    state = experienceState
                )
            }

            VoiceModeControls(
                listening = speechInputState.status == TrudySpeechInputStatus.LISTENING,
                microphoneEnabled = microphoneEnabled,
                thinking = conversationState.isThinking,
                onMicrophone = {
                    when {
                        conversationState.isThinking -> Unit
                        speechInputState.status == TrudySpeechInputStatus.LISTENING -> {
                            microphoneEnabled = false
                            speechInputController.cancel()
                        }
                        else -> {
                            microphoneEnabled = true
                            if (voiceState.status == TrudyVoiceUiStatus.SPEAKING) onStopSpeaking()
                            beginListening()
                        }
                    }
                },
                onKeyboard = { onKeyboardMode(speechInputState.partialTranscript) },
                onEnd = onEndSession
            )
        }

        AnimatedVisibility(
            visible = settingsExpanded,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp).zIndex(2f),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140))
        ) { settingsContent() }

        AnimatedVisibility(
            visible = voiceState.promptVisible && !settingsExpanded,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp).zIndex(1f),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180))
        ) { promptContent() }
    }
}

@Composable
private fun VoiceModeHeader(
    state: TrudyVoiceExperienceState,
    voiceEnabled: Boolean,
    onBackToChat: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.superhumanTopButton(onClick = onBackToChat)
                .semantics { contentDescription = "Return to Trudy text chat" },
            contentAlignment = Alignment.Center
        ) {
            Text("‹", color = VoiceNavy, fontSize = 29.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text("TRUDY", color = VoiceNavy, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.4.sp)
            Text(state.label, color = if (state == TrudyVoiceExperienceState.ERROR) VoiceMuted else VoiceCyan, fontSize = 11.sp)
        }
        Box(
            Modifier.superhumanTopButton(onClick = onSettings)
                .semantics { contentDescription = "Trudy voice settings, ${if (voiceEnabled) "voice on" else "voice off"}" },
            contentAlignment = Alignment.Center
        ) {
            Text("⋯", color = VoiceNavy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TrudyOrganicOrb(
    modifier: Modifier = Modifier,
    state: TrudyVoiceExperienceState,
    inputAmplitude: Float,
    modules: List<TrudyVoiceContextModule>
) {
    val path = remember { Path() }
    val pointX = remember { FloatArray(8) }
    val pointY = remember { FloatArray(8) }
    val transition = rememberInfiniteTransition(label = "Trudy orb motion")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Trudy orb phase"
    )
    val energy = animateFloatAsState(
        targetValue = state.energy,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "Trudy orb energy"
    )
    val tension = animateFloatAsState(
        targetValue = state.tension,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "Trudy orb tension"
    )
    val amplitude = animateFloatAsState(
        targetValue = if (state == TrudyVoiceExperienceState.LISTENING) inputAmplitude.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(90, easing = LinearEasing),
        label = "Microphone response"
    )

    BoxWithConstraints(
        modifier.fillMaxWidth()
            .semantics {
                contentDescription = "Trudy voice is ${state.label.lowercase()}. ${state.guidance}"
            },
        contentAlignment = Alignment.Center
    ) {
        val orbSize = minOf(238.dp, maxWidth, maxHeight)
        Box(
            Modifier.size(orbSize)
                .graphicsLayer {
                    val wave = sin(phase.value * TWO_PI).toFloat()
                    val liveScale = 1f + energy.value * .018f * wave + amplitude.value * .075f
                    scaleX = liveScale
                    scaleY = liveScale + tension.value * .008f * cos(phase.value * TWO_PI).toFloat()
                    translationY = if (state == TrudyVoiceExperienceState.IDLE) wave * 3.2f else wave * 1.4f
                }
                .drawWithCache {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = (min(size.width, size.height) * .39f).coerceAtLeast(1f)
                    val coreBrush = Brush.radialGradient(
                        colors = listOf(Color(0xFFD8FFFA), VoiceAqua, VoiceCyan, VoiceBlue),
                        center = Offset(size.width * .37f, size.height * .30f),
                        radius = radius * 1.65f
                    )
                    val rippleStroke = Stroke(width = 1.5f)
                    onDrawBehind {
                        val phaseRadians = phase.value * TWO_PI
                        val pulse = sin(phaseRadians).toFloat()
                        val deformation = .022f + tension.value * .09f + amplitude.value * .045f
                        val baseRadius = radius * (1f + energy.value * .018f * pulse)

                        drawCircle(
                            color = VoiceCyan.copy(alpha = .08f + energy.value * .06f),
                            radius = baseRadius * (1.22f + amplitude.value * .12f)
                        )
                        if (state == TrudyVoiceExperienceState.LISTENING || state == TrudyVoiceExperienceState.SPEAKING) {
                            val ripple = ((phase.value * 1.7f) % 1f)
                            drawCircle(
                                color = VoiceBlue.copy(alpha = (1f - ripple) * .13f),
                                radius = baseRadius * (1.05f + ripple * .34f),
                                style = rippleStroke
                            )
                        }

                        for (index in 0 until 8) {
                            val angle = -PI / 2.0 + index * TWO_PI / 8.0
                            val harmonic = sin(phaseRadians * (1.0 + tension.value) + index * 1.73).toFloat()
                            val speakingWave = if (state == TrudyVoiceExperienceState.SPEAKING) {
                                cos(phaseRadians * 2.0 + index).toFloat() * .025f
                            } else 0f
                            val localRadius = baseRadius * (1f + deformation * harmonic + speakingWave)
                            pointX[index] = center.x + cos(angle).toFloat() * localRadius
                            pointY[index] = center.y + sin(angle).toFloat() * localRadius
                        }

                        path.reset()
                        path.moveTo(
                            (pointX[7] + pointX[0]) / 2f,
                            (pointY[7] + pointY[0]) / 2f
                        )
                        for (index in 0 until 8) {
                            val next = (index + 1) % 8
                            path.quadraticBezierTo(
                                pointX[index],
                                pointY[index],
                                (pointX[index] + pointX[next]) / 2f,
                                (pointY[index] + pointY[next]) / 2f
                            )
                        }
                        path.close()
                        drawPath(path, coreBrush)

                        val lightTravel = if (state == TrudyVoiceExperienceState.THINKING) .10f else .045f
                        drawCircle(
                            color = Color.White.copy(alpha = .22f),
                            radius = baseRadius * .32f,
                            center = Offset(
                                center.x - baseRadius * (.28f + sin(phaseRadians).toFloat() * lightTravel),
                                center.y - baseRadius * (.30f + cos(phaseRadians).toFloat() * lightTravel)
                            )
                        )
                    }
                }
        )

        AnimatedVisibility(
            visible = modules.isNotEmpty(),
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(360)),
            exit = fadeOut(tween(260))
        ) {
            Box(Modifier.fillMaxSize()) {
                modules.forEachIndexed { index, module ->
                    if (index < CONTEXT_ALIGNMENTS.size) {
                        VoiceContextChip(
                            label = module.label,
                            modifier = Modifier.align(CONTEXT_ALIGNMENTS[index]).padding(CONTEXT_PADDINGS[index])
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceContextChip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier.background(superhumanSurface.copy(alpha = .90f), RoundedCornerShape(14.dp))
            .border(1.dp, VoiceBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .semantics { contentDescription = "$label context used" }
    ) {
        Text(label, color = VoiceNavy, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VoiceTranscriptCard(text: String, state: TrudyVoiceExperienceState) {
    Column(
        Modifier.fillMaxWidth().widthIn(max = 430.dp)
            .background(superhumanSurface.copy(alpha = .86f), RoundedCornerShape(20.dp))
            .border(1.dp, VoiceBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = if (text == state.guidance) VoiceMuted else VoiceNavy,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = if (text == state.guidance) FontWeight.Normal else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 4
        )
        Spacer(Modifier.height(5.dp))
        Text(state.guidance, color = VoiceMuted, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VoiceModeControls(
    listening: Boolean,
    microphoneEnabled: Boolean,
    thinking: Boolean,
    onMicrophone: () -> Unit,
    onKeyboard: () -> Unit,
    onEnd: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VoiceControlButton(
            symbol = if (listening) "■" else "●",
            label = when {
                thinking -> "Thinking"
                listening -> "Mute"
                microphoneEnabled -> "Mic"
                else -> "Unmute"
            },
            description = when {
                thinking -> "Microphone unavailable while Trudy is thinking"
                listening -> "Mute microphone"
                else -> "Start microphone"
            },
            prominent = true,
            enabled = !thinking,
            onClick = onMicrophone
        )
        VoiceControlButton("⌨", "Chat", "Return to keyboard chat", onClick = onKeyboard)
        VoiceControlButton("×", "End", "End Trudy voice session", onClick = onEnd)
    }
}

@Composable
private fun VoiceControlButton(
    symbol: String,
    label: String,
    description: String,
    prominent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(if (prominent) 64.dp else 56.dp)
                .background(
                    color = when {
                        !enabled -> superhumanSurface.copy(alpha = .54f)
                        prominent -> VoiceCyan
                        else -> superhumanSurface.copy(alpha = .90f)
                    },
                    shape = CircleShape
                )
                .border(1.dp, if (prominent && enabled) VoiceCyan else VoiceBorder, CircleShape)
                .superhumanClickable(enabled = enabled, onClick = onClick)
                .semantics {
                    contentDescription = description
                    if (!enabled) disabled()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                symbol,
                color = if (prominent && enabled) Color.White else VoiceNavy,
                fontSize = if (symbol == "×") 25.sp else 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = VoiceMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun currentVoiceTranscript(
    conversationState: TrudyUiState,
    voiceState: TrudyVoiceUiState,
    inputState: TrudySpeechInputState,
    experienceState: TrudyVoiceExperienceState
): String {
    val candidate = when (experienceState) {
        TrudyVoiceExperienceState.LISTENING -> inputState.partialTranscript
        TrudyVoiceExperienceState.THINKING -> conversationState.messages.lastOrNull {
            it.role == TrudyMessageRole.USER && it.text.isNotBlank()
        }?.text
        TrudyVoiceExperienceState.SPEAKING -> conversationState.messages.lastOrNull {
            it.role == TrudyMessageRole.TRUDY && it.status == TrudyMessageStatus.COMPLETE && it.text.isNotBlank()
        }?.text
        TrudyVoiceExperienceState.NO_INPUT -> inputState.message
        TrudyVoiceExperienceState.ERROR -> inputState.message
            ?: conversationState.errorMessage
            ?: voiceState.errorMessage
        TrudyVoiceExperienceState.IDLE -> null
    }
    return candidate?.trim()?.takeIf(String::isNotBlank)?.ellipsize(240) ?: experienceState.guidance
}

private fun String.ellipsize(maxLength: Int): String =
    if (length <= maxLength) this else take(maxLength - 1).trimEnd() + "…"

private const val TWO_PI = PI * 2.0
private val CONTEXT_ALIGNMENTS = listOf(
    Alignment.TopStart,
    Alignment.TopEnd,
    Alignment.BottomStart,
    Alignment.BottomEnd
)
private val CONTEXT_PADDINGS = listOf(
    androidx.compose.foundation.layout.PaddingValues(start = 10.dp, top = 24.dp),
    androidx.compose.foundation.layout.PaddingValues(end = 10.dp, top = 46.dp),
    androidx.compose.foundation.layout.PaddingValues(start = 18.dp, bottom = 38.dp),
    androidx.compose.foundation.layout.PaddingValues(end = 16.dp, bottom = 20.dp)
)
