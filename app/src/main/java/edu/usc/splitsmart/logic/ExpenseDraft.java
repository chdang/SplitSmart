package edu.usc.splitsmart.logic;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.SplitType;

/**
 * What the expense form sends when creating or editing an expense. The handler checks it, works
 * out each participant's share, and saves it.
 */
public final class ExpenseDraft {
    private final String groupId;
    private final String payerId;
    private final String description;
    private final long totalCents;
    private final Category category;
    private final SplitType splitType;
    private final LocalDate expenseDate;
    private final Map<String, Long> allocations;

    /**
     * @param allocations one entry per participant, keyed by user id, in the order participants
     *     were listed. What the value means depends on the split type:
     *     <ul>
     *       <li>EQUAL: ignored (the form sends 0).
     *       <li>PERCENTAGE: basis points, so 25.5% is 2550. The values must total 10000.
     *       <li>EXACT_AMOUNT: cents. The values must total {@code totalCents}.
     *     </ul>
     */
    public ExpenseDraft(String groupId, String payerId, String description, long totalCents,
                        Category category, SplitType splitType, LocalDate expenseDate,
                        Map<String, Long> allocations) {
        this.groupId = groupId;
        this.payerId = payerId;
        this.description = description;
        this.totalCents = totalCents;
        this.category = category;
        this.splitType = splitType;
        this.expenseDate = expenseDate;
        this.allocations = Collections.unmodifiableMap(new LinkedHashMap<>(allocations));
    }

    public String getGroupId() {
        return groupId;
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

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public Map<String, Long> getAllocations() {
        return allocations;
    }
}
