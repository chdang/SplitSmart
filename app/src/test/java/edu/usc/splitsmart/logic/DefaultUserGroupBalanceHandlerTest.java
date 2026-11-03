package edu.usc.splitsmart.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.ExpenseShare;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;

/**
 * Balance math only. Shares are written out by hand as the split calculator would produce
 * them, since the split calculator itself belongs to role 4.
 */
public class DefaultUserGroupBalanceHandlerTest {

    private static final String ANA = "u-ana";
    private static final String BEN = "u-ben";
    private static final String CAL = "u-cal";
    private static final String DEE = "u-dee";

    private static final List<String> MEMBERS = Arrays.asList(ANA, BEN, CAL, DEE);

    private int nextId;

    @Test
    public void equalSplitPaidByUser() {
        // $30.00 split three ways: 1000 each, Ana paid.
        Expense dinner = expense(ANA, 3000, SplitType.EQUAL,
                share(ANA, 1000), share(BEN, 1000), share(CAL, 1000));

        Map<String, Long> net = balances(ANA, list(dinner), none());

        assertEquals(1000L, (long) net.get(BEN));
        assertEquals(1000L, (long) net.get(CAL));
        assertEquals(0L, DefaultUserGroupBalanceHandler.totalPayable(net));
        assertEquals(2000L, DefaultUserGroupBalanceHandler.totalReceivable(net));
    }

    @Test
    public void equalSplitSeenByParticipant() {
        Expense dinner = expense(ANA, 3000, SplitType.EQUAL,
                share(ANA, 1000), share(BEN, 1000), share(CAL, 1000));

        Map<String, Long> net = balances(BEN, list(dinner), none());

        assertEquals(-1000L, (long) net.get(ANA));
        assertEquals(0L, (long) net.get(CAL));
        assertEquals(1000L, DefaultUserGroupBalanceHandler.totalPayable(net));
        assertEquals(0L, DefaultUserGroupBalanceHandler.totalReceivable(net));
    }

    @Test
    public void percentageSplit() {
        // $100.01 at 50/30/20, Ben paid; the leftover cent goes to the first participant.
        Expense rent = expense(BEN, 10001, SplitType.PERCENTAGE,
                share(ANA, 5001), share(BEN, 3000), share(CAL, 2000));

        Map<String, Long> net = balances(ANA, list(rent), none());

        assertEquals(-5001L, (long) net.get(BEN));
        assertEquals(0L, (long) net.get(CAL));
        assertEquals(5001L, DefaultUserGroupBalanceHandler.totalPayable(net));
    }

    @Test
    public void exactSplitNetsAgainstOtherExpenses() {
        // Ana paid $20.00 exact (Ben 1234, Cal 766); Ben paid $5.00 exact, all Ana's.
        Expense groceries = expense(ANA, 2000, SplitType.EXACT_AMOUNT,
                share(BEN, 1234), share(CAL, 766));
        Expense coffee = expense(BEN, 500, SplitType.EXACT_AMOUNT, share(ANA, 500));

        Map<String, Long> net = balances(ANA, list(groceries, coffee), none());

        assertEquals(734L, (long) net.get(BEN));
        assertEquals(766L, (long) net.get(CAL));
        assertEquals(1500L, DefaultUserGroupBalanceHandler.totalReceivable(net));
    }

    @Test
    public void opposingDebtsNetToOneBalance() {
        // Ana owes Ben $20.00 from one expense; Ben owes Ana $5.00 from another.
        Expense tickets = expense(BEN, 2000, SplitType.EXACT_AMOUNT, share(ANA, 2000));
        Expense snacks = expense(ANA, 500, SplitType.EXACT_AMOUNT, share(BEN, 500));

        Map<String, Long> anaView = balances(ANA, list(tickets, snacks), none());
        Map<String, Long> benView = balances(BEN, list(tickets, snacks), none());

        // Ana owes Ben $15.00, not $20.00 owed one way and $5.00 the other.
        assertEquals(-1500L, (long) anaView.get(BEN));
        assertEquals(1500L, DefaultUserGroupBalanceHandler.totalPayable(anaView));
        assertEquals(0L, DefaultUserGroupBalanceHandler.totalReceivable(anaView));
        assertEquals(1500L, (long) benView.get(ANA));
        assertEquals(1500L, DefaultUserGroupBalanceHandler.totalReceivable(benView));
        assertEquals(0L, DefaultUserGroupBalanceHandler.totalPayable(benView));
    }

