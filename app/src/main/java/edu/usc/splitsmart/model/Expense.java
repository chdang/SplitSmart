package edu.usc.splitsmart.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Something a group member paid for, divided into shares. The creator is whoever entered the
 * expense and is the only person who can edit it (R7). The payer is whoever actually paid, and
 * can be a different member.
 */
public final class Expense {
    private final String id;
    private final String groupId;
    private final String creatorId;
    private final String payerId;
    private final String description;
    private final long totalCents;
    private final Category category;
    private final SplitType splitType;
    private final LocalDate expenseDate;
    private final Instant createdAt;
    private final List<ExpenseShare> shares;

    public Expense(String id, String groupId, String creatorId, String payerId, String description,
                   long totalCents, Category category, SplitType splitType, LocalDate expenseDate,
                   Instant createdAt, List<ExpenseShare> shares) {
        this.id = id;
        this.groupId = groupId;
        this.creatorId = creatorId;
        this.payerId = payerId;
        this.description = description;
        this.totalCents = totalCents;
        this.category = category;
        this.splitType = splitType;
        this.expenseDate = expenseDate;
        this.createdAt = createdAt;
        this.shares = Collections.unmodifiableList(new ArrayList<>(shares));
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public String getPayerId() {
        return payerId;
    }

    public String getDescription() {
        return description;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public Category getCategory() {
        return category;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    /** The day the expense happened, chosen by the user. */
    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    /** When the expense was entered in the app. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** One share per participant, in the order participants were listed. */
    public List<ExpenseShare> getShares() {
        return shares;
    }

    /** True if the user is one of the participants. */
    public boolean hasShareFor(String userId) {
        for (ExpenseShare share : shares) {
            if (share.getUserId().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    /** How much the user owes for this expense, or 0 if they aren't a participant. */
    public long owedBy(String userId) {
        for (ExpenseShare share : shares) {
            if (share.getUserId().equals(userId)) {
                return share.getOwedCents();
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Expense && Objects.equals(id, ((Expense) other).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
