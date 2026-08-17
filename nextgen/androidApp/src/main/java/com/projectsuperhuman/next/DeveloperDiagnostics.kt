package com.projectsuperhuman.next

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small persistent diagnostic recorder for physical-device debugging.
 * It is entirely opt-in: no events are stored unless Developer mode is enabled in Settings.
 * Events are committed synchronously so the last native voice stage survives a process crash.
 */
object DeveloperDiagnostics {
    private const val PREFS = "project_superhuman_developer_diagnostics"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 160
    private const val SEP = "\u001e"

    @Volatile private var appContext: Context? = null
    @Volatile private var enabledCache: Boolean = false

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        // During H19C bring-up, seed the exact physical device supplied by the user so the
        // wearable card can take its existing reconnect path straight to connectGatt() instead
        // of depending on BLE advertisement discovery.
        H19cKnownDeviceBootstrap.seed(app)
        enabledCache = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
    }

    fun isEnabled(context: Context? = null): Boolean {
        context?.let { if (appContext == null) initialize(it) }
        return enabledCache
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        enabledCache = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).commit()
        if (enabled) log("developer.enabled", deviceSummary())
    }

    fun clear(context: Context) {
        initialize(context)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_EVENTS).commit()
    }

    fun log(stage: String, detail: String? = null) {
        if (!enabledCache) return
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getString(KEY_EVENTS, "").orEmpty()
            .split(SEP).filter { it.isNotBlank() }
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        val maxMb = runtime.maxMemory() / (1024L * 1024L)
        val line = buildString {
            append(stamp).append("  ").append(stage)
            append("  heap=").append(usedMb).append('/').append(maxMb).append("MB")
            detail?.takeIf { it.isNotBlank() }?.let { append("  ").append(it.replace('\n', ' ')) }
        }
        val updated = (old + line).takeLast(MAX_EVENTS).joinToString(SEP)
        // commit(), rather than apply(), is intentional: preserve the final stage if native code kills the process.
        prefs.edit().putString(KEY_EVENTS, updated).commit()
    }

    fun events(context: Context): List<String> {
        initialize(context)
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EVENTS, "").orEmpty()
            .split(SEP).filter { it.isNotBlank() }
    }

    fun latest(context: Context, limit: Int = 20): List<String> = events(context).takeLast(limit)

    fun exportText(context: Context): String {
        val captured = events(context)
        return buildString {
            appendLine("Project Superhuman developer diagnostics")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
            appendLine("Device: ${deviceSummary()}")
            appendLine("Developer mode: ${if (isEnabled(context)) "ON" else "OFF"}")
            appendLine("Events: ${captured.size}")
            appendLine()
            if (captured.isEmpty()) {
                appendLine("No diagnostic events recorded.")
            } else {
                captured.forEach(::appendLine)
            }
        }
    }

    fun deviceSummary(): String = "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}; ABI=${Build.SUPPORTED_ABIS.joinToString()}"
}