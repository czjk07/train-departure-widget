package com.czjk.trainwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import java.time.LocalDate
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
        private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
        private const val MILLIS_PER_SECOND = 1_000L

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
            val state = Schedule.stateAt(LocalTime.now(), LocalDate.now().dayOfWeek)
            val views = buildViews(context, state)
            manager.updateAppWidget(appWidgetIds, views)
        }

        private fun buildViews(context: Context, state: WidgetState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_departure)
            val res = context.resources
            views.setTextViewText(R.id.label, res.getString(R.string.label_catch_train))
            views.setOnClickPendingIntent(R.id.widget_root, tickPendingIntent(context))

            if (state.mode == WidgetMode.TRAIN) {
                views.setTextViewText(
                    R.id.train_time,
                    TIME_FORMAT.format(state.trainTime),
                )
                views.setTextColor(R.id.train_time, res.getColor(R.color.text))
                views.setTextViewTextSize(R.id.train_time, TypedValue.COMPLEX_UNIT_SP, 44f)
                views.setTextViewText(
                    R.id.sub,
                    if (state.isLastTrain) {
                        res.getString(R.string.sub_last_train)
                    } else {
                        res.getString(
                            R.string.sub_next_after,
                            TIME_FORMAT.format(state.nextTrainTime),
                        )
                    },
                )
                views.setTextViewText(
                    R.id.foot_left,
                    res.getString(
                        R.string.foot_arrive_wait,
                        TIME_FORMAT.format(state.arriveAtStop),
                        state.waitMinutes,
                    ),
                )
                bindBusCard(views, context, state.bus1, R.id.bus1_card, R.id.bus1_name, R.id.bus1_countdown, R.id.bus1_dep, R.id.bus1_seat)
                bindBusCard(views, context, state.bus2, R.id.bus2_card, R.id.bus2_name, R.id.bus2_countdown, R.id.bus2_dep, R.id.bus2_seat)
            } else {
                views.setTextViewText(
                    R.id.train_time,
                    res.getString(
                        if (state.mode == WidgetMode.WEEKEND) {
                            R.string.weekend_msg
                        } else {
                            R.string.no_trains_msg
                        },
                    ),
                )
                views.setTextColor(R.id.train_time, res.getColor(R.color.text))
                views.setTextViewTextSize(R.id.train_time, TypedValue.COMPLEX_UNIT_SP, 16f)
                views.setTextViewText(R.id.sub, "")
                views.setTextViewText(R.id.foot_left, "")
                views.setTextViewText(R.id.bus1_name, "")
                views.setTextViewText(R.id.bus1_countdown, "")
                views.setTextViewText(R.id.bus1_dep, "")
                views.setTextViewText(R.id.bus1_seat, "")
                views.setTextViewText(R.id.bus2_name, "")
                views.setTextViewText(R.id.bus2_countdown, "")
                views.setTextViewText(R.id.bus2_dep, "")
                views.setTextViewText(R.id.bus2_seat, "")
            }

            views.setTextViewText(R.id.foot_clock, CLOCK_FORMAT.format(LocalTime.now()))
            return views
        }

        private fun bindBusCard(
            views: RemoteViews,
            context: Context,
            card: BusCard?,
            cardRootId: Int,
            nameId: Int,
            countdownId: Int,
            departsId: Int,
            seatId: Int,
        ) {
            if (card == null) return
            val res = context.resources
            views.setTextViewText(nameId, res.getString(R.string.bus_name, card.route))
            if (card.catchable) {
                views.setTextViewText(countdownId, formatCountdown(card.countdownSeconds))
                views.setTextViewTextSize(countdownId, TypedValue.COMPLEX_UNIT_SP, 26f)
                views.setTextColor(countdownId, slackColorRes(card.slackColor))
                views.setInt(cardRootId, "setBackgroundResource", R.drawable.bus_card)
                views.setTextViewText(
                    departsId,
                    res.getString(R.string.departs, TIME_FORMAT.format(card.departs)),
                )
                views.setTextViewText(
                    seatId,
                    res.getString(R.string.out_of_seat, TIME_FORMAT.format(card.outOfSeatBy)),
                )
            } else {
                views.setTextViewText(countdownId, res.getString(R.string.cant_make_it))
                views.setTextViewTextSize(countdownId, TypedValue.COMPLEX_UNIT_SP, 16f)
                views.setTextColor(countdownId, res.getColor(R.color.off))
                views.setInt(cardRootId, "setBackgroundResource", R.drawable.bus_card_off)
                views.setTextViewText(
                    departsId,
                    res.getString(R.string.departed_or_soon, TIME_FORMAT.format(card.departs)),
                )
                views.setTextViewText(seatId, "")
            }
        }

        private fun slackColorRes(color: SlackColor): Int = when (color) {
            SlackColor.OK -> R.color.ok
            SlackColor.WARN -> R.color.warn
            SlackColor.BAD -> R.color.bad
            SlackColor.OFF -> R.color.off
        }

        private fun formatCountdown(totalSeconds: Long): String {
            val s = totalSeconds.coerceAtLeast(0)
            return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
        }

        private fun scheduleNextTick(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val now = System.currentTimeMillis()
            val nextSecondBoundary = now - now % MILLIS_PER_SECOND + MILLIS_PER_SECOND
            val pendingIntent = tickPendingIntent(context)
            val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextSecondBoundary,
                    pendingIntent,
                )
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    nextSecondBoundary,
                    MILLIS_PER_SECOND,
                    pendingIntent,
                )
            }
        }

        private fun cancelTick(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.cancel(tickPendingIntent(context))
        }
    }
}
