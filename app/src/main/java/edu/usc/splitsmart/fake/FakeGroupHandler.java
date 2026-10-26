package edu.usc.splitsmart.fake;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.logic.GroupHandler;
import edu.usc.splitsmart.logic.MonthlyStatistics;
import edu.usc.splitsmart.logic.SplitSmartException;
import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.model.User;

/** Stand-in for the real GroupHandler. Replace in AppServices when role 3's version lands. */
public final class FakeGroupHandler implements GroupHandler {
    private final FakeStore store;

    public FakeGroupHandler(FakeStore store) {
        this.store = store;
    }

    @Override
    public void createGroup(String ownerId, String name, String description, List<String> emails,
                            Callback<Group> callback) {
        if (name.trim().isEmpty()) {
            store.fail(callback, SplitSmartException.Code.INVALID_INPUT);
            return;
        }
        List<String> memberIds = new ArrayList<>();
        memberIds.add(ownerId);
        if (!resolveEmails(emails, memberIds, callback)) {
            return;
        }
        String id = FakeStore.newId();
        Group group = new Group(id, name.trim(), description.trim(), ownerId, Instant.now());
        store.groups.put(id, group);
        store.members.put(id, memberIds);
        store.succeed(callback, group);
    }

    @Override
    public void getGroup(String groupId, Callback<Group> callback) {
        Group group = store.groups.get(groupId);
        if (group == null) {
            store.fail(callback, SplitSmartException.Code.NOT_FOUND);
        } else {
            store.succeed(callback, group);
        }
    }

    @Override
    public void getGroups(String userId, Callback<List<Group>> callback) {
        List<Group> result = new ArrayList<>();
        for (Group group : store.groups.values()) {
            if (store.isMember(group.getId(), userId)) {
                result.add(group);
            }
        }
        result.sort(Comparator.comparing(Group::getCreatedAt).reversed());
        store.succeed(callback, result);
    }

    @Override
    public void getMembers(String groupId, Callback<List<User>> callback) {
        List<String> ids = store.members.get(groupId);
        if (ids == null) {
            store.fail(callback, SplitSmartException.Code.NOT_FOUND);
            return;
        }
        List<User> result = new ArrayList<>();
        for (String id : ids) {
            result.add(store.users.get(id));
        }
        store.succeed(callback, result);
    }

    @Override
    public void getGroupExpenses(String groupId, Callback<List<Expense>> callback) {
        store.succeed(callback, store.expensesFor(groupId));
    }

    @Override
    public void getMonthlyStatistics(String groupId, YearMonth month,
                                     Callback<MonthlyStatistics> callback) {
        int count = 0;
        long total = 0;
        Map<Category, Long> byCategory = new EnumMap<>(Category.class);
        for (Expense expense : store.expensesFor(groupId)) {
            if (YearMonth.from(expense.getExpenseDate()).equals(month)) {
                count++;
                total += expense.getTotalCents();
                byCategory.merge(expense.getCategory(), expense.getTotalCents(), Long::sum);
            }
        }
        store.succeed(callback, new MonthlyStatistics(month, count, total, byCategory));
    }

    @Override
    public void updateGroup(String groupId, String actorId, String name, String description,
                            List<String> memberEmails, Callback<Group> callback) {
        Group group = store.groups.get(groupId);
        if (group == null) {
            store.fail(callback, SplitSmartException.Code.NOT_FOUND);
            return;
        }
        if (!group.getOwnerId().equals(actorId)) {
            store.fail(callback, SplitSmartException.Code.NOT_OWNER);
            return;
        }
        if (name.trim().isEmpty()) {
            store.fail(callback, SplitSmartException.Code.INVALID_INPUT);
            return;
        }
        List<String> memberIds = new ArrayList<>(store.members.get(groupId));
        if (!resolveEmails(memberEmails, memberIds, callback)) {
            return;
        }
        Group updated = new Group(groupId, name.trim(), description.trim(), group.getOwnerId(),
                group.getCreatedAt());
        store.groups.put(groupId, updated);
        store.members.put(groupId, memberIds);
        store.succeed(callback, updated);
    }

    @Override
    public void deleteGroup(String groupId, String actorId, Callback<Void> callback) {
        Group group = store.groups.get(groupId);
        if (group == null) {
            store.fail(callback, SplitSmartException.Code.NOT_FOUND);
            return;
        }
        if (!group.getOwnerId().equals(actorId)) {
            store.fail(callback, SplitSmartException.Code.NOT_OWNER);
            return;
        }
        store.groups.remove(groupId);
        store.members.remove(groupId);
        Iterator<Expense> expenses = store.expenses.values().iterator();
        while (expenses.hasNext()) {
            if (expenses.next().getGroupId().equals(groupId)) {
                expenses.remove();
            }
        }
        store.settlements.removeIf(settlement -> settlement.getGroupId().equals(groupId));
        store.succeed(callback, null);
    }

    @Override
    public void authenticateOwner(String groupId, String userId, Callback<Boolean> callback) {
        Group group = store.groups.get(groupId);
        store.succeed(callback, group != null && group.getOwnerId().equals(userId));
    }

    /**
     * Adds the account id for each email to {@code memberIds}. If any email has no account,
     * reports MISSING_ACCOUNTS through the callback and returns false.
     */
    private boolean resolveEmails(List<String> emails, List<String> memberIds,
                                  Callback<?> callback) {
        List<String> missing = new ArrayList<>();
        for (String email : emails) {
            User user = store.findByEmail(email);
            if (user == null) {
                missing.add(email);
            } else if (!memberIds.contains(user.getId())) {
                memberIds.add(user.getId());
            }
        }
        if (!missing.isEmpty()) {
            store.fail(callback,
                    new SplitSmartException(SplitSmartException.Code.MISSING_ACCOUNTS, missing));
            return false;
        }
        return true;
    }
}
