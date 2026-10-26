package edu.usc.splitsmart.logic;

import java.time.Instant;

import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Settlement;

/** One entry in a group's history: either an expense or a settlement (R15). */
public final class ActivityItem {

    public enum Type {
        EXPENSE,
        SETTLEMENT
    }

    private final Type type;
    private final Expense expense;
    private final Settlement settlement;

    private ActivityItem(Type type, Expense expense, Settlement settlement) {
        this.type = type;
        this.expense = expense;
        this.settlement = settlement;
    }

    public static ActivityItem of(Expense expense) {
        return new ActivityItem(Type.EXPENSE, expense, null);
    }

    public static ActivityItem of(Settlement settlement) {
        return new ActivityItem(Type.SETTLEMENT, null, settlement);
    }

    public Type getType() {
        return type;
    }

    /** Set only when the type is EXPENSE. */
    public Expense getExpense() {
        return expense;
    }

    /** Set only when the type is SETTLEMENT. */
    public Settlement getSettlement() {
        return settlement;
    }

    /** When it was recorded in the app. History is sorted by this, newest first. */
    public Instant getOccurredAt() {
        return type == Type.EXPENSE ? expense.getCreatedAt() : settlement.getSettledAt();
    }
}
