package edu.usc.splitsmart.logic;

import java.time.YearMonth;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import edu.usc.splitsmart.model.Category;

/** A group's spending in one month, by expense date (R17). Settlements aren't spending. */
public final class MonthlyStatistics {
    private final YearMonth month;
    private final int expenseCount;
    private final long totalCents;
    private final Map<Category, Long> spendingByCategory;

    public MonthlyStatistics(YearMonth month, int expenseCount, long totalCents,
                             Map<Category, Long> spendingByCategory) {
        this.month = month;
        this.expenseCount = expenseCount;
        this.totalCents = totalCents;
        EnumMap<Category, Long> copy = new EnumMap<>(Category.class);
        copy.putAll(spendingByCategory);
        this.spendingByCategory = Collections.unmodifiableMap(copy);
    }

    public YearMonth getMonth() {
        return month;
    }

    public int getExpenseCount() {
        return expenseCount;
    }

    public long getTotalCents() {
        return totalCents;
    }

    /** Cents spent per category. Categories with no spending may be missing. */
    public Map<Category, Long> getSpendingByCategory() {
        return spendingByCategory;
    }
}
