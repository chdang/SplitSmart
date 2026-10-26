package edu.usc.splitsmart.logic;

import java.util.List;

import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Settlement;

/** Creates, splits, edits, and deletes expenses, and records settlements (R6-R11, R16). */
public interface ExpenseHandler {

    /**
     * Checks the draft, works out each share, and saves the expense with {@code creatorId} as
     * its creator. Fails with NOT_A_MEMBER, INVALID_AMOUNT, INVALID_SPLIT, or INVALID_INPUT.
     */
    void createExpense(String creatorId, ExpenseDraft draft, Callback<Expense> callback);

    /** Fails with NOT_FOUND if the expense doesn't exist. */
    void getExpense(String expenseId, Callback<Expense> callback);

    /** Every expense in the group, newest expense date first. */
    void getExpenses(String groupId, Callback<List<Expense>> callback);

    /**
     * Replaces the expense's details and shares. Fails with NOT_CREATOR if {@code actorId}
     * didn't create it (R7), or with the same errors as createExpense.
     */
    void updateExpense(String expenseId, String actorId, ExpenseDraft draft,
                       Callback<Expense> callback);

    /** Deletes the expense and its shares. Fails with NOT_CREATOR (R7). */
    void deleteExpense(String expenseId, String actorId, Callback<Void> callback);

    /** Returns true if the user created the expense. */
    void authenticateCreator(String expenseId, String userId, Callback<Boolean> callback);

    /**
     * Records that {@code payerId} paid {@code receiverId} outside the app. Fails with
     * INVALID_AMOUNT if the amount isn't positive, NOT_A_MEMBER if either person isn't in the
     * group, or INVALID_INPUT if they're the same person (R16).
     */
    void recordSettlement(String groupId, String payerId, String receiverId, long amountCents,
                          Callback<Settlement> callback);
}
