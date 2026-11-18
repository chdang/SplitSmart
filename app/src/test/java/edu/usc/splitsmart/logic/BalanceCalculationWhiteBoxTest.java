package edu.usc.splitsmart.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.ExpenseShare;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.repository.GroupRepository;

/**
 * White-box tests for Balance Calculation (R12, R13), written as JUnit local unit tests. Each
 * test was designed by reading {@link DefaultUserGroupBalanceHandler} and choosing inputs that
 * force a specific path through it, so that together they execute every branch of the expense
 * loop, the settlement loop, both total calculations, the membership guard in
 * {@code loadBalances}, and its error forwarding.
 *
 * <p>Plain JUnit with no Android runtime: none of these paths reach {@code Async}.
 * Run with {@code ./gradlew testDebugUnitTest --tests '*BalanceCalculationWhiteBoxTest'}.
 */
public class BalanceCalculationWhiteBoxTest {

    private static final String TOMMY = "u-tommy";
    private static final String ALEX = "u-alex";
    private static final String SAM = "u-sam";
    private static final String JORDAN = "u-jordan";

    /** Join order, which netBalances must preserve in its result. */
    private static final List<String> MEMBERS = Arrays.asList(TOMMY, ALEX, SAM, JORDAN);

    private int nextId;

    /**
     * WB1: netBalances, expense loop. The inner loop over shares has four paths:
     * (a) the participant is the payer, so the share is skipped with {@code continue};
     * (b) the user paid, so the participant owes the user;
     * (c) the user is the participant, so the user owes the payer;
     * (d) the user is neither, so the user's balances must not change.
     */
    @Test
    public void netBalancesTakesEveryBranchOfTheExpenseLoop() {
        // (a) and (b): Tommy paid $30.00 split three ways, including his own $10.00.
        Expense dinner = expense(TOMMY, share(TOMMY, 1000), share(ALEX, 1000), share(SAM, 1000));
        // (c) and (a): Alex paid $9.00; Tommy's share is $4.00 and Alex's own $5.00 is skipped.
        Expense coffee = expense(ALEX, share(TOMMY, 400), share(ALEX, 500));
        // (d): Sam paid for Alex and Jordan; Tommy wasn't part of it.
        Expense tickets = expense(SAM, share(ALEX, 1000), share(JORDAN, 1000));

        Map<String, Long> net = DefaultUserGroupBalanceHandler.netBalances(TOMMY, MEMBERS,
                list(dinner, coffee, tickets), none());

        assertEquals(600L, (long) net.get(ALEX));   // +1000 from (b), -400 from (c)
        assertEquals(1000L, (long) net.get(SAM));   // +1000 from (b); Sam's own expense is (d)
        assertEquals(0L, (long) net.get(JORDAN));   // only (d) touches Jordan
        // If (a) didn't skip the payer's own share, Tommy would owe himself and get an entry.
        assertFalse(net.containsKey(TOMMY));
        assertEquals(Arrays.asList(ALEX, SAM, JORDAN), new ArrayList<>(net.keySet()));
    }

    /**
     * WB2: netBalances, settlement loop. Three paths: the user paid the settlement (their debt
     * to the receiver shrinks), the user received it (what the payer owes them shrinks), and a
     * settlement between two other members (must be ignored). The Jordan case also pays a debt
     * off exactly, the boundary where the balance should land on 0.
     */
    @Test
    public void netBalancesTakesEveryBranchOfTheSettlementLoop() {
        Expense groceries = expense(ALEX, share(TOMMY, 1500), share(ALEX, 1500)); // Tommy owes Alex 1500
        Expense rideshare = expense(TOMMY, share(JORDAN, 1000));                  // Jordan owes Tommy 1000
        Expense lunch = expense(SAM, share(ALEX, 800));                           // Alex owes Sam 800

        Settlement tommyPaysAlex = settlement(TOMMY, ALEX, 500);     // user is the payer
        Settlement jordanPaysTommy = settlement(JORDAN, TOMMY, 1000); // user is the receiver
        Settlement alexPaysSam = settlement(ALEX, SAM, 800);         // user is neither

        Map<String, Long> net = DefaultUserGroupBalanceHandler.netBalances(TOMMY, MEMBERS,
                list(groceries, rideshare, lunch),
                list(tommyPaysAlex, jordanPaysTommy, alexPaysSam));

        assertEquals(-1000L, (long) net.get(ALEX)); // -1500 + 500
        assertEquals(0L, (long) net.get(JORDAN));   // +1000 - 1000
        assertEquals(0L, (long) net.get(SAM));      // Alex paying Sam is not Tommy's business
    }

    /**
     * WB3: totalPayable and totalReceivable. Each loops over the balances and branches on the
     * sign. Covers the empty map (loop body never runs) and a map with negative, positive, and
     * zero balances, where zero must count toward neither total.
     */
    @Test
    public void totalsCountEachBalanceOnlyOnTheSideItsSignBelongsTo() {
        Map<String, Long> empty = new LinkedHashMap<>();
        assertEquals(0L, DefaultUserGroupBalanceHandler.totalPayable(empty));
        assertEquals(0L, DefaultUserGroupBalanceHandler.totalReceivable(empty));

        Map<String, Long> mixed = new LinkedHashMap<>();
        mixed.put(ALEX, 700L);      // owes the user
        mixed.put(SAM, -250L);      // the user owes
        mixed.put(JORDAN, 0L);      // settled
        mixed.put("u-priya", -50L); // the user owes

        long payable = DefaultUserGroupBalanceHandler.totalPayable(mixed);
        long receivable = DefaultUserGroupBalanceHandler.totalReceivable(mixed);

        assertEquals(300L, payable);    // 250 + 50, reported as a positive number
        assertEquals(700L, receivable);
        // Every non-zero balance is counted exactly once, on one side.
        assertEquals(700L - 250L + 0L - 50L, receivable - payable);
    }

