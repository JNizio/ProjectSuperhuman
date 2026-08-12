package com.projectsuperhuman.next

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val BreathNavy = Color(0xFF082D66)
private val BreathBlue = Color(0xFF0D6CB4)
private val BreathCyan = Color(0xFF21A8C5)
private val BreathMint = Color(0xFF55CDB8)
private val BreathInk = Color(0xFF0B1F35)
private val BreathMuted = Color(0xFF64748B)
private val BreathBg = Color(0xFFF4F9FC)

private enum class GuidedBreathPhase {
    READY, BREATHING, RETENTION, RECOVERY_INHALE, RECOVERY_HOLD, COMPLETE
}

@Composable
internal fun GuidedDeepBreathRoutine(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(GuidedBreathPhase.READY) }
    var round by remember { mutableIntStateOf(1) }
    var breath by remember { mutableIntStateOf(1) }
    var inhale by remember { mutableStateOf(true) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var safePositionConfirmed by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var saved by remember { mutableStateOf(false) }

    fun reset() {
        phase = GuidedBreathPhase.READY
        round = 1
        breath = 1
        inhale = true
        secondsLeft = 0
        running = false
        startedAt = 0L
        saved = false
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
    }

    fun nextAfterRecovery() {
        if (round == 1) {
            round = 2
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
                                "protocol" to "2-round custom deep-breath retention",
                                "breathsPerRound" to "30",
                                "round1RetentionSec" to "90",
                                "round2RetentionSec" to "120",
                                "recoveryHoldSec" to "20"
                            )
                        )
                    )
                )
            }
        }
    }

    val bubbleScaleTarget = when (phase) {
        GuidedBreathPhase.BREATHING -> if (inhale) 1.0f else 0.58f
        GuidedBreathPhase.RECOVERY_INHALE -> 1.0f
        GuidedBreathPhase.RETENTION, GuidedBreathPhase.RECOVERY_HOLD -> 0.72f
        GuidedBreathPhase.COMPLETE -> 0.78f
        GuidedBreathPhase.READY -> 0.72f
    }
    val bubbleScale by animateFloatAsState(
        targetValue = bubbleScaleTarget,
        animationSpec = tween(durationMillis = if (phase == GuidedBreathPhase.BREATHING) 1450 else 700),
        label = "breathing-bubble"
    )

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
                Text("2 rounds · 30 breaths · timed retention", color = BreathMuted, fontSize = 10.sp)
            }
        }

        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF072B5D), Color(0xFF0C6591), Color(0xFF20A8B7))),
                RoundedCornerShape(28.dp)
            ).padding(vertical = 26.dp, horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (phase) {
                        GuidedBreathPhase.READY -> "READY"
                        GuidedBreathPhase.BREATHING -> "ROUND $round · BREATH $breath / 30"
                        GuidedBreathPhase.RETENTION -> "ROUND $round · RETENTION"
                        GuidedBreathPhase.RECOVERY_INHALE -> "RECOVERY BREATH"
                        GuidedBreathPhase.RECOVERY_HOLD -> "HOLD THE RECOVERY BREATH"
                        GuidedBreathPhase.COMPLETE -> "ROUTINE COMPLETE"
                    },
                    color = Color.White.copy(alpha = .72f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp
                )
                Spacer(Modifier.height(20.dp))
                Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size((190f * bubbleScale).dp)
                            .background(
                                Brush.radialGradient(listOf(Color(0xFF8AF1DD), Color(0xFF44CDB9), Color(0xFF1684A8))),
                                CircleShape
                            )
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val primary = when (phase) {
                            GuidedBreathPhase.READY -> "Begin"
                            GuidedBreathPhase.BREATHING -> if (inhale) "Breathe in" else "Let go"
                            GuidedBreathPhase.RETENTION, GuidedBreathPhase.RECOVERY_HOLD -> String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60)
                            GuidedBreathPhase.RECOVERY_INHALE -> "Deep breath in"
                            GuidedBreathPhase.COMPLETE -> "Done"
                        }
                        Text(primary, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        if (phase == GuidedBreathPhase.BREATHING) Text(if (inhale) "deep and full" else "relaxed exhale", color = Color.White.copy(alpha = .75f), fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.height(13.dp))
                Text(
                    when (phase) {
                        GuidedBreathPhase.READY -> "The bubble expands on the inhale and contracts on the relaxed exhale."
                        GuidedBreathPhase.BREATHING -> "Follow the bubble · no forced exhale"
                        GuidedBreathPhase.RETENTION -> if (round == 1) "Target 1:30 · stop early whenever you need air" else "Target 2:00 · stop early whenever you need air"
                        GuidedBreathPhase.RECOVERY_INHALE -> "Take one full recovery breath"
                        GuidedBreathPhase.RECOVERY_HOLD -> "20 second recovery hold"
                        GuidedBreathPhase.COMPLETE -> "Two rounds saved to your mindfulness history"
                    },
                    color = Color.White.copy(alpha = .78f), fontSize = 9.sp, textAlign = TextAlign.Center
                )
            }
        }

        if (phase == GuidedBreathPhase.READY) {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
                Text("Your routine", color = BreathInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                RoutineLine("ROUND 1", "30 deep breaths → 1:30 retention → 1 deep breath → 0:20 hold")
                RoutineLine("ROUND 2", "30 deep breaths → 2:00 retention → 1 deep breath → 0:20 hold")
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().background(if (safePositionConfirmed) Color(0xFFE5F7F2) else Color(0xFFFFF4E8), RoundedCornerShape(15.dp))
                        .clickable { safePositionConfirmed = !safePositionConfirmed }.padding(13.dp)
                ) {
                    Text(
                        if (safePositionConfirmed) "✓ I am seated or lying down in a safe place" else "Tap to confirm: I am seated or lying down in a safe place",
                        color = if (safePositionConfirmed) BreathMint else Color(0xFFB66719), fontSize = 9.sp, fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text("Do not use this breathing routine in or near water, in the shower, while driving, standing, or anywhere a faint could cause injury.", color = BreathMuted, fontSize = 8.sp, lineHeight = 13.sp)
            }
        }

        when {
            phase == GuidedBreathPhase.READY -> BreathAction("START ROUTINE", safePositionConfirmed) { start() }
            phase == GuidedBreathPhase.COMPLETE -> {
                BreathAction("DO ANOTHER SESSION", true) { reset() }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    BreathSmallAction(if (running) "PAUSE" else "RESUME", Modifier.weight(1f)) { running = !running }
                    if (phase == GuidedBreathPhase.RETENTION) {
                        BreathSmallAction("BREATHE NOW", Modifier.weight(1f)) { secondsLeft = 0; phase = GuidedBreathPhase.RECOVERY_INHALE }
                    } else {
                        BreathSmallAction("END", Modifier.weight(1f)) { reset() }
                    }
                }
            }
        }

        Text("This is a custom guided routine based on the timings you chose, not a claim of an official or medically prescribed Wim Hof Method protocol.", color = BreathMuted, fontSize = 8.sp, lineHeight = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(18.dp))
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
        Modifier.fillMaxWidth().background(if (enabled) BreathNavy else Color(0xFFCBD5E1), RoundedCornerShape(17.dp))
            .clickable(enabled = enabled) { onClick() }.padding(15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun BreathSmallAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.background(Color.White, RoundedCornerShape(16.dp)).clickable { onClick() }.padding(14.dp), contentAlignment = Alignment.Center) {
        Text(label, color = BreathBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}
