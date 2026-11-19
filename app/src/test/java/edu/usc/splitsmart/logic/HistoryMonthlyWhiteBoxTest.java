package edu.usc.splitsmart.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.ExpenseShare;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.SplitSmartDatabase;
import edu.usc.splitsmart.repository.SqliteGroupRepository;
import edu.usc.splitsmart.repository.SqliteUserRepository;

/**
 * White-box tests for history and monthly summaries (Project 2.4, Ariana). Inputs are picked
 * from the branches in {@link DefaultGroupHandler#monthlyStatistics},
 * {@link DefaultGroupHandler#getMonthlyStatistics}, and
 * {@link DefaultUserGroupBalanceHandler#history}. The handler test runs against the real seeded
 * SQLite repositories, so the month query in SqliteGroupRepository is exercised too.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class HistoryMonthlyWhiteBoxTest {
    /** Seeded accounts and group, from SampleData. Priya is not in Apartment 4B. */
    private static final String TOMMY = "u-tommy";
    private static final String ALEX = "u-alex";
    private static final String SAM = "u-sam";
    private static final String APARTMENT = "g-apartment";
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00Z");

    private Context context;
    private SplitSmartDatabase database;
    private SqliteUserRepository users;
    private SqliteGroupRepository groups;
    private SessionStore sessions;
    private DefaultGroupHandler groupHandler;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("splitsmart.db");
        database = new SplitSmartDatabase(context);
        users = new SqliteUserRepository(database);
        groups = new SqliteGroupRepository(database);
        sessions = new SessionStore(context);
        sessions.clear();
        groupHandler = new DefaultGroupHandler(users, groups,
                new DefaultSessionHandler(users, sessions));
    }

    @After
    public void tearDown() {
        sessions.clear();
        database.close();
        context.deleteDatabase("splitsmart.db");
    }

    /**
     * WB-21. The expense loop in monthlyStatistics run 0, 1, and many times, with both sides of
     * the "is this expense in the month" branch. The many case puts expenses on the first and
     * last day of October (inside) and on September 30 and November 1 (just outside), and two
     * Food expenses so the per-category merge adds to an existing entry as well as making one.
     */
    @Test
    public void monthlyStatisticsLoopCountsOnlyTheMonthAndMergesCategories() {
        MonthlyStatistics none = DefaultGroupHandler.monthlyStatistics(OCTOBER,
                Collections.emptyList());
        assertEquals(OCTOBER, none.getMonth());
        assertEquals(0, none.getExpenseCount());
        assertEquals(0, none.getTotalCents());
        assertTrue(none.getSpendingByCategory().isEmpty());

        MonthlyStatistics one = DefaultGroupHandler.monthlyStatistics(OCTOBER,
                Collections.singletonList(expense("e1", 1250, Category.HOUSING, 10, 15)));
        assertEquals(1, one.getExpenseCount());
        assertEquals(1250, one.getTotalCents());
        assertEquals(Collections.singletonMap(Category.HOUSING, 1250L),
                one.getSpendingByCategory());

        MonthlyStatistics many = DefaultGroupHandler.monthlyStatistics(OCTOBER, Arrays.asList(
                expense("first", 1000, Category.FOOD, 10, 1),
                expense("last", 500, Category.FOOD, 10, 31),
                expense("rent", 80000, Category.HOUSING, 10, 20),
                expense("dayBefore", 9999, Category.ENTERTAINMENT, 9, 30),
                expense("dayAfter", 7777, Category.TRANSPORTATION, 11, 1)));
        assertEquals(3, many.getExpenseCount());
        assertEquals(81500, many.getTotalCents());
        Map<Category, Long> byCategory = many.getSpendingByCategory();
        assertEquals(Long.valueOf(1500), byCategory.get(Category.FOOD));
        assertEquals(Long.valueOf(80000), byCategory.get(Category.HOUSING));
        assertFalse(byCategory.containsKey(Category.ENTERTAINMENT));
        assertFalse(byCategory.containsKey(Category.TRANSPORTATION));
    }

    /**
     * WB-22. The three paths through getMonthlyStatistics: nobody logged in, logged in but not a
     * member, and a member. The login check must come first, so a logged-out call fails with
     * NOT_LOGGED_IN even for a group that doesn't exist. The member case reads through the real
     * month query, with expenses on both boundary days of October and one on each side.
     */
    @Test
    public void getMonthlyStatisticsChecksLoginThenMembershipBeforeReading() {
        groups.saveExpense(expense("first", 1000, Category.FOOD, 10, 1));
        groups.saveExpense(expense("last", 2500, Category.HOUSING, 10, 31));
        groups.saveExpense(expense("sep30", 4000, Category.FOOD, 9, 30));
        groups.saveExpense(expense("nov1", 8000, Category.FOOD, 11, 1));

        assertError(SplitSmartException.Code.NOT_LOGGED_IN, HistoryMonthlyWhiteBoxTest
                .<MonthlyStatistics>await(cb -> groupHandler.getMonthlyStatistics(
                        APARTMENT, OCTOBER, cb)));
        assertError(SplitSmartException.Code.NOT_LOGGED_IN, HistoryMonthlyWhiteBoxTest
                .<MonthlyStatistics>await(cb -> groupHandler.getMonthlyStatistics(
                        "g-missing", OCTOBER, cb)));

        sessions.setCurrentUser(users.findByEmail("priya@usc.edu"));
        assertError(SplitSmartException.Code.NOT_A_MEMBER, HistoryMonthlyWhiteBoxTest
                .<MonthlyStatistics>await(cb -> groupHandler.getMonthlyStatistics(
                        APARTMENT, OCTOBER, cb)));

        sessions.setCurrentUser(users.findByEmail("tommy@usc.edu"));
        MonthlyStatistics october = HistoryMonthlyWhiteBoxTest.<MonthlyStatistics>await(
                cb -> groupHandler.getMonthlyStatistics(APARTMENT, OCTOBER, cb)).value();
        assertEquals(2, october.getExpenseCount());
        assertEquals(3500, october.getTotalCents());
        assertEquals(Long.valueOf(1000), october.getSpendingByCategory().get(Category.FOOD));
        assertEquals(Long.valueOf(2500), october.getSpendingByCategory().get(Category.HOUSING));
    }

    /**
     * WB-23. history() runs its expense loop and its settlement loop 0 or more times each and
     * then sorts newest first. Cases: both lists empty, expenses only, and expenses and
     * settlements interleaved in time, including an expense and a settlement recorded at the
     * same instant. The sort is stable, so on a tie the expense (added first) stays first.
     */
    @Test
    public void historyMergesBothLoopsNewestFirstAndKeepsTiesInOrder() {
        assertTrue(DefaultUserGroupBalanceHandler.history(Collections.emptyList(),
                Collections.emptyList()).isEmpty());

        Expense older = expenseAt("older", T0);
        Expense newer = expenseAt("newer", T0.plusSeconds(60));
        List<ActivityItem> expensesOnly = DefaultUserGroupBalanceHandler.history(
                Arrays.asList(older, newer), Collections.emptyList());
        assertEquals(2, expensesOnly.size());
        assertSame(newer, expensesOnly.get(0).getExpense());
        assertSame(older, expensesOnly.get(1).getExpense());

        Settlement middle = settlementAt("s-middle", T0.plusSeconds(30));
        Settlement newest = settlementAt("s-newest", T0.plusSeconds(120));
        Expense tieExpense = expenseAt("tie", T0.plusSeconds(90));
        Settlement tieSettlement = settlementAt("s-tie", T0.plusSeconds(90));
        List<ActivityItem> mixed = DefaultUserGroupBalanceHandler.history(
                Arrays.asList(older, newer, tieExpense),
                Arrays.asList(middle, newest, tieSettlement));
        assertEquals(6, mixed.size());
        assertSame(newest, mixed.get(0).getSettlement());
        assertSame(tieExpense, mixed.get(1).getExpense());
        assertSame(tieSettlement, mixed.get(2).getSettlement());
        assertSame(newer, mixed.get(3).getExpense());
        assertSame(middle, mixed.get(4).getSettlement());
        assertSame(older, mixed.get(5).getExpense());
        assertEquals(ActivityItem.Type.SETTLEMENT, mixed.get(0).getType());
        assertEquals(ActivityItem.Type.EXPENSE, mixed.get(1).getType());
    }

    /** An Apartment 4B expense paid by Tommy and split equally with Alex, on 2026-month-day. */
    private static Expense expense(String id, long totalCents, Category category, int month,
                                   int day) {
        long half = totalCents / 2;
        return new Expense(id, APARTMENT, TOMMY, TOMMY, id, "", totalCents, category,
                SplitType.EQUAL, LocalDate.of(2026, month, day), T0,
                Arrays.asList(new ExpenseShare(id, TOMMY, totalCents - half),
                        new ExpenseShare(id, ALEX, half)));
    }

    private static Expense expenseAt(String id, Instant createdAt) {
        return new Expense(id, APARTMENT, TOMMY, TOMMY, id, "", 1000, Category.FOOD,
                SplitType.EQUAL, LocalDate.of(2026, 10, 15), createdAt,
                Collections.singletonList(new ExpenseShare(id, TOMMY, 1000)));
    }

    private static Settlement settlementAt(String id, Instant settledAt) {
        return new Settlement(id, APARTMENT, SAM, SAM, TOMMY, 500, "", settledAt);
    }

    private static SplitSmartException assertError(SplitSmartException.Code code, Result<?> result) {
        assertTrue("Expected " + code + " but got " + result.error,
                result.error instanceof SplitSmartException);
        SplitSmartException error = (SplitSmartException) result.error;
        assertEquals(code, error.getCode());
        return error;
    }

    private static <T> Result<T> await(Consumer<Callback<T>> operation) {
        Result<T> result = new Result<>();
        operation.accept(result);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (result.calls == 0 && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertEquals("Expected exactly one callback", 1, result.calls);
        return result;
    }

    private static final class Result<T> implements Callback<T> {
        private int calls;
        private T result;
        private Exception error;

        @Override
        public void onSuccess(T value) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            calls++;
            result = value;
        }

        @Override
        public void onError(Exception failure) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            calls++;
            error = failure;
        }

        T value() {
            if (error != null) throw new AssertionError("Unexpected handler failure", error);
            return result;
        }
    }
}
