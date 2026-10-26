package edu.usc.splitsmart.model;

/** How much one participant owes for one expense, in cents. */
public final class ExpenseShare {
    private final String expenseId;
    private final String userId;
    private final long owedCents;

    public ExpenseShare(String expenseId, String userId, long owedCents) {
        this.expenseId = expenseId;
        this.userId = userId;
        this.owedCents = owedCents;
    }

    public String getExpenseId() {
        return expenseId;
    }

    public String getUserId() {
        return userId;
    }

    public long getOwedCents() {
        return owedCents;
    }
}
