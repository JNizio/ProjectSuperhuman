package com.projectsuperhuman.next

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val TrudyNavy = Color(0xFF123D70)
private val TrudyBg = Color(0xFFF8FBFD)
private val TrudyMuted = Color(0xFF748294)
private val TrudyCyan = Color(0xFF1CC8C8)
private val TrudyCyanSoft = Color(0xFFE8FAFA)
private val TrudyBorder = Color(0xFFE1E8EE)
private val TrudyWarningBg = Color(0xFFFFF8E8)
private val TrudyWarningText = Color(0xFF7B6430)

@Composable
internal fun NativeTrudy(
    state: TrudyConversationState,
    controller: TrudyConversationController,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val uiState = state.uiState

    fun execute(request: TrudySendRequest?) {
        request ?: return
        scope.launch {
            val result = try {
                controller.respondTo(request.conversationRequest)
            } catch (_: Throwable) {
                TrudyControllerResult.Failure("Trudy couldn't complete that response. Try again.")
            }
            state.complete(request, result)
        }
    }

    fun send(textOverride: String? = null) = execute(state.beginSend(textOverride))
    fun retry(messageId: Long) = execute(state.retryFailed(messageId))

    LaunchedEffect(uiState.messages.size, uiState.isThinking) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(TrudyBg).imePadding()) {
        TrudyHeader(onBack)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.showWelcome) {
                TrudyWelcome(onPrompt = ::send)
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
                            onToggleEvidence = { state.toggleEvidence(message.id) },
                            onRetry = { retry(message.id) }
                        )
                    }
                }
            }
        }

        TrudyComposer(
            value = state.inputText,
            enabled = !uiState.isThinking,
            onValueChange = state::updateInput,
            onSend = { send() }
        )
    }
}

@Composable
private fun TrudyHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
            Text("‹", color = TrudyNavy, fontSize = 29.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.size(14.dp))
        Box(
            Modifier.size(42.dp).background(TrudyCyanSoft, CircleShape)
                .border(1.dp, TrudyCyan.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(14.dp).background(TrudyCyan, CircleShape)) }
        Spacer(Modifier.size(12.dp))
        Column {
            Text("TRUDY", color = TrudyNavy, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.4.sp)
            Text("Project Superhuman assistant", color = TrudyMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TrudyWelcome(onPrompt: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).background(TrudyCyanSoft, CircleShape)
                .border(1.dp, TrudyCyan.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(24.dp).background(TrudyCyan, CircleShape)) }
        Spacer(Modifier.height(18.dp))
        Text("Ask Trudy", color = TrudyNavy, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(7.dp))
        Text(
            "Ask about your Project Superhuman context. Evidence and uncertainty can appear with each answer when the backend provides them.",
            color = TrudyMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(22.dp))
        listOf("How was my sleep?", "What changed today?", "What should I pay attention to?").forEach { prompt ->
            Box(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, TrudyBorder, RoundedCornerShape(16.dp))
                    .superhumanClickable { onPrompt(prompt) }
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) { Text(prompt, color = TrudyNavy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun TrudyMessageBubble(
    message: TrudyMessage,
    evidenceExpanded: Boolean,
    onToggleEvidence: () -> Unit,
    onRetry: () -> Unit
) {
    val isUser = message.role == TrudyMessageRole.USER
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            Modifier.widthIn(max = 330.dp)
                .background(if (isUser) TrudyNavy else Color.White, RoundedCornerShape(18.dp))
                .border(1.dp, if (isUser) TrudyNavy else TrudyBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 15.dp, vertical = 12.dp)
        ) {
            when (message.status) {
                TrudyMessageStatus.SENDING -> TrudyThinkingContent(message.activity)
                TrudyMessageStatus.ERROR -> TrudyErrorContent(message, onRetry)
                TrudyMessageStatus.COMPLETE -> Text(
                    message.text,
                    color = if (isUser) Color.White else Color(0xFF203246),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }

        if (!isUser && message.status == TrudyMessageStatus.COMPLETE) {
            message.activity?.let { activity ->
                Text(activity.label, color = TrudyMuted, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp, top = 5.dp))
            }
            message.notices.forEach { notice -> TrudyNoticeRow(notice) }
            if (message.evidence.isNotEmpty()) {
                TrudyEvidenceBlock(message.evidence, evidenceExpanded, onToggleEvidence)
            }
        }
    }
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
        Text(message.text, color = Color(0xFF9A3D3D), fontSize = 12.sp, lineHeight = 17.sp)
        if (message.retryable) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.background(Color(0xFFFFF1F1), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFF0CACA), RoundedCornerShape(10.dp))
                    .superhumanClickable(onClick = onRetry)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) { Text("Retry", color = Color(0xFF8F3B3B), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TrudyNoticeRow(notice: TrudyNotice) {
    val caution = notice.level == TrudyNoticeLevel.CAUTION
    Box(
        Modifier.widthIn(max = 330.dp).padding(top = 6.dp)
            .background(if (caution) TrudyWarningBg else Color(0xFFF2F7F8), RoundedCornerShape(11.dp))
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
    Column(Modifier.widthIn(max = 330.dp).padding(top = 6.dp)) {
        Row(
            Modifier.superhumanClickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(TrudyCyan, CircleShape))
            Spacer(Modifier.size(6.dp))
            Text(
                if (evidence.size == 1) "1 evidence item" else "${evidence.size} evidence items",
                color = TrudyMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(5.dp))
            Text(if (expanded) "⌃" else "⌄", color = TrudyMuted, fontSize = 11.sp)
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                evidence.forEach { item ->
                    Box(
                        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp))
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

@Composable
private fun TrudyComposer(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(color = Color.White, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (enabled) "Ask Trudy…" else "Waiting for Trudy…", color = TrudyMuted) },
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Spacer(Modifier.size(10.dp))
            val canSend = enabled && value.isNotBlank()
            Box(
                Modifier.size(52.dp).background(if (canSend) TrudyCyan else Color(0xFFE5ECEF), CircleShape)
                    .superhumanClickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Text("↑", color = if (canSend) Color.White else TrudyMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
