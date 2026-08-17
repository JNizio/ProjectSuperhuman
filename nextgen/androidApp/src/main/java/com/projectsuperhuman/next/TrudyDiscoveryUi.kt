package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val DiscoveryNavy = Color(0xFF123D70)
private val DiscoveryInk = Color(0xFF19354F)
private val DiscoveryMuted = Color(0xFF748294)
private val DiscoveryCyan = Color(0xFF1CC8C8)
private val DiscoveryCyanSoft = Color(0xFFE8FAFA)
private val DiscoveryBlueSoft = Color(0xFFEDF5FB)
private val DiscoveryVioletSoft = Color(0xFFF2EFFB)
private val DiscoveryBorder = Color(0xFFDDE7ED)
private val DiscoveryWhite = Color(0xFFFEFFFF)

private data class TrudyStarter(
    val eyebrow: String,
    val title: String,
    val description: String,
    val prompt: String,
    val tint: Color
)

private val trudyStarters = listOf(
    TrudyStarter(
        eyebrow = "READING",
        title = "Check a value",
        description = "Ask for a recorded metric at a specific time.",
        prompt = "What was my resting heart rate yesterday?",
        tint = DiscoveryCyanSoft
    ),
    TrudyStarter(
        eyebrow = "TREND",
        title = "See a trend",
        description = "Look across days, weeks or months instead of one reading.",
        prompt = "How has my sleep changed this month?",
        tint = DiscoveryBlueSoft
    ),
    TrudyStarter(
        eyebrow = "WHY",
        title = "Investigate why",
        description = "Check what changed and which related signals are worth attention.",
        prompt = "Why have I felt more tired lately?",
        tint = DiscoveryVioletSoft
    ),
    TrudyStarter(
        eyebrow = "PATTERN",
        title = "Find connections",
        description = "Compare signals across sleep, training, food, mood and environment.",
        prompt = "Does caffeine line up with worse sleep?",
        tint = DiscoveryCyanSoft
    ),
    TrudyStarter(
        eyebrow = "COMPARE",
        title = "Compare periods",
        description = "Put two time windows side by side and quantify the difference.",
        prompt = "Compare my sleep this month with last month.",
        tint = DiscoveryBlueSoft
    ),
    TrudyStarter(
        eyebrow = "SHIFT",
        title = "Find a turning point",
        description = "Ask when a meaningful change first became visible in your data.",
        prompt = "When did my sleep start getting worse?",
        tint = DiscoveryVioletSoft
    )
)