    @Test
    public void partialSettlementReducesDebt() {
        Expense dinner = expense(ANA, 3000, SplitType.EQUAL,
                share(ANA, 1000), share(BEN, 1000), share(CAL, 1000));
        Settlement benPaysSome = settlement(BEN, ANA, 400);

        assertEquals(600L, (long) balances(ANA, list(dinner), list(benPaysSome)).get(BEN));
        assertEquals(-600L, (long) balances(BEN, list(dinner), list(benPaysSome)).get(ANA));
    }

    @Test
    public void fullSettlementClearsDebt() {
        Expense dinner = expense(ANA, 3000, SplitType.EQUAL,
                share(ANA, 1000), share(BEN, 1000), share(CAL, 1000));
        Settlement benPaysAll = settlement(BEN, ANA, 1000);

        Map<String, Long> net = balances(ANA, list(dinner), list(benPaysAll));

        assertEquals(0L, (long) net.get(BEN));
        assertEquals(1000L, (long) net.get(CAL));
        assertEquals(1000L, DefaultUserGroupBalanceHandler.totalReceivable(net));
    }

    @Test
    public void memberWithNoActivityHasZeroEntry() {
        Expense dinner = expense(ANA, 3000, SplitType.EQUAL,
                share(ANA, 1000), share(BEN, 1000), share(CAL, 1000));

        Map<String, Long> net = balances(ANA, list(dinner), none());

        assertEquals(0L, (long) net.get(DEE));
        // Every other member, in join order, and never the user.
        assertEquals(Arrays.asList(BEN, CAL, DEE), new ArrayList<>(net.keySet()));
    }

    @Test
    public void historyIsNewestFirst() {
        Expense older = expenseAt(Instant.ofEpochMilli(1000));
        Expense newer = expenseAt(Instant.ofEpochMilli(3000));
        Settlement middle = new Settlement("s", "g", BEN, BEN, ANA, 100,
                Instant.ofEpochMilli(2000));

        List<ActivityItem> items =
                DefaultUserGroupBalanceHandler.history(list(older, newer), list(middle));

        assertEquals(newer, items.get(0).getExpense());
        assertEquals(middle, items.get(1).getSettlement());
        assertEquals(older, items.get(2).getExpense());
    }

    @Test
    public void historyLabelsEachItemByType() {
        Expense older = expenseAt(Instant.ofEpochMilli(1000));
        Expense newer = expenseAt(Instant.ofEpochMilli(3000));
        Settlement middle = new Settlement("s", "g", BEN, BEN, ANA, 100,
                Instant.ofEpochMilli(2000));

        List<ActivityItem> items =
                DefaultUserGroupBalanceHandler.history(list(older, newer), list(middle));

        assertEquals(ActivityItem.Type.EXPENSE, items.get(0).getType());
        assertNull(items.get(0).getSettlement());
        assertEquals(ActivityItem.Type.SETTLEMENT, items.get(1).getType());
        assertNull(items.get(1).getExpense());
        assertEquals(ActivityItem.Type.EXPENSE, items.get(2).getType());
        assertNull(items.get(2).getSettlement());
    }

    private Map<String, Long> balances(String userId, List<Expense> expenses,
                                       List<Settlement> settlements) {
        return DefaultUserGroupBalanceHandler.netBalances(userId, MEMBERS, expenses, settlements);
    }

    private Expense expense(String payerId, long totalCents, SplitType type,
                            ExpenseShare... shares) {
        return new Expense("e" + nextId++, "g", payerId, payerId, "test", totalCents,
                Category.FOOD, type, LocalDate.of(2026, 1, 1), Instant.EPOCH,
                Arrays.asList(shares));
    }

    private Expense expenseAt(Instant createdAt) {
        return new Expense("e" + nextId++, "g", ANA, ANA, "test", 100, Category.FOOD,
                SplitType.EQUAL, LocalDate.of(2026, 1, 1), createdAt,
                Collections.singletonList(share(ANA, 100)));
    }

    private static ExpenseShare share(String userId, long owedCents) {
        return new ExpenseShare("e", userId, owedCents);
    }

    private Settlement settlement(String payerId, String receiverId, long cents) {
        return new Settlement("s" + nextId++, "g", payerId, payerId, receiverId, cents,
                Instant.EPOCH);
    }

    @SafeVarargs
    private static <T> List<T> list(T... items) {
        return Arrays.asList(items);
    }

    private static <T> List<T> none() {
        return Collections.emptyList();
    }
}
