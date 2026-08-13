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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TrudyNavy = Color(0xFF123D70)
private val TrudyBg = Color(0xFFF8FBFD)
private val TrudyMuted = Color(0xFF748294)
private val TrudyCyan = Color(0xFF1CC8C8)
private val TrudyCyanSoft = Color(0xFFE8FAFA)
private val TrudyBorder = Color(0xFFE1E8EE)

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

    fun send(textOverride: String? = null) {
        val text = state.beginSend(textOverride) ?: return
        scope.launch {
            try {
                delay(450)
                state.completeReply(controller.respondTo(text))
            } catch (_: Throwable) {
                state.fail("Demo response failed. Try sending that message again.")
            }
        }
    }

    val renderedItems = uiState.messages.size + if (uiState.isThinking) 1 else 0
    LaunchedEffect(renderedItems) {
        if (renderedItems > 0) listState.animateScrollToItem(renderedItems - 1)
    }

    Column(
        Modifier.fillMaxSize()
            .background(TrudyBg)
            .imePadding()
    ) {
        TrudyHeader(onBack)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.showWelcome) {
                TrudyWelcome(onPrompt = ::send)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 17.dp,
                        end = 17.dp,
                        top = 12.dp,
                        bottom = 18.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        TrudyMessageBubble(message)
                    }
                    if (uiState.isThinking) {
                        item(key = "trudy-thinking") { TrudyThinkingBubble() }
                    }
                }
            }
        }

        uiState.errorMessage?.let { TrudyError(it) }
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
            Modifier.size(42.dp)
                .background(TrudyCyanSoft, CircleShape)
                .border(1.dp, TrudyCyan.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(14.dp).background(TrudyCyan, CircleShape))
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text("TRUDY", color = TrudyNavy, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.4.sp)
            Text("Project Superhuman assistant · demo shell", color = TrudyMuted, fontSize = 11.sp)
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
            Modifier.size(72.dp)
                .background(TrudyCyanSoft, CircleShape)
                .border(1.dp, TrudyCyan.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(24.dp).background(TrudyCyan, CircleShape))
        }
        Spacer(Modifier.height(18.dp))
        Text("Ask Trudy", color = TrudyNavy, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(7.dp))
        Text(
            "This is the native conversation shell. Responses are local demo placeholders until the health intelligence backend is connected.",
            color = TrudyMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(22.dp))
        listOf(
            "How was my sleep?",
            "What changed today?",
            "What should I pay attention to?"
        ).forEach { prompt ->
            Box(
                Modifier.fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, TrudyBorder, RoundedCornerShape(16.dp))
                    .superhumanClickable { onPrompt(prompt) }
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Text(prompt, color = TrudyNavy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TrudyMessageBubble(message: TrudyMessage) {
    val isUser = message.role == TrudyMessageRole.USER
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            Modifier.widthIn(max = 330.dp)
                .background(
                    if (isUser) TrudyNavy else Color.White,
                    RoundedCornerShape(18.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) TrudyNavy else TrudyBorder,
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 15.dp, vertical = 12.dp)
        ) {
            Text(
                message.text,
                color = if (isUser) Color.White else Color(0xFF203246),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
        if (!isUser && message.evidence.isNotEmpty()) {
            Row(
                Modifier.padding(top = 5.dp, start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(TrudyCyan, CircleShape))
                Spacer(Modifier.size(6.dp))
                Text(message.evidence.joinToString(" · "), color = TrudyMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun TrudyThinkingBubble() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            Modifier.background(Color.White, RoundedCornerShape(18.dp))
                .border(1.dp, TrudyBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(TrudyCyan, CircleShape))
            Spacer(Modifier.size(8.dp))
            Text("Trudy is thinking…", color = TrudyMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TrudyError(message: String) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 4.dp)
            .background(Color(0xFFFFF1F1), RoundedCornerShape(13.dp))
            .border(1.dp, Color(0xFFF0CACA), RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 9.dp)
    ) {
        Text(message, color = Color(0xFF9A3D3D), fontSize = 11.sp)
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
                placeholder = { Text("Ask Trudy…", color = TrudyMuted) },
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier.size(52.dp)
                    .background(if (enabled && value.isNotBlank()) TrudyCyan else Color(0xFFE5ECEF), CircleShape)
                    .superhumanClickable(enabled = enabled && value.isNotBlank(), onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Text("↑", color = if (enabled && value.isNotBlank()) Color.White else TrudyMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