@Composable
internal fun TrudyDiscoveryWelcome(
    onPrompt: (String) -> Unit,
    onVoiceMode: () -> Unit,
    onGuide: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 8.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { TrudyDiscoveryHero(onPrompt = onPrompt) }
        item {
            TrudyDiscoverySectionHeader(
                eyebrow = "START WITH A GOAL",
                title = "What do you want to understand?"
            )
        }
        trudyStarters.chunked(2).forEach { pair ->
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Stretch
                ) {
                    pair.forEach { starter ->
                        TrudyStarterCard(starter, onPrompt, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            TrudyDiscoverySectionHeader(
                eyebrow = "GO DEEPER",
                title = "Trudy can reason across your context"
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrudyDeepPrompt(
                    label = "TODAY",
                    text = "What should I pay attention to today?",
                    onPrompt = onPrompt
                )
                TrudyDeepPrompt(
                    label = "CROSS-DOMAIN",
                    text = "Could hotter weather explain my heart rate?",
                    onPrompt = onPrompt
                )
                TrudyDeepPrompt(
                    label = "MULTI-SIGNAL",
                    text = "How did my sleep and resting heart rate change together?",
                    onPrompt = onPrompt
                )
            }
        }
        item { TrudyHowItWorksCard(onGuide = onGuide, onVoiceMode = onVoiceMode) }
        item { TrudyConversationTipCard() }
    }
}

@Composable
private fun TrudyDiscoveryHero(onPrompt: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFE9FAF8), Color(0xFFF5F8FD), Color(0xFFF3F0FC))
                ),
                RoundedCornerShape(28.dp)
            )
            .border(1.dp, DiscoveryBorder, RoundedCornerShape(28.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "PERSONAL HEALTH INTELLIGENCE",
                    color = DiscoveryNavy,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ask what changed — and what could explain it",
                    color = DiscoveryInk,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 27.sp
                )
            }
            Spacer(Modifier.size(12.dp))
            Box(
                Modifier.size(62.dp)
                    .background(
                        Brush.linearGradient(listOf(DiscoveryNavy, Color(0xFF256C8A), DiscoveryCyan)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("T", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            "Trudy can read your recorded data, compare periods, investigate changes, connect signals across health domains, and show the evidence behind an answer.",
            color = DiscoveryMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            TrudyCapabilityPill("YOUR DATA")
            TrudyCapabilityPill("CROSS-DOMAIN")
            TrudyCapabilityPill("EVIDENCE-AWARE")
        }
        Spacer(Modifier.height(13.dp))
        Box(
            Modifier.fillMaxWidth()
                .background(DiscoveryNavy, RoundedCornerShape(16.dp))
                .superhumanClickable { onPrompt("What should I pay attention to today?") }
                .semantics { contentDescription = "Ask Trudy what to pay attention to today" }
                .padding(horizontal = 15.dp, vertical = 13.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("START WITH TODAY", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("→", color = DiscoveryCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TrudyCapabilityPill(text: String) {
    Box(
        Modifier.background(Color.White.copy(alpha = 0.84f), RoundedCornerShape(30.dp))
            .border(1.dp, DiscoveryBorder, RoundedCornerShape(30.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = DiscoveryNavy, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp)
    }
}

@Composable
private fun TrudyDiscoverySectionHeader(eyebrow: String, title: String) {
    Column(Modifier.padding(start = 2.dp, top = 3.dp)) {
        Text(eyebrow, color = DiscoveryMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 1.0.sp)
        Spacer(Modifier.height(2.dp))
        Text(title, color = DiscoveryNavy, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TrudyStarterCard(starter: TrudyStarter, onPrompt: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.defaultMinSize(minHeight = 158.dp)
            .background(DiscoveryWhite, RoundedCornerShape(21.dp))
            .border(1.dp, DiscoveryBorder, RoundedCornerShape(21.dp))
            .superhumanClickable { onPrompt(starter.prompt) }
            .semantics { contentDescription = "${starter.title}. Example: ${starter.prompt}" }
            .padding(12.dp)
    ) {
        Box(
            Modifier.background(starter.tint, RoundedCornerShape(9.dp)).padding(horizontal = 7.dp, vertical = 5.dp)
        ) {
            Text(starter.eyebrow, color = DiscoveryNavy, fontSize = 6.sp, fontWeight = FontWeight.Black, letterSpacing = .65.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(starter.title, color = DiscoveryInk, fontSize = 12.sp, fontWeight = FontWeight.Black, lineHeight = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(starter.description, color = DiscoveryMuted, fontSize = 8.sp, lineHeight = 11.sp)
        Spacer(Modifier.weight(1f))
        Text("TRY", color = DiscoveryCyan, fontSize = 6.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
        Spacer(Modifier.height(2.dp))
        Text(starter.prompt, color = DiscoveryNavy, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, lineHeight = 11.sp, maxLines = 3)
    }
}

@Composable
private fun TrudyDeepPrompt(label: String, text: String, onPrompt: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(17.dp))
            .border(1.dp, DiscoveryBorder, RoundedCornerShape(17.dp))
            .superhumanClickable { onPrompt(text) }
            .semantics { contentDescription = "Ask Trudy: $text" }
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = DiscoveryCyan, fontSize = 6.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
            Spacer(Modifier.height(2.dp))
            Text(text, color = DiscoveryNavy, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
        }
        Spacer(Modifier.size(10.dp))
        Text("→", color = DiscoveryMuted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrudyHowItWorksCard(onGuide: () -> Unit, onVoiceMode: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFFF0F8F8), RoundedCornerShape(22.dp))
            .border(1.dp, DiscoveryCyan.copy(alpha = .15f), RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Text("HOW TRUDY WORKS", color = DiscoveryCyan, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
        Spacer(Modifier.height(5.dp))
        Text("She keeps facts, patterns and uncertainty separate.", color = DiscoveryInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text(
            "If two devices disagree, Trudy keeps the sources separate. If a date or measurement is missing, she should say so instead of inventing it. Clinical records are used as context, not as automatic diagnoses.",
            color = DiscoveryMuted,
            fontSize = 9.sp,
            lineHeight = 13.sp
        )
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrudySecondaryAction("EXPLORE FEATURES", "Open Trudy feature guide", onGuide, Modifier.weight(1f))
            TrudySecondaryAction("VOICE", "Open Trudy voice mode", onVoiceMode, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrudySecondaryAction(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.defaultMinSize(minHeight = 42.dp)
            .background(Color.White, RoundedCornerShape(13.dp))
            .border(1.dp, DiscoveryBorder, RoundedCornerShape(13.dp))
            .superhumanClickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = DiscoveryNavy, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .55.sp)
    }
}

@Composable
private fun TrudyConversationTipCard() {
    Row(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, DiscoveryBorder, RoundedCornerShape(18.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(27.dp).background(DiscoveryCyanSoft, CircleShape), contentAlignment = Alignment.Center) {
            Text("?", color = DiscoveryNavy, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Talk to Trudy naturally", color = DiscoveryNavy, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            Text(
                "Add a timeframe when useful — “this week”, “last month”, or a real date. Then follow up with “Why?”, “What about last month?” or “What evidence are you using?” without starting over.",
                color = DiscoveryMuted,
                fontSize = 8.sp,
                lineHeight = 12.sp
            )
        }
    }
}

private data class TrudyGuideFeature(
    val title: String,
    val description: String,
    val example: String
)

private val guideFeatures = listOf(
    TrudyGuideFeature(
        "Readings & history",
        "Ask for a recorded value now, yesterday, or inside a specific period.",
        "What was my resting heart rate yesterday?"
    ),
    TrudyGuideFeature(
        "Trends & comparisons",
        "Compare periods and ask whether a metric really improved, worsened or stayed mixed.",
        "Compare my sleep this month with last month."
    ),
    TrudyGuideFeature(
        "Why & what changed",
        "Trudy can investigate related sleep, exercise, recovery, nutrition, hydration, emotional and environmental signals instead of only listing metrics.",
        "Why have I felt more tired lately?"
    ),
    TrudyGuideFeature(
        "Connections",
        "Ask whether two tracked factors line up. Trudy should describe the personal pattern without pretending an association proves cause.",
        "Does caffeine line up with worse sleep?"
    ),
    TrudyGuideFeature(
        "Turning points & events",
        "Ask when a shift became visible. For “since I started…” questions, a stored or supplied event date gives Trudy a real before/after boundary.",
        "When did my sleep start getting worse?"
    ),
    TrudyGuideFeature(
        "Evidence & conflicting sources",
        "Tap Evidence under a reply or ask what evidence was used. If H19C, Health Connect or another source disagrees, Trudy should preserve that disagreement.",
        "What evidence are you using?"
    ),
    TrudyGuideFeature(
        "Clinical context",
        "Recorded labs and conditions can inform context, while Trudy keeps measurement findings separate from diagnoses or clinician-confirmation claims.",
        "What clinical context is relevant to this?"
    ),
    TrudyGuideFeature(
        "Missing information",
        "When an explanation is weak, Trudy can identify the one or two missing measurements or contextual details most likely to reduce uncertainty.",
        "What information would help explain this better?"
    )
)

@Composable
internal fun TrudyGuideDialog(
    onDismiss: () -> Unit,
    onPrompt: (String) -> Unit,
    onVoiceMode: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFFF9FCFD),
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 15.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("TRUDY GUIDE", color = DiscoveryCyan, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("What can I ask?", color = DiscoveryNavy, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                    Box(
                        Modifier.size(44.dp).superhumanClickable(onClick = onDismiss)
                            .semantics { contentDescription = "Close Trudy guide" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("×", color = DiscoveryMuted, fontSize = 22.sp)
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "You do not need special commands. Ask a normal health question and include the timeframe or change you care about when you can.",
                            color = DiscoveryMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, bottom = 3.dp)
                        )
                    }
                    guideFeatures.forEachIndexed { index, feature ->
                        item {
                            TrudyGuideFeatureCard(
                                index = index + 1,
                                feature = feature,
                                onPrompt = {
                                    onDismiss()
                                    onPrompt(feature.example)
                                }
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            TrudySecondaryAction(
                                "TRY A WHY QUESTION",
                                "Ask Trudy why you have felt more tired lately",
                                onClick = {
                                    onDismiss()
                                    onPrompt("Why have I felt more tired lately?")
                                },
                                modifier = Modifier.weight(1f)
                            )
                            TrudySecondaryAction(
                                "OPEN VOICE",
                                "Open Trudy voice mode",
                                onClick = {
                                    onDismiss()
                                    onVoiceMode()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrudyGuideFeatureCard(index: Int, feature: TrudyGuideFeature, onPrompt: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, DiscoveryBorder, RoundedCornerShape(18.dp))
            .superhumanClickable(onClick = onPrompt)
            .semantics { contentDescription = "${feature.title}. Example: ${feature.example}" }
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(29.dp).background(DiscoveryCyanSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(index.toString(), color = DiscoveryNavy, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(feature.title, color = DiscoveryInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            Text(feature.description, color = DiscoveryMuted, fontSize = 8.sp, lineHeight = 12.sp)
            Spacer(Modifier.height(5.dp))
            Text("TRY · ${feature.example}", color = DiscoveryNavy, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, lineHeight = 11.sp)
        }
        Spacer(Modifier.size(7.dp))
        Text("→", color = DiscoveryCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun TrudyFollowUpStrip(onPrompt: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        TrudyFollowUpChip("Why?", onPrompt)
        TrudyFollowUpChip("What evidence are you using?", onPrompt)
        TrudyFollowUpChip("What about last month?", onPrompt)
    }
}

@Composable
private fun TrudyFollowUpChip(text: String, onPrompt: (String) -> Unit) {
    Box(
        Modifier.defaultMinSize(minHeight = 38.dp)
            .background(Color.White, RoundedCornerShape(30.dp))
            .border(1.dp, DiscoveryBorder, RoundedCornerShape(30.dp))
            .superhumanClickable { onPrompt(text) }
            .semantics { contentDescription = "Follow up with Trudy: $text" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = DiscoveryNavy, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
    }
}
