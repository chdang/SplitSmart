package edu.usc.splitsmart.logic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.ExpenseShare;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.GroupRepository;

/** Validates expense operations and persists them through GroupRepository (R6-R11). */
public final class DefaultExpenseHandler implements ExpenseHandler {
    private final GroupRepository groups;
    private final SessionHandler session;

    public DefaultExpenseHandler(GroupRepository groups, SessionHandler session) {
        this.groups = groups;
        this.session = session;
    }

    @Override
    public void createExpense(String creatorId, ExpenseDraft draft, Callback<Expense> callback) {
        withCurrentUser(currentId -> {
            // The supplied creator cannot impersonate a different logged-in member.
            if (!currentId.equals(creatorId)) {
                throw error(SplitSmartException.Code.INVALID_INPUT);
            }
            Expense expense = build(UUID.randomUUID().toString(), currentId, draft, Instant.now());
            groups.saveExpense(expense);
            return expense;
        }, callback);
    }

    @Override
    public void getExpense(String expenseId, Callback<Expense> callback) {
        withCurrentUser(currentId -> {
            Expense expense = requireExpense(expenseId);
            requireMember(expense.getGroupId(), currentId);
            return expense;
        }, callback);
    }

    @Override
    public void getExpenses(String groupId, Callback<List<Expense>> callback) {
        withCurrentUser(currentId -> {
            requireMember(groupId, currentId);
            return groups.findExpensesByGroup(groupId);
        }, callback);
    }

    @Override
    public void updateExpense(String expenseId, String actorId, ExpenseDraft draft,
                              Callback<Expense> callback) {
        withCurrentUser(currentId -> {
            Expense existing = requireExpense(expenseId);
            requireCreator(existing, currentId, actorId);
            if (draft == null || !existing.getGroupId().equals(draft.getGroupId())) {
                throw error(SplitSmartException.Code.INVALID_INPUT);
            }
            Expense updated = build(expenseId, existing.getCreatorId(), draft,
                    existing.getCreatedAt());
            groups.replaceExpense(updated);
            return updated;
        }, callback);
    }

    @Override
    public void deleteExpense(String expenseId, String actorId, Callback<Void> callback) {
        withCurrentUser(currentId -> {
            Expense existing = requireExpense(expenseId);
            requireCreator(existing, currentId, actorId);
            requireMember(existing.getGroupId(), currentId);
            groups.deleteExpense(expenseId);
            return null;
        }, callback);
    }

    @Override
    public void authenticateCreator(String expenseId, String userId, Callback<Boolean> callback) {
        withCurrentUser(currentId -> {
            Expense existing = groups.findExpenseById(expenseId);
            if (existing == null) {
                return false;
            }
            requireMember(existing.getGroupId(), currentId);
            return existing.getCreatorId().equals(userId);
        }, callback);
    }

    /** Compatibility with the existing interface; settlement storage belongs to Role 3. */
    @Override
    public void recordSettlement(String groupId, String payerId, String receiverId, long amountCents,
                                 Callback<Settlement> callback) {
        withCurrentUser(currentId -> {
            if (amountCents <= 0) {
                throw error(SplitSmartException.Code.INVALID_AMOUNT);
            }
            if (isBlank(payerId) || isBlank(receiverId) || payerId.equals(receiverId)) {
                throw error(SplitSmartException.Code.INVALID_INPUT);
            }
            requireMember(groupId, currentId);
            requireMember(groupId, payerId);
            requireMember(groupId, receiverId);
            return groups.saveSettlement(groupId, currentId, payerId, receiverId,
                    amountCents, Instant.now());
        }, callback);
    }

    private Expense build(String id, String creatorId, ExpenseDraft draft, Instant createdAt)
            throws SplitSmartException {
        if (draft == null || isBlank(draft.getGroupId()) || isBlank(draft.getPayerId())
                || isBlank(draft.getDescription()) || draft.getCategory() == null
                || draft.getExpenseDate() == null) {
            throw error(SplitSmartException.Code.INVALID_INPUT);
        }
        requireMember(draft.getGroupId(), creatorId);
        requireMember(draft.getGroupId(), draft.getPayerId());
        Map<String, Long> owed = SplitCalculator.calculate(draft.getTotalCents(),
                draft.getSplitType(), draft.getAllocations());
        List<ExpenseShare> shares = new ArrayList<>();
        long remaining = draft.getTotalCents();
        for (Map.Entry<String, Long> entry : owed.entrySet()) {
            requireMember(draft.getGroupId(), entry.getKey());
            if (entry.getValue() < 0 || entry.getValue() > remaining) {
                throw error(SplitSmartException.Code.INVALID_SPLIT);
            }
            remaining -= entry.getValue();
            Long basisPoints = draft.getSplitType() == SplitType.PERCENTAGE
                    ? draft.getAllocations().get(entry.getKey()) : null;
            shares.add(new ExpenseShare(id, entry.getKey(), entry.getValue(), basisPoints));
        }
        if (remaining != 0) {
            throw error(SplitSmartException.Code.INVALID_SPLIT);
        }
        return new Expense(id, draft.getGroupId(), creatorId, draft.getPayerId(),
                draft.getDescription().trim(), draft.getTotalCents(), draft.getCategory(),
                draft.getSplitType(), draft.getExpenseDate(), createdAt, shares);
    }

    private Expense requireExpense(String id) throws SplitSmartException {
        Expense expense = groups.findExpenseById(id);
        if (expense == null) {
            throw error(SplitSmartException.Code.NOT_FOUND);
        }
        return expense;
    }

    private void requireMember(String groupId, String userId) throws SplitSmartException {
        if (isBlank(groupId) || isBlank(userId) || !groups.isMember(groupId, userId)) {
            throw error(SplitSmartException.Code.NOT_A_MEMBER);
        }
    }

    private void requireCreator(Expense expense, String currentId, String actorId)
            throws SplitSmartException {
        if (!currentId.equals(actorId) || !expense.getCreatorId().equals(currentId)) {
            throw error(SplitSmartException.Code.NOT_CREATOR);
        }
    }

    private <T> void withCurrentUser(UserWork<T> work, Callback<T> callback) {
        // Session state is confined to the caller/main thread; only its snapshot goes to Async.
        User current = session.getCurrentUser();
        Async.run(() -> {
            if (current == null) {
                throw error(SplitSmartException.Code.NOT_LOGGED_IN);
            }
            return work.run(current.getId());
        }, callback);
    }

    private interface UserWork<T> {
        T run(String currentId) throws Exception;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static SplitSmartException error(SplitSmartException.Code code) {
        return new SplitSmartException(code);
    }
}
