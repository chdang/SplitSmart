package edu.usc.splitsmart.ui.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formats dates for display. */
public final class Dates {
    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);
    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault());

    private Dates() {
    }

    /** "2026-10-12". */
    public static String shortDate(LocalDate date) {
        return ISO_DATE.format(date);
    }

    public static String shortDate(Instant instant) {
        return shortDate(instant.atZone(ZoneId.systemDefault()).toLocalDate());
    }

    /** "2026-10-12". */
    public static String fullDate(LocalDate date) {
        return ISO_DATE.format(date);
    }

    /** "October 2026". */
    public static String month(YearMonth month) {
        return MONTH.format(month);
    }
}
