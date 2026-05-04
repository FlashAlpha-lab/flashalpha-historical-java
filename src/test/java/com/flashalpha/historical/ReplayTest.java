package com.flashalpha.historical;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.*;

/** Unit tests for {@link Replay} — calendar logic and helpers. */
public class ReplayTest {

    @Test
    public void isTradingDay_AcceptsWeekday() {
        assertTrue(Replay.isTradingDay(LocalDate.of(2024, 1, 2)));   // Tuesday
    }

    @Test
    public void isTradingDay_RejectsWeekends() {
        assertFalse(Replay.isTradingDay(LocalDate.of(2024, 1, 6)));  // Saturday
        assertFalse(Replay.isTradingDay(LocalDate.of(2024, 1, 7)));  // Sunday
    }

    @Test
    public void isTradingDay_RejectsKnownHolidays() {
        assertFalse(Replay.isTradingDay(LocalDate.of(2024, 1, 1)));   // New Year
        assertFalse(Replay.isTradingDay(LocalDate.of(2024, 12, 25))); // Christmas
        assertFalse(Replay.isTradingDay(LocalDate.of(2024, 7, 4)));   // July 4
    }

    @Test
    public void iterDays_SkipsWeekendsAndHolidays() {
        List<LocalDateTime> days = Replay.iterDays(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 8));
        assertEquals(5, days.size());
        assertEquals(LocalDate.of(2024, 1, 2), days.get(0).toLocalDate());
        assertEquals(LocalDate.of(2024, 1, 8), days.get(4).toLocalDate());
        for (LocalDateTime d : days) {
            assertEquals(LocalTime.of(16, 0), d.toLocalTime());
        }
    }

    @Test
    public void iterMinutes_DefaultStep_Yields391Stamps() {
        List<LocalDateTime> minutes = Replay.iterMinutes(
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2), 1);
        assertEquals(391, minutes.size());
        assertEquals(LocalTime.of(9, 30), minutes.get(0).toLocalTime());
        assertEquals(LocalTime.of(16, 0), minutes.get(minutes.size() - 1).toLocalTime());
    }

    @Test
    public void iterMinutes_30MinStep_Yields14Stamps() {
        List<LocalDateTime> minutes = Replay.iterMinutes(
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2), 30);
        assertEquals(14, minutes.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void iterMinutes_RejectsZeroStep() {
        Replay.iterMinutes(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2), 0);
    }
}
