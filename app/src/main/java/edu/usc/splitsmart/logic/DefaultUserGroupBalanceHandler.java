package edu.usc.splitsmart.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.ExpenseShare;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.GroupRepository;

/**
 * Works out REAL balances from a group's expenses and settlements (R12-R15). Nothing is stored:
 * every call recalculates, so edits, deletes, and settlements show up right away (R14).
 *
 * <p>Expenses come from the expense handler and members from the group handler. Settlements
 * have no handler read, so they come from {@link GroupRepository#findSettlementsByGroup}.
 */
public class DefaultUserGroupBalanceHandler implements UserGroupBalanceHandler {

    private final GroupHandler groupHandler;

    private final MemberAuthenticationHandler membership;

    private final ExpenseHandler expenses;

    private final GroupRepository groups;

    public DefaultUserGroupBalanceHandler(GroupHandler groupHandler,
                                          MemberAuthenticationHandler membership,
                                          ExpenseHandler expenses, GroupRepository groups) {
        this.groupHandler = groupHandler;
        this.membership = membership;
        this.expenses = expenses;
        this.groups = groups;
    }

    /**
     * The user's net balance with every other member, keyed by member id. Positive means that
     * member owes the user; negative means the user owes them; 0 means they're settled up (R13).
     * Fails with NOT_A_MEMBER if the user isn't in the group.
     */
    @Override
    public void getIndividualBalances(String groupId, String userId,
                                      Callback<Map<String, Long>> callback) {
        loadBalances(groupId, userId, callback);
    }

    /** Total cents the user owes, after netting each pair (R12). Fails with NOT_A_MEMBER. */
    @Override
    public void getTotalPayable(String groupId, String userId, Callback<Long> callback) {
        loadBalances(groupId, userId, then(callback,
                net -> callback.onSuccess(totalPayable(net))));
    }

    /** Total cents owed to the user, after netting each pair (R12). Fails with NOT_A_MEMBER. */
    @Override
    public void getTotalReceivable(String groupId, String userId, Callback<Long> callback) {
        loadBalances(groupId, userId, then(callback,
                net -> callback.onSuccess(totalReceivable(net))));
    }

    /** Every expense and settlement in the group, newest first (R15). */
    @Override
    public void getHistory(String groupId, Callback<List<ActivityItem>> callback) {
        expenses.getExpenses(groupId, then(callback, groupExpenses ->
                Async.run(() -> history(groupExpenses, groups.findSettlementsByGroup(groupId)),
                        callback)));
    }

    /**
     * Checks membership, then gathers members, expenses, and settlements and nets them. Each
     * handler call already returns on the main thread; only the settlement read needs Async.
     */
    private void loadBalances(String groupId, String userId,
                              Callback<Map<String, Long>> callback) {
        membership.authenticateMember(groupId, userId, then(callback, isMember -> {
            if (!Boolean.TRUE.equals(isMember)) {
                callback.onError(new SplitSmartException(SplitSmartException.Code.NOT_A_MEMBER));
                return;
            }
            groupHandler.getMembers(groupId, then(callback, members ->
                    expenses.getExpenses(groupId, then(callback, groupExpenses ->
                            Async.run(() -> netBalances(userId, idsOf(members), groupExpenses,
                                    groups.findSettlementsByGroup(groupId)), callback)))));
        }));
    }

    /**
     * Positive: that member owes the user. Negative: the user owes that member. Every other
     * member gets an entry, in {@code memberIds} order, even when it nets to 0.
     */
    static Map<String, Long> netBalances(String userId, List<String> memberIds,
                                         List<Expense> groupExpenses,
                                         List<Settlement> settlements) {
        Map<String, Long> net = new LinkedHashMap<>();
        for (String memberId : memberIds) {
            if (!memberId.equals(userId)) {
                net.put(memberId, 0L);
            }
        }
        for (Expense expense : groupExpenses) {
            String payer = expense.getPayerId();
            for (ExpenseShare share : expense.getShares()) {
                String participant = share.getUserId();
                if (participant.equals(payer)) {
                    continue;
                }
                if (payer.equals(userId)) {
                    net.merge(participant, share.getOwedCents(), Long::sum);
                } else if (participant.equals(userId)) {
                    net.merge(payer, -share.getOwedCents(), Long::sum);
                }
            }
        }
        for (Settlement settlement : settlements) {
            if (settlement.getPayerId().equals(userId)) {
                net.merge(settlement.getReceiverId(), settlement.getAmountCents(), Long::sum);
            } else if (settlement.getReceiverId().equals(userId)) {
                net.merge(settlement.getPayerId(), -settlement.getAmountCents(), Long::sum);
            }
        }
        return net;
    }

    /** Sum of what the user owes across members, as a positive number of cents. */
    static long totalPayable(Map<String, Long> net) {
        long total = 0;
        for (long cents : net.values()) {
            if (cents < 0) {
                total -= cents;
            }
        }
        return total;
    }

    /** Sum of what members owe the user, in cents. */
    static long totalReceivable(Map<String, Long> net) {
        long total = 0;
        for (long cents : net.values()) {
            if (cents > 0) {
                total += cents;
            }
        }
        return total;
    }

    /** Expenses then settlements, sorted newest first; ties keep that order. */
    static List<ActivityItem> history(List<Expense> groupExpenses, List<Settlement> settlements) {
        List<ActivityItem> items = new ArrayList<>();
        for (Expense expense : groupExpenses) {
            items.add(ActivityItem.of(expense));
        }
        for (Settlement settlement : settlements) {
            items.add(ActivityItem.of(settlement));
        }
        items.sort(Comparator.comparing(ActivityItem::getOccurredAt).reversed());
        return items;
    }

    private static List<String> idsOf(List<User> members) {
        List<String> ids = new ArrayList<>();
        for (User member : members) {
            ids.add(member.getId());
        }
        return ids;
    }

    /** Runs {@code next} on success and forwards any error to {@code outer}. */
    private static <T> Callback<T> then(Callback<?> outer, Consumer<T> next) {
        return new Callback<T>() {
            @Override
            public void onSuccess(T result) {
                next.accept(result);
            }

            @Override
            public void onError(Exception error) {
                outer.onError(error);
            }
        };
    }
}
