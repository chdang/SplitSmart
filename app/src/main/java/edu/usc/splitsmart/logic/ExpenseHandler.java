package edu.usc.splitsmart.logic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Settlement;

/** Creates, splits, edits, and deletes expenses, and records settlements (R6-R11, R16). */
public interface ExpenseHandler {

    /**
     * Checks the draft, works out each share, and saves the expense. In the real handler,
     * creatorId must match the logged-in user. Fails with NOT_LOGGED_IN, NOT_A_MEMBER,
     * INVALID_AMOUNT, INVALID_SPLIT, or INVALID_INPUT.
     */
    void createExpense(String creatorId, ExpenseDraft draft, Callback<Expense> callback);

    /** Fails with NOT_FOUND if absent. The real handler requires a logged-in group member. */
    void getExpense(String expenseId, Callback<Expense> callback);

    /** Every expense in the group, newest expense date first. Requires membership when real. */
    void getExpenses(String groupId, Callback<List<Expense>> callback);

    /**
     * R8 reporting filters, combined with AND. Null means no filter. A user is involved if they
     * paid or have a share (including a zero share); creator alone does not count. Date bounds
     * are inclusive and use expenseDate, not createdAt. Preserves newest-first ordering.
     * A reversed date range fails with INVALID_INPUT. Existing implementations remain compatible.
     */
    default void getExpenses(String groupId, String userId, LocalDate fromDate, LocalDate toDate,
                             Callback<List<Expense>> callback) {
        getExpenses(groupId, new Callback<List<Expense>>() {
            @Override
            public void onSuccess(List<Expense> expenses) {
                if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
                    callback.onError(new SplitSmartException(SplitSmartException.Code.INVALID_INPUT));
                    return;
                }
                List<Expense> filtered = new ArrayList<>();
                for (Expense expense : expenses) {
                    if (userId != null && !userId.equals(expense.getPayerId())
                            && !expense.hasShareFor(userId)) {
                        continue;
                    }
                    if (fromDate != null && expense.getExpenseDate().isBefore(fromDate)) {
                        continue;
                    }
                    if (toDate != null && expense.getExpenseDate().isAfter(toDate)) {
                        continue;
                    }
                    filtered.add(expense);
                }
                callback.onSuccess(filtered);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Replaces the expense's details and shares. Fails with NOT_CREATOR if {@code actorId}
     * isn't the logged-in creator (R7), or with the same errors as createExpense. The group,
     * creator, ID, and creation time cannot change.
     */
    void updateExpense(String expenseId, String actorId, ExpenseDraft draft,
                       Callback<Expense> callback);

    /** Deletes the expense and its shares. Requires the logged-in creator (R7). */
    void deleteExpense(String expenseId, String actorId, Callback<Void> callback);

    /** Returns true if the user created the expense, false if absent. Requires membership when real. */
    void authenticateCreator(String expenseId, String userId, Callback<Boolean> callback);

    /**
     * Records that {@code payerId} paid {@code receiverId} outside the app, with an optional
     * note. Fails with INVALID_AMOUNT if the amount isn't positive, NOT_A_MEMBER if either
     * person isn't in the group, or INVALID_INPUT if they're the same person (R16).
     */
    void recordSettlement(String groupId, String payerId, String receiverId, long amountCents,
                          String note, Callback<Settlement> callback);

    /** Fails with NOT_FOUND if absent. The real handler requires a logged-in group member. */
    void getSettlement(String settlementId, Callback<Settlement> callback);

    /**
     * Changes who paid whom, how much, and the note. Fails with NOT_RECORDER if {@code actorId}
     * isn't the logged-in member who recorded it, or with the same errors as recordSettlement.
     * The group, recorder, ID, and recorded time cannot change (R16).
     */
    void updateSettlement(String settlementId, String actorId, String payerId, String receiverId,
                          long amountCents, String note, Callback<Settlement> callback);

    /** Deletes the settlement, releasing what it offset. Requires the logged-in recorder (R16). */
    void deleteSettlement(String settlementId, String actorId, Callback<Void> callback);

    /** True if the user recorded the settlement, false if absent. Requires membership when real. */
    void authenticateRecorder(String settlementId, String userId, Callback<Boolean> callback);
}
