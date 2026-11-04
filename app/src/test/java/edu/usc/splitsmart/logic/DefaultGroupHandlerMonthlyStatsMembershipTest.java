package edu.usc.splitsmart.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
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
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.SplitSmartDatabase;
import edu.usc.splitsmart.repository.SqliteGroupRepository;
import edu.usc.splitsmart.repository.SqliteUserRepository;

/** Only logged-in group members can see a group's monthly summary (R17). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class DefaultGroupHandlerMonthlyStatsMembershipTest {
    /** Seeded group with Tommy as a member; Priya isn't in it. */
    private static final String GROUP = "g-apartment";
    private static final String TOMMY = "u-tommy";
    private static final String PRIYA = "u-priya";
    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    private Context context;
    private SplitSmartDatabase database;
    private SqliteGroupRepository groups;
    private SqliteUserRepository users;
    private SessionStore sessions;
    private DefaultGroupHandler handler;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("splitsmart.db");
        database = new SplitSmartDatabase(context);
        groups = new SqliteGroupRepository(database);
        users = new SqliteUserRepository(database);
        sessions = new SessionStore(context);
        sessions.clear();
        handler = new DefaultGroupHandler(users, groups,
                new DefaultSessionHandler(users, sessions));
        groups.saveExpense(new Expense("e-groceries", GROUP, TOMMY, TOMMY, "Groceries", "", 4250,
                Category.FOOD, SplitType.EQUAL, LocalDate.of(2026, 3, 12), Instant.EPOCH,
                Collections.singletonList(new ExpenseShare("e-groceries", TOMMY, 4250))));
    }

    @After
    public void tearDown() {
        sessions.clear();
        database.close();
        context.deleteDatabase("splitsmart.db");
    }

    @Test
    public void memberGetsStats() {
        loginAs(TOMMY);

        MonthlyStatistics stats = DefaultGroupHandlerMonthlyStatsMembershipTest.<MonthlyStatistics>await(
                cb -> handler.getMonthlyStatistics(GROUP, MARCH, cb)).value();

        assertEquals(1, stats.getExpenseCount());
        assertEquals(4250L, stats.getTotalCents());
        assertEquals(4250L, (long) stats.getSpendingByCategory().get(Category.FOOD));
    }

    @Test
    public void nonMemberFailsWithNotAMember() {
        loginAs(PRIYA);

        assertError(SplitSmartException.Code.NOT_A_MEMBER,
                DefaultGroupHandlerMonthlyStatsMembershipTest.<MonthlyStatistics>await(
                        cb -> handler.getMonthlyStatistics(GROUP, MARCH, cb)));
    }

    @Test
    public void nobodyLoggedInFailsWithNotLoggedIn() {
        assertError(SplitSmartException.Code.NOT_LOGGED_IN,
                DefaultGroupHandlerMonthlyStatsMembershipTest.<MonthlyStatistics>await(
                        cb -> handler.getMonthlyStatistics(GROUP, MARCH, cb)));
    }

    private void loginAs(String id) {
        sessions.setCurrentUser(users.findById(id));
    }

    private static void assertError(SplitSmartException.Code code, Result<?> result) {
        assertTrue("Expected " + code + " but got " + result.error,
                result.error instanceof SplitSmartException);
        assertEquals(code, ((SplitSmartException) result.error).getCode());
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
