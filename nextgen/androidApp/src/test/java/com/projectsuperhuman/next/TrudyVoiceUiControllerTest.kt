package com.projectsuperhuman.next

import java.io.File
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrudyVoiceUiControllerTest {
    @Test
    fun completedAssistantMessageSupportsSpeakButUserMessageDoesNot() {
        val assistant = TrudyMessage(1, TrudyMessageRole.TRUDY, "Answer")
        val user = TrudyMessage(2, TrudyMessageRole.USER, "Question")
        val sending = assistant.copy(id = 3, status = TrudyMessageStatus.SENDING)
        assertTrue(assistant.supportsVoicePlayback())
        assertFalse(user.supportsVoicePlayback())
        assertFalse(sending.supportsVoicePlayback())
    }

    @Test
    fun speakAssistantMessageAndStopCurrentSpeech() = runTest {
        val source = FakeTrudyVoiceRuntimeSource(playbackDelayMs = 10_000)
        val controller = controller(source)
        runCurrent()
        controller.speak(7, "**Hello** from Trudy")
        runCurrent()
        assertEquals(TrudyVoiceUiStatus.SPEAKING, controller.state.value.status)
        assertEquals(7, controller.state.value.activeMessageId)
        assertEquals(listOf("Hello from Trudy"), source.spokenTexts)
        controller.stop()
        assertEquals(TrudyVoiceUiStatus.STOPPED, controller.state.value.status)
        assertEquals(null, controller.state.value.activeMessageId)
        assertTrue(source.stopCalls >= 2)
        controller.close()
    }

    @Test
    fun selectingAnotherAnswerCancelsPreviousOwnership() = runTest {
        val source = FakeTrudyVoiceRuntimeSource(playbackDelayMs = 10_000)
        val controller = controller(source)
        runCurrent()
        controller.speak(1, "First answer")
        runCurrent()
        assertEquals(1, controller.state.value.activeMessageId)
        controller.speak(2, "Second answer")
        runCurrent()
        assertEquals(2, controller.state.value.activeMessageId)
        assertEquals(TrudyVoiceUiStatus.SPEAKING, controller.state.value.status)
        assertTrue(source.stopCalls >= 2)
        controller.close()
    }

    @Test
    fun modelNotInstalledRequiresExplicitInstall() = runTest {
        val source = FakeTrudyVoiceRuntimeSource(FakeTrudyVoiceScenario.NOT_INSTALLED)
        val controller = controller(source)
        runCurrent()
        controller.speak(4, "Read this")
        runCurrent()
        assertEquals(TrudyVoiceUiStatus.MODEL_NOT_INSTALLED, controller.state.value.status)
        assertTrue(controller.state.value.promptVisible)
        assertEquals(0, source.installCalls)
        assertTrue(controller.state.value.modelInfo.approximateDownloadBytes != null)
        controller.close()
    }

    @Test
    fun installProgressMapsAndPendingSpeechStartsAfterInstall() = runTest {
        val source = FakeTrudyVoiceRuntimeSource(FakeTrudyVoiceScenario.NOT_INSTALLED, playbackDelayMs = 10_000)
        val controller = controller(source)
        runCurrent()
        controller.speak(5, "Pending answer")
        runCurrent()
        controller.installModel()
        runCurrent()
        assertEquals(TrudyVoiceUiStatus.DOWNLOADING, controller.state.value.status)
        assertEquals(10, controller.state.value.installProgressPercent)
        advanceTimeBy(10)
        runCurrent()
        assertEquals(50, controller.state.value.installProgressPercent)
        advanceTimeBy(10)
        runCurrent()
        assertEquals(1, source.installCalls)
        assertEquals(TrudyVoiceModelInstallation.INSTALLED, controller.state.value.modelInfo.installation)
        assertEquals(5, controller.state.value.activeMessageId)
        assertEquals(TrudyVoiceUiStatus.SPEAKING, controller.state.value.status)
        controller.close()
    }

    @Test
    fun installFailureCanRetry() = runTest {
        val source = FakeTrudyVoiceRuntimeSource(FakeTrudyVoiceScenario.NOT_INSTALLED)
        source.failInstallation = true
        val controller = controller(source)
        runCurrent()
        controller.installModel()
        advanceUntilIdle()
        assertEquals(TrudyVoiceUiStatus.ERROR, controller.state.value.status)
        assertEquals(1, source.installCalls)
        source.failInstallation = false
        controller.retryLastVoiceAction()
        advanceUntilIdle()
        assertEquals(2, source.installCalls)
        assertEquals(TrudyVoiceUiStatus.READY, controller.state.value.status)
        controller.close()
    }

    @Test
    fun installedRuntimeMapsToReadyWithoutCreatingSpeechRuntime() = runTest {
        val source = FakeTrudyVoiceRuntimeSource(FakeTrudyVoiceScenario.READY)
        val controller = controller(source)
        runCurrent()
        assertEquals(TrudyVoiceUiStatus.READY, controller.state.value.status)
        assertTrue(source.createdConfigs.isEmpty())
        controller.close()
    }

    @Test
    fun synthesisErrorDoesNotCorruptSuccessfulChatMessage() = runTest {
        val conversation = TrudyConversationState()
        conversation.updateInput("Question")
        val request = assertNotNull(conversation.beginSend())
        conversation.complete(request, TrudyControllerResult.Success(TrudyReply("Successful answer")))

        val source = FakeTrudyVoiceRuntimeSource(FakeTrudyVoiceScenario.FAILURE)
        val controller = controller(source)
        runCurrent()
        controller.speak(request.assistantMessageId, "Successful answer")
        advanceUntilIdle()
        assertEquals(TrudyVoiceUiStatus.ERROR, controller.state.value.status)
        val message = conversation.uiState.messages.first { it.id == request.assistantMessageId }
        assertEquals(TrudyMessageStatus.COMPLETE, message.status)
        assertEquals("Successful answer", message.text)
        controller.close()
    }

    @Test
    fun leavingTrudyAndNewPromptCancelSpeech() = runTest {
        val source = FakeTrudyVoiceRuntimeSource(playbackDelayMs = 10_000)
        val controller = controller(source)
        runCurrent()
        controller.speak(1, "First")
        runCurrent()
        controller.onUserPromptSubmitted()
        assertEquals(TrudyVoiceUiStatus.STOPPED, controller.state.value.status)
        controller.speak(2, "Second")
        runCurrent()
        controller.onTrudyHidden()
        assertEquals(TrudyVoiceUiStatus.STOPPED, controller.state.value.status)
        assertEquals(null, controller.state.value.activeMessageId)
        controller.close()
    }

    @Test
    fun autoSpeakIsOffByDefaultAndCanBeEnabled() = runTest {
        val source = FakeTrudyVoiceRuntimeSource(playbackDelayMs = 10_000)
        val store = InMemoryTrudyVoicePreferenceStore()
        val controller = DefaultTrudyVoiceController(source, store, scope = this)
        runCurrent()
        assertFalse(controller.state.value.preferences.autoSpeak)
        controller.maybeAutoSpeak(1, "No audio")
        runCurrent()
        assertTrue(source.spokenTexts.isEmpty())
        controller.setAutoSpeak(true)
        controller.maybeAutoSpeak(2, "Auto answer")
        runCurrent()
        assertEquals(listOf("Auto answer"), source.spokenTexts)
        controller.close()
    }

    @Test
    fun voiceSelectionAndSpeedPreferencesArePersistedAndUsed() = runTest {
        val source = FakeTrudyVoiceRuntimeSource(playbackDelayMs = 10_000)
        val store = InMemoryTrudyVoicePreferenceStore()
        val controller = DefaultTrudyVoiceController(source, store, scope = this)
        runCurrent()
        controller.selectVoice("preview-b")
        controller.setSpeed(1.25f)
        runCurrent()
        val saved = assertNotNull(store.current())
        assertEquals("preview-b", saved.selectedVoiceId)
        assertEquals(1.25f, saved.speed)
        controller.speak(9, "Preference check")
        runCurrent()
        val config = source.createdConfigs.last()
        assertEquals("preview-b", config.voiceId)
        assertEquals(1.25f, config.speed)
        controller.close()
    }

    @Test
    fun textConversationSucceedsWhenVoiceUnavailable() = runTest {
        val source = FakeTrudyVoiceRuntimeSource(FakeTrudyVoiceScenario.UNAVAILABLE)
        val voice = controller(source)
        runCurrent()
        assertEquals(TrudyVoiceUiStatus.UNAVAILABLE, voice.state.value.status)

        val conversation = TrudyConversationState()
        conversation.updateInput("How was my sleep?")
        val request = assertNotNull(conversation.beginSend())
        assertTrue(conversation.complete(request, TrudyControllerResult.Success(TrudyReply("Text still works."))))
        val answer = conversation.uiState.messages.first { it.id == request.assistantMessageId }
        assertEquals(TrudyMessageStatus.COMPLETE, answer.status)
        assertEquals("Text still works.", answer.text)
        voice.close()
    }

    @Test
    fun removeAndReinstallUseExplicitLifecycle() = runTest {
        val source = FakeTrudyVoiceRuntimeSource(FakeTrudyVoiceScenario.READY)
        val controller = controller(source)
        runCurrent()
        controller.removeModel()
        advanceUntilIdle()
        assertEquals(1, source.removeCalls)
        assertEquals(TrudyVoiceModelInstallation.NOT_INSTALLED, controller.state.value.modelInfo.installation)
        controller.installModel()
        advanceUntilIdle()
        assertEquals(1, source.installCalls)
        assertEquals(TrudyVoiceUiStatus.READY, controller.state.value.status)
        controller.reinstallModel()
        advanceUntilIdle()
        assertEquals(2, source.removeCalls)
        assertEquals(2, source.installCalls)
        assertEquals(TrudyVoiceUiStatus.READY, controller.state.value.status)
        controller.close()
    }

    @Test
    fun composeVoiceUiDoesNotReferenceKokoroInferenceInternals() {
        val candidates = listOf(
            File("src/main/java/com/projectsuperhuman/next/NativeTrudy.kt"),
            File("androidApp/src/main/java/com/projectsuperhuman/next/NativeTrudy.kt"),
            File("nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeTrudy.kt")
        )
        val file = candidates.firstOrNull { it.isFile }
        if (file != null) {
            val source = file.readText()
            listOf("KokoroInferenceBackend", "KokoroInferenceRequest", "KokoroModelFiles", "TrudyPcmAudio", "modelPath").forEach {
                assertFalse(it in source, "Compose must not reference $it")
            }
        }
    }

    private fun TestScope.controller(source: FakeTrudyVoiceRuntimeSource): DefaultTrudyVoiceController =
        DefaultTrudyVoiceController(source, InMemoryTrudyVoicePreferenceStore(), scope = this)
}
