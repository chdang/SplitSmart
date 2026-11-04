package edu.usc.splitsmart.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;

import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.ExpenseShare;
import edu.usc.splitsmart.model.SplitType;

/** The R17 summary math. The SQL month filter needs a device, so it isn't covered here. */
public class MonthlyStatisticsTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    private int nextId;

    @Test
    public void countsTotalsAndGroupsByCategory() {
        MonthlyStatistics stats = DefaultGroupHandler.monthlyStatistics(MARCH, Arrays.asList(
                expense(LocalDate.of(2026, 3, 2), 1250, Category.FOOD),
                expense(LocalDate.of(2026, 3, 15), 120000, Category.HOUSING),
                expense(LocalDate.of(2026, 3, 31), 799, Category.FOOD)));

        assertEquals(MARCH, stats.getMonth());
        assertEquals(3, stats.getExpenseCount());
        assertEquals(122049L, stats.getTotalCents());
        assertEquals(2049L, (long) stats.getSpendingByCategory().get(Category.FOOD));
        assertEquals(120000L, (long) stats.getSpendingByCategory().get(Category.HOUSING));
        assertFalse(stats.getSpendingByCategory().containsKey(Category.ENTERTAINMENT));
    }

    @Test
    public void ignoresExpensesFromOtherMonths() {
        MonthlyStatistics stats = DefaultGroupHandler.monthlyStatistics(MARCH, Arrays.asList(
                expense(LocalDate.of(2026, 2, 28), 500, Category.FOOD),
                expense(LocalDate.of(2026, 3, 1), 300, Category.TRANSPORTATION),
                expense(LocalDate.of(2026, 4, 1), 700, Category.FOOD),
                expense(LocalDate.of(2025, 3, 10), 900, Category.FOOD)));

        assertEquals(1, stats.getExpenseCount());
        assertEquals(300L, stats.getTotalCents());
        assertEquals(1, stats.getSpendingByCategory().size());
    }

    @Test
    public void emptyMonthIsAllZero() {
        MonthlyStatistics stats =
                DefaultGroupHandler.monthlyStatistics(MARCH, Collections.emptyList());

        assertEquals(0, stats.getExpenseCount());
        assertEquals(0L, stats.getTotalCents());
        assertEquals(0, stats.getSpendingByCategory().size());
    }

    private Expense expense(LocalDate date, long totalCents, Category category) {
        String id = "e" + nextId++;
        return new Expense(id, "g", "u-ana", "u-ana", "test", "", totalCents, category,
                SplitType.EQUAL, date, Instant.EPOCH,
                Collections.singletonList(new ExpenseShare(id, "u-ana", totalCents)));
    }
}
