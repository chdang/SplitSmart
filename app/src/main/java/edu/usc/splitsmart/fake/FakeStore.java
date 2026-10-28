package edu.usc.splitsmart.fake;

import android.os.Handler;
import android.os.Looper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.logic.SplitSmartException;
import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.model.User;

/**
 * In-memory data shared by the fake handlers, so the screens can run before the real database
 * exists. Everything resets when the app process ends.
 *
 * <p>Delete this package once every real handler is wired into AppServices.
 */
public final class FakeStore {

    /** Results arrive after a short delay so loading indicators can be seen and checked. */
    private static final long DELAY_MS = 250;

    private final Handler mainThread = new Handler(Looper.getMainLooper());

    final Map<String, User> users = new LinkedHashMap<>();
    /** User id to password. Plain text is acceptable only because this is throwaway fake data. */
    final Map<String, String> passwords = new HashMap<>();
    final Map<String, Group> groups = new LinkedHashMap<>();
    /** Group id to member ids, in the order they joined. */
    final Map<String, List<String>> members = new HashMap<>();
    final Map<String, Expense> expenses = new LinkedHashMap<>();
    final List<Settlement> settlements = new ArrayList<>();
    String currentUserId;

    public FakeStore() {
        seed();
    }

    <T> void succeed(Callback<T> callback, T result) {
        mainThread.postDelayed(() -> callback.onSuccess(result), DELAY_MS);
    }

    void fail(Callback<?> callback, SplitSmartException.Code code) {
        fail(callback, new SplitSmartException(code));
    }

    void fail(Callback<?> callback, SplitSmartException error) {
        mainThread.postDelayed(() -> callback.onError(error), DELAY_MS);
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    User findByEmail(String email) {
        String wanted = email.trim().toLowerCase(Locale.US);
        for (User user : users.values()) {
            if (user.getEmail().equals(wanted)) {
                return user;
            }
        }
        return null;
    }

    boolean isMember(String groupId, String userId) {
        List<String> ids = members.get(groupId);
        return ids != null && ids.contains(userId);
    }

    /** The group's expenses, newest expense date first. */
    List<Expense> expensesFor(String groupId) {
        List<Expense> result = new ArrayList<>();
        for (Expense expense : expenses.values()) {
            if (expense.getGroupId().equals(groupId)) {
                result.add(expense);
            }
        }
        result.sort(Comparator.comparing(Expense::getExpenseDate)
                .thenComparing(Expense::getCreatedAt)
                .reversed());
        return result;
    }

    List<Settlement> settlementsFor(String groupId) {
        List<Settlement> result = new ArrayList<>();
        for (Settlement settlement : settlements) {
            if (settlement.getGroupId().equals(groupId)) {
                result.add(settlement);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------------------------------
    // Sample data. Log in as tommy@usc.edu with the password "password".
    // ---------------------------------------------------------------------------------------

    private void seed() {
        String tommy = addUser("Tommy Trojan", "tommy@usc.edu", "1234567890");
        String alex = addUser("Alex Kim", "alex@usc.edu", "2345678901");
        String sam = addUser("Sam Rivera", "sam@usc.edu", "3456789012");
        String jordan = addUser("Jordan Lee", "jordan@usc.edu", "4567890123");
        String priya = addUser("Priya Patel", "priya@usc.edu", "5678901234");

        String apartment = addGroup("Apartment 4B", "Rent, utilities, and groceries", 60,
                tommy, alex, sam, jordan);
        Map<String, Long> rentSplit = allocations(tommy, 2500, alex, 2500, sam, 3000, jordan, 2000);
        addExpense(apartment, tommy, tommy, "Rent", 320000, Category.HOUSING,
                SplitType.PERCENTAGE, 40, rentSplit);
        addExpense(apartment, alex, alex, "Costco run", 18640, Category.FOOD, SplitType.EQUAL,
                33, allocations(tommy, 0, alex, 0, sam, 0, jordan, 0));
        addSettlement(apartment, alex, tommy, 80000, 20);
        addExpense(apartment, tommy, tommy, "Rent", 320000, Category.HOUSING,
                SplitType.PERCENTAGE, 12, rentSplit);
        addExpense(apartment, sam, sam, "Uber to LAX", 6425, Category.TRANSPORTATION,
                SplitType.EXACT_AMOUNT, 6, allocations(tommy, 2100, sam, 2200, jordan, 2125));
        // Tommy entered this one, but Jordan paid for it.
        addExpense(apartment, tommy, jordan, "Trader Joe's", 7218, Category.FOOD,
                SplitType.EQUAL, 3, allocations(tommy, 0, alex, 0, sam, 0, jordan, 0));
        addExpense(apartment, tommy, tommy, "Dodgers tickets", 14000, Category.ENTERTAINMENT,
                SplitType.EQUAL, 1, allocations(tommy, 0, alex, 0));

        // Sam owns this group, so Tommy can't edit it or Sam's expenses.
        String ramen = addGroup("Ramen Night", "Fridays after class", 30, sam, tommy, priya);
        addExpense(ramen, sam, sam, "Daikokuya", 5490, Category.FOOD, SplitType.EQUAL, 9,
                allocations(sam, 0, tommy, 0, priya, 0));
        addExpense(ramen, priya, priya, "Tsujita", 6150, Category.FOOD, SplitType.EXACT_AMOUNT, 2,
                allocations(priya, 2250, tommy, 1900, sam, 2000));
    }

    private String addUser(String name, String email, String uscId) {
        // Derived from the email rather than random, so it matches the id the seeded database
        // gives the same account. Login is real while these handlers are still fake, and
        // without this the real user id would find none of the sample groups below.
        String id = "u-" + email.substring(0, email.indexOf('@'));
        users.put(id, new User(id, name, email, uscId));
        passwords.put(id, "password");
        return id;
    }

    private String addGroup(String name, String description, int daysAgo, String ownerId,
                            String... memberIds) {
        String id = newId();
        groups.put(id, new Group(id, name, description, ownerId, daysAgoInstant(daysAgo)));
        List<String> ids = new ArrayList<>();
        ids.add(ownerId);
        for (String memberId : memberIds) {
            if (!ids.contains(memberId)) {
                ids.add(memberId);
            }
        }
        members.put(id, ids);
        return id;
    }

    private void addExpense(String groupId, String creatorId, String payerId, String description,
                            long totalCents, Category category, SplitType splitType, int daysAgo,
                            Map<String, Long> allocations) {
        String id = newId();
        expenses.put(id, new Expense(id, groupId, creatorId, payerId, description, totalCents,
                category, splitType, LocalDate.now().minusDays(daysAgo), daysAgoInstant(daysAgo),
                FakeExpenseHandler.calculateShares(id, totalCents, splitType, allocations)));
    }

    private void addSettlement(String groupId, String payerId, String receiverId, long cents,
                               int daysAgo) {
        settlements.add(new Settlement(newId(), groupId, payerId, payerId, receiverId, cents,
                daysAgoInstant(daysAgo)));
    }

    /** Builds an ordered allocation map from alternating user ids and values. */
    private static Map<String, Long> allocations(Object... idValuePairs) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < idValuePairs.length; i += 2) {
            result.put((String) idValuePairs[i], ((Number) idValuePairs[i + 1]).longValue());
        }
        return result;
    }

    private static Instant daysAgoInstant(int days) {
        return Instant.now().minus(Duration.ofDays(days));
    }
}
