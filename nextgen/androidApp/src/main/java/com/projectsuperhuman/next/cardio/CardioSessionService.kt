package com.projectsuperhuman.next

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
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

    override fun onCreate() {
        super.onCreate()
        NativeDataHub.initialize(applicationContext)
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
        startForeground(NOTIFICATION_ID, buildNotification(null, "Cardio session active"))

        scope.launch {
            when (intent?.action) {
                ACTION_PAUSE -> coordinator.pause()
                ACTION_RESUME -> coordinator.resume()
                ACTION_FINISH -> {
                    val result = coordinator.quickSave()
                    if (result.success) {
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
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,
                    buildNotification(draft, label + " · " + formatElapsed(timing.activeSeconds))
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
