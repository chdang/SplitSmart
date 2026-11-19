
package edu.usc.splitsmart.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.ExpenseShare;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.GroupRepository;

/**
 * White-box tests for DefaultExpenseHandler using hand-written fakes (no Mockito).
 *
 * Runs under Robolectric because Async posts results to Looper.getMainLooper(), which doesn't
 * exist in a plain JVM test. Result.await() pumps that looper until the callback arrives.
 *
 * Remaining assumption (not yet confirmed): SplitSmartException has getCode().
 */
@RunWith(RobolectricTestRunner.class)
public class DefaultExpenseHandlerTest {

    private static final String GROUP = "g1";
    private static final String ME = "u1";
    private static final String OTHER = "u2";
    private static final String STRANGER = "u9";
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    private FakeGroupRepository repo;
    private FakeSession session;
    private DefaultExpenseHandler handler;

    @Before
    public void setUp() {
        repo = new FakeGroupRepository();
        repo.addMember(GROUP, ME);
        repo.addMember(GROUP, OTHER);
        session = new FakeSession(newUser(ME, "me", "1234567890"));
        handler = new DefaultExpenseHandler(repo, session);
    }

    // ================================================================ 1. SESSION

    @Test
    public void testNotLoggedIn_returnsNotLoggedIn() throws Exception {
        session.current = null;
        repo.seedExpense("e1", ME);

        Result<Expense> create = new Result<>();
        handler.createExpense(ME, validDraft(), create);
        assertFailed(create, SplitSmartException.Code.NOT_LOGGED_IN);

        Result<Expense> get = new Result<>();
        handler.getExpense("e1", get);
        assertFailed(get, SplitSmartException.Code.NOT_LOGGED_IN);

        Result<List<Expense>> list = new Result<>();
        handler.getExpenses(GROUP, list);
        assertFailed(list, SplitSmartException.Code.NOT_LOGGED_IN);

        Result<Expense> update = new Result<>();
        handler.updateExpense("e1", ME, validDraft(), update);
        assertFailed(update, SplitSmartException.Code.NOT_LOGGED_IN);

        Result<Void> delete = new Result<>();
        handler.deleteExpense("e1", ME, delete);
        assertFailed(delete, SplitSmartException.Code.NOT_LOGGED_IN);

        Result<Boolean> auth = new Result<>();
        handler.authenticateCreator("e1", ME, auth);
        assertFailed(auth, SplitSmartException.Code.NOT_LOGGED_IN);

        Result<Settlement> settle = new Result<>();
        handler.recordSettlement(GROUP, ME, OTHER, 100, "", settle);
        assertFailed(settle, SplitSmartException.Code.NOT_LOGGED_IN);

        // Nothing should have been written.
        assertEquals(0, repo.saveCount);
        assertEquals(0, repo.replaceCount);
        assertEquals(0, repo.deleteCount);
        assertEquals(0, repo.settlementCount);
    }

    // ================================================== 2. CREATOR IMPERSONATION

    @Test
    public void testCreatorImpersonation() throws Exception {
        // createExpense: creatorId must equal the logged-in user (INVALID_INPUT).
        Result<Expense> create = new Result<>();
        handler.createExpense(OTHER, validDraft(), create);
        assertFailed(create, SplitSmartException.Code.INVALID_INPUT);
        assertEquals(0, repo.saveCount);

        // updateExpense: actorId differs from the logged-in user (NOT_CREATOR).
        repo.seedExpense("e1", ME);
        Result<Expense> updateActor = new Result<>();
        handler.updateExpense("e1", OTHER, validDraft(), updateActor);
        assertFailed(updateActor, SplitSmartException.Code.NOT_CREATOR);

        // updateExpense: logged-in user is not the expense's creator (NOT_CREATOR).
        repo.seedExpense("e2", OTHER);
        Result<Expense> updateNotCreator = new Result<>();
        handler.updateExpense("e2", ME, validDraft(), updateNotCreator);
        assertFailed(updateNotCreator, SplitSmartException.Code.NOT_CREATOR);
        assertEquals(0, repo.replaceCount);

        // deleteExpense: same two cases.
        Result<Void> deleteActor = new Result<>();
        handler.deleteExpense("e1", OTHER, deleteActor);
        assertFailed(deleteActor, SplitSmartException.Code.NOT_CREATOR);

        Result<Void> deleteNotCreator = new Result<>();
        handler.deleteExpense("e2", ME, deleteNotCreator);
        assertFailed(deleteNotCreator, SplitSmartException.Code.NOT_CREATOR);
        assertEquals(0, repo.deleteCount);
    }

