package edu.usc.splitsmart.logic;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.SplitSmartDatabase;
import edu.usc.splitsmart.repository.SqliteGroupRepository;
import edu.usc.splitsmart.repository.SqliteUserRepository;

/** Project 2.4, Kyuhong Lee: white-box tests 16-20, R9-R11 and R16. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SettlementsAndSplittingWhiteBoxTest {
    private static final String GROUP = "g-apartment";
    private static final String TOMMY = "u-tommy";
    private static final String ALEX = "u-alex";
    private Context context;
    private SplitSmartDatabase database;
    private SqliteGroupRepository groups;
    private SqliteUserRepository users;
    private SessionStore sessions;
    private ExpenseHandler handler;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("splitsmart.db");
        database = new SplitSmartDatabase(context);
        groups = new SqliteGroupRepository(database);
        users = new SqliteUserRepository(database);
        sessions = new SessionStore(context);
        signIn(TOMMY);
        handler = new DefaultExpenseHandler(groups, new DefaultSessionHandler(users, sessions));
    }

    @After
    public void tearDown() {
        sessions.clear();
        database.close();
        context.deleteDatabase("splitsmart.db");
    }

    /** WB16: the input guards, equal-return path, and both outcomes of the penny condition. */
    @Test
    public void equalSplitCoversValidationAndPennyRemainderBranches() throws Exception {
        assertSplitError(0, SplitType.EQUAL, allocations(0, 0), SplitSmartException.Code.INVALID_AMOUNT);
        assertSplitError(-1, SplitType.EQUAL, allocations(0, 0), SplitSmartException.Code.INVALID_AMOUNT);
        assertSplitError(1, null, allocations(0, 0), SplitSmartException.Code.INVALID_SPLIT);
        assertSplitError(1, SplitType.EQUAL, null, SplitSmartException.Code.INVALID_SPLIT);
        assertSplitError(1, SplitType.EQUAL, Collections.emptyMap(), SplitSmartException.Code.INVALID_SPLIT);
        Map<String, Long> invalidId = new LinkedHashMap<>();
        invalidId.put(null, 0L);
        assertSplitError(1, SplitType.EQUAL, invalidId, SplitSmartException.Code.INVALID_SPLIT);
        invalidId.clear();
        invalidId.put(" ", 0L);
        assertSplitError(1, SplitType.EQUAL, invalidId, SplitSmartException.Code.INVALID_SPLIT);

        assertEquals(Arrays.asList(334L, 333L, 333L), values(1000, SplitType.EQUAL, allocations(0, 0, 0)));
        assertEquals(Arrays.asList(1L, 0L, 0L), values(1, SplitType.EQUAL, allocations(0, 0, 0)));
        assertEquals(Arrays.asList(1L, 1L), values(2, SplitType.EQUAL, allocations(0, 0)));
        assertEquals(Collections.singletonList(Long.MAX_VALUE),
                values(Long.MAX_VALUE, SplitType.EQUAL, allocations(0)));
    }

    /** WB17: percentage validation, fractional ordering/ties, rounding, and long-limit math. */
    @Test
    public void percentageSplitCoversValidationOrderingAndOverflowSafeMath() throws Exception {
        for (Map<String, Long> input : Arrays.asList(allocations(9999), allocations(10001),
                allocations(-1, 10001))) {
            assertSplitError(100, SplitType.PERCENTAGE, input, SplitSmartException.Code.INVALID_SPLIT);
        }
        Map<String, Long> nullWeight = allocations(10000);
        nullWeight.put("p0", null);
        assertSplitError(100, SplitType.PERCENTAGE, nullWeight, SplitSmartException.Code.INVALID_SPLIT);
        assertEquals(Arrays.asList(255L, 745L),
                values(1000, SplitType.PERCENTAGE, allocations(2550, 7450)));
        assertEquals(Arrays.asList(0L, 0L, 1L),
                values(1, SplitType.PERCENTAGE, allocations(0, 1000, 9000)));
        assertEquals(Arrays.asList(1L, 0L, 0L),
                values(1, SplitType.PERCENTAGE, allocations(5000, 5000, 0)));

        Map<String, Long> large = SplitCalculator.calculate(Long.MAX_VALUE, SplitType.PERCENTAGE,
                allocations(3333, 6667));
        BigInteger sum = BigInteger.valueOf(large.get("p0")).add(BigInteger.valueOf(large.get("p1")));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), sum);
        BigInteger floor = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3333))
                .divide(BigInteger.valueOf(10000));
        BigInteger roundingPenny = BigInteger.valueOf(large.get("p0")).subtract(floor);
        assertTrue(roundingPenny.signum() >= 0 && roundingPenny.compareTo(BigInteger.ONE) <= 0);
    }

    /** WB18: zero/exact allocations, bounded subtraction, invalid values, and independent output. */
    @Test
    public void exactSplitCoversBoundedValidationAndCopiesAllocations() throws Exception {
        Map<String, Long> input = allocations(0, 400, 600);
        Map<String, Long> result = SplitCalculator.calculate(1000, SplitType.EXACT_AMOUNT, input);
        assertEquals(input, result);
        result.put("p0", 1L);
        assertEquals(Long.valueOf(0), input.get("p0"));
        for (Map<String, Long> invalid : Arrays.asList(allocations(99), allocations(101),
                allocations(-1, 101), allocations(Long.MAX_VALUE, Long.MAX_VALUE, 102))) {
            assertSplitError(100, SplitType.EXACT_AMOUNT, invalid, SplitSmartException.Code.INVALID_SPLIT);
        }
        Map<String, Long> nullAmount = allocations(100);
        nullAmount.put("p0", null);
        assertSplitError(100, SplitType.EXACT_AMOUNT, nullAmount, SplitSmartException.Code.INVALID_SPLIT);
    }

    /** WB19: fail before saving for invalid amount, identities, memberships, or missing session. */
    @Test
    public void settlementValidationRejectsInvalidRequestsWithoutWriting() {
        addDebt();
        assertError(SplitSmartException.Code.INVALID_AMOUNT, record(TOMMY, ALEX, 0));
        assertError(SplitSmartException.Code.INVALID_AMOUNT, record(TOMMY, ALEX, -1));
        assertError(SplitSmartException.Code.INVALID_INPUT, record(" ", ALEX, 1));
        assertError(SplitSmartException.Code.INVALID_INPUT, record(TOMMY, null, 1));
        assertError(SplitSmartException.Code.INVALID_INPUT, record(TOMMY, TOMMY, 1));
        assertError(SplitSmartException.Code.NOT_A_MEMBER, record("missing-user", ALEX, 1));
        assertError(SplitSmartException.Code.NOT_A_MEMBER, record(TOMMY, "u-priya", 1));
        signIn("u-priya");
        assertError(SplitSmartException.Code.NOT_A_MEMBER, record(TOMMY, ALEX, 1));
        sessions.clear();
        assertError(SplitSmartException.Code.NOT_LOGGED_IN, record(TOMMY, ALEX, 1));
        assertTrue(groups.findSettlementsByGroup(GROUP).isEmpty());
    }

    /** WB20: exact debt boundary, the edit ignoreId branch, wrong direction, and recorder checks. */
    @Test
    public void settlementDebtBoundaryAndEditExcludeExistingPayment() {
        addDebt();
        assertAboveDebt(record(TOMMY, ALEX, 1501), 1500);
        assertAboveDebt(record(ALEX, TOMMY, 1), 0);
        Settlement first = record(TOMMY, ALEX, 500).value();
        assertEquals(TOMMY, first.getRecorderId());
        assertEquals("Cash", first.getNote());
        Settlement edited = SettlementsAndSplittingWhiteBoxTest.<Settlement>await(cb ->
                handler.updateSettlement(first.getId(), TOMMY, TOMMY, ALEX, 1500, " Updated ", cb)).value();
        assertEquals(1500, edited.getAmountCents());
        assertEquals(first.getSettledAt().toEpochMilli(), edited.getSettledAt().toEpochMilli());
        assertAboveDebt(SettlementsAndSplittingWhiteBoxTest.<Settlement>await(cb ->
                handler.updateSettlement(first.getId(), TOMMY, TOMMY, ALEX, 1501, "", cb)), 1500);
        assertEquals(1500, groups.findSettlementById(first.getId()).getAmountCents());
        SettlementsAndSplittingWhiteBoxTest.<Settlement>await(cb ->
                handler.updateSettlement(first.getId(), TOMMY, TOMMY, ALEX, 500, "", cb)).value();
        Settlement last = record(TOMMY, ALEX, 1000).value();
        assertAboveDebt(record(TOMMY, ALEX, 1), 0);
        assertEquals(2, groups.findSettlementsByGroup(GROUP).size());
        SettlementsAndSplittingWhiteBoxTest.<Void>await(cb ->
                handler.deleteSettlement(last.getId(), TOMMY, cb)).value();
        assertEquals(1, groups.findSettlementsByGroup(GROUP).size());
        assertError(SplitSmartException.Code.NOT_FOUND, SettlementsAndSplittingWhiteBoxTest.<Settlement>await(cb ->
                handler.getSettlement(last.getId(), cb)));
        signIn(ALEX);
        assertError(SplitSmartException.Code.NOT_RECORDER, SettlementsAndSplittingWhiteBoxTest.<Void>await(cb ->
                handler.deleteSettlement(first.getId(), ALEX, cb)));
        assertError(SplitSmartException.Code.NOT_RECORDER, SettlementsAndSplittingWhiteBoxTest.<Void>await(cb ->
                handler.deleteSettlement(first.getId(), TOMMY, cb)));
    }

    private void addDebt() {
        Map<String, Long> people = new LinkedHashMap<>();
        people.put(TOMMY, 0L);
        people.put(ALEX, 0L);
        ExpenseDraft draft = new ExpenseDraft(GROUP, ALEX, "Debt fixture", "", 3000,
                Category.OTHER, SplitType.EQUAL, LocalDate.of(2026, 10, 15), people);
        SettlementsAndSplittingWhiteBoxTest.<Expense>await(cb -> handler.createExpense(TOMMY, draft, cb)).value();
    }

    private Result<Settlement> record(String payer, String receiver, long cents) {
        return await(cb -> handler.recordSettlement(GROUP, payer, receiver, cents, " Cash ", cb));
    }

    private void signIn(String id) {
        sessions.setCurrentUser(users.findById(id));
    }

    private static Map<String, Long> allocations(long... amounts) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < amounts.length; i++) result.put("p" + i, amounts[i]);
        return result;
    }

    private static java.util.List<Long> values(long total, SplitType type, Map<String, Long> input)
            throws SplitSmartException {
        return new ArrayList<>(SplitCalculator.calculate(total, type, input).values());
    }

    private static void assertSplitError(long total, SplitType type, Map<String, Long> input,
                                         SplitSmartException.Code expected) {
        assertEquals(expected, assertThrows(SplitSmartException.class,
                () -> SplitCalculator.calculate(total, type, input)).getCode());
    }

    private static void assertError(SplitSmartException.Code expected, Result<?> result) {
        assertTrue("Expected " + expected + " but got " + result.error,
                result.error instanceof SplitSmartException);
        assertEquals(expected, ((SplitSmartException) result.error).getCode());
    }

    private static void assertAboveDebt(Result<Settlement> result, long owed) {
        assertError(SplitSmartException.Code.AMOUNT_ABOVE_DEBT, result);
        assertEquals(Collections.singletonList(Long.toString(owed)),
                ((SplitSmartException) result.error).getDetails());
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

        public void onSuccess(T value) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            calls++;
            result = value;
        }

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
