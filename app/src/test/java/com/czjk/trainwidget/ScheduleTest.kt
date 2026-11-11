package com.czjk.trainwidget

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleTest {

    @Test
    fun `at 15_50 shows the 16_34 train`() {
        val state = Schedule.stateAt(LocalTime.of(15, 50))

        assertEquals(LocalTime.of(16, 34), state.train?.time)
        assertEquals(31L, state.bus1?.minutesUntil)
        assertEquals(34L, state.bus2?.minutesUntil)
        assertFalse(state.bus1?.departed ?: true)
        assertFalse(state.bus2?.departed ?: true)
    }

    @Test
    fun `at 15_39 the 16_04 train is still catchable via bus 2`() {
        val state = Schedule.stateAt(LocalTime.of(15, 39))

        assertEquals(LocalTime.of(16, 4), state.train?.time)
        assertEquals(12L, state.bus1?.minutesUntil)
        assertEquals(15L, state.bus2?.minutesUntil)
    }

    @Test
    fun `at 15_40 the 16_04 train is already lost`() {
        val state = Schedule.stateAt(LocalTime.of(15, 40))

        assertEquals(LocalTime.of(16, 34), state.train?.time)
    }

    @Test
    fun `at 15_36 the 16_04 train is catchable via bus 1`() {
        val state = Schedule.stateAt(LocalTime.of(15, 36))

        assertEquals(LocalTime.of(16, 4), state.train?.time)
        assertEquals(15L, state.bus1?.minutesUntil)
    }

    @Test
    fun `before the first train shows the first train of the day`() {
        val state = Schedule.stateAt(LocalTime.of(10, 0))

        assertEquals(LocalTime.of(16, 4), state.train?.time)
        assertEquals(351L, state.bus1?.minutesUntil)
    }

    @Test
    fun `at 16_30 rolls to the 17_04 train`() {
        val state = Schedule.stateAt(LocalTime.of(16, 30))

        assertEquals(LocalTime.of(17, 4), state.train?.time)
        assertEquals(21L, state.bus1?.minutesUntil)
        assertEquals(24L, state.bus2?.minutesUntil)
    }

    @Test
    fun `at 18_10 the last train is still catchable`() {
        val state = Schedule.stateAt(LocalTime.of(18, 10))

        assertEquals(LocalTime.of(18, 34), state.train?.time)
        assertEquals(12L, state.bus1?.minutesUntil)
        assertEquals(15L, state.bus2?.minutesUntil)
    }

    @Test
    fun `at 18_11 no train is catchable`() {
        val state = Schedule.stateAt(LocalTime.of(18, 11))

        assertNull(state.train)
        assertNull(state.bus1)
        assertNull(state.bus2)
        assertFalse(state.catchable)
    }

    @Test
    fun `late at night no train is catchable`() {
        val state = Schedule.stateAt(LocalTime.of(23, 0))

        assertNull(state.train)
        assertFalse(state.catchable)
    }

    @Test
    fun `at 16_09 the 16_34 train is catchable exactly at the bus 2 boundary`() {
        val state = Schedule.stateAt(LocalTime.of(16, 9))

        assertEquals(LocalTime.of(16, 34), state.train?.time)
        assertEquals(12L, state.bus1?.minutesUntil)
        assertEquals(15L, state.bus2?.minutesUntil)
        assertFalse(state.bus1?.departed ?: true)
        assertFalse(state.bus2?.departed ?: true)
    }

    @Test
    fun `at 16_10 the 16_34 train is lost`() {
        val state = Schedule.stateAt(LocalTime.of(16, 10))

        assertEquals(LocalTime.of(17, 4), state.train?.time)
    }
}
