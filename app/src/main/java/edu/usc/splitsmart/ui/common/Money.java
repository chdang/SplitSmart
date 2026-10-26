package edu.usc.splitsmart.ui.common;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formats and parses amounts for display. Amounts are whole cents in a long, and percentages
 * are basis points (25.5% is 2550), matching the handlers.
 */
public final class Money {
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);

    private Money() {
    }

    /** 1250 becomes "$12.50". */
    public static String format(long cents) {
        return CURRENCY.format(BigDecimal.valueOf(cents, 2));
    }

    /** 1250 becomes "12.50", for putting an amount back into a text field. */
    public static String toInput(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    /**
     * Reads what the user typed, such as "12", "12.5", or "$1,200.00", as cents. Returns null
     * if it isn't a non-negative amount with at most two decimal places.
     */
    @Nullable
    public static Long parseCents(@Nullable CharSequence text) {
        return parseHundredths(text);
    }

    /** 2550 becomes "25.5%". */
    public static String formatPercent(long basisPoints) {
        return percentToInput(basisPoints) + "%";
    }

    /** 2550 becomes "25.5", for putting a percentage back into a text field. */
    public static String percentToInput(long basisPoints) {
        return BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString();
    }

    /** Reads a percentage such as "25" or "33.33" as basis points, or null if it isn't valid. */
    @Nullable
    public static Long parseBasisPoints(@Nullable CharSequence text) {
        return parseHundredths(text);
    }

    @Nullable
    private static Long parseHundredths(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.toString().trim().replace("$", "").replace(",", "")
                .replace("%", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            if (value.signum() < 0 || value.stripTrailingZeros().scale() > 2) {
                return null;
            }
            return value.movePointRight(2).setScale(0).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }
}
