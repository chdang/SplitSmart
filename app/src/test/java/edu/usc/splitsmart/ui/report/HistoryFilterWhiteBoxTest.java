package edu.usc.splitsmart.ui.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import edu.usc.splitsmart.logic.ActivityItem;
import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.ExpenseShare;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.model.User;

/**
 * White-box tests for the history screen's search, kind filter, and sort (Project 2.4, Ariana).
 * Inputs are picked from the branches in {@link HistoryFilter}: each case of the kind and sort
 * switches, the empty-query shortcut, and each field the search looks in. Runs under
 * Robolectric only because category names come from string resources.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class HistoryFilterWhiteBoxTest {
    private static final String TOMMY = "u-tommy";
    private static final String ALEX = "u-alex";
    private static final String SAM = "u-sam";
    /** Paid for something, then left the group, so the filter has no name for them. */
    private static final String FORMER = "u-former";
    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00Z");

    private HistoryFilter filter;

    // Recorded in this order, one minute apart. Amounts are all different so sort order is
    // unambiguous: groceries $42.10, rent $1,200.00, settlement $15.00, taxi $8.75 (former
    // member), settlement $300.00.
    private ActivityItem groceries;
    private ActivityItem rent;
    private ActivityItem alexPaysTommy;
    private ActivityItem taxi;
    private ActivityItem samPaysAlex;
    private List<ActivityItem> all;

    @Before
    public void setUp() {
        filter = new HistoryFilter(RuntimeEnvironment.getApplication());
        filter.setMembers(Arrays.asList(
                new User(TOMMY, "Tommy Trojan", "tommy@usc.edu", "1234567890"),
                new User(ALEX, "Alex Kim", "alex@usc.edu", "2345678901"),
                new User(SAM, "Sam Rivera", "sam@usc.edu", "3456789012")));

        groceries = ActivityItem.of(expense("groceries", "Trader Joe's run", "weekly shop",
                4210, Category.FOOD, TOMMY, 0));
        rent = ActivityItem.of(expense("rent", "October rent", null, 120000,
                Category.HOUSING, ALEX, 1));
        alexPaysTommy = ActivityItem.of(settlement("s1", ALEX, TOMMY, 1500, "venmo", 2));
        taxi = ActivityItem.of(expense("taxi", "Airport ride", "", 875,
                Category.TRANSPORTATION, FORMER, 3));
        samPaysAlex = ActivityItem.of(settlement("s2", SAM, ALEX, 30000, null, 4));
        all = Arrays.asList(groceries, rent, alexPaysTommy, taxi, samPaysAlex);
    }

    /**
     * WB-24. Every case of the kind switch and of the sort switch. The kind is checked alone and
     * combined with a query (the two checks are ANDed); whitespace-only text is trimmed to empty
     * and takes the shortcut that matches everything. Payments are sorted on their amount and
     * expenses on their total, so the amount sorts mix the two types. NEWEST is the default
     * before any sort is chosen.
     */
    @Test
    public void kindAndSortSwitchesTakeEveryCase() {
        assertEquals(HistoryFilter.Sort.NEWEST, filter.getSort());
        filter.setQuery("   ");

        filter.setKind(HistoryFilter.Kind.ALL);
        assertEquals(Arrays.asList(samPaysAlex, taxi, alexPaysTommy, rent, groceries),
                filter.apply(all));

        filter.setKind(HistoryFilter.Kind.EXPENSES);
        assertEquals(Arrays.asList(taxi, rent, groceries), filter.apply(all));

        filter.setKind(HistoryFilter.Kind.PAYMENTS);
        assertEquals(Arrays.asList(samPaysAlex, alexPaysTommy), filter.apply(all));

        // "alex" hits rent (Alex paid) and both payments (Alex on one side). The kind narrows it.
        filter.setQuery("alex");
        filter.setKind(HistoryFilter.Kind.EXPENSES);
        assertEquals(Collections.singletonList(rent), filter.apply(all));
        filter.setKind(HistoryFilter.Kind.PAYMENTS);
        assertEquals(Arrays.asList(samPaysAlex, alexPaysTommy), filter.apply(all));

        assertTrue(filter.apply(Collections.emptyList()).isEmpty());

        filter.setQuery("");
        filter.setKind(HistoryFilter.Kind.ALL);
        filter.setSort(HistoryFilter.Sort.OLDEST);
        assertEquals(Arrays.asList(groceries, rent, alexPaysTommy, taxi, samPaysAlex),
                filter.apply(all));

        filter.setSort(HistoryFilter.Sort.LARGEST);
        assertEquals(Arrays.asList(rent, samPaysAlex, groceries, alexPaysTommy, taxi),
                filter.apply(all));

        filter.setSort(HistoryFilter.Sort.SMALLEST);
        assertEquals(Arrays.asList(taxi, alexPaysTommy, groceries, samPaysAlex, rent),
                filter.apply(all));

        filter.setSort(HistoryFilter.Sort.NEWEST);
        assertEquals(Arrays.asList(samPaysAlex, taxi, alexPaysTommy, rent, groceries),
                filter.apply(all));

        // Sorting copies into a new list and leaves the loaded history alone.
        List<ActivityItem> loaded = new ArrayList<>(all);
        filter.apply(loaded);
        assertEquals(all, loaded);
    }

    /**
     * WB-25. The search loop over each field it can look in, so every field is the one that
     * matches at least once: an expense's description, note, category name, and payer, and a
     * payment's note, payer, and receiver. Queries are mixed-case and padded to cover the
     * lower-casing and trimming. Null notes and a former member's missing name must not crash
     * or match, and text that is in no field matches nothing.
     */
    @Test
    public void searchLooksInEveryFieldIgnoringCaseAndSpaces() {
        assertEquals(Collections.singletonList(groceries), search("  TRADER  "));    // description
        assertEquals(Collections.singletonList(groceries), search("Weekly"));        // note
        assertEquals(Collections.singletonList(rent), search("housing"));            // category
        assertEquals(Collections.singletonList(taxi), search("Transport"));          // category
        assertEquals(Collections.singletonList(alexPaysTommy), search("VENMO"));     // payment note
        assertEquals(Collections.singletonList(samPaysAlex), search("rivera"));      // payment payer
        // Tommy paid for groceries (expense payer) and received Alex's payment (receiver).
        assertEquals(Arrays.asList(alexPaysTommy, groceries), search("Tommy"));

        // The former member has no name on file, so their expense only matches on its own text.
        assertTrue(search("former").isEmpty());
        assertEquals(Collections.singletonList(taxi), search("airport"));

        assertTrue(search("zzz-no-such-text").isEmpty());

        // A null query (no text at all) is treated like an empty one and shows every row.
        assertEquals(5, search(null).size());
    }

    private static Expense expense(String id, String description, String note, long totalCents,
                                   Category category, String payerId, int minutesAfterT0) {
        return new Expense(id, "g-apartment", TOMMY, payerId, description, note, totalCents,
                category, SplitType.EQUAL, LocalDate.of(2026, 10, 15),
                T0.plusSeconds(60L * minutesAfterT0),
                Collections.singletonList(new ExpenseShare(id, TOMMY, totalCents)));
    }

    private static Settlement settlement(String id, String payerId, String receiverId,
                                         long amountCents, String note, int minutesAfterT0) {
        return new Settlement(id, "g-apartment", payerId, payerId, receiverId, amountCents, note,
                T0.plusSeconds(60L * minutesAfterT0));
    }

    private List<ActivityItem> search(String text) {
        filter.setKind(HistoryFilter.Kind.ALL);
        filter.setSort(HistoryFilter.Sort.NEWEST);
        filter.setQuery(text);
        return filter.apply(all);
    }
}
