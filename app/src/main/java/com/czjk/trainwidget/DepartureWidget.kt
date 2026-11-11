package com.czjk.trainwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class DepartureWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
        scheduleNextTick(context)
    }

    override fun onEnabled(context: Context) {
        scheduleNextTick(context)
    }

    override fun onDisabled(context: Context) {
        cancelTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            val manager = AppWidgetManager.getInstance(context)
            updateWidgets(context, manager, manager.getAppWidgetIds(providerComponent(context)))
            scheduleNextTick(context)
        }
    }

    companion object {
        private const val ACTION_TICK = "com.czjk.trainwidget.ACTION_TICK"
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

        private fun providerComponent(context: Context) =
            ComponentName(context, DepartureWidget::class.java)

        private fun tickPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, DepartureWidget::class.java).setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            val state = Schedule.stateAt(LocalTime.now())
            val views = buildViews(context, state)
            manager.updateAppWidget(appWidgetIds, views)
        }

        private fun buildViews(context: Context, state: WidgetState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_departure)
            val res = context.resources
            views.setTextViewText(
                R.id.train_time,
                state.train?.let { TIME_FORMAT.format(it.time) } ?: res.getString(R.string.no_train_time),
            )
            views.setTextViewText(R.id.bus1_detail, busDetail(context, state.bus1))
            views.setTextViewText(R.id.bus2_detail, busDetail(context, state.bus2))
            views.setOnClickPendingIntent(R.id.widget_root, tickPendingIntent(context))
            views.setTextColor(
                R.id.train_time,
                if (state.catchable) {
                    res.getColor(R.color.train_time)
                } else {
                    res.getColor(R.color.bus_detail)
                },
            )
            return views
        }

        private fun busDetail(context: Context, bus: BusStatus?): String {
            val res = context.resources
            if (bus == null) return res.getString(R.string.no_bus)
            if (bus.departed) return res.getString(R.string.departed)
            val departure = TIME_FORMAT.format(bus.departure.departure.time)
            val countdown = if (bus.minutesUntil <= 0L) {
                res.getString(R.string.departing_now)
            } else {
                res.getString(R.string.minute_format, bus.minutesUntil)
            }
            return res.getString(R.string.bus_detail_format, departure, countdown)
        }

        private fun scheduleNextTick(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val now = System.currentTimeMillis()
            val nextMinuteBoundary = now - now % MILLIS_PER_MINUTE + MILLIS_PER_MINUTE
            val pendingIntent = tickPendingIntent(context)
            val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextMinuteBoundary,
                    pendingIntent,
                )
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    nextMinuteBoundary,
                    MILLIS_PER_MINUTE,
                    pendingIntent,
                )
            }
        }

        private fun cancelTick(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.cancel(tickPendingIntent(context))
        }

        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
