package edu.usc.splitsmart.fake;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.logic.ActivityItem;
import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.logic.SplitSmartException;
import edu.usc.splitsmart.logic.UserGroupBalanceHandler;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.ExpenseShare;
import edu.usc.splitsmart.model.Settlement;

/** Stand-in for the real balance calculator. Replace in AppServices when role 5's version lands. */
public final class FakeUserGroupBalanceHandler implements UserGroupBalanceHandler {
    private final FakeStore store;

    public FakeUserGroupBalanceHandler(FakeStore store) {
        this.store = store;
    }

    @Override
    public void getIndividualBalances(String groupId, String userId,
                                      Callback<Map<String, Long>> callback) {
        if (!store.isMember(groupId, userId)) {
            store.fail(callback, SplitSmartException.Code.NOT_A_MEMBER);
            return;
        }
        store.succeed(callback, balances(groupId, userId));
    }

    @Override
    public void getTotalPayable(String groupId, String userId, Callback<Long> callback) {
        if (!store.isMember(groupId, userId)) {
            store.fail(callback, SplitSmartException.Code.NOT_A_MEMBER);
            return;
        }
        long total = 0;
        for (long net : balances(groupId, userId).values()) {
            if (net < 0) {
                total -= net;
            }
        }
        store.succeed(callback, total);
    }

    @Override
    public void getTotalReceivable(String groupId, String userId, Callback<Long> callback) {
        if (!store.isMember(groupId, userId)) {
            store.fail(callback, SplitSmartException.Code.NOT_A_MEMBER);
            return;
        }
        long total = 0;
        for (long net : balances(groupId, userId).values()) {
            if (net > 0) {
                total += net;
            }
        }
        store.succeed(callback, total);
    }

    @Override
    public void getHistory(String groupId, Callback<List<ActivityItem>> callback) {
        List<ActivityItem> items = new ArrayList<>();
        for (Expense expense : store.expensesFor(groupId)) {
            items.add(ActivityItem.of(expense));
        }
        for (Settlement settlement : store.settlementsFor(groupId)) {
            items.add(ActivityItem.of(settlement));
        }
        items.sort(Comparator.comparing(ActivityItem::getOccurredAt).reversed());
        store.succeed(callback, items);
    }

    /** Positive: that member owes the user. Negative: the user owes that member. */
    private Map<String, Long> balances(String groupId, String userId) {
        Map<String, Long> net = new LinkedHashMap<>();
        for (String memberId : store.members.get(groupId)) {
            if (!memberId.equals(userId)) {
                net.put(memberId, 0L);
            }
        }
        for (Expense expense : store.expensesFor(groupId)) {
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
        for (Settlement settlement : store.settlementsFor(groupId)) {
            if (settlement.getPayerId().equals(userId)) {
                net.merge(settlement.getReceiverId(), settlement.getAmountCents(), Long::sum);
            } else if (settlement.getReceiverId().equals(userId)) {
                net.merge(settlement.getPayerId(), -settlement.getAmountCents(), Long::sum);
            }
        }
        return net;
    }
}