    // ============================================ 3. INVALID OR BLANK FIELDS

    @Test
    public void testInvalidOrBlankFields() throws Exception {
        // createExpense: null draft
        Result<Expense> nullDraft = new Result<>();
        handler.createExpense(ME, null, nullDraft);
        assertFailed(nullDraft, SplitSmartException.Code.INVALID_INPUT);

        // createExpense: null / empty / whitespace for each string field
        for (String bad : new String[]{null, "", "   "}) {
            Result<Expense> g = new Result<>();
            handler.createExpense(ME, newDraft(bad, ME, "Dinner", 1000, Category.values()[0],
                    SplitType.EQUAL, DATE, equalAllocations(ME, OTHER)), g);
            assertFailed(g, SplitSmartException.Code.INVALID_INPUT);

            Result<Expense> p = new Result<>();
            handler.createExpense(ME, newDraft(GROUP, bad, "Dinner", 1000, Category.values()[0],
                    SplitType.EQUAL, DATE, equalAllocations(ME, OTHER)), p);
            assertFailed(p, SplitSmartException.Code.INVALID_INPUT);

            Result<Expense> d = new Result<>();
            handler.createExpense(ME, newDraft(GROUP, ME, bad, 1000, Category.values()[0],
                    SplitType.EQUAL, DATE, equalAllocations(ME, OTHER)), d);
            assertFailed(d, SplitSmartException.Code.INVALID_INPUT);
        }

        // createExpense: null category, null date
        Result<Expense> noCategory = new Result<>();
        handler.createExpense(ME, newDraft(GROUP, ME, "Dinner", 1000, null,
                SplitType.EQUAL, DATE, equalAllocations(ME, OTHER)), noCategory);
        assertFailed(noCategory, SplitSmartException.Code.INVALID_INPUT);

        Result<Expense> noDate = new Result<>();
        handler.createExpense(ME, newDraft(GROUP, ME, "Dinner", 1000, Category.values()[0],
                SplitType.EQUAL, null, equalAllocations(ME, OTHER)), noDate);
        assertFailed(noDate, SplitSmartException.Code.INVALID_INPUT);
        assertEquals(0, repo.saveCount);

        // updateExpense: null draft, and draft for a different group
        repo.seedExpense("e1", ME);
        Result<Expense> updNull = new Result<>();
        handler.updateExpense("e1", ME, null, updNull);
        assertFailed(updNull, SplitSmartException.Code.INVALID_INPUT);

        Result<Expense> updWrongGroup = new Result<>();
        handler.updateExpense("e1", ME, newDraft("g2", ME, "Dinner", 1000, Category.values()[0],
                SplitType.EQUAL, DATE, equalAllocations(ME, OTHER)), updWrongGroup);
        assertFailed(updWrongGroup, SplitSmartException.Code.INVALID_INPUT);
        assertEquals(0, repo.replaceCount);

        // recordSettlement: non-positive amount
        for (long bad : new long[]{0L, -1L}) {
            Result<Settlement> r = new Result<>();
            handler.recordSettlement(GROUP, ME, OTHER, bad, "", r);
            assertFailed(r, SplitSmartException.Code.INVALID_AMOUNT);
        }

        // recordSettlement: blank payer / receiver, and payer == receiver
        for (String bad : new String[]{null, "", "   "}) {
            Result<Settlement> payer = new Result<>();
            handler.recordSettlement(GROUP, bad, OTHER, 100, "", payer);
            assertFailed(payer, SplitSmartException.Code.INVALID_INPUT);

            Result<Settlement> receiver = new Result<>();
            handler.recordSettlement(GROUP, ME, bad, 100, "", receiver);
            assertFailed(receiver, SplitSmartException.Code.INVALID_INPUT);
        }
        Result<Settlement> same = new Result<>();
        handler.recordSettlement(GROUP, ME, ME, 100, "", same);
        assertFailed(same, SplitSmartException.Code.INVALID_INPUT);

        // recordSettlement: more than the payer owes the receiver (AMOUNT_ABOVE_DEBT).
        // e1 (seeded above) was paid by ME, so OTHER owes ME $5.00 and ME owes OTHER nothing.
        Result<Settlement> tooMuch = new Result<>();
        handler.recordSettlement(GROUP, OTHER, ME, 501, "", tooMuch);
        assertFailed(tooMuch, SplitSmartException.Code.AMOUNT_ABOVE_DEBT);

        Result<Settlement> wrongWay = new Result<>();
        handler.recordSettlement(GROUP, ME, OTHER, 1, "", wrongWay);
        assertFailed(wrongWay, SplitSmartException.Code.AMOUNT_ABOVE_DEBT);
        assertEquals(0, repo.settlementCount);
    }

