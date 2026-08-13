package com.projectsuperhuman.next

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.m1x.HealthBridge

private val ShellNavy = Color(0xFF123D70)
private val ShellBg = Color(0xFFF8FBFD)
private val ShellMuted = Color(0xFF748294)
private val ShellTrudy = Color(0xFF1CC8C8)

class NextShellActivity : ComponentActivity() {
    private lateinit var trudyVoiceController: TrudyVoiceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NativeDataHub.initialize(this)
        EnvironmentalUiRuntime.installSource(AndroidEnvironmentalSource(this))
        MiniMetricsBackgroundSync.ensureScheduled(this)
        val trudyRuntime = TrudyRuntimeFactory.create()
        trudyVoiceController = AndroidTrudyVoiceControllerFactory.create(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                SuperhumanShell(
                    openCompatibility = { startActivity(Intent(this, HealthBridge::class.java)) },
                    trudyController = trudyRuntime.controller,
                    trudyVoiceController = trudyVoiceController
                )
            }
        }
    }

    override fun onStop() {
        if (::trudyVoiceController.isInitialized) trudyVoiceController.onTrudyHidden()
        super.onStop()
    }

    override fun onDestroy() {
        if (::trudyVoiceController.isInitialized) trudyVoiceController.close()
        super.onDestroy()
    }
}

private enum class ShellPage {
    HOME, SETTINGS, TRUDY, CLINICAL, BODY, SLEEP, EMOTIONAL, ENVIRONMENT, BLOOD_PRESSURE, HYDRATION, NUTRITION, EXERCISE, MINDFULNESS, BREATHWORK, HEART_RATE, STEPS, BLOOD_OXYGEN, CALORIES
}

@Composable
private fun SuperhumanShell(
    openCompatibility: () -> Unit,
    trudyController: TrudyConversationController,
    trudyVoiceController: TrudyVoiceController
) {
    var page by remember { mutableStateOf(ShellPage.HOME) }
    val noCompatibility: () -> Unit = {}
    val hasPersistentTopBar = page == ShellPage.SETTINGS
    val trudyState = remember { TrudyConversationState() }

    HomeNavigationBridge.openBreathwork = { page = ShellPage.BREATHWORK }

    Surface(color = ShellBg, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxSize().padding(top = if (hasPersistentTopBar) 92.dp else 0.dp)) {
                when (page) {
                    ShellPage.HOME -> NativeLiveHome(
                        openClinical = { page = ShellPage.CLINICAL },
                        openBody = { page = ShellPage.BODY },
                        openSleep = { page = ShellPage.SLEEP },
                        openBloodPressure = { page = ShellPage.BLOOD_PRESSURE },
                        openHydration = { page = ShellPage.HYDRATION },
                        openNutrition = { page = ShellPage.NUTRITION },
                        openExercise = { page = ShellPage.EXERCISE },
                        openMindfulness = { page = ShellPage.MINDFULNESS },
                        openEnvironment = { page = ShellPage.ENVIRONMENT },
                        openEmotional = { page = ShellPage.EMOTIONAL },
                        openMiniMetric = { metric ->
                            page = when (metric) {
                                HomeMiniMetric.HEART_RATE -> ShellPage.HEART_RATE
                                HomeMiniMetric.STEPS -> ShellPage.STEPS
                                HomeMiniMetric.BLOOD_OXYGEN -> ShellPage.BLOOD_OXYGEN
                                HomeMiniMetric.CALORIES -> ShellPage.CALORIES
                            }
                        },
                        topContent = {
                            NativeTopBar(
                                title = "PROJECT SUPERHUMAN",
                                onSettings = { page = ShellPage.SETTINGS },
                                onTrudy = { page = ShellPage.TRUDY }
                            )
                        }
                    )
                    ShellPage.SETTINGS -> NativeSettingsParity(noCompatibility)
                    ShellPage.TRUDY -> NativeTrudy(
                        state = trudyState,
                        controller = trudyController,
                        voiceController = trudyVoiceController,
                        onBack = { page = ShellPage.HOME }
                    )
                    ShellPage.CLINICAL -> NativeClinicalPage({ page = ShellPage.HOME }, openCompatibility)
                    ShellPage.BODY -> NativeBodyPage({ page = ShellPage.HOME }, openCompatibility)
                    ShellPage.SLEEP -> UnifiedSleepPage({ page = ShellPage.HOME }, openCompatibility)
                    ShellPage.EMOTIONAL -> NativeEmotionalPage { page = ShellPage.HOME }
                    ShellPage.ENVIRONMENT -> NativeEnvironmentalPage { page = ShellPage.HOME }
                    ShellPage.BLOOD_PRESSURE -> NativeBloodPressurePage({ page = ShellPage.HOME }, openCompatibility)
                    ShellPage.HYDRATION -> NativeHydrationScreen { page = ShellPage.HOME }
                    ShellPage.NUTRITION -> NativeNutritionWithFoodEditorPage { page = ShellPage.HOME }
                    ShellPage.EXERCISE -> NativeExercisePage({ page = ShellPage.HOME }, openCompatibility)
                    ShellPage.MINDFULNESS -> NativeMindfulnessPage({ page = ShellPage.HOME }, openCompatibility)
                    ShellPage.BREATHWORK -> GuidedDeepBreathRoutine { page = ShellPage.HOME }
                    ShellPage.HEART_RATE -> UnifiedMiniMetricPage(HomeMiniMetric.HEART_RATE) { page = ShellPage.HOME }
                    ShellPage.STEPS -> UnifiedMiniMetricPage(HomeMiniMetric.STEPS) { page = ShellPage.HOME }
                    ShellPage.BLOOD_OXYGEN -> UnifiedMiniMetricPage(HomeMiniMetric.BLOOD_OXYGEN) { page = ShellPage.HOME }
                    ShellPage.CALORIES -> UnifiedMiniMetricPage(HomeMiniMetric.CALORIES) { page = ShellPage.HOME }
                }
            }

            if (hasPersistentTopBar) {
                NativeTopBar(title = "SETTINGS", onSettings = { page = ShellPage.HOME }, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun NativeTopBar(
    title: String,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onTrudy: (() -> Unit)? = null
) {
    Box(modifier.fillMaxWidth().height(92.dp).padding(horizontal = 22.dp)) {
        Box(Modifier.width(54.dp).height(54.dp).align(Alignment.CenterStart), contentAlignment = Alignment.Center) {
            if (title == "PROJECT SUPERHUMAN") {
                Image(
                    painter = painterResource(id = R.drawable.superhuman_logo_foreground),
                    contentDescription = "Project Superhuman",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("PS", color = ShellNavy, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = .5.sp)
            }
        }

        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = ShellNavy, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.8.sp, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(if (title == "PROJECT SUPERHUMAN") "Human performance system" else "App & data controls", color = ShellMuted, fontSize = 11.sp, maxLines = 1)
        }

        Row(
            Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            onTrudy?.let { openTrudy ->
                Box(Modifier.superhumanTopButton(onClick = openTrudy), contentAlignment = Alignment.Center) {
                    Text("T", color = ShellTrudy, fontSize = 17.sp, fontWeight = FontWeight.Black)
                }
            }
            Box(Modifier.superhumanTopButton(onClick = onSettings), contentAlignment = Alignment.Center) {
                Text(if (title == "SETTINGS") "×" else "⚙", color = ShellNavy, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
