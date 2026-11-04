package edu.usc.splitsmart.ui.report;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import edu.usc.splitsmart.logic.ActivityItem;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.Labels;

/**
 * The search text, kind filter, and sort order of the history screen (R15). Holds the current
 * choices and narrows a loaded history down to the rows that should be on screen, so the list
 * can be re-filtered without going back to the database.
 */
final class HistoryFilter {

    /** Which rows to show. */
    enum Kind {
        ALL,
        EXPENSES,
        PAYMENTS
    }

    /** Row order. The handler returns newest first, which is the default. */
    enum Sort {
        NEWEST,
        OLDEST,
        LARGEST,
        SMALLEST
    }

    private final Context context;
    /** Lower-cased member names by id, used to match a search against who was involved. */
    private final Map<String, String> names = new HashMap<>();
    private String query = "";
    private Kind kind = Kind.ALL;
    private Sort sort = Sort.NEWEST;

    HistoryFilter(Context context) {
        this.context = context;
    }

    void setMembers(List<User> members) {
        names.clear();
        for (User member : members) {
            names.put(member.getId(), lower(member.getName()));
        }
    }

    void setQuery(String text) {
        query = lower(text).trim();
    }

    void setKind(Kind newKind) {
        kind = newKind;
    }

    void setSort(Sort newSort) {
        sort = newSort;
    }

    Sort getSort() {
        return sort;
    }

    /** The rows of {@code items} that match the current search and filter, in sort order. */
    List<ActivityItem> apply(List<ActivityItem> items) {
        List<ActivityItem> matches = new ArrayList<>();
        for (ActivityItem item : items) {
            if (matchesKind(item) && matchesQuery(item)) {
                matches.add(item);
            }
        }
        sort(matches);
        return matches;
    }

    private boolean matchesKind(ActivityItem item) {
        switch (kind) {
            case EXPENSES:
                return item.getType() == ActivityItem.Type.EXPENSE;
            case PAYMENTS:
                return item.getType() == ActivityItem.Type.SETTLEMENT;
            case ALL:
            default:
                return true;
        }
    }

    private boolean matchesQuery(ActivityItem item) {
        if (query.isEmpty()) {
            return true;
        }
        for (String field : searchableText(item)) {
            if (field.contains(query)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Everything a search can hit for one row, lower-cased. An expense matches on what it was
     * for, its note, its category, and who paid; a payment matches on its note and both sides.
     */
    private List<String> searchableText(ActivityItem item) {
        List<String> fields = new ArrayList<>();
        if (item.getType() == ActivityItem.Type.EXPENSE) {
            Expense expense = item.getExpense();
            fields.add(lower(expense.getDescription()));
            fields.add(lower(expense.getNote()));
            fields.add(lower(Labels.category(context, expense.getCategory())));
            fields.add(nameOf(expense.getPayerId()));
        } else {
            Settlement settlement = item.getSettlement();
            fields.add(lower(settlement.getNote()));
            fields.add(nameOf(settlement.getPayerId()));
            fields.add(nameOf(settlement.getReceiverId()));
        }
        return fields;
    }

    private void sort(List<ActivityItem> matches) {
        switch (sort) {
            case OLDEST:
                Collections.sort(matches, (left, right) ->
                        left.getOccurredAt().compareTo(right.getOccurredAt()));
                break;
            case LARGEST:
                Collections.sort(matches, (left, right) ->
                        Long.compare(amountOf(right), amountOf(left)));
                break;
            case SMALLEST:
                Collections.sort(matches, (left, right) ->
                        Long.compare(amountOf(left), amountOf(right)));
                break;
            case NEWEST:
            default:
                Collections.sort(matches, (left, right) ->
                        right.getOccurredAt().compareTo(left.getOccurredAt()));
                break;
        }
    }

    private static long amountOf(ActivityItem item) {
        return item.getType() == ActivityItem.Type.EXPENSE
                ? item.getExpense().getTotalCents()
                : item.getSettlement().getAmountCents();
    }

    /** A former member has no name to match, so their rows only match on their other text. */
    private String nameOf(String userId) {
        String name = names.get(userId);
        return name == null ? "" : name;
    }

    private static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.getDefault());
    }
}
