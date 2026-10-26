package edu.usc.splitsmart.fake;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.logic.ExpenseDraft;
import edu.usc.splitsmart.logic.ExpenseHandler;
import edu.usc.splitsmart.logic.SplitSmartException;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.ExpenseShare;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;

/**
 * Stand-in for the real ExpenseHandler. Its split math is a quick version so the screens show
 * believable numbers; role 4's split calculator is the real one. Replace in AppServices when
 * role 4's version lands.
 */
public final class FakeExpenseHandler implements ExpenseHandler {
    private final FakeStore store;

    public FakeExpenseHandler(FakeStore store) {
        this.store = store;
    }

    @Override
    public void createExpense(String creatorId, ExpenseDraft draft, Callback<Expense> callback) {
        SplitSmartException problem = validate(creatorId, draft);
        if (problem != null) {
            store.fail(callback, problem);
            return;
        }
        String id = FakeStore.newId();
        Expense expense = build(id, creatorId, draft, Instant.now());
        store.expenses.put(id, expense);
        store.succeed(callback, expense);
    }

    @Override
    public void getExpense(String expenseId, Callback<Expense> callback) {
        Expense expense = store.expenses.get(expenseId);
        if (expense == null) {
            store.fail(callback, SplitSmartException.Code.NOT_FOUND);
        } else {
            store.succeed(callback, expense);
        }
    }

    @Override
    public void getExpenses(String groupId, Callback<List<Expense>> callback) {
        store.succeed(callback, store.expensesFor(groupId));
    }

    @Override
    public void updateExpense(String expenseId, String actorId, ExpenseDraft draft,
                              Callback<Expense> callback) {
        Expense existing = store.expenses.get(expenseId);
        if (existing == null) {
            store.fail(callback, SplitSmartException.Code.NOT_FOUND);
            return;
        }
        if (!existing.getCreatorId().equals(actorId)) {
            store.fail(callback, SplitSmartException.Code.NOT_CREATOR);
            return;
        }
        SplitSmartException problem = validate(actorId, draft);
        if (problem != null) {
            store.fail(callback, problem);
            return;
        }
        Expense updated = build(expenseId, existing.getCreatorId(), draft, existing.getCreatedAt());
        store.expenses.put(expenseId, updated);
        store.succeed(callback, updated);
    }

    @Override
    public void deleteExpense(String expenseId, String actorId, Callback<Void> callback) {
        Expense existing = store.expenses.get(expenseId);
        if (existing == null) {
            store.fail(callback, SplitSmartException.Code.NOT_FOUND);
            return;
        }
        if (!existing.getCreatorId().equals(actorId)) {
            store.fail(callback, SplitSmartException.Code.NOT_CREATOR);
            return;
        }
        store.expenses.remove(expenseId);
        store.succeed(callback, null);
    }

    @Override
    public void authenticateCreator(String expenseId, String userId, Callback<Boolean> callback) {
        Expense expense = store.expenses.get(expenseId);
        store.succeed(callback, expense != null && expense.getCreatorId().equals(userId));
    }

    @Override
    public void recordSettlement(String groupId, String payerId, String receiverId,
                                 long amountCents, Callback<Settlement> callback) {
        if (amountCents <= 0) {
            store.fail(callback, SplitSmartException.Code.INVALID_AMOUNT);
            return;
        }
        if (payerId.equals(receiverId)) {
            store.fail(callback, SplitSmartException.Code.INVALID_INPUT);
            return;
        }
        if (!store.isMember(groupId, payerId) || !store.isMember(groupId, receiverId)) {
            store.fail(callback, SplitSmartException.Code.NOT_A_MEMBER);
            return;
        }
        String recorderId = store.currentUserId != null ? store.currentUserId : payerId;
        Settlement settlement = new Settlement(FakeStore.newId(), groupId, recorderId, payerId,
                receiverId, amountCents, Instant.now());
        store.settlements.add(settlement);
        store.succeed(callback, settlement);
    }

    private SplitSmartException validate(String actorId, ExpenseDraft draft) {
        String groupId = draft.getGroupId();
        if (!store.isMember(groupId, actorId) || !store.isMember(groupId, draft.getPayerId())) {
            return new SplitSmartException(SplitSmartException.Code.NOT_A_MEMBER);
        }
        if (draft.getDescription().trim().isEmpty()) {
            return new SplitSmartException(SplitSmartException.Code.INVALID_INPUT);
        }
        if (draft.getTotalCents() <= 0) {
            return new SplitSmartException(SplitSmartException.Code.INVALID_AMOUNT);
        }
        Map<String, Long> allocations = draft.getAllocations();
        if (allocations.isEmpty()) {
            return new SplitSmartException(SplitSmartException.Code.INVALID_SPLIT);
        }
        long sum = 0;
        for (Map.Entry<String, Long> entry : allocations.entrySet()) {
            if (!store.isMember(groupId, entry.getKey()) || entry.getValue() < 0) {
                return new SplitSmartException(SplitSmartException.Code.INVALID_SPLIT);
            }
            sum += entry.getValue();
        }
        boolean adds = draft.getSplitType() == SplitType.EQUAL
                || (draft.getSplitType() == SplitType.PERCENTAGE && sum == 10000)
                || (draft.getSplitType() == SplitType.EXACT_AMOUNT && sum == draft.getTotalCents());
        return adds ? null : new SplitSmartException(SplitSmartException.Code.INVALID_SPLIT);
    }

    private Expense build(String id, String creatorId, ExpenseDraft draft, Instant createdAt) {
        return new Expense(id, draft.getGroupId(), creatorId, draft.getPayerId(),
                draft.getDescription().trim(), draft.getTotalCents(), draft.getCategory(),
                draft.getSplitType(), draft.getExpenseDate(), createdAt,
                calculateShares(id, draft.getTotalCents(), draft.getSplitType(),
                        draft.getAllocations()));
    }

    /** Leftover cents from rounding go one each to the first participants listed. */
    static List<ExpenseShare> calculateShares(String expenseId, long totalCents, SplitType type,
                                              Map<String, Long> allocations) {
        List<String> ids = new ArrayList<>(allocations.keySet());
        long[] owed = new long[ids.size()];
        if (type == SplitType.EXACT_AMOUNT) {
            for (int i = 0; i < ids.size(); i++) {
                owed[i] = allocations.get(ids.get(i));
            }
        } else {
            long assigned = 0;
            for (int i = 0; i < ids.size(); i++) {
                owed[i] = type == SplitType.EQUAL
                        ? totalCents / ids.size()
                        : totalCents * allocations.get(ids.get(i)) / 10000;
                assigned += owed[i];
            }
            for (int i = 0; assigned < totalCents; i = (i + 1) % ids.size()) {
                owed[i]++;
                assigned++;
            }
        }
        List<ExpenseShare> shares = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            shares.add(new ExpenseShare(expenseId, ids.get(i), owed[i]));
        }
        return shares;
    }
}
