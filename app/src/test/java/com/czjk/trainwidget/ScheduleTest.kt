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
    fun `at 15_50 shows the 16_34 train`() {
        val state = Schedule.stateAt(LocalTime.of(15, 50), DayOfWeek.MONDAY)

        assertEquals(WidgetMode.TRAIN, state.mode)
        assertEquals(LocalTime.of(16, 34), state.trainTime)
        assertEquals(LocalTime.of(17, 4), state.nextTrainTime)
        assertFalse(state.isLastTrain)
    }

    @Test
    fun `at 15_50 bus 1 countdown is 31_00 with green slack`() {
        val state = Schedule.stateAt(LocalTime.of(15, 50), DayOfWeek.MONDAY)

        val bus1 = state.bus1!!
        assertTrue(bus1.catchable)
        assertEquals(LocalTime.of(16, 21), bus1.departs)
        assertEquals(LocalTime.of(16, 18), bus1.outOfSeatBy)
        assertEquals(1860L, bus1.countdownSeconds)
        assertEquals(780L, bus1.slackSeconds)
        assertEquals(SlackColor.OK, bus1.slackColor)
    }

    @Test
    fun `at 15_50 bus 2 countdown is 34_00`() {
        val state = Schedule.stateAt(LocalTime.of(15, 50), DayOfWeek.MONDAY)

        val bus2 = state.bus2!!
        assertTrue(bus2.catchable)
        assertEquals(2040L, bus2.countdownSeconds)
        assertEquals(960L, bus2.slackSeconds)
        assertEquals(SlackColor.OK, bus2.slackColor)
    }

    @Test
    fun `at 15_50 arrival is via bus 1 with 9 minute wait`() {
        val state = Schedule.stateAt(LocalTime.of(15, 50), DayOfWeek.MONDAY)

        assertEquals(LocalTime.of(16, 25), state.arriveAtStop)
        assertEquals(9L, state.waitMinutes)
    }

    @Test
    fun `at 15_36 the 16_04 train is catchable only via bus 2`() {
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
    fun `at 15_36_01 the 16_04 train is lost by one second`() {
        val state = Schedule.stateAt(LocalTime.of(15, 36, 1), DayOfWeek.MONDAY)

        assertEquals(LocalTime.of(16, 34), state.trainTime)
    }

    @Test
    fun `slack colors follow thresholds`() {
        fun slackAt(minutes: Long): SlackColor =
            Schedule.stateAt(
                LocalTime.of(16, 21).minusSeconds(minutes * 60 + Schedule.NEED_SECONDS),
                DayOfWeek.MONDAY,
            ).bus1!!.slackColor

        assertEquals(SlackColor.OK, slackAt(11))
        assertEquals(SlackColor.WARN, slackAt(10))
        assertEquals(SlackColor.WARN, slackAt(4))
        assertEquals(SlackColor.BAD, slackAt(3))
    }

    @Test
    fun `at 18_07 the last train is catchable via bus 2 only`() {
        val state = Schedule.stateAt(LocalTime.of(18, 7), DayOfWeek.MONDAY)

        assertEquals(LocalTime.of(18, 34), state.trainTime)
        assertTrue(state.isLastTrain)
        assertNull(state.nextTrainTime)
        assertFalse(state.bus1!!.catchable)
        assertTrue(state.bus2!!.catchable)
        assertEquals(LocalTime.of(18, 29), state.arriveAtStop)
        assertEquals(5L, state.waitMinutes)
    }

    @Test
    fun `at 18_08 no more trains today`() {
        val state = Schedule.stateAt(LocalTime.of(18, 8), DayOfWeek.MONDAY)

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
