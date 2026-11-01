package com.projectsuperhuman.next

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.m1x.HealthBridge

private val ShellNavy get() = superhumanBrandText
private val ShellBg get() = superhumanBackground
private val ShellMuted get() = superhumanTextMuted
private val ShellTrudy get() = superhumanAccent

class NextShellActivity : ComponentActivity() {
    private lateinit var trudyVoiceController: TrudyVoiceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SuperhumanAppearance.initialize(this)
        NativeDataHub.initialize(this)
        SmartDeviceRuntime.initialize(this)
        EnvironmentalUiRuntime.installSource(AndroidEnvironmentalSource(this))
        MiniMetricsBackgroundSync.ensureScheduled(this)
        val trudyRuntime = TrudyRuntimeFactory.create(appContext = applicationContext)
        trudyVoiceController = AndroidTrudyVoiceControllerFactory.create(this)
        enableEdgeToEdge()
        setContent {
            /*
             * Resolve ProjectSuperhumanTheme first. This makes isSystemInDarkTheme() the source of
             * truth before the shell, custom semantic colors and Android system bars are composed.
             */
            ProjectSuperhumanTheme {
                val darkMode = SuperhumanAppearance.darkMode
                SideEffect {
                    val transparent = AndroidColor.TRANSPARENT
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(transparent, transparent) { darkMode },
                        navigationBarStyle = SystemBarStyle.auto(transparent, transparent) { darkMode }
                    )
                }
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
    HOME, INSIGHTS, EXPERIMENTS, SETTINGS, SMART_DEVICES, TRUDY, CLINICAL, BODY, SLEEP, EMOTIONAL, ENVIRONMENT, VITALS, HYDRATION, NUTRITION, EXERCISE, MINDFULNESS, BREATHWORK, HEART_RATE, STEPS, BLOOD_OXYGEN, CALORIES
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
    val bottomBarHeight = 76.dp
    val trudyState = remember { TrudyConversationState() }

    HomeNavigationBridge.openBreathwork = { page = ShellPage.BREATHWORK }
    SmartDevicesNavigationBridge.open = { page = ShellPage.SMART_DEVICES }

    Surface(color = ShellBg, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (hasPersistentTopBar) 92.dp else 0.dp,
                        bottom = bottomBarHeight
                    )
            ) {
                when (page) {
                    ShellPage.HOME -> NativeLiveHome(
                        openClinical = { page = ShellPage.CLINICAL },
                        openSleep = { page = ShellPage.SLEEP },
                        openVitals = { page = ShellPage.VITALS },
                        openHydration = { page = ShellPage.HYDRATION },
                        openNutrition = { page = ShellPage.NUTRITION },
                        openExercise = { page = ShellPage.EXERCISE },
                        openMindfulness = { page = ShellPage.MINDFULNESS },
                        openEnvironment = { page = ShellPage.ENVIRONMENT },
                        openEmotional = { page = ShellPage.EMOTIONAL },
                        openInsights = { page = ShellPage.INSIGHTS },
                        openExperiments = { page = ShellPage.EXPERIMENTS },
                        openMiniMetric = { metric ->
                            page = when (metric) {
                                HomeMiniMetric.HEART_RATE -> ShellPage.HEART_RATE
                                HomeMiniMetric.STEPS -> ShellPage.STEPS
                                HomeMiniMetric.BLOOD_OXYGEN -> ShellPage.BLOOD_OXYGEN
                                HomeMiniMetric.CALORIES -> ShellPage.CALORIES
                            }
                        },
                        topContent = {
                            NativeHomeTitle()
                        }
                    )
                    ShellPage.INSIGHTS -> NativeInsightsPage(onBack = { page = ShellPage.HOME })
                    ShellPage.EXPERIMENTS -> NativeExperimentsPage { page = ShellPage.HOME }
                    ShellPage.SETTINGS -> NativeSettingsWithUsage(noCompatibility) { page = ShellPage.SMART_DEVICES }
                    ShellPage.SMART_DEVICES -> SmartDevicesHub(onBack = { page = ShellPage.SETTINGS })
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
                    ShellPage.VITALS -> NativeVitalsPage { page = ShellPage.HOME }
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
                NativeTopBar(
                    title = "SETTINGS",
                    onSettings = { page = ShellPage.HOME },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            ShellBottomNavigation(
                selectedPage = page,
                onHome = { page = ShellPage.HOME },
                onBody = { page = ShellPage.BODY },
                onTrudy = { page = ShellPage.TRUDY },
                onSettings = { page = ShellPage.SETTINGS },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
        Column(
            Modifier.align(Alignment.CenterStart)
                .padding(end = if (onTrudy != null) 124.dp else 68.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                title,
                color = ShellNavy,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 1.65.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Text(
                if (title == "PROJECT SUPERHUMAN") "Human performance system" else "App & data controls",
                color = ShellMuted,
                fontSize = 11.sp,
                maxLines = 1
            )
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


@Composable
private fun NativeHomeTitle() {
    val date = java.time.LocalDate.now().format(
        java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.ENGLISH)
    ).uppercase(java.util.Locale.ENGLISH)
    val greeting = when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good morning,"
        in 12..16 -> "Good afternoon,"
        else -> "Good evening,"
    }
    Row(
        Modifier.fillMaxWidth().padding(
            horizontal = SuperhumanLayout.pageHorizontal,
            vertical = 23.dp
        ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(date, color = ShellMuted, fontSize = 11.sp, lineHeight = 15.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(9.dp))
            Text(greeting, color = ShellNavy, fontSize = 30.sp, lineHeight = 35.sp,
                fontWeight = FontWeight.Light)
            Text("Jakub.", color = superhumanTextPrimary, fontSize = 30.sp, lineHeight = 35.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Small steps. Big results.", color = ShellMuted, fontSize = 13.sp)
        }
        Box(
            Modifier.size(50.dp)
                .background(superhumanSurface, CircleShape)
                .border(1.dp, superhumanBorder.copy(alpha = .65f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("☼", color = superhumanTextMuted, fontSize = 27.sp)
        }
    }
}

@Composable
private fun ShellBottomNavigation(
    selectedPage: ShellPage,
    onHome: () -> Unit,
    onBody: () -> Unit,
    onTrudy: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Floating navigation: the canvas remains black around the card.
    // Keep the container and selection intentionally calmer than primary dashboard metrics.
    val dark = SuperhumanAppearance.darkMode
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .height(68.dp)
            .background(if (dark) Color(0xFF17191D) else superhumanSurfaceElevated, shape)
            .border(
                1.dp,
                if (dark) Color.White.copy(alpha = .075f) else superhumanBorder,
                shape
            )
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val selectedTint = if (dark) Color(0xFFF2F3F5) else superhumanBlue
        ShellBottomItem("Home", selectedPage == ShellPage.HOME, selectedTint, onHome) {
            color -> ShellTablerIcon(R.drawable.tabler_home, color)
        }
        ShellBottomItem("Body", selectedPage == ShellPage.BODY, selectedTint, onBody) {
            color -> ShellTablerIcon(R.drawable.tabler_user, color)
        }
        ShellBottomItem("Trudy", selectedPage == ShellPage.TRUDY, selectedTint, onTrudy) {
            color -> ShellTablerIcon(R.drawable.tabler_message_circle, color)
        }
        ShellBottomItem(
            "Settings", selectedPage == ShellPage.SETTINGS || selectedPage == ShellPage.SMART_DEVICES,
            selectedTint, onSettings
        ) {
            color -> ShellTablerIcon(R.drawable.tabler_settings, color)
        }
    }
}

@Composable
private fun ShellBottomItem(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val color = if (selected) accent else ShellMuted
    val selectedShape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .width(70.dp)
            .height(54.dp)
            .background(
                if (selected) {
                    if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .095f)
                    else superhumanBlue.copy(alpha = .085f)
                } else Color.Transparent,
                selectedShape
            )
            .superhumanClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.height(27.dp), contentAlignment = Alignment.Center) {
            icon(color)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label, color = color, fontSize = 10.sp, lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ShellTablerIcon(drawableRes: Int, color: Color) {
    Icon(
        painter = painterResource(id = drawableRes),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(25.dp)
    )
}