    // ======================================== 4. NOT FOUND AND NON-MEMBER ERRORS

    @Test
    public void testNotFoundAndNonMemberErrors() throws Exception {
        // ---- NOT_FOUND ----
        Result<Expense> get = new Result<>();
        handler.getExpense("missing", get);
        assertFailed(get, SplitSmartException.Code.NOT_FOUND);

        Result<Expense> update = new Result<>();
        handler.updateExpense("missing", ME, validDraft(), update);
        assertFailed(update, SplitSmartException.Code.NOT_FOUND);

        Result<Void> delete = new Result<>();
        handler.deleteExpense("missing", ME, delete);
        assertFailed(delete, SplitSmartException.Code.NOT_FOUND);

        // ---- NOT_A_MEMBER: createExpense (creator, payer, participant) ----
        repo.removeMember(GROUP, ME);
        Result<Expense> creatorOut = new Result<>();
        handler.createExpense(ME, validDraft(), creatorOut);
        assertFailed(creatorOut, SplitSmartException.Code.NOT_A_MEMBER);
        repo.addMember(GROUP, ME);

        Result<Expense> payerOut = new Result<>();
        handler.createExpense(ME, newDraft(GROUP, STRANGER, "Dinner", 1000, Category.values()[0],
                SplitType.EQUAL, DATE, equalAllocations(ME, OTHER)), payerOut);
        assertFailed(payerOut, SplitSmartException.Code.NOT_A_MEMBER);

        Result<Expense> participantOut = new Result<>();
        handler.createExpense(ME, newDraft(GROUP, ME, "Dinner", 1000, Category.values()[0],
                SplitType.EQUAL, DATE, equalAllocations(ME, STRANGER)), participantOut);
        assertFailed(participantOut, SplitSmartException.Code.NOT_A_MEMBER);
        assertEquals(0, repo.saveCount);

        // ---- NOT_A_MEMBER: read/delete/authenticate when caller left the group ----
        repo.seedExpense("e1", ME);
        repo.removeMember(GROUP, ME);

        Result<Expense> getOut = new Result<>();
        handler.getExpense("e1", getOut);
        assertFailed(getOut, SplitSmartException.Code.NOT_A_MEMBER);

        Result<List<Expense>> listOut = new Result<>();
        handler.getExpenses(GROUP, listOut);
        assertFailed(listOut, SplitSmartException.Code.NOT_A_MEMBER);

        Result<Void> deleteOut = new Result<>();
        handler.deleteExpense("e1", ME, deleteOut);
        assertFailed(deleteOut, SplitSmartException.Code.NOT_A_MEMBER);
        assertEquals(0, repo.deleteCount);

        Result<Boolean> authOut = new Result<>();
        handler.authenticateCreator("e1", ME, authOut);
        assertFailed(authOut, SplitSmartException.Code.NOT_A_MEMBER);
        repo.addMember(GROUP, ME);

        // ---- NOT_A_MEMBER: recordSettlement (current user, payer, receiver) ----
        Result<Settlement> payer = new Result<>();
        handler.recordSettlement(GROUP, STRANGER, OTHER, 100, "", payer);
        assertFailed(payer, SplitSmartException.Code.NOT_A_MEMBER);

        Result<Settlement> receiver = new Result<>();
        handler.recordSettlement(GROUP, ME, STRANGER, 100, "", receiver);
        assertFailed(receiver, SplitSmartException.Code.NOT_A_MEMBER);

        repo.removeMember(GROUP, ME);
        Result<Settlement> current = new Result<>();
        handler.recordSettlement(GROUP, OTHER, ME, 100, "", current);
        assertFailed(current, SplitSmartException.Code.NOT_A_MEMBER);
        assertEquals(0, repo.settlementCount);
    }

