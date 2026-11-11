package com.czjk.trainwidget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object LockScreenNotifier {

    private const val CHANNEL_ID = "train_departure"
    private const val NOTIFICATION_ID = 1

    fun update(context: Context, state: WidgetState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            },
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.apply {
            setSmallIcon(R.drawable.ic_train)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setVisibility(Notification.VISIBILITY_PUBLIC)
            setCategory(Notification.CATEGORY_TRANSPORT)
            when (state.mode) {
                WidgetMode.TRAIN -> {
                    setContentTitle(
                        context.getString(
                            R.string.notif_title,
                            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                                .format(state.trainTime),
                        ),
                    )
                    setContentText(busLines(context, state))
                }
                WidgetMode.WEEKEND -> {
                    setContentTitle(context.getString(R.string.weekend_msg))
                    setContentText("")
                }
                WidgetMode.NO_TRAINS -> {
                    setContentTitle(context.getString(R.string.no_trains_msg))
                    setContentText("")
                }
            }
        }
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun busLines(context: Context, state: WidgetState): String {
        val timeFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        return listOfNotNull(state.bus1, state.bus2)
            .joinToString(" · ") { card ->
                context.getString(
                    R.string.notif_bus_line,
                    card.route,
                    timeFormat.format(card.departs),
                    card.countdownSeconds / 60,
                )
            }
    }
}
