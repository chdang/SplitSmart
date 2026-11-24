package edu.usc.splitsmart.logic;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
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
import edu.usc.splitsmart.model.Reminder;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.SplitSmartDatabase;
import edu.usc.splitsmart.repository.SqliteGroupRepository;
import edu.usc.splitsmart.repository.SqliteReminderRepository;
import edu.usc.splitsmart.repository.SqliteUserRepository;

/**
 * White-box tests for in-app payment reminders: DefaultReminderHandler's rules for who may
 * remind whom, on top of the real SQLite reminders table, plus read state and persistence.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReminderIntegrationTest {
    // Sample data: Tommy, Alex, Sam, Jordan are in g-apartment; Priya is not.
    private static final String GROUP = "g-apartment";
    private static final String TOMMY = "u-tommy";
    private static final String ALEX = "u-alex";
    private static final String SAM = "u-sam";
    private static final String PRIYA = "u-priya";

    private Context context;
    private SplitSmartDatabase database;
    private SqliteGroupRepository groups;
    private SqliteUserRepository users;
    private SessionStore sessions;
    private ExpenseHandler expenses;
    private ReminderHandler reminders;

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

    // ------------------------------------------------------------------ sending

    @Test
    public void remindingSomeoneWhoOwes_savesUnreadReminderForWhatTheyOwe() {
        tommyPaysSplitWithAlex(2000); // Alex owes Tommy $10.00

        Reminder sent = ReminderIntegrationTest.<Reminder>ok(cb -> reminders.sendReminder(GROUP, ALEX, cb));

        assertEquals(TOMMY, sent.getSenderId());
        assertEquals("Tommy Trojan", sent.getSenderName());
        assertEquals(ALEX, sent.getRecipientId());
        assertEquals(GROUP, sent.getGroupId());
        assertEquals("Apartment 4B", sent.getGroupName());
        assertEquals(1000, sent.getAmountCents());
        assertFalse(sent.isRead());
    }

    @Test
    public void amountIsNetOfSettlements_andStaysAsSentAfterLaterPayments() {
        tommyPaysSplitWithAlex(2000); // Alex owes $10.00
        loginAs(ALEX);
        ReminderIntegrationTest.<Settlement>ok(cb -> expenses.recordSettlement(GROUP, ALEX, TOMMY, 400, "", cb));
        loginAs(TOMMY);

        Reminder sent = ReminderIntegrationTest.<Reminder>ok(cb -> reminders.sendReminder(GROUP, ALEX, cb));
        assertEquals(600, sent.getAmountCents());

        // Paying the rest doesn't rewrite the reminder Alex already received.
        loginAs(ALEX);
        ReminderIntegrationTest.<Settlement>ok(cb -> expenses.recordSettlement(GROUP, ALEX, TOMMY, 600, "", cb));
        assertEquals(600, ReminderIntegrationTest.<List<Reminder>>ok(reminders::getReceivedReminders).get(0).getAmountCents());
    }

    @Test
    public void remindingSomeoneWhoOwesNothing_failsWithNothingOwed() {
        // Nobody owes anybody yet.
        assertError(SplitSmartException.Code.NOTHING_OWED,
                ReminderIntegrationTest.<Reminder>await(cb -> reminders.sendReminder(GROUP, ALEX, cb)));

        // Tommy owes Alex, not the other way round.
        tommyPaysSplitWithAlex(2000);
        loginAs(ALEX);
        assertError(SplitSmartException.Code.NOTHING_OWED,
                ReminderIntegrationTest.<Reminder>await(cb -> reminders.sendReminder(GROUP, TOMMY, cb)));

        // Fully settled up.
        ReminderIntegrationTest.<Settlement>ok(cb -> expenses.recordSettlement(GROUP, ALEX, TOMMY, 1000, "", cb));
        loginAs(TOMMY);
        assertError(SplitSmartException.Code.NOTHING_OWED,
                ReminderIntegrationTest.<Reminder>await(cb -> reminders.sendReminder(GROUP, ALEX, cb)));
        assertEquals(0, reminderRows());
    }

    @Test
    public void invalidRecipientsAndSenders_areRejected() {
        tommyPaysSplitWithAlex(2000);

        assertError(SplitSmartException.Code.INVALID_INPUT,
                ReminderIntegrationTest.<Reminder>await(cb -> reminders.sendReminder(GROUP, TOMMY, cb)));
        assertError(SplitSmartException.Code.INVALID_INPUT,
                ReminderIntegrationTest.<Reminder>await(cb -> reminders.sendReminder(GROUP, PRIYA, cb)));
        assertError(SplitSmartException.Code.INVALID_INPUT,
                ReminderIntegrationTest.<Reminder>await(cb -> reminders.sendReminder(GROUP, null, cb)));
        assertError(SplitSmartException.Code.NOT_FOUND,
                ReminderIntegrationTest.<Reminder>await(cb -> reminders.sendReminder("missing", ALEX, cb)));

        loginAs(PRIYA);
        assertError(SplitSmartException.Code.NOT_A_MEMBER,
                ReminderIntegrationTest.<Reminder>await(cb -> reminders.sendReminder(GROUP, ALEX, cb)));

        sessions.clear();
        assertError(SplitSmartException.Code.NOT_LOGGED_IN,
                ReminderIntegrationTest.<Reminder>await(cb -> reminders.sendReminder(GROUP, ALEX, cb)));
        assertError(SplitSmartException.Code.NOT_LOGGED_IN,
                ReminderIntegrationTest.<List<Reminder>>await(reminders::getReceivedReminders));
        assertEquals(0, reminderRows());
    }

    // ------------------------------------------------------------------ inbox

    @Test
    public void inboxShowsOnlyTheUsersReminders_newestFirst() {
        tommyPaysSplitWithAlexAndSam(3000); // Alex and Sam each owe Tommy $10.00
        Reminder first = ReminderIntegrationTest.<Reminder>ok(cb -> reminders.sendReminder(GROUP, ALEX, cb));
        ReminderIntegrationTest.<Reminder>ok(cb -> reminders.sendReminder(GROUP, SAM, cb));
        Reminder second = ReminderIntegrationTest.<Reminder>ok(cb -> reminders.sendReminder(GROUP, ALEX, cb));

        loginAs(ALEX);
        List<Reminder> inbox = ReminderIntegrationTest.<List<Reminder>>ok(reminders::getReceivedReminders);
        assertEquals(Arrays.asList(second.getId(), first.getId()), ids(inbox));
        assertEquals(Integer.valueOf(2), ReminderIntegrationTest.<Integer>ok(reminders::getUnreadCount));

        loginAs(TOMMY);
        assertTrue(ReminderIntegrationTest.<List<Reminder>>ok(reminders::getReceivedReminders).isEmpty());
        assertEquals(Integer.valueOf(0), ReminderIntegrationTest.<Integer>ok(reminders::getUnreadCount));
    }

    @Test
    public void recipientMarksReadAndUnread_andCountFollows() {
        tommyPaysSplitWithAlex(2000);
        Reminder sent = ReminderIntegrationTest.<Reminder>ok(cb -> reminders.sendReminder(GROUP, ALEX, cb));
        loginAs(ALEX);

        ReminderIntegrationTest.<Void>ok(cb -> reminders.setRead(sent.getId(), true, cb));
        assertTrue(ReminderIntegrationTest.<List<Reminder>>ok(reminders::getReceivedReminders).get(0).isRead());
        assertEquals(Integer.valueOf(0), ReminderIntegrationTest.<Integer>ok(reminders::getUnreadCount));

        ReminderIntegrationTest.<Void>ok(cb -> reminders.setRead(sent.getId(), false, cb));
        assertFalse(ReminderIntegrationTest.<List<Reminder>>ok(reminders::getReceivedReminders).get(0).isRead());
        assertEquals(Integer.valueOf(1), ReminderIntegrationTest.<Integer>ok(reminders::getUnreadCount));
    }

    @Test
    public void onlyTheRecipientCanChangeReadState() {
        tommyPaysSplitWithAlex(2000);
        Reminder sent = ReminderIntegrationTest.<Reminder>ok(cb -> reminders.sendReminder(GROUP, ALEX, cb));

        // Neither the sender nor a bystander can, and it looks the same as a missing reminder.
        assertError(SplitSmartException.Code.NOT_FOUND,
                ReminderIntegrationTest.<Void>await(cb -> reminders.setRead(sent.getId(), true, cb)));
        loginAs(SAM);
        assertError(SplitSmartException.Code.NOT_FOUND,
                ReminderIntegrationTest.<Void>await(cb -> reminders.setRead(sent.getId(), true, cb)));
        assertError(SplitSmartException.Code.NOT_FOUND,
                ReminderIntegrationTest.<Void>await(cb -> reminders.setRead("missing", true, cb)));

        loginAs(ALEX);
        assertFalse(ReminderIntegrationTest.<List<Reminder>>ok(reminders::getReceivedReminders).get(0).isRead());
    }

    // ------------------------------------------------------------------ persistence

    @Test
    public void remindersAndReadStatePersistAcrossDatabaseReopen() {
        tommyPaysSplitWithAlex(2000);
        Reminder sent = ReminderIntegrationTest.<Reminder>ok(cb -> reminders.sendReminder(GROUP, ALEX, cb));
        loginAs(ALEX);
        ReminderIntegrationTest.<Void>ok(cb -> reminders.setRead(sent.getId(), true, cb));

        database.close();
        open();

        List<Reminder> inbox = ReminderIntegrationTest.<List<Reminder>>ok(reminders::getReceivedReminders);
        assertEquals(Collections.singletonList(sent.getId()), ids(inbox));
        assertTrue(inbox.get(0).isRead());
        assertEquals(1000, inbox.get(0).getAmountCents());
    }

    @Test
    public void deletingTheGroup_deletesItsReminders() {
        tommyPaysSplitWithAlex(2000);
        ReminderIntegrationTest.<Reminder>ok(cb -> reminders.sendReminder(GROUP, ALEX, cb));

        groups.deleteGroup(GROUP);

        assertEquals(0, reminderRows());
        loginAs(ALEX);
        assertTrue(ReminderIntegrationTest.<List<Reminder>>ok(reminders::getReceivedReminders).isEmpty());
    }

    @Test
    public void upgradeFromVersion4_addsRemindersTable() {
        SQLiteDatabase db = database.getWritableDatabase();
        db.execSQL("DROP TABLE " + SplitSmartDatabase.REMINDERS);

        database.onUpgrade(db, 4, 5);

        tommyPaysSplitWithAlex(2000);
        ReminderIntegrationTest.<Reminder>ok(cb -> reminders.sendReminder(GROUP, ALEX, cb));
        assertEquals(1, reminderRows());
    }

    // ------------------------------------------------------------------ helpers

    private void open() {
        database = new SplitSmartDatabase(context);
        groups = new SqliteGroupRepository(database);
        users = new SqliteUserRepository(database);
        DefaultSessionHandler session = new DefaultSessionHandler(users, sessions);
        expenses = new DefaultExpenseHandler(groups, session);
        reminders = new DefaultReminderHandler(groups, new SqliteReminderRepository(database), session);
    }

    private void loginAs(String id) {
        sessions.setCurrentUser(users.findById(id));
    }

    private void tommyPaysSplitWithAlex(long totalCents) {
        createEqualExpense(totalCents, TOMMY, ALEX);
    }

    private void tommyPaysSplitWithAlexAndSam(long totalCents) {
        createEqualExpense(totalCents, TOMMY, ALEX, SAM);
    }

    /** Tommy pays and splits equally among the participants, logged in as Tommy. */
    private void createEqualExpense(long totalCents, String... participants) {
        String previous = sessions.getCurrentUser() == null ? null : sessions.getCurrentUser().getId();
        loginAs(TOMMY);
        Map<String, Long> allocations = new LinkedHashMap<>();
        for (String participant : participants) allocations.put(participant, 0L);
        ExpenseDraft draft = new ExpenseDraft(GROUP, TOMMY, "Groceries", "", totalCents,
                Category.FOOD, SplitType.EQUAL, LocalDate.of(2026, 11, 1), allocations);
        ReminderIntegrationTest.<Expense>ok(cb -> expenses.createExpense(TOMMY, draft, cb));
        if (previous != null) loginAs(previous);
    }

    private int reminderRows() {
        return (int) android.database.DatabaseUtils.queryNumEntries(
                database.getReadableDatabase(), SplitSmartDatabase.REMINDERS);
    }

    private static List<String> ids(List<Reminder> list) {
        List<String> ids = new ArrayList<>();
        for (Reminder reminder : list) ids.add(reminder.getId());
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
        while (result.calls == 0 && System.nanoTime() < deadline) {
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