    // ================================================================ 5. SUCCESS

    @Test
    public void testCreateExpenseSuccess() throws Exception {
        // EQUAL split: description is trimmed, creator is the logged-in user, shares sum to total.
        Result<Expense> equal = new Result<>();
        handler.createExpense(ME, newDraft(GROUP, OTHER, "  Dinner  ", 1000,
                Category.values()[0], SplitType.EQUAL, DATE, equalAllocations(ME, OTHER)), equal);
        assertSucceeded(equal);

        assertEquals(1, repo.saveCount);
        assertEquals("Dinner", equal.value.getDescription());
        assertEquals(ME, equal.value.getCreatorId());
        assertEquals(OTHER, equal.value.getPayerId());
        assertEquals(GROUP, equal.value.getGroupId());
        assertNotNull(equal.value.getId());
        assertEquals(2, equal.value.getShares().size());
        long sum = 0;
        for (ExpenseShare s : equal.value.getShares()) {
            sum += s.getOwedCents();
            assertNull(s.getBasisPoints());
        }
        assertEquals(1000L, sum);
        assertEquals(equal.value, repo.findExpenseById(equal.value.getId()));

        // PERCENTAGE split: each share carries its basis points.
        Map<String, Long> pct = new LinkedHashMap<>();
        pct.put(ME, 7000L);
        pct.put(OTHER, 3000L);
        Result<Expense> percent = new Result<>();
        handler.createExpense(ME, newDraft(GROUP, ME, "Rent", 1000, Category.values()[0],
                SplitType.PERCENTAGE, DATE, pct), percent);
        assertSucceeded(percent);
        for (ExpenseShare s : percent.value.getShares()) {
            assertEquals(pct.get(s.getUserId()), s.getBasisPoints());
        }
    }

    @Test
    public void testReadSuccess() throws Exception {
        Expense e1 = repo.seedExpense("e1", ME);
        Expense e2 = repo.seedExpense("e2", OTHER);

        Result<Expense> get = new Result<>();
        handler.getExpense("e1", get);
        assertSucceeded(get);
        assertEquals(e1, get.value);

        Result<List<Expense>> list = new Result<>();
        handler.getExpenses(GROUP, list);
        assertSucceeded(list);
        assertEquals(2, list.value.size());
        assertTrue(list.value.contains(e1));
        assertTrue(list.value.contains(e2));

        // authenticateCreator: true for creator, false for non-creator, false for unknown expense
        Result<Boolean> isCreator = new Result<>();
        handler.authenticateCreator("e1", ME, isCreator);
        assertSucceeded(isCreator);
        assertEquals(Boolean.TRUE, isCreator.value);

        Result<Boolean> notCreator = new Result<>();
        handler.authenticateCreator("e1", OTHER, notCreator);
        assertSucceeded(notCreator);
        assertEquals(Boolean.FALSE, notCreator.value);

        Result<Boolean> unknown = new Result<>();
        handler.authenticateCreator("missing", ME, unknown);
        assertSucceeded(unknown);
        assertEquals(Boolean.FALSE, unknown.value);
    }

