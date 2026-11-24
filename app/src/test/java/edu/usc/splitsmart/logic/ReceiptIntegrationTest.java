package edu.usc.splitsmart.logic;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Receipt;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.SplitSmartDatabase;
import edu.usc.splitsmart.repository.SqliteGroupRepository;
import edu.usc.splitsmart.repository.SqliteReceiptRepository;
import edu.usc.splitsmart.repository.SqliteUserRepository;

/**
 * White-box tests for receipt management: DefaultReceiptHandler's permission rules on top of
 * the real SQLite receipts table, including persistence and cascading deletes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReceiptIntegrationTest {
    // Sample data: Tommy, Alex, Sam, Jordan are in g-apartment; Priya is not.
    private static final String GROUP = "g-apartment";
    private static final String TOMMY = "u-tommy";
    private static final String ALEX = "u-alex";
    private static final String PRIYA = "u-priya";

    private static final byte[] IMAGE_A = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
    private static final byte[] IMAGE_B = {(byte) 0xFF, (byte) 0xD8, 9, 8, 7, 6};

    private Context context;
    private SplitSmartDatabase database;
    private SqliteGroupRepository groups;
    private SqliteUserRepository users;
    private SessionStore sessions;
    private ExpenseHandler expenses;
    private ReceiptHandler receipts;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("splitsmart.db");
        sessions = new SessionStore(context);
        open();
        loginAs(TOMMY);
    }

    @After
    public void tearDown() {
        sessions.clear();
        database.close();
        context.deleteDatabase("splitsmart.db");
    }

    // ------------------------------------------------------------------ attach and view

    @Test
    public void creatorAttachesReceipts_membersListAndViewThem_inOrder() {
        Expense expense = createExpense();
        List<Receipt> saved = ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.addReceipts(expense.getId(), TOMMY,
                Arrays.asList(IMAGE_A, IMAGE_B), cb));
        assertEquals(2, saved.size());
        for (Receipt receipt : saved) {
            assertEquals(expense.getId(), receipt.getExpenseId());
            assertEquals(TOMMY, receipt.getUploaderId());
        }

        // Another group member (not the creator) can see and open them.
        loginAs(ALEX);
        List<Receipt> listed = ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.getReceipts(expense.getId(), cb));
        assertEquals(ids(saved), ids(listed));
        assertArrayEquals(IMAGE_A, ReceiptIntegrationTest.<byte[]>ok(cb -> receipts.getReceiptImage(saved.get(0).getId(), cb)));
        assertArrayEquals(IMAGE_B, ReceiptIntegrationTest.<byte[]>ok(cb -> receipts.getReceiptImage(saved.get(1).getId(), cb)));
    }

    @Test
    public void receiptsPersistAcrossDatabaseReopen() {
        Expense expense = createExpense();
        Receipt saved = ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.addReceipts(expense.getId(), TOMMY,
                Collections.singletonList(IMAGE_A), cb)).get(0);

        database.close();
        open();

        List<Receipt> listed = ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.getReceipts(expense.getId(), cb));
        assertEquals(Collections.singletonList(saved.getId()), ids(listed));
        assertArrayEquals(IMAGE_A, ReceiptIntegrationTest.<byte[]>ok(cb -> receipts.getReceiptImage(saved.getId(), cb)));
    }

    @Test
    public void expenseWithNoReceipts_listsEmpty() {
        Expense expense = createExpense();
        assertTrue(ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.getReceipts(expense.getId(), cb)).isEmpty());
    }

    // ------------------------------------------------------------------ delete

    @Test
    public void creatorDeletesReceipt_onlyThatOneIsRemoved() {
        Expense expense = createExpense();
        List<Receipt> saved = ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.addReceipts(expense.getId(), TOMMY,
                Arrays.asList(IMAGE_A, IMAGE_B), cb));

        ReceiptIntegrationTest.<Void>ok(cb -> receipts.deleteReceipt(saved.get(0).getId(), TOMMY, cb));

        List<Receipt> left = ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.getReceipts(expense.getId(), cb));
        assertEquals(Collections.singletonList(saved.get(1).getId()), ids(left));
        assertError(SplitSmartException.Code.NOT_FOUND,
                ReceiptIntegrationTest.<byte[]>await(cb -> receipts.getReceiptImage(saved.get(0).getId(), cb)));
        assertError(SplitSmartException.Code.NOT_FOUND,
                ReceiptIntegrationTest.<Void>await(cb -> receipts.deleteReceipt(saved.get(0).getId(), TOMMY, cb)));
    }

    @Test
    public void deletingExpense_removesItsReceipts() {
        Expense expense = createExpense();
        ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.addReceipts(expense.getId(), TOMMY, Arrays.asList(IMAGE_A, IMAGE_B), cb));
        assertEquals(2, receiptRows());

        ReceiptIntegrationTest.<Void>ok(cb -> expenses.deleteExpense(expense.getId(), TOMMY, cb));

        assertEquals(0, receiptRows());
        assertError(SplitSmartException.Code.NOT_FOUND,
                ReceiptIntegrationTest.<List<Receipt>>await(cb -> receipts.getReceipts(expense.getId(), cb)));
    }

    @Test
    public void deletingGroup_removesReceiptsOfItsExpenses() {
        Expense expense = createExpense();
        ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.addReceipts(expense.getId(), TOMMY, Collections.singletonList(IMAGE_A), cb));
        groups.deleteGroup(GROUP);
        assertEquals(0, receiptRows());
    }

    // ------------------------------------------------------------------ permissions

    @Test
    public void nonCreatorMember_cannotAttachOrDelete() {
        Expense expense = createExpense();
        Receipt saved = ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.addReceipts(expense.getId(), TOMMY,
                Collections.singletonList(IMAGE_A), cb)).get(0);
        loginAs(ALEX);

        assertError(SplitSmartException.Code.NOT_CREATOR, ReceiptIntegrationTest.<List<Receipt>>await(cb -> receipts.addReceipts(
                expense.getId(), ALEX, Collections.singletonList(IMAGE_B), cb)));
        assertError(SplitSmartException.Code.NOT_CREATOR,
                ReceiptIntegrationTest.<Void>await(cb -> receipts.deleteReceipt(saved.getId(), ALEX, cb)));
        // Claiming to be the creator doesn't help while logged in as someone else.
        assertError(SplitSmartException.Code.NOT_CREATOR,
                ReceiptIntegrationTest.<Void>await(cb -> receipts.deleteReceipt(saved.getId(), TOMMY, cb)));
        assertEquals(1, receiptRows());
    }

    @Test
    public void nonMember_cannotViewReceipts() {
        Expense expense = createExpense();
        Receipt saved = ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.addReceipts(expense.getId(), TOMMY,
                Collections.singletonList(IMAGE_A), cb)).get(0);
        loginAs(PRIYA);

        assertError(SplitSmartException.Code.NOT_A_MEMBER,
                ReceiptIntegrationTest.<List<Receipt>>await(cb -> receipts.getReceipts(expense.getId(), cb)));
        assertError(SplitSmartException.Code.NOT_A_MEMBER,
                ReceiptIntegrationTest.<byte[]>await(cb -> receipts.getReceiptImage(saved.getId(), cb)));
    }

    @Test
    public void loggedOut_everyCallFails() {
        Expense expense = createExpense();
        Receipt saved = ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.addReceipts(expense.getId(), TOMMY,
                Collections.singletonList(IMAGE_A), cb)).get(0);
        sessions.clear();

        assertError(SplitSmartException.Code.NOT_LOGGED_IN, ReceiptIntegrationTest.<List<Receipt>>await(cb -> receipts.addReceipts(
                expense.getId(), TOMMY, Collections.singletonList(IMAGE_B), cb)));
        assertError(SplitSmartException.Code.NOT_LOGGED_IN,
                ReceiptIntegrationTest.<List<Receipt>>await(cb -> receipts.getReceipts(expense.getId(), cb)));
        assertError(SplitSmartException.Code.NOT_LOGGED_IN,
                ReceiptIntegrationTest.<byte[]>await(cb -> receipts.getReceiptImage(saved.getId(), cb)));
        assertError(SplitSmartException.Code.NOT_LOGGED_IN,
                ReceiptIntegrationTest.<Void>await(cb -> receipts.deleteReceipt(saved.getId(), TOMMY, cb)));
    }

    // ------------------------------------------------------------------ validation

    @Test
    public void invalidImages_areRejectedAndNothingIsSaved() {
        Expense expense = createExpense();
        byte[] tooLarge = new byte[ReceiptHandler.MAX_IMAGE_BYTES + 1];
        List<List<byte[]>> invalid = Arrays.asList(null, new ArrayList<>(),
                Collections.singletonList(new byte[0]),
                Collections.singletonList(tooLarge),
                // One bad image spoils the batch, so the good one isn't saved either.
                Arrays.asList(IMAGE_A, null));
        for (List<byte[]> images : invalid) {
            assertError(SplitSmartException.Code.INVALID_RECEIPT,
                    ReceiptIntegrationTest.<List<Receipt>>await(cb -> receipts.addReceipts(expense.getId(), TOMMY, images, cb)));
        }
        assertEquals(0, receiptRows());

        byte[] largest = new byte[ReceiptHandler.MAX_IMAGE_BYTES];
        largest[0] = 1;
        ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.addReceipts(expense.getId(), TOMMY, Collections.singletonList(largest), cb));
        assertEquals(1, receiptRows());
    }

    @Test
    public void unknownExpenseOrReceipt_isNotFound() {
        assertError(SplitSmartException.Code.NOT_FOUND, ReceiptIntegrationTest.<List<Receipt>>await(cb -> receipts.addReceipts(
                "missing", TOMMY, Collections.singletonList(IMAGE_A), cb)));
        assertError(SplitSmartException.Code.NOT_FOUND,
                ReceiptIntegrationTest.<List<Receipt>>await(cb -> receipts.getReceipts("missing", cb)));
        assertError(SplitSmartException.Code.NOT_FOUND,
                ReceiptIntegrationTest.<byte[]>await(cb -> receipts.getReceiptImage("missing", cb)));
        assertError(SplitSmartException.Code.NOT_FOUND,
                ReceiptIntegrationTest.<Void>await(cb -> receipts.deleteReceipt("missing", TOMMY, cb)));
    }

    // ------------------------------------------------------------------ schema upgrade

    @Test
    public void upgradeFromVersion3_addsReceiptsTableWithoutLosingExpenses() {
        Expense expense = createExpense();
        SQLiteDatabase db = database.getWritableDatabase();
        // Version 3 had neither receipts nor reminders (version 5).
        db.execSQL("DROP TABLE " + SplitSmartDatabase.RECEIPTS);
        db.execSQL("DROP TABLE " + SplitSmartDatabase.REMINDERS);

        database.onUpgrade(db, 3, 5);

        assertNotNull(groups.findExpenseById(expense.getId()));
        ReceiptIntegrationTest.<List<Receipt>>ok(cb -> receipts.addReceipts(expense.getId(), TOMMY, Collections.singletonList(IMAGE_A), cb));
        assertEquals(1, receiptRows());
    }

    // ------------------------------------------------------------------ helpers

    private void open() {
        database = new SplitSmartDatabase(context);
        groups = new SqliteGroupRepository(database);
        users = new SqliteUserRepository(database);
        DefaultSessionHandler session = new DefaultSessionHandler(users, sessions);
        expenses = new DefaultExpenseHandler(groups, session);
        receipts = new DefaultReceiptHandler(groups, new SqliteReceiptRepository(database), session);
    }

    private void loginAs(String id) {
        sessions.setCurrentUser(users.findById(id));
    }

    private Expense createExpense() {
        Map<String, Long> allocations = new LinkedHashMap<>();
        allocations.put(TOMMY, 0L);
        allocations.put(ALEX, 0L);
        ExpenseDraft draft = new ExpenseDraft(GROUP, TOMMY, "Groceries", "", 2000,
                Category.FOOD, SplitType.EQUAL, LocalDate.of(2026, 11, 1), allocations);
        return ReceiptIntegrationTest.<Expense>ok(cb -> expenses.createExpense(TOMMY, draft, cb));
    }

    private int receiptRows() {
        try (Cursor c = database.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + SplitSmartDatabase.RECEIPTS, null)) {
            c.moveToFirst();
            return c.getInt(0);
        }
    }

    private static List<String> ids(List<Receipt> list) {
        List<String> ids = new ArrayList<>();
        for (Receipt receipt : list) ids.add(receipt.getId());
        return ids;
    }

    private static void assertError(SplitSmartException.Code code, Result<?> result) {
        assertTrue("Expected " + code + " but got " + result.error,
                result.error instanceof SplitSmartException);
        assertEquals(code, ((SplitSmartException) result.error).getCode());
    }

    private static <T> T ok(Consumer<Callback<T>> operation) {
        return await(operation).value();
    }

    private static <T> Result<T> await(Consumer<Callback<T>> operation) {
        Result<T> result = new Result<>();
        operation.accept(result);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        BooleanSupplier done = () -> result.calls != 0;
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertEquals("Expected exactly one callback", 1, result.calls);
        return result;
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
