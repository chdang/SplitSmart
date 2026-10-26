package edu.usc.splitsmart.ui.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formats dates for display. */
public final class Dates {
    private static final DateTimeFormatter SHORT =
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault());
    private static final DateTimeFormatter WITH_YEAR =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault());
    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault());

    private Dates() {
    }

    /** "Oct 12", or "Oct 12, 2025" if it isn't this year. */
    public static String shortDate(LocalDate date) {
        return date.getYear() == LocalDate.now().getYear() ? SHORT.format(date)
                : WITH_YEAR.format(date);
    }

    public static String shortDate(Instant instant) {
        return shortDate(instant.atZone(ZoneId.systemDefault()).toLocalDate());
    }

    /** "Oct 12, 2026". */
    public static String fullDate(LocalDate date) {
        return WITH_YEAR.format(date);
    }

    /** "October 2026". */
    public static String month(YearMonth month) {
        return MONTH.format(month);
    }
}