    @Test
    public void testUpdateDeleteAndSettlementSuccess() throws Exception {
        // update: id, creator and createdAt preserved; replaced rather than newly saved
        repo.seedExpense("e1", ME);
        Result<Expense> update = new Result<>();
        handler.updateExpense("e1", ME, newDraft(GROUP, ME, "New desc", 2000,
                Category.values()[0], SplitType.EQUAL, DATE, equalAllocations(ME, OTHER)), update);
        assertSucceeded(update);
        assertEquals(1, repo.replaceCount);
        assertEquals(0, repo.saveCount);
        assertEquals("e1", update.value.getId());
        assertEquals(ME, update.value.getCreatorId());
        assertEquals(CREATED, update.value.getCreatedAt());
        assertEquals("New desc", update.value.getDescription());
        assertEquals(2000L, update.value.getTotalCents());

        // delete: removed from the repository
        Result<Void> delete = new Result<>();
        handler.deleteExpense("e1", ME, delete);
        assertSucceeded(delete);
        assertEquals(1, repo.deleteCount);
        assertNull(repo.findExpenseById("e1"));

        // settlement: saved with the current user as recorder. ME paid this expense, so OTHER
        // owes ME exactly $5.00, and a $5.00 payment (the whole debt) is allowed.
        repo.seedExpense("e2", ME);
        Result<Settlement> settle = new Result<>();
        handler.recordSettlement(GROUP, OTHER, ME, 500, "", settle);
        assertSucceeded(settle);
        assertEquals(1, repo.settlementCount);
        assertEquals(ME, repo.lastRecorderId);
        assertEquals(OTHER, repo.lastPayerId);
        assertEquals(ME, repo.lastReceiverId);
        assertEquals(500L, repo.lastAmountCents);
    }

    // ============================================================ TEST HELPERS

    private static User newUser(String id, String name, String uscId) {
        return new User(id, name, name + "@usc.edu", uscId);
    }

    private static ExpenseDraft newDraft(String groupId, String payerId, String description,
                                         long totalCents, Category category, SplitType splitType,
                                         LocalDate date, Map<String, Long> allocations) {
        return new ExpenseDraft(groupId, payerId, description, "", totalCents, category, splitType,
                date, allocations);
    }

    private static ExpenseDraft validDraft() {
        return newDraft(GROUP, ME, "Dinner", 1000, Category.values()[0], SplitType.EQUAL, DATE,
                equalAllocations(ME, OTHER));
    }