    /**
     * WB4: loadBalances, membership guard. When authenticateMember answers false, the handler
     * must report NOT_A_MEMBER exactly once and return before it reads members, expenses, or
     * settlements. getIndividualBalances, getTotalPayable, and getTotalReceivable all go through
     * this guard, so all three are checked.
     */
    @Test
    public void nonMemberIsRejectedBeforeAnyGroupDataIsRead() {
        List<String> reads = new ArrayList<>();
        MemberAuthenticationHandler notAMember =
                (groupId, userId, callback) -> callback.onSuccess(false);
        DefaultUserGroupBalanceHandler handler = new DefaultUserGroupBalanceHandler(
                recorder(GroupHandler.class, reads), notAMember,
                recorder(ExpenseHandler.class, reads), recorder(GroupRepository.class, reads));

        Recorded<Map<String, Long>> individual = new Recorded<>();
        Recorded<Long> payable = new Recorded<>();
        Recorded<Long> receivable = new Recorded<>();
        handler.getIndividualBalances("g-apartment", "u-priya", individual);
        handler.getTotalPayable("g-apartment", "u-priya", payable);
        handler.getTotalReceivable("g-apartment", "u-priya", receivable);

        for (Recorded<?> result : Arrays.asList(individual, payable, receivable)) {
            assertEquals("Expected exactly one callback", 1, result.calls);
            assertTrue("Expected an error, got " + result.value,
                    result.error instanceof SplitSmartException);
            assertEquals(SplitSmartException.Code.NOT_A_MEMBER,
                    ((SplitSmartException) result.error).getCode());
        }
        assertEquals("A non-member's request must not read any group data",
                Collections.emptyList(), reads);
    }

    /**
     * WB5: loadBalances, error forwarding. Past the guard, each step passes failures back
     * through {@code then()}, and getTotalPayable and getTotalReceivable wrap the result in one
     * more {@code then()}. When the member lookup fails, that exact error must reach the caller
     * exactly once through every layer, and the chain must stop: expenses and settlements are
     * never read.
     */
    @Test
    public void failureWhileLoadingIsForwardedOnceAndStopsTheChain() {
        SplitSmartException loggedOut =
                new SplitSmartException(SplitSmartException.Code.NOT_LOGGED_IN);
        List<String> reads = new ArrayList<>();
        MemberAuthenticationHandler isMember =
                (groupId, userId, callback) -> callback.onSuccess(true);
        DefaultUserGroupBalanceHandler handler = new DefaultUserGroupBalanceHandler(
                stub(GroupHandler.class, reads, "getMembers", loggedOut), isMember,
                recorder(ExpenseHandler.class, reads), recorder(GroupRepository.class, reads));

        Recorded<Map<String, Long>> individual = new Recorded<>();
        Recorded<Long> payable = new Recorded<>();
        Recorded<Long> receivable = new Recorded<>();
        handler.getIndividualBalances("g-apartment", TOMMY, individual);
        handler.getTotalPayable("g-apartment", TOMMY, payable);
        handler.getTotalReceivable("g-apartment", TOMMY, receivable);

        for (Recorded<?> result : Arrays.asList(individual, payable, receivable)) {
            assertEquals("Expected exactly one callback", 1, result.calls);
            assertSame("The original error should arrive unchanged", loggedOut, result.error);
        }
        // One member lookup per call, and nothing after it.
        assertEquals(Collections.nCopies(3, "GroupHandler.getMembers"), reads);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private Expense expense(String payerId, ExpenseShare... shares) {
        long total = 0;
        for (ExpenseShare share : shares) {
            total += share.getOwedCents();
        }
        return new Expense("e" + nextId++, "g-apartment", payerId, payerId, "test", "", total,
                Category.FOOD, SplitType.EXACT_AMOUNT, LocalDate.of(2026, 10, 15), Instant.EPOCH,
                Arrays.asList(shares));
    }

    private static ExpenseShare share(String userId, long owedCents) {
        return new ExpenseShare("e", userId, owedCents);
    }

    private Settlement settlement(String payerId, String receiverId, long cents) {
        return new Settlement("s" + nextId++, "g-apartment", payerId, payerId, receiverId, cents,
                "", Instant.EPOCH);
    }

    /**
     * A stand-in for a whole interface that writes down every method called on it. Lets the
     * tests prove which steps ran, without hand-writing dozens of methods.
     */
    private static <T> T recorder(Class<T> type, List<String> calls) {
        return stub(type, calls, null, null);
    }

    /** Like {@link #recorder}, but {@code failingMethod} also reports {@code error}. */
    private static <T> T stub(Class<T> type, List<String> calls, String failingMethod,
                              Exception error) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        switch (method.getName()) {
                            case "equals":
                                return proxy == args[0];
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            default:
                                return type.getSimpleName() + " stub";
                        }
                    }
                    calls.add(type.getSimpleName() + "." + method.getName());
                    if (method.getName().equals(failingMethod)) {
                        // Handler methods take their callback last.
                        ((Callback<?>) args[args.length - 1]).onError(error);
                    }
                    return null;
                }));
    }

    /** Records what a handler sent back, and how many times. */
    private static final class Recorded<T> implements Callback<T> {
        private int calls;
        private T value;
        private Exception error;

        @Override
        public void onSuccess(T result) {
            calls++;
            value = result;
        }

        @Override
        public void onError(Exception failure) {
            calls++;
            error = failure;
        }
    }

    @SafeVarargs
    private static <T> List<T> list(T... items) {
        return Arrays.asList(items);
    }

    private static <T> List<T> none() {
        return Collections.emptyList();
    }
}
