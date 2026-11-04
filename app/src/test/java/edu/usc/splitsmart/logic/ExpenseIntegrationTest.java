package edu.usc.splitsmart.logic;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.ExpenseShare;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.SplitSmartDatabase;
import edu.usc.splitsmart.repository.SqliteGroupRepository;
import edu.usc.splitsmart.repository.SqliteUserRepository;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.expense.EditExpenseActivity;

/** Exercises real SQLite, session checks, callbacks, and AppServices wiring together. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ExpenseIntegrationTest {
    private static final String GROUP = "g-apartment";
    private static final String TOMMY = "u-tommy";
    private static final String ALEX = "u-alex";
    private static final String SAM = "u-sam";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 15);

    private Context context;
    private SplitSmartDatabase database;
    private SqliteGroupRepository groups;
    private SqliteUserRepository users;
    private SessionStore sessions;
    private DefaultSessionHandler session;
    private ExpenseHandler handler;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("splitsmart.db");
        database = new SplitSmartDatabase(context);
        groups = new SqliteGroupRepository(database);
        users = new SqliteUserRepository(database);
        sessions = new SessionStore(context);
        session = new DefaultSessionHandler(users, sessions);
        handler = new DefaultExpenseHandler(groups, session);
        loginAs(TOMMY);
    }

    @After
    public void tearDown() {
        sessions.clear();
        database.close();
        context.deleteDatabase("splitsmart.db");
    }

    @Test
    public void createAllowsDifferentPayerAndPersistsOrderedSharesAcrossReopen() {
        Expense created = create(draft(GROUP, ALEX, 1000, SplitType.EQUAL, DATE,
                allocations(SAM, 0L, TOMMY, 0L, ALEX, 0L)));
        assertEquals(TOMMY, created.getCreatorId());
        assertEquals(ALEX, created.getPayerId());
        assertEquals("Dinner", created.getDescription());
        assertEquals(334, created.owedBy(SAM));
        database.close();
        database = new SplitSmartDatabase(context);
        groups = new SqliteGroupRepository(database);
        Expense reloaded = groups.findExpenseById(created.getId());
        assertNotNull(reloaded);
        assertEquals(Arrays.asList(SAM, TOMMY, ALEX), participantIds(reloaded));
        assertEquals(334, reloaded.owedBy(SAM));
        assertEquals(DATE, reloaded.getExpenseDate());
        assertEquals(created.getCreatedAt().toEpochMilli(), reloaded.getCreatedAt().toEpochMilli());
    }

    @Test
    public void validatesLoginIdentityAndAllMembershipsBeforeSaving() {
        ExpenseDraft valid = standardDraft();
        assertError(SplitSmartException.Code.INVALID_INPUT,
                ExpenseIntegrationTest.<Expense>await(cb -> handler.createExpense(ALEX, valid, cb)));
        assertError(SplitSmartException.Code.NOT_A_MEMBER,
                ExpenseIntegrationTest.<Expense>await(cb -> handler.createExpense(TOMMY,
                        draft(GROUP, "u-priya", 100, SplitType.EQUAL, DATE,
                                allocations(TOMMY, 0L)), cb)));
        assertError(SplitSmartException.Code.NOT_A_MEMBER,
                ExpenseIntegrationTest.<Expense>await(cb -> handler.createExpense(TOMMY,
                        draft(GROUP, ALEX, 100, SplitType.EQUAL, DATE,
                                allocations("u-priya", 0L)), cb)));
        loginAs("u-priya");
        assertError(SplitSmartException.Code.NOT_A_MEMBER,
                ExpenseIntegrationTest.<Expense>await(cb -> handler.createExpense("u-priya", valid, cb)));
        sessions.clear();
        assertError(SplitSmartException.Code.NOT_LOGGED_IN,
                ExpenseIntegrationTest.<Expense>await(cb -> handler.createExpense(TOMMY, valid, cb)));
        assertTrue(groups.findExpensesByGroup(GROUP).isEmpty());
    }

    @Test
    public void invalidDraftsNeverSaveAnExpense() {
        List<ExpenseDraft> invalid = Arrays.asList(null,
                draft(GROUP, ALEX, 0, SplitType.EQUAL, DATE, allocations(TOMMY, 0L)),
                draft(GROUP, ALEX, 100, SplitType.EXACT_AMOUNT, DATE, allocations(TOMMY, 99L)),
                draft(GROUP, ALEX, 100, SplitType.PERCENTAGE, DATE, allocations(TOMMY, 9999L)),
                draft(GROUP, ALEX, 100, null, DATE, allocations(TOMMY, 0L)),
                draft(GROUP, ALEX, 100, SplitType.EQUAL, null, allocations(TOMMY, 0L)),
                draft(GROUP, ALEX, 100, SplitType.EQUAL, DATE, Collections.emptyMap()),
                new ExpenseDraft(GROUP, ALEX, " ", "", 100, Category.FOOD, SplitType.EQUAL,
                        DATE, allocations(TOMMY, 0L)),
                new ExpenseDraft(GROUP, ALEX, "Dinner", "", 100, null, SplitType.EQUAL,
                        DATE, allocations(TOMMY, 0L)));
        for (ExpenseDraft draft : invalid) {
            Result<Expense> result = ExpenseIntegrationTest.<Expense>await(cb -> handler.createExpense(TOMMY, draft, cb));
            assertTrue(result.error instanceof SplitSmartException);
            assertTrue(groups.findExpensesByGroup(GROUP).isEmpty());
        }
    }

    @Test
    public void payerCannotEditDeleteOrImpersonateCreator() {
        Expense created = create(standardDraft());
        loginAs(ALEX);
        assertFalse(ExpenseIntegrationTest.<Boolean>await(cb ->
                handler.authenticateCreator(created.getId(), ALEX, cb)).value());
        for (String actor : Arrays.asList(ALEX, TOMMY)) {
            assertError(SplitSmartException.Code.NOT_CREATOR,
                    ExpenseIntegrationTest.<Expense>await(cb -> handler.updateExpense(created.getId(), actor, standardDraft(), cb)));
            assertError(SplitSmartException.Code.NOT_CREATOR,
                    ExpenseIntegrationTest.<Void>await(cb -> handler.deleteExpense(created.getId(), actor, cb)));
        }
        assertEquals(1000, groups.findExpenseById(created.getId()).getTotalCents());
    }

    @Test
    public void creatorCanReplaceAllSharesButCannotMoveExpenseToAnotherGroup() {
        Expense created = create(standardDraft());
        assertTrue(ExpenseIntegrationTest.<Boolean>await(cb ->
                handler.authenticateCreator(created.getId(), TOMMY, cb)).value());
        ExpenseDraft revised = draft(GROUP, SAM, 2001, SplitType.EXACT_AMOUNT, DATE.minusDays(1),
                allocations(SAM, 2000L, TOMMY, 1L));
        Expense updated = ExpenseIntegrationTest.<Expense>await(cb ->
                handler.updateExpense(created.getId(), TOMMY, revised, cb)).value();
        assertEquals(created.getId(), updated.getId());
        assertEquals(created.getCreatedAt().toEpochMilli(), updated.getCreatedAt().toEpochMilli());
        Expense stored = groups.findExpenseById(created.getId());
        // The revised draft asked for an earlier date, but the date is fixed at creation so an
        // edit can never move the expense into a different monthly summary (R17).
        assertEquals(DATE, stored.getExpenseDate());
        assertEquals(Arrays.asList(SAM, TOMMY), participantIds(stored));
        assertFalse(stored.hasShareFor(ALEX));
        assertEquals(2000, stored.owedBy(SAM));
        assertError(SplitSmartException.Code.INVALID_INPUT,
                ExpenseIntegrationTest.<Expense>await(cb -> handler.updateExpense(created.getId(), TOMMY,
                        draft("g-ramen", SAM, 100, SplitType.EQUAL, DATE, allocations(TOMMY, 0L)), cb)));
        assertEquals(GROUP, groups.findExpenseById(created.getId()).getGroupId());
    }

    @Test
    public void failedValidationDuringEditLeavesOriginalDetailsAndShares() {
        Expense created = create(standardDraft());
        assertError(SplitSmartException.Code.INVALID_SPLIT,
                ExpenseIntegrationTest.<Expense>await(cb -> handler.updateExpense(created.getId(), TOMMY,
                        draft(GROUP, SAM, 100, SplitType.EXACT_AMOUNT, DATE, allocations(SAM, 99L)), cb)));
        Expense stored = groups.findExpenseById(created.getId());
        assertEquals(ALEX, stored.getPayerId());
        assertEquals(1000, stored.getTotalCents());
        assertEquals(Arrays.asList(TOMMY, ALEX), participantIds(stored));
    }

    @Test
    public void deleteCascadesSharesAndMissingExpensesReturnExpectedResults() {
        Expense created = create(standardDraft());
        ExpenseIntegrationTest.<Void>await(cb -> handler.deleteExpense(created.getId(), TOMMY, cb)).value();
        assertNull(groups.findExpenseById(created.getId()));
        assertEquals(0, shareCount(created.getId()));
        assertError(SplitSmartException.Code.NOT_FOUND,
                ExpenseIntegrationTest.<Expense>await(cb -> handler.getExpense(created.getId(), cb)));
        assertError(SplitSmartException.Code.NOT_FOUND,
                ExpenseIntegrationTest.<Expense>await(cb -> handler.updateExpense(created.getId(), TOMMY, standardDraft(), cb)));
        assertError(SplitSmartException.Code.NOT_FOUND,
                ExpenseIntegrationTest.<Void>await(cb -> handler.deleteExpense(created.getId(), TOMMY, cb)));
        assertFalse(ExpenseIntegrationTest.<Boolean>await(cb ->
                handler.authenticateCreator(created.getId(), TOMMY, cb)).value());
    }

    @Test
    public void readsAreRestrictedToLoggedInGroupMembers() {
        Expense created = create(standardDraft());
        loginAs("u-priya");
        assertError(SplitSmartException.Code.NOT_A_MEMBER,
                ExpenseIntegrationTest.<Expense>await(cb -> handler.getExpense(created.getId(), cb)));
        assertError(SplitSmartException.Code.NOT_A_MEMBER,
                ExpenseIntegrationTest.<List<Expense>>await(cb -> handler.getExpenses(GROUP, cb)));
        sessions.clear();
        assertError(SplitSmartException.Code.NOT_LOGGED_IN,
                ExpenseIntegrationTest.<List<Expense>>await(cb -> handler.getExpenses(GROUP, cb)));
    }

    @Test
    public void filtersCombineUserAndInclusiveExpenseDatesAndPreserveOrder() {
        Expense old = create(draft(GROUP, ALEX, 1, SplitType.EQUAL, DATE.minusDays(1),
                allocations(SAM, 0L)));
        Expense payerOnly = create(draft(GROUP, ALEX, 1, SplitType.EQUAL, DATE,
                allocations(SAM, 0L)));
        Expense zeroShare = create(draft(GROUP, SAM, 1, SplitType.EXACT_AMOUNT, DATE.plusDays(1),
                allocations(ALEX, 0L, SAM, 1L)));
        create(draft(GROUP, SAM, 1, SplitType.EQUAL, DATE.plusDays(2), allocations(SAM, 0L)));
        create(draft("g-ramen", SAM, 1, SplitType.EQUAL, DATE, allocations(TOMMY, 0L)));
        List<Expense> filtered = ExpenseIntegrationTest.<List<Expense>>await(cb ->
                handler.getExpenses(GROUP, ALEX, DATE, DATE.plusDays(1), cb)).value();
        assertEquals(Arrays.asList(zeroShare, payerOnly), filtered);
        assertEquals(Collections.singletonList(old), ExpenseIntegrationTest.<List<Expense>>await(cb ->
                handler.getExpenses(GROUP, null, null, DATE.minusDays(1), cb)).value());
        assertEquals(4, ExpenseIntegrationTest.<List<Expense>>await(cb ->
                handler.getExpenses(GROUP, null, null, null, cb)).value().size());
        assertTrue(ExpenseIntegrationTest.<List<Expense>>await(cb ->
                handler.getExpenses(GROUP, TOMMY, null, null, cb)).value().isEmpty());
        assertError(SplitSmartException.Code.INVALID_INPUT,
                ExpenseIntegrationTest.<List<Expense>>await(cb -> handler.getExpenses(GROUP, null, DATE.plusDays(1), DATE, cb)));
    }

    @Test
    public void failedShareInsertRollsBackCreateAndReplacement() {
        Expense original = create(standardDraft());
        List<ExpenseShare> invalidShares = Arrays.asList(
                new ExpenseShare("broken", TOMMY, 100),
                new ExpenseShare("broken", "missing-user", 100));
        Expense broken = expense("broken", 200, invalidShares);
        assertThrows(SQLiteConstraintException.class, () -> groups.saveExpense(broken));
        assertNull(groups.findExpenseById("broken"));
        assertEquals(0, shareCount("broken"));
        Expense replacement = expense(original.getId(), 200, Arrays.asList(
                new ExpenseShare(original.getId(), TOMMY, 100),
                new ExpenseShare(original.getId(), "missing-user", 100)));
        assertThrows(SQLiteConstraintException.class, () -> groups.replaceExpense(replacement));
        Expense restored = groups.findExpenseById(original.getId());
        assertEquals(1000, restored.getTotalCents());
        assertEquals(Arrays.asList(TOMMY, ALEX), participantIds(restored));
        assertEquals(500, restored.owedBy(ALEX));
    }

    @Test
    public void existingSettlementEntryPointUsesRealRepositoryAndCurrentRecorder() {
        // Sam fronted $10 split evenly, so Alex owes Sam $5 and has a debt worth settling.
        create(draft(GROUP, SAM, 1000, SplitType.EQUAL, DATE, allocations(ALEX, 0L, SAM, 0L)));
        Settlement settlement = ExpenseIntegrationTest.<Settlement>await(cb ->
                handler.recordSettlement(GROUP, ALEX, SAM, 100, "", cb)).value();
        assertEquals(TOMMY, settlement.getRecorderId());
        assertEquals(1, groups.findSettlementsByGroup(GROUP).size());
        // $1 of the $5 is now paid off, so $4.01 is more than Alex still owes.
        assertError(SplitSmartException.Code.AMOUNT_ABOVE_DEBT,
                ExpenseIntegrationTest.<Settlement>await(cb -> handler.recordSettlement(GROUP, ALEX, SAM, 401, "", cb)));
        assertError(SplitSmartException.Code.INVALID_AMOUNT,
                ExpenseIntegrationTest.<Settlement>await(cb -> handler.recordSettlement(GROUP, ALEX, SAM, 0, "", cb)));
        assertError(SplitSmartException.Code.INVALID_INPUT,
                ExpenseIntegrationTest.<Settlement>await(cb -> handler.recordSettlement(GROUP, ALEX, ALEX, 1, "", cb)));
        assertError(SplitSmartException.Code.NOT_A_MEMBER,
                ExpenseIntegrationTest.<Settlement>await(cb -> handler.recordSettlement(GROUP, ALEX, "u-priya", 1, "", cb)));
    }

    @Test
    public void groupListAndAppServicesUsePersistentExpenses() {
        AppServices.init(context);
        assertTrue(AppServices.expenses() instanceof DefaultExpenseHandler);
        Expense created = ExpenseIntegrationTest.<Expense>await(cb ->
                AppServices.expenses().createExpense(TOMMY, standardDraft(), cb)).value();
        List<Expense> list = ExpenseIntegrationTest.<List<Expense>>await(cb ->
                AppServices.groups().getGroupExpenses(GROUP, cb)).value();
        assertEquals(Collections.singletonList(created), list);
    }

    @Test
    public void percentageFormRetainsOriginalPercentagesAndShowsRoundedShares() {
        AppServices.init(context);
        Expense created = ExpenseIntegrationTest.<Expense>await(cb ->
                AppServices.expenses().createExpense(TOMMY,
                        draft(GROUP, ALEX, 1, SplitType.PERCENTAGE, DATE,
                                allocations(TOMMY, 3333L, ALEX, 6667L)), cb)).value();
        Intent intent = new Intent(context, EditExpenseActivity.class)
                .putExtra(Extras.GROUP_ID, GROUP).putExtra(Extras.EXPENSE_ID, created.getId());
        try (var controller = Robolectric.buildActivity(EditExpenseActivity.class, intent).setup()) {
            EditExpenseActivity activity = controller.get();
            awaitCondition(() -> activity.findViewById(R.id.save_button).getVisibility() == View.VISIBLE
                    && ((TextView) activity.findViewById(R.id.description_input))
                    .getText().toString().equals("Dinner"));
            LinearLayout rows = activity.findViewById(R.id.participants_container);
            int checked = 0;
            for (int i = 0; i < rows.getChildCount(); i++) {
                View row = rows.getChildAt(i);
                CheckBox check = row.findViewById(R.id.participant_check);
                if (!check.isChecked()) continue;
                checked++;
                TextView input = row.findViewById(R.id.participant_value_input);
                TextView preview = row.findViewById(R.id.participant_preview);
                if (check.getText().toString().contains("Tommy")) {
                    assertEquals("33.33", input.getText().toString());
                    assertEquals("$0.00", preview.getText().toString());
                } else {
                    assertEquals("66.67", input.getText().toString());
                    assertEquals("$0.01", preview.getText().toString());
                }
            }
            assertEquals(2, checked);
            activity.findViewById(R.id.save_button).performClick();
            awaitCondition(activity::isFinishing);
        }
        Expense reloaded = groups.findExpenseById(created.getId());
        for (ExpenseShare share : reloaded.getShares()) {
            assertEquals(Long.valueOf(share.getUserId().equals(TOMMY) ? 3333 : 6667),
                    share.getBasisPoints());
        }
        assertEquals(0, reloaded.owedBy(TOMMY));
        assertEquals(1, reloaded.owedBy(ALEX));
    }

    @Test
    public void upgradingVersionOnePreservesExistingAccountsGroupsExpensesAndShares() {
        Expense created = create(standardDraft());
        SQLiteDatabase db = database.getWritableDatabase();
        // Restore precisely the v1 tables, then let SQLiteOpenHelper perform the upgrade. Foreign
        // keys are off while tables are swapped so the drops do not cascade the rows away.
        db.setForeignKeyConstraintsEnabled(false);
        db.execSQL("CREATE TABLE legacy_shares (expense_id TEXT NOT NULL REFERENCES expenses(id) "
                + "ON DELETE CASCADE, user_id TEXT NOT NULL REFERENCES users(id), "
                + "owed_cents INTEGER NOT NULL, position INTEGER NOT NULL, "
                + "PRIMARY KEY (expense_id, user_id))");
        db.execSQL("INSERT INTO legacy_shares SELECT expense_id, user_id, owed_cents, position "
                + "FROM expense_shares");
        db.execSQL("DROP TABLE expense_shares");
        db.execSQL("ALTER TABLE legacy_shares RENAME TO expense_shares");
        db.execSQL("CREATE INDEX idx_shares_expense ON expense_shares(expense_id)");
        // v1 had no note column on either table, so the upgrade has one to add. SQLite here is
        // too old for ALTER TABLE DROP COLUMN, so both tables are rebuilt without it.
        db.execSQL("CREATE TABLE legacy_expenses (id TEXT PRIMARY KEY, "
                + "group_id TEXT NOT NULL REFERENCES split_groups(id) ON DELETE CASCADE, "
                + "creator_id TEXT NOT NULL REFERENCES users(id), "
                + "payer_id TEXT NOT NULL REFERENCES users(id), description TEXT NOT NULL, "
                + "total_cents INTEGER NOT NULL, category TEXT NOT NULL, "
                + "split_type TEXT NOT NULL, expense_date TEXT NOT NULL, "
                + "created_at INTEGER NOT NULL)");
        db.execSQL("INSERT INTO legacy_expenses SELECT id, group_id, creator_id, payer_id, "
                + "description, total_cents, category, split_type, expense_date, created_at "
                + "FROM expenses");
        db.execSQL("DROP TABLE expenses");
        db.execSQL("ALTER TABLE legacy_expenses RENAME TO expenses");
        db.execSQL("CREATE INDEX idx_expenses_group ON expenses(group_id)");

        db.execSQL("CREATE TABLE legacy_settlements (id TEXT PRIMARY KEY, "
                + "group_id TEXT NOT NULL REFERENCES split_groups(id) ON DELETE CASCADE, "
                + "recorder_id TEXT NOT NULL REFERENCES users(id), "
                + "payer_id TEXT NOT NULL REFERENCES users(id), "
                + "receiver_id TEXT NOT NULL REFERENCES users(id), "
                + "amount_cents INTEGER NOT NULL, settled_at INTEGER NOT NULL)");
        db.execSQL("INSERT INTO legacy_settlements SELECT id, group_id, recorder_id, payer_id, "
                + "receiver_id, amount_cents, settled_at FROM settlements");
        db.execSQL("DROP TABLE settlements");
        db.execSQL("ALTER TABLE legacy_settlements RENAME TO settlements");
        db.execSQL("CREATE INDEX idx_settlements_group ON settlements(group_id)");
        db.setVersion(1);
        database.close();

        database = new SplitSmartDatabase(context);
        groups = new SqliteGroupRepository(database);
        users = new SqliteUserRepository(database);
        Expense retained = groups.findExpenseById(created.getId());
        assertEquals(3, database.getReadableDatabase().getVersion());
        assertNotNull(users.findById(TOMMY));
        assertTrue(groups.isMember(GROUP, TOMMY));
        assertEquals(1000, retained.getTotalCents());
        assertEquals(500, retained.owedBy(ALEX));
        assertEquals(Arrays.asList(TOMMY, ALEX), participantIds(retained));
        assertNull(retained.getShares().get(0).getBasisPoints());
        // Rows that predate the note column read back as "" rather than null.
        assertEquals("", retained.getNote());
    }

    private Expense create(ExpenseDraft draft) {
        return ExpenseIntegrationTest.<Expense>await(cb -> handler.createExpense(TOMMY, draft, cb)).value();
    }

    private void loginAs(String id) {
        sessions.setCurrentUser(users.findById(id));
    }

    private int shareCount(String expenseId) {
        try (Cursor c = database.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM expense_shares WHERE expense_id = ?", new String[]{expenseId})) {
            assertTrue(c.moveToFirst());
            return c.getInt(0);
        }
    }

    private static ExpenseDraft standardDraft() {
        return draft(GROUP, ALEX, 1000, SplitType.EQUAL, DATE, allocations(TOMMY, 0L, ALEX, 0L));
    }

    private static ExpenseDraft draft(String group, String payer, long total, SplitType type,
                                       LocalDate date, Map<String, Long> allocations) {
        return new ExpenseDraft(group, payer, " Dinner ", "", total, Category.FOOD, type, date,
                allocations);
    }

    private static Expense expense(String id, long total, List<ExpenseShare> shares) {
        return new Expense(id, GROUP, TOMMY, ALEX, "Changed", "", total, Category.FOOD,
                SplitType.EXACT_AMOUNT, DATE, Instant.now(), shares);
    }

    private static Map<String, Long> allocations(Object... entries) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put((String) entries[i], (Long) entries[i + 1]);
        }
        return result;
    }

    private static List<String> participantIds(Expense expense) {
        List<String> ids = new ArrayList<>();
        for (ExpenseShare share : expense.getShares()) ids.add(share.getUserId());
        return ids;
    }

    private static void assertError(SplitSmartException.Code code, Result<?> result) {
        assertTrue("Expected " + code + " but got " + result.error,
                result.error instanceof SplitSmartException);
        assertEquals(code, ((SplitSmartException) result.error).getCode());
    }

    private static <T> Result<T> await(Consumer<Callback<T>> operation) {
        Result<T> result = new Result<>();
        operation.accept(result);
        awaitCondition(() -> result.calls != 0);
        assertEquals("Expected exactly one callback", 1, result.calls);
        return result;
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertTrue("Timed out waiting for asynchronous work", condition.getAsBoolean());
    }

    private static final class Result<T> implements Callback<T> {
        private int calls;
        private T result;
        private Exception error;

        @Override
        public void onSuccess(T value) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            calls++;
            result = value;
        }

        @Override
        public void onError(Exception failure) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            calls++;
            error = failure;
        }

        T value() {
            if (error != null) throw new AssertionError("Unexpected handler failure", error);
            return result;
        }
    }
}
