package com.czjk.trainwidget

import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class BusDeparture(val route: String, val time: LocalTime) {
    fun minutesUntil(now: LocalTime): Long = ChronoUnit.MINUTES.between(now, time)
}

data class TrainDeparture(
    val time: LocalTime,
    val bus1: BusDeparture,
    val bus2: BusDeparture,
)

data class BusStatus(
    val departure: BusDeparture,
    val departed: Boolean,
    val minutesUntil: Long,
)

data class WidgetState(
    val train: TrainDeparture?,
    val bus1: BusStatus?,
    val bus2: BusStatus?,
) {
    val catchable: Boolean get() = train != null
}

object Schedule {

    const val LEAD_MINUTES = 15L

    val trains: List<TrainDeparture> = listOf(
        TrainDeparture(
            LocalTime.of(16, 4),
            BusDeparture("114", LocalTime.of(15, 51)),
            BusDeparture("106", LocalTime.of(15, 54)),
        ),
        TrainDeparture(
            LocalTime.of(16, 34),
            BusDeparture("114", LocalTime.of(16, 21)),
            BusDeparture("106", LocalTime.of(16, 24)),
        ),
        TrainDeparture(
            LocalTime.of(17, 4),
            BusDeparture("114", LocalTime.of(16, 51)),
            BusDeparture("106", LocalTime.of(16, 54)),
        ),
        TrainDeparture(
            LocalTime.of(17, 34),
            BusDeparture("114", LocalTime.of(17, 21)),
            BusDeparture("106", LocalTime.of(17, 24)),
        ),
        TrainDeparture(
            LocalTime.of(18, 4),
            BusDeparture("114", LocalTime.of(17, 51)),
            BusDeparture("106", LocalTime.of(17, 54)),
        ),
        TrainDeparture(
            LocalTime.of(18, 34),
            BusDeparture("114", LocalTime.of(18, 22)),
            BusDeparture("106", LocalTime.of(18, 25)),
        ),
    )

    fun stateAt(now: LocalTime): WidgetState {
        val readyBy = now.plusMinutes(LEAD_MINUTES)
        val target = trains.firstOrNull { readyBy <= it.bus2.time || readyBy <= it.bus1.time }
            ?: return WidgetState(null, null, null)
        return WidgetState(
            train = target,
            bus1 = statusFor(now, target.bus1),
            bus2 = statusFor(now, target.bus2),
        )
    }

    private fun statusFor(now: LocalTime, bus: BusDeparture): BusStatus {
        val minutes = bus.minutesUntil(now)
        return BusStatus(
            departure = bus,
            departed = minutes < 0,
            minutesUntil = minutes.coerceAtLeast(0),
        )
    }
}
