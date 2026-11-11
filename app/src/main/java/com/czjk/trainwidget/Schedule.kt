package com.czjk.trainwidget

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class WidgetMode { WEEKEND, NO_TRAINS, TRAIN }

data class BusCard(
    val route: String,
    val departs: LocalTime,
    val outOfSeatBy: LocalTime,
    val catchable: Boolean,
    val countdownSeconds: Long,
    val slackSeconds: Long,
) {
    val slackColor: SlackColor
        get() = when {
            !catchable -> SlackColor.OFF
            slackSeconds > 600 -> SlackColor.OK
            slackSeconds > 180 -> SlackColor.WARN
            else -> SlackColor.BAD
        }
}

enum class SlackColor { OK, WARN, BAD, OFF }

data class WidgetState(
    val mode: WidgetMode,
    val trainTime: LocalTime? = null,
    val nextTrainTime: LocalTime? = null,
    val isLastTrain: Boolean = false,
    val bus1: BusCard? = null,
    val bus2: BusCard? = null,
    val arriveAtStop: LocalTime? = null,
    val waitMinutes: Long? = null,
)

object Schedule {

    const val BUFFER_MIN = 15L
    const val WALK_MIN = 3L
    const val RIDE_MIN = 4L
    const val NEED_SECONDS = (BUFFER_MIN + WALK_MIN) * 60

    const val BUS1_ROUTE = "114"
    const val BUS2_ROUTE = "106"

    data class Train(val time: LocalTime, val bus1: LocalTime, val bus2: LocalTime)

    val trains: List<Train> = listOf(
        Train(LocalTime.of(16, 4), LocalTime.of(15, 51), LocalTime.of(15, 54)),
        Train(LocalTime.of(16, 34), LocalTime.of(16, 21), LocalTime.of(16, 24)),
        Train(LocalTime.of(17, 4), LocalTime.of(16, 51), LocalTime.of(16, 54)),
        Train(LocalTime.of(17, 34), LocalTime.of(17, 21), LocalTime.of(17, 24)),
        Train(LocalTime.of(18, 4), LocalTime.of(17, 51), LocalTime.of(17, 54)),
        Train(LocalTime.of(18, 34), LocalTime.of(18, 22), LocalTime.of(18, 25)),
    )

    fun stateAt(now: LocalTime, day: DayOfWeek): WidgetState {
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return WidgetState(WidgetMode.WEEKEND)
        }
        val nowS = now.toSecondOfDay().toLong()
        val index = trains.indexOfFirst { it.bus2.toSecondOfDay() - nowS >= 0 }
        if (index < 0) {
            return WidgetState(WidgetMode.NO_TRAINS)
        }
        val target = trains[index]
        val firstCatchable = listOf(target.bus1, target.bus2)
            .firstOrNull { it.toSecondOfDay() - nowS >= NEED_SECONDS }
        val arrive = firstCatchable.plusMinutes(RIDE_MIN)
        return WidgetState(
            mode = WidgetMode.TRAIN,
            trainTime = target.time,
            nextTrainTime = trains.getOrNull(index + 1)?.time,
            isLastTrain = index == trains.lastIndex,
            bus1 = busCard(BUS1_ROUTE, target.bus1, nowS),
            bus2 = busCard(BUS2_ROUTE, target.bus2, nowS),
            arriveAtStop = arrive,
            waitMinutes = ChronoUnit.MINUTES.between(arrive, target.time),
        )
    }

    private fun busCard(route: String, departs: LocalTime, nowS: Long): BusCard {
        val toDep = departs.toSecondOfDay() - nowS
        val catchable = toDep >= NEED_SECONDS
        return BusCard(
            route = route,
            departs = departs,
            outOfSeatBy = departs.minusMinutes(WALK_MIN),
            catchable = catchable,
            countdownSeconds = toDep.coerceAtLeast(0),
            slackSeconds = toDep - NEED_SECONDS,
        )
    }
}
