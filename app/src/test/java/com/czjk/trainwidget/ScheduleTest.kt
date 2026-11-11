package com.czjk.trainwidget

import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {

    @Test
    fun `at 15_50 shows the 16_04 train until its second bus departs`() {
        val state = Schedule.stateAt(LocalTime.of(15, 50), DayOfWeek.MONDAY)

        assertEquals(WidgetMode.TRAIN, state.mode)
        assertEquals(LocalTime.of(16, 4), state.trainTime)
        assertEquals(LocalTime.of(16, 34), state.nextTrainTime)
        assertNull(state.arriveAtStop)
        assertNull(state.waitMinutes)
    }

    @Test
    fun `at 16_13 still shows the 16_34 train`() {
        val state = Schedule.stateAt(LocalTime.of(16, 13), DayOfWeek.MONDAY)

        assertEquals(LocalTime.of(16, 34), state.trainTime)
        assertFalse(state.bus1!!.catchable)
        assertFalse(state.bus2!!.catchable)
        assertEquals(480L, state.bus1!!.countdownSeconds)
        assertEquals(660L, state.bus2!!.countdownSeconds)
    }

    @Test
    fun `at 16_24 the 16_34 train still shows at zero countdown`() {
        val state = Schedule.stateAt(LocalTime.of(16, 24), DayOfWeek.MONDAY)

        assertEquals(LocalTime.of(16, 34), state.trainTime)
        assertEquals(0L, state.bus2!!.countdownSeconds)
    }

    @Test
    fun `at 16_24_01 rolls to the 17_04 train`() {
        val state = Schedule.stateAt(LocalTime.of(16, 24, 1), DayOfWeek.MONDAY)

        assertEquals(LocalTime.of(17, 4), state.trainTime)
        assertEquals(LocalTime.of(17, 34), state.nextTrainTime)
    }

    @Test
    fun `at 15_36 the 16_04 train is catchable via bus 2`() {
        val state = Schedule.stateAt(LocalTime.of(15, 36), DayOfWeek.MONDAY)

        assertEquals(LocalTime.of(16, 4), state.trainTime)
        assertFalse(state.bus1!!.catchable)
        assertEquals(900L, state.bus1!!.countdownSeconds)
        assertEquals(SlackColor.OFF, state.bus1!!.slackColor)
        assertTrue(state.bus2!!.catchable)
        assertEquals(1080L, state.bus2!!.countdownSeconds)
        assertEquals(SlackColor.BAD, state.bus2!!.slackColor)
        assertEquals(LocalTime.of(15, 58), state.arriveAtStop)
        assertEquals(6L, state.waitMinutes)
    }

    @Test
    fun `at 15_50 bus countdowns are 1_00 and 4_00`() {
        val state = Schedule.stateAt(LocalTime.of(15, 50), DayOfWeek.MONDAY)

        assertEquals(60L, state.bus1!!.countdownSeconds)
        assertEquals(240L, state.bus2!!.countdownSeconds)
        assertEquals(LocalTime.of(15, 48), state.bus1!!.outOfSeatBy)
        assertEquals(LocalTime.of(15, 51), state.bus2!!.outOfSeatBy)
    }

    @Test
    fun `slack colors follow thresholds`() {
        val early = Schedule.stateAt(LocalTime.of(10, 0), DayOfWeek.MONDAY)
        assertEquals(SlackColor.OK, early.bus1!!.slackColor)

        val warnZone = Schedule.stateAt(LocalTime.of(15, 57), DayOfWeek.MONDAY)
        assertEquals(SlackColor.WARN, warnZone.bus1!!.slackColor)

        val badZone = Schedule.stateAt(LocalTime.of(16, 0), DayOfWeek.MONDAY)
        assertEquals(SlackColor.BAD, badZone.bus1!!.slackColor)

        val missed = Schedule.stateAt(LocalTime.of(16, 13), DayOfWeek.MONDAY)
        assertEquals(SlackColor.OFF, missed.bus1!!.slackColor)
    }

    @Test
    fun `at 18_25 the last train still shows at zero countdown`() {
        val state = Schedule.stateAt(LocalTime.of(18, 25), DayOfWeek.MONDAY)

        assertEquals(LocalTime.of(18, 34), state.trainTime)
        assertTrue(state.isLastTrain)
        assertNull(state.nextTrainTime)
        assertEquals(0L, state.bus2!!.countdownSeconds)
    }

    @Test
    fun `at 18_25_01 no more trains today`() {
        val state = Schedule.stateAt(LocalTime.of(18, 25, 1), DayOfWeek.MONDAY)

        assertEquals(WidgetMode.NO_TRAINS, state.mode)
        assertNull(state.trainTime)
    }

    @Test
    fun `saturday shows weekend mode`() {
        val state = Schedule.stateAt(LocalTime.of(15, 50), DayOfWeek.SATURDAY)

        assertEquals(WidgetMode.WEEKEND, state.mode)
        assertNull(state.trainTime)
    }

    @Test
    fun `sunday shows weekend mode`() {
        val state = Schedule.stateAt(LocalTime.of(15, 50), DayOfWeek.SUNDAY)

        assertEquals(WidgetMode.WEEKEND, state.mode)
    }

    @Test
    fun `before the first train shows the first train of the day`() {
        val state = Schedule.stateAt(LocalTime.of(10, 0), DayOfWeek.MONDAY)

        assertEquals(LocalTime.of(16, 4), state.trainTime)
        assertEquals(LocalTime.of(16, 34), state.nextTrainTime)
        assertEquals(351 * 60L, state.bus1!!.countdownSeconds)
    }
}
