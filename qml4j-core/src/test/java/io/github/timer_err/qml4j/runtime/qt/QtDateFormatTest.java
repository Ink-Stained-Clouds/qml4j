package io.github.timer_err.qml4j.runtime.qt;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QtDateFormatTest {

    // Built in the system zone so formatting back in the system zone is deterministic
    // regardless of the test machine's timezone. 2026-06-08 is a Monday.
    private static final long MILLIS =
        ZonedDateTime.of(2026, 6, 8, 10, 52, 7, 0, ZoneId.systemDefault()).toInstant().toEpochMilli();

    @Test
    void timeTokens() {
        assertEquals("10:52", QtDateFormat.format(MILLIS, "hh:mm"));
        assertEquals("10 52", QtDateFormat.format(MILLIS, "hh mm"));
        assertEquals("10:52:07", QtDateFormat.format(MILLIS, "hh:mm:ss"));
    }

    @Test
    void dateTokens() {
        assertEquals("08", QtDateFormat.format(MILLIS, "dd"));
        assertEquals("8", QtDateFormat.format(MILLIS, "d"));
        assertEquals("Mon", QtDateFormat.format(MILLIS, "ddd"));
        assertEquals("Monday", QtDateFormat.format(MILLIS, "dddd"));
        assertEquals("Jun", QtDateFormat.format(MILLIS, "MMM"));
        assertEquals("8 Jun", QtDateFormat.format(MILLIS, "d MMM"));
        assertEquals("2026-06-08", QtDateFormat.format(MILLIS, "yyyy-MM-dd"));
    }

    // Non-letter characters are literals (Qt reserves letters as field tokens; the MD3
    // widgets' formats only ever separate tokens with spaces/`:`/`-`).
    @Test
    void nonLetterLiteralsPassThroughAndNaNIsEmpty() {
        assertEquals("10:52 / 08", QtDateFormat.format(MILLIS, "hh:mm / dd"));
        assertEquals("", QtDateFormat.format(Double.NaN, "hh:mm"));
    }
}
