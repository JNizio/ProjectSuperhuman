package com.projectsuperhuman.next

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BreathNavy get() = if (SuperhumanAppearance.darkMode) Color(0xFF8FC5FF) else Color(0xFF082D66)
private val BreathBlue get() = superhumanBlue
private val BreathMint get() = if (SuperhumanAppearance.darkMode) superhumanAccent else Color(0xFF55CDB8)
private val BreathInk get() = if (SuperhumanAppearance.darkMode) superhumanTextPrimary else Color(0xFF0B1F35)
private val BreathMuted get() = if (SuperhumanAppearance.darkMode) superhumanTextMuted else Color(0xFF64748B)
private val BreathBg get() = if (SuperhumanAppearance.darkMode) superhumanBackground else Color(0xFFF4F9FC)
private val BreathSurface get() = if (SuperhumanAppearance.darkMode) superhumanSurface else Color.White
private val BreathSafeSurface get() = if (SuperhumanAppearance.darkMode) Color(0xFF102F2B) else Color(0xFFE5F7F2)
private val BreathWarningSurface get() = if (SuperhumanAppearance.darkMode) superhumanWarningSurface else Color(0xFFFFF4E8)
private val BreathWarningText get() = if (SuperhumanAppearance.darkMode) Color(0xFFFFBD70) else Color(0xFFB66719)
private val BreathDisabled get() = if (SuperhumanAppearance.darkMode) Color(0xFF2A3A49) else Color(0xFFCBD5E1)
private val BreathHeroButtonText = Color(0xFF082D66)

private enum class GuidedBreathPhase {
    READY, BREATHING, RETENTION, RECOVERY_INHALE, RECOVERY_HOLD, COMPLETE
}

private fun createBreathworkPlayer(context: Context): MediaPlayer? {
    runCatching {
        context.assets.openFd("breathwork_soundtrack.mp3").use { afd ->
            return MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                isLooping = true
                setVolume(0.42f, 0.42f)
                prepare()
            }
        }
    }

    return runCatching {
        val cacheFile = java.io.File(context.cacheDir, "superhuman_breathwork_soundtrack_v2.mp3")
        if (!cacheFile.exists() || cacheFile.length() < 1_000_000L) {
            context.assets.open("breathwork_soundtrack.mp3").use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        check(cacheFile.length() > 1_000_000L) { "Breathwork soundtrack asset is missing or incomplete" }
        MediaPlayer().apply {
            setDataSource(cacheFile.absolutePath)
            isLooping = true
            setVolume(0.42f, 0.42f)
            prepare()
        }
    }.getOrNull()
}

