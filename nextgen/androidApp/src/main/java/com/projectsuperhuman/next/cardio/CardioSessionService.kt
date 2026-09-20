package com.projectsuperhuman.next

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal object CardioSessionForeground {
    fun start(context: Context) {
        val intent = Intent(context, CardioSessionService::class.java)
            .setAction(CardioSessionService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, CardioSessionService::class.java))
    }
}

internal class CardioSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: CardioSessionStore
    private lateinit var controller: CardioLiveSessionController
    private lateinit var coordinator: CardioSessionCoordinator
    private val nof1Repository = CardioNof1Repository()

    override fun onCreate() {
        super.onCreate()
        NativeDataHub.initialize(applicationContext)
        CardioSensorRuntime.initialize(applicationContext)
        CardioGpsRuntime.initialize(applicationContext)
        store = DataStoreCardioSessionStore(applicationContext)
        controller = CardioLiveSessionController()
        coordinator = CardioSessionCoordinator(
            store = store,
            repository = DataVaultCardioRepository(),
            controller = controller
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCardioForeground(null, "Cardio session active")

        scope.launch {
            store.load().draft?.let(::ensureRuntimeOwnership)
            when (intent?.action) {
                ACTION_PAUSE -> {
                    coordinator.pause()
                    val now = System.currentTimeMillis()
                    CardioSensorRuntime.pauseSession(now)
                    CardioGpsRuntime.pause(now, manual = true)
                }
                ACTION_RESUME -> {
                    coordinator.resume()
                    val now = System.currentTimeMillis()
                    CardioSensorRuntime.resumeSession(now)
                    CardioGpsRuntime.resume(now, manual = true)
                }
                ACTION_FINISH -> {
                    var heartRateSamples: List<CardioHeartRateSample> = emptyList()
                    var rrIntervals: List<CardioRrIntervalSample> = emptyList()
                    var telemetry: CardioLiveTelemetrySnapshot? = null
                    val result = coordinator.quickSave { session ->
                        CardioSensorRuntime.pauseSession(session.endedAt)
                        CardioGpsRuntime.pause(session.endedAt, manual = true)
                        val summary = CardioSensorRuntime.snapshot(session.endedAt)
                            .takeIf { it.sampleCount > 0 }
                        telemetry = CardioGpsRuntime.snapshot(session.endedAt)
                        heartRateSamples = CardioSensorRuntime.rawHeartRateSamples()
                        rrIntervals = CardioSensorRuntime.rawRrIntervals()
                        var enriched = summary?.let(session::withCardioHeartRateSummary) ?: session
                        enriched = CardioGpsRuntime.enrichSession(enriched, telemetry)
                        enriched
                    }
                    if (result.success && result.session != null) {
                        nof1Repository.persistLiveTelemetry(
                            result.session,
                            heartRateSamples,
                            rrIntervals,
                            telemetry
                        )
                        CardioSensorRuntime.stopSession(result.session.endedAt)
                        CardioGpsRuntime.stop(result.session.endedAt)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@launch
                    }
                }
            }

            val draft = store.load().draft
            if (draft == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                val timing = controller.timing(draft)
                val label = when (draft.phase) {
                    CardioLivePhase.RECORDING -> "Recording"
                    CardioLivePhase.PAUSED -> "Paused"
                    CardioLivePhase.FINISHING -> "Ready to save"
                }
                startCardioForeground(
                    draft,
                    label + " · " + formatElapsed(timing.activeSeconds)
                )
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureRuntimeOwnership(draft: CardioLiveDraft) {
        if (!CardioSensorRuntime.hasActiveSession()) {
            CardioSensorRuntime.startSession(draft.sessionId, draft.startedAtEpochMs)
            if (draft.phase != CardioLivePhase.RECORDING) {
                CardioSensorRuntime.pauseSession(System.currentTimeMillis())
            }
        }
        if (!CardioGpsRuntime.hasActiveSession(draft.sessionId)) {
            CardioGpsRuntime.restoreSession(
                draft.sessionId,
                draft.activity,
                draft.startedAtEpochMs,
                paused = draft.phase != CardioLivePhase.RECORDING
            )
        }
    }

    private fun startCardioForeground(draft: CardioLiveDraft?, status: String) {
        val notification = buildNotification(draft, status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (
                draft != null &&
                CardioGpsProcessor.gpsEligible(draft.activity) &&
                CardioGpsRuntime.hasPermission()
            ) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notification, types)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val types = if (
                draft != null &&
                CardioGpsProcessor.gpsEligible(draft.activity) &&
                CardioGpsRuntime.hasPermission()
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
            }
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(draft: CardioLiveDraft?, status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, NextShellActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_superhuman)
            .setContentTitle(draft?.activity?.displayName ?: "Project Superhuman Cardio")
            .setContentText(status)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)

        when (draft?.phase) {
            CardioLivePhase.RECORDING -> builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    servicePendingIntent(ACTION_PAUSE, 101)
                ).build()
            )
            CardioLivePhase.PAUSED -> builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    servicePendingIntent(ACTION_RESUME, 102)
                ).build()
            )
            else -> Unit
        }

        if (draft != null) {
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_save,
                    "Stop & Save",
                    servicePendingIntent(ACTION_FINISH, 103)
                ).build()
            )
        }

        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, CardioSessionService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Active cardio workout",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps an active Project Superhuman cardio session recoverable in the background."
                setShowBadge(false)
            }
        )
    }

    private fun formatElapsed(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val secs = safe % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    companion object {
        const val ACTION_START = "com.projectsuperhuman.next.cardio.START"
        const val ACTION_PAUSE = "com.projectsuperhuman.next.cardio.PAUSE"
        const val ACTION_RESUME = "com.projectsuperhuman.next.cardio.RESUME"
        const val ACTION_FINISH = "com.projectsuperhuman.next.cardio.FINISH"

        private const val CHANNEL_ID = "cardio_active_session"
        private const val NOTIFICATION_ID = 11320
    }
}
