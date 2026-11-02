package edu.usc.splitsmart.model;

/** How much one participant owes for one expense, in cents. */
public final class ExpenseShare {
    private final String expenseId;
    private final String userId;
    private final long owedCents;
    private final Long basisPoints;

    public ExpenseShare(String expenseId, String userId, long owedCents) {
        this(expenseId, userId, owedCents, null);
    }

    /** Original percentage allocation, or null for other split types and legacy shares. */
    public ExpenseShare(String expenseId, String userId, long owedCents, Long basisPoints) {
        this.expenseId = expenseId;
        this.userId = userId;
        this.owedCents = owedCents;
        this.basisPoints = basisPoints;
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

    /** Basis points entered by the creator, before rounding to cents; may be null. */
    public Long getBasisPoints() {
        return basisPoints;
    }
}