@Composable
internal fun GuidedDeepBreathRoutine(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soundtrack = remember { createBreathworkPlayer(context) }

    var phase by remember { mutableStateOf(GuidedBreathPhase.READY) }
    var round by remember { mutableIntStateOf(1) }
    var breath by remember { mutableIntStateOf(1) }
    var inhale by remember { mutableStateOf(true) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var safePositionConfirmed by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var saved by remember { mutableStateOf(false) }

    DisposableEffect(soundtrack) {
        onDispose { runCatching { soundtrack?.release() } }
    }

    LaunchedEffect(running, phase) {
        val active = running && phase != GuidedBreathPhase.READY && phase != GuidedBreathPhase.COMPLETE
        runCatching {
            if (active) {
                if (soundtrack?.isPlaying != true) soundtrack?.start()
            } else if (soundtrack?.isPlaying == true) {
                soundtrack.pause()
            }
        }
    }

    fun reset() {
        phase = GuidedBreathPhase.READY
        round = 1
        breath = 1
        inhale = true
        secondsLeft = 0
        running = false
        startedAt = 0L
        saved = false
        runCatching { soundtrack?.pause(); soundtrack?.seekTo(0) }
    }

    fun start() {
        if (!safePositionConfirmed) return
        round = 1
        breath = 1
        inhale = true
        secondsLeft = 0
        phase = GuidedBreathPhase.BREATHING
        startedAt = System.currentTimeMillis()
        running = true
        saved = false
        runCatching { soundtrack?.seekTo(0); soundtrack?.start() }
    }

    fun nextAfterRecovery() {
        if (round < 3) {
            round += 1
            breath = 1
            inhale = true
            secondsLeft = 0
            phase = GuidedBreathPhase.BREATHING
        } else {
            phase = GuidedBreathPhase.COMPLETE
            running = false
        }
    }

    LaunchedEffect(phase, running, breath, inhale, secondsLeft, round) {
        if (!running) return@LaunchedEffect
        when (phase) {
            GuidedBreathPhase.BREATHING -> {
                delay(1500)
                if (inhale) {
                    inhale = false
                } else if (breath >= 30) {
                    secondsLeft = if (round == 1) 90 else 120
                    phase = GuidedBreathPhase.RETENTION
                } else {
                    breath += 1
                    inhale = true
                }
            }
            GuidedBreathPhase.RETENTION -> {
                delay(1000)
                if (secondsLeft > 1) secondsLeft -= 1
                else {
                    secondsLeft = 0
                    phase = GuidedBreathPhase.RECOVERY_INHALE
                }
            }
            GuidedBreathPhase.RECOVERY_INHALE -> {
                delay(2500)
                secondsLeft = 20
                phase = GuidedBreathPhase.RECOVERY_HOLD
            }
            GuidedBreathPhase.RECOVERY_HOLD -> {
                delay(1000)
                if (secondsLeft > 1) secondsLeft -= 1 else nextAfterRecovery()
            }
            else -> Unit
        }
    }

    LaunchedEffect(phase) {
        if (phase == GuidedBreathPhase.COMPLETE && !saved && startedAt > 0L) {
            saved = true
            val endedAt = System.currentTimeMillis()
            val mins = ((endedAt - startedAt) / 60000.0).coerceAtLeast(0.1)
            scope.launch {
                NativeDataHub.saveValues(
                    listOf(
                        HealthValue(
                            HealthDomain.MINDFULNESS,
                            "mindfulness_session_minutes",
                            mins,
                            "min",
                            endedAt,
                            "guided-deep-breath",
                            mapOf(
                                "mode" to "GUIDED_DEEP_BREATH",
                                "protocol" to "3-round custom deep-breath retention",
                                "breathsPerRound" to "30",
                                "round1RetentionSec" to "90",
                                "round2RetentionSec" to "120",
                                "round3RetentionSec" to "120",
                                "recoveryHoldSec" to "20",
                                "soundtrack" to if (soundtrack != null) "bundled" else "unavailable"
                            )
                        )
                    )
                )
            }
        }
    }

    val bubbleScaleTarget = when (phase) {
        GuidedBreathPhase.BREATHING -> if (inhale) 1.0f else 0.62f
        GuidedBreathPhase.RECOVERY_INHALE -> 1.0f
        GuidedBreathPhase.RETENTION, GuidedBreathPhase.RECOVERY_HOLD -> 0.78f
        GuidedBreathPhase.COMPLETE -> 0.84f
        GuidedBreathPhase.READY -> 0.78f
    }
    val bubbleScale by animateFloatAsState(
        targetValue = bubbleScaleTarget,
        animationSpec = tween(durationMillis = if (phase == GuidedBreathPhase.BREATHING) 1450 else 700),
        label = "breathing-bubble"
    )

    when (phase) {
        GuidedBreathPhase.READY -> BreathworkSetupScreen(
            soundtrackAvailable = soundtrack != null,
            safePositionConfirmed = safePositionConfirmed,
            onSafePositionToggle = { safePositionConfirmed = !safePositionConfirmed },
            onBack = onBack,
            onStart = { start() }
        )

        GuidedBreathPhase.COMPLETE -> BreathworkCompleteScreen(
            onBack = onBack,
            onAgain = { reset() }
        )

        else -> BreathworkSessionScreen(
            phase = phase,
            round = round,
            breath = breath,
            inhale = inhale,
            secondsLeft = secondsLeft,
            bubbleScale = bubbleScale,
            running = running,
            soundtrackAvailable = soundtrack != null,
            onTogglePause = { running = !running },
            onBreatheNow = { secondsLeft = 0; phase = GuidedBreathPhase.RECOVERY_INHALE },
            onEnd = { reset() }
        )
    }
}

@Composable
private fun BreathworkSetupScreen(
    soundtrackAvailable: Boolean,
    safePositionConfirmed: Boolean,
    onSafePositionToggle: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(BreathBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("←", color = BreathBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Guided breathwork", color = BreathInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("3 rounds · 30 breaths · timed retention", color = BreathMuted, fontSize = 10.sp)
            }
        }

        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF072B5D), Color(0xFF0C6591), Color(0xFF20A8B7))),
                RoundedCornerShape(28.dp)
            ).padding(vertical = 24.dp, horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("READY", color = Color.White.copy(alpha = .72f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(202.dp).background(
                            Brush.radialGradient(listOf(Color(0xFF8AF1DD), Color(0xFF44CDB9), Color(0xFF1684A8))),
                            CircleShape
                        )
                    )
                    Text("Begin", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Once started, the routine switches into a distraction-free full-screen breathing view.",
                    color = Color.White.copy(alpha = .78f), fontSize = 9.sp, lineHeight = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.width(270.dp)
                )
                if (!soundtrackAvailable) {
                    Spacer(Modifier.height(8.dp))
                    Text("Soundtrack unavailable in this build", color = Color.White.copy(alpha = .50f), fontSize = 7.sp)
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(BreathSurface, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Text("Your routine", color = BreathInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            RoutineLine("ROUND 1", "30 deep breaths → 1:30 retention → 1 deep breath → 0:20 hold")
            RoutineLine("ROUND 2", "30 deep breaths → 2:00 retention → 1 deep breath → 0:20 hold")
            RoutineLine("ROUND 3", "30 deep breaths → 2:00 retention → 1 deep breath → 0:20 hold")
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().background(if (safePositionConfirmed) BreathSafeSurface else BreathWarningSurface, RoundedCornerShape(15.dp))
                    .clickable { onSafePositionToggle() }.padding(13.dp)
            ) {
                Text(
                    if (safePositionConfirmed) "✓ I am seated or lying down in a safe place" else "Tap to confirm: I am seated or lying down in a safe place",
                    color = if (safePositionConfirmed) BreathMint else BreathWarningText, fontSize = 9.sp, fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                "Do not use this breathing routine in or near water, in the shower, while driving, standing, or anywhere a faint could cause injury.",
                color = BreathMuted, fontSize = 8.sp, lineHeight = 13.sp
            )
        }

        BreathAction("START ROUTINE", safePositionConfirmed) { onStart() }
        Text(
            "This is a custom guided routine based on the timings you chose, not a claim of an official or medically prescribed Wim Hof Method protocol.",
            color = BreathMuted, fontSize = 8.sp, lineHeight = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun BreathworkSessionScreen(
    phase: GuidedBreathPhase,
    round: Int,
    breath: Int,
    inhale: Boolean,
    secondsLeft: Int,
    bubbleScale: Float,
    running: Boolean,
    soundtrackAvailable: Boolean,
    onTogglePause: () -> Unit,
    onBreatheNow: () -> Unit,
    onEnd: () -> Unit
) {
    val phaseLabel = when (phase) {
        GuidedBreathPhase.BREATHING -> if (inhale) "BREATHE IN" else "LET GO"
        GuidedBreathPhase.RETENTION -> "RETENTION"
        GuidedBreathPhase.RECOVERY_INHALE -> "RECOVERY BREATH"
        GuidedBreathPhase.RECOVERY_HOLD -> "RECOVERY HOLD"
        else -> ""
    }
    val mainText = when (phase) {
        GuidedBreathPhase.BREATHING -> if (inhale) "Breathe in" else "Let go"
        GuidedBreathPhase.RETENTION, GuidedBreathPhase.RECOVERY_HOLD -> String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60)
        GuidedBreathPhase.RECOVERY_INHALE -> "Deep breath in"
        else -> ""
    }
    val helper = when (phase) {
        GuidedBreathPhase.BREATHING -> "Breath $breath of 30"
        GuidedBreathPhase.RETENTION -> if (round == 1) "Target 1:30 · breathe whenever you need air" else "Target 2:00 · breathe whenever you need air"
        GuidedBreathPhase.RECOVERY_INHALE -> "Take one full recovery breath"
        GuidedBreathPhase.RECOVERY_HOLD -> "20 second recovery hold"
        else -> ""
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF041F47), Color(0xFF07547B), Color(0xFF1496A6), Color(0xFF0B5578))
            )
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ROUND $round OF 3", color = Color.White.copy(alpha = .68f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(phaseLabel, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (index in 1..3) {
                        Box(
                            Modifier.size(if (index == round) 11.dp else 8.dp)
                                .background(if (index <= round) BreathMint else Color.White.copy(alpha = .24f), CircleShape)
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.65f))

            Box(Modifier.size(344.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size((320f * bubbleScale).dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFB6F9EC), Color(0xFF59DFC7), Color(0xFF1A9DB0), Color(0xFF0D6B91))
                            ),
                            CircleShape
                        )
                )
                Column(Modifier.width(235.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        mainText,
                        color = Color.White,
                        fontSize = if (phase == GuidedBreathPhase.RETENTION || phase == GuidedBreathPhase.RECOVERY_HOLD) 46.sp else 30.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    if (phase == GuidedBreathPhase.BREATHING) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (inhale) "deep and full" else "relaxed exhale",
                            color = Color.White.copy(alpha = .78f), fontSize = 11.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(helper, color = Color.White.copy(alpha = .80f), fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
            if (!soundtrackAvailable) {
                Spacer(Modifier.height(7.dp))
                Text("Soundtrack unavailable", color = Color.White.copy(alpha = .48f), fontSize = 8.sp)
            }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SessionAction(if (running) "PAUSE" else "RESUME", Modifier.weight(1f), false) { onTogglePause() }
                if (phase == GuidedBreathPhase.RETENTION) {
                    SessionAction("BREATHE NOW", Modifier.weight(1.35f), true) { onBreatheNow() }
                } else {
                    SessionAction("END", Modifier.weight(1f), false) { onEnd() }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Stay seated or lying down. Never practise breath retention in or near water.",
                color = Color.White.copy(alpha = .46f), fontSize = 7.sp, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BreathworkCompleteScreen(onBack: () -> Unit, onAgain: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF041F47), Color(0xFF0B6C86), Color(0xFF21A6A5)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(196.dp).background(
                    Brush.radialGradient(listOf(Color(0xFFB6F9EC), Color(0xFF55CDB8), Color(0xFF1684A8))),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(26.dp))
            Text("Session complete", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(7.dp))
            Text("3 rounds completed and saved to your mindfulness history.", color = Color.White.copy(alpha = .72f), fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(30.dp))
            SessionAction("DO ANOTHER SESSION", Modifier.fillMaxWidth(), true) { onAgain() }
            Spacer(Modifier.height(10.dp))
            SessionAction("BACK TO MINDFULNESS", Modifier.fillMaxWidth(), false) { onBack() }
        }
    }
}

@Composable
private fun RoutineLine(label: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = BreathBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(56.dp))
        Text(detail, color = BreathMuted, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BreathAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(if (enabled) BreathNavy else BreathDisabled, RoundedCornerShape(17.dp))
            .clickable(enabled = enabled) { onClick() }.padding(15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SessionAction(label: String, modifier: Modifier, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier.background(
            if (primary) Color.White else Color.White.copy(alpha = .12f),
            RoundedCornerShape(17.dp)
        ).clickable { onClick() }.padding(vertical = 15.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (primary) BreathHeroButtonText else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
    }
}
