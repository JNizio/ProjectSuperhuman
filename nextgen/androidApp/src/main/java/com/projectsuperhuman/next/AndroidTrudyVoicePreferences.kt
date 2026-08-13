package com.projectsuperhuman.next

import android.content.Context

class AndroidTrudyVoicePreferenceStore(context: Context) : TrudyVoicePreferenceStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(defaults: TrudyVoicePreferences): TrudyVoicePreferences = TrudyVoicePreferences(
        enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
        selectedVoiceId = prefs.getString(KEY_VOICE_ID, defaults.selectedVoiceId)
            ?.takeIf { it.isNotBlank() } ?: defaults.selectedVoiceId,
        speed = prefs.getFloat(KEY_SPEED, defaults.speed).coerceIn(0.5f, 2.0f),
        autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, defaults.autoSpeak)
    )

    override fun save(preferences: TrudyVoicePreferences) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, preferences.enabled)
            .putString(KEY_VOICE_ID, preferences.selectedVoiceId)
            .putFloat(KEY_SPEED, preferences.speed)
            .putBoolean(KEY_AUTO_SPEAK, preferences.autoSpeak)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "trudy_voice_preferences"
        const val KEY_ENABLED = "enabled"
        const val KEY_VOICE_ID = "voice_id"
        const val KEY_SPEED = "speed"
        const val KEY_AUTO_SPEAK = "auto_speak"
    }
}

object AndroidTrudyVoiceControllerFactory {
    fun create(
        context: Context,
        runtimeSource: TrudyVoiceRuntimeSource = TrudyVoiceRuntimeSourceFactory.createAndroid(context)
    ): TrudyVoiceController = DefaultTrudyVoiceController(
        runtimeSource = runtimeSource,
        preferenceStore = AndroidTrudyVoicePreferenceStore(context)
    )
}
