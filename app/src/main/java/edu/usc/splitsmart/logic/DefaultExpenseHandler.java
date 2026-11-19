package edu.usc.splitsmart.logic;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
            Expense expense = build(UUID.randomUUID().toString(), currentId, draft,
                    draft == null ? null : draft.getExpenseDate(), Instant.now());
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
            // The date is fixed at creation, so an edit can never move the expense to another
            // month in the summary (R17).
            Expense updated = build(expenseId, existing.getCreatorId(), draft,
                    existing.getExpenseDate(), existing.getCreatedAt());
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
                                 String note, Callback<Settlement> callback) {
        withCurrentUser(currentId -> {
            checkSettlement(groupId, payerId, receiverId, amountCents);
            requireMember(groupId, currentId);
            requireWithinDebt(groupId, payerId, receiverId, amountCents, null);
            return groups.saveSettlement(groupId, currentId, payerId, receiverId,
                    amountCents, trimmed(note), Instant.now());
        }, callback);
    }

    @Override
    public void getSettlement(String settlementId, Callback<Settlement> callback) {
        withCurrentUser(currentId -> {
            Settlement settlement = requireSettlement(settlementId);
            requireMember(settlement.getGroupId(), currentId);
            return settlement;
        }, callback);
    }

    @Override
    public void updateSettlement(String settlementId, String actorId, String payerId,
                                 String receiverId, long amountCents, String note,
                                 Callback<Settlement> callback) {
        withCurrentUser(currentId -> {
            Settlement existing = requireSettlement(settlementId);
            requireRecorder(existing, currentId, actorId);
            checkSettlement(existing.getGroupId(), payerId, receiverId, amountCents);
            // The settlement being edited is already saved, so it must not count against itself.
            requireWithinDebt(existing.getGroupId(), payerId, receiverId, amountCents,
                    settlementId);
            return groups.updateSettlement(settlementId, payerId, receiverId, amountCents,
                    trimmed(note));
        }, callback);
    }

    @Override
    public void deleteSettlement(String settlementId, String actorId, Callback<Void> callback) {
        withCurrentUser(currentId -> {
            Settlement existing = requireSettlement(settlementId);
            requireRecorder(existing, currentId, actorId);
            requireMember(existing.getGroupId(), currentId);
            groups.deleteSettlement(settlementId);
            return null;
        }, callback);
    }

    @Override
    public void authenticateRecorder(String settlementId, String userId,
                                     Callback<Boolean> callback) {
        withCurrentUser(currentId -> {
            Settlement existing = groups.findSettlementById(settlementId);
            if (existing == null) {
                return false;
            }
            requireMember(existing.getGroupId(), currentId);
            return existing.getRecorderId().equals(userId);
        }, callback);
    }

    /** The amount and the two people, checked the same way for a new and an edited settlement. */
    private void checkSettlement(String groupId, String payerId, String receiverId,
                                 long amountCents) throws SplitSmartException {
        if (amountCents <= 0) {
            throw error(SplitSmartException.Code.INVALID_AMOUNT);
        }
        if (isBlank(payerId) || isBlank(receiverId) || payerId.equals(receiverId)) {
            throw error(SplitSmartException.Code.INVALID_INPUT);
        }
        requireMember(groupId, payerId);
        requireMember(groupId, receiverId);
    }

    /**
     * Rejects a payment larger than what the payer still owes the receiver, so a settlement can
     * only cancel a debt and never create one in the other direction (R16). {@code ignoreId} is
     * the settlement being edited, which is already stored and must be left out of the balance.
     */
    private void requireWithinDebt(String groupId, String payerId, String receiverId,
                                   long amountCents, String ignoreId) throws SplitSmartException {
        List<Settlement> others = new ArrayList<>();
        for (Settlement settlement : groups.findSettlementsByGroup(groupId)) {
            if (!settlement.getId().equals(ignoreId)) {
                others.add(settlement);
            }
        }
        Map<String, Long> net = DefaultUserGroupBalanceHandler.netBalances(payerId,
                Collections.singletonList(receiverId), groups.findExpensesByGroup(groupId), others);
        // Negative means the payer owes the receiver, which is the only direction worth paying.
        long owed = Math.max(-net.get(receiverId), 0);
        if (amountCents > owed) {
            throw new SplitSmartException(SplitSmartException.Code.AMOUNT_ABOVE_DEBT,
                    Collections.singletonList(Long.toString(owed)));
        }
    }

    private Settlement requireSettlement(String id) throws SplitSmartException {
        Settlement settlement = groups.findSettlementById(id);
        if (settlement == null) {
            throw error(SplitSmartException.Code.NOT_FOUND);
        }
        return settlement;
    }

    private void requireRecorder(Settlement settlement, String currentId, String actorId)
            throws SplitSmartException {
        if (!currentId.equals(actorId) || !settlement.getRecorderId().equals(currentId)) {
            throw error(SplitSmartException.Code.NOT_RECORDER);
        }
    }

    private static String trimmed(String note) {
        return note == null ? "" : note.trim();
    }

    private Expense build(String id, String creatorId, ExpenseDraft draft, LocalDate expenseDate,
                          Instant createdAt) throws SplitSmartException {
        if (draft == null || isBlank(draft.getGroupId()) || isBlank(draft.getPayerId())
                || isBlank(draft.getDescription()) || draft.getCategory() == null
                || expenseDate == null) {
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
                draft.getDescription().trim(), trimmed(draft.getNote()), draft.getTotalCents(),
                draft.getCategory(), draft.getSplitType(), expenseDate, createdAt, shares);
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