    /** EQUAL splits ignore the values (the form sends 0); the keys are the participants. */
    private static Map<String, Long> equalAllocations(String... ids) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String id : ids) m.put(id, 0L);
        return m;
    }

    private static <T> void assertFailed(Result<T> r, SplitSmartException.Code code)
            throws InterruptedException {
        r.await();
        assertNotNull("expected " + code + " but no error was delivered", r.error);
        assertTrue("expected SplitSmartException but got " + r.error,
                r.error instanceof SplitSmartException);
        assertEquals(code, ((SplitSmartException) r.error).getCode());
    }

    private static <T> void assertSucceeded(Result<T> r) throws InterruptedException {
        r.await();
        assertNull("unexpected error: " + r.error, r.error);
    }

    /** Callback that records the outcome and lets the test wait for it. */
    private static final class Result<T> implements Callback<T> {
        private final CountDownLatch latch = new CountDownLatch(1);
        T value;
        Exception error;

        @Override public void onSuccess(T v) { value = v; latch.countDown(); }
        @Override public void onError(Exception e) { error = e; latch.countDown(); }

        /** Async does its work on a worker thread, then posts the callback to the main looper. */
        void await() throws InterruptedException {
            long deadline = System.currentTimeMillis() + 2000;
            while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                latch.await(10, TimeUnit.MILLISECONDS);
            }
            assertEquals("callback never fired", 0, latch.getCount());
        }
    }

    // ================================================================== FAKES

    /** In-memory SessionHandler. Set {@code current} to control who is logged in (null = nobody). */
    private static final class FakeSession implements SessionHandler {
        User current;

        FakeSession(User current) { this.current = current; }

        @Override public void login(String email, String password, Callback<User> callback) {
            // DefaultExpenseHandler never logs anyone in; tests set `current` directly.
            throw new UnsupportedOperationException();
        }

        @Override public void logout() { current = null; }

        @Override public User getCurrentUser() { return current; }

        @Override public User requireCurrentUser() throws SplitSmartException {
            if (current == null) {
                throw new SplitSmartException(SplitSmartException.Code.NOT_LOGGED_IN);
            }
            return current;
        }
    }

    /** In-memory GroupRepository. Implements every interface method; only the ones the handler uses do real work. */
    private static final class FakeGroupRepository implements GroupRepository {
        private final Map<String, Expense> expenses = new LinkedHashMap<>();
        private final Set<String> members = new HashSet<>();

        int saveCount, replaceCount, deleteCount, settlementCount;
        String lastRecorderId, lastPayerId, lastReceiverId;
        long lastAmountCents;

        void addMember(String groupId, String userId) { members.add(groupId + "|" + userId); }
        void removeMember(String groupId, String userId) { members.remove(groupId + "|" + userId); }

        /** Puts a ready-made expense into the repository without counting as a save. */
        Expense seedExpense(String id, String creatorId) {
            List<ExpenseShare> shares = new ArrayList<>();
            shares.add(new ExpenseShare(id, ME, 500L, null));
            shares.add(new ExpenseShare(id, OTHER, 500L, null));
            Expense e = new Expense(id, GROUP, creatorId, creatorId, "Seeded", "", 1000L,
                    Category.values()[0], SplitType.EQUAL, DATE, CREATED, shares);
            expenses.put(id, e);
            return e;
        }

        @Override public boolean isMember(String groupId, String userId) {
            return members.contains(groupId + "|" + userId);
        }

        @Override public void saveExpense(Expense expense) {
            saveCount++;
            expenses.put(expense.getId(), expense);
        }

        @Override public void replaceExpense(Expense expense) {
            replaceCount++;
            expenses.put(expense.getId(), expense);
        }

        @Override public void deleteExpense(String expenseId) {
            deleteCount++;
            expenses.remove(expenseId);
        }

        @Override public Expense findExpenseById(String expenseId) {
            return expenses.get(expenseId);
        }

        @Override public List<Expense> findExpensesByGroup(String groupId) {
            List<Expense> result = new ArrayList<>();
            for (Expense e : expenses.values()) {
                if (e.getGroupId().equals(groupId)) result.add(e);
            }
            return result;
        }

        // ---- Methods DefaultExpenseHandler never calls. They throw so an unexpected call is loud. ----

        @Override public Group saveGroup(String name, String description, String ownerId,
                                         Instant createdAt, List<String> memberIds) {
            throw new UnsupportedOperationException();
        }

        @Override public Group updateGroup(String groupId, String name, String description) {
            throw new UnsupportedOperationException();
        }

        @Override public void addMembers(String groupId, List<String> userIds) {
            throw new UnsupportedOperationException();
        }

        @Override public void deleteGroup(String groupId) {
            throw new UnsupportedOperationException();
        }

        @Override public Group findGroupById(String groupId) {
            throw new UnsupportedOperationException();
        }

        @Override public List<Group> findGroupsByUser(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override public List<User> findMembers(String groupId) {
            throw new UnsupportedOperationException();
        }

        @Override public boolean isOwner(String groupId, String userId) {
            throw new UnsupportedOperationException();
        }

        /** No settlements are ever stored here, so the handler's debt check sees none. */
        @Override public List<Settlement> findSettlementsByGroup(String groupId) {
            return new ArrayList<>();
        }

        @Override public List<Expense> findExpensesByGroupAndMonth(String groupId, YearMonth month) {
            throw new UnsupportedOperationException();
        }

        /** Returns null: nothing here needs the Settlement object, so tests check the recorded args. */
        @Override public Settlement saveSettlement(String groupId, String recorderId, String payerId,
                                                   String receiverId, long amountCents, String note,
                                                   Instant when) {
            settlementCount++;
            lastRecorderId = recorderId;
            lastPayerId = payerId;
            lastReceiverId = receiverId;
            lastAmountCents = amountCents;
            return null;
        }

        @Override public Settlement updateSettlement(String settlementId, String payerId,
                                                     String receiverId, long amountCents, String note) {
            throw new UnsupportedOperationException();
        }

        @Override public void deleteSettlement(String settlementId) {
            throw new UnsupportedOperationException();
        }

        @Override public Settlement findSettlementById(String settlementId) {
            throw new UnsupportedOperationException();
        }
    }
}
 