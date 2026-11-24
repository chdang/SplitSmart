package edu.usc.splitsmart.ui.reminder;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.logic.ExpenseDraft;
import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.model.Reminder;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.balance.IndividualGroupBalanceActivity;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.group.GroupListActivity;

/**
 * Black-box UI tests for in-app payment reminders: sending one from the "Who owes whom" screen,
 * seeing it in the recipient's inbox with sender, amount, and group, and marking it read or
 * unread.
 *
 * <p>SETUP: same as ExpenseManagementEspressoTest. Relies on the sample accounts (Tommy Trojan
 * and Alex Kim, password "password"). Each test makes its own uniquely named group with both,
 * and inbox rows are found by that group name, since reminders from earlier runs stay in the
 * inbox.
 */
@RunWith(AndroidJUnit4.class)
public class PaymentReminderEspressoTest {

    private static final String TOMMY_EMAIL = "tommy@usc.edu";
    private static final String ALEX_EMAIL = "alex@usc.edu";
    private static final String PASSWORD = "password";
    // Generous: the first test also pays for opening the database and hashing passwords.
    private static final long WAIT_MS = 20000;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<ActivityScenario<?>> scenarios = new ArrayList<>();

    private String groupId;
    private String groupName;
    private String tommyId;
    private String alexId;

    @Before
    public void setUp() throws Exception {
        // Other test classes delete the database file between tests, which leaves the app's
        // open connection pointing at a removed file. Reopen it so this class works in any order.
        instrumentation.runOnMainSync(() -> AppServices.init(context));
        alexId = loginAs(ALEX_EMAIL).getId();
        tommyId = loginAs(TOMMY_EMAIL).getId();
        groupName = "Reminder UI test " + System.nanoTime();
        Group group = call(cb -> AppServices.groups().createGroup(tommyId, groupName,
                "created by UI test", Collections.singletonList(ALEX_EMAIL), cb));
        groupId = group.getId();
    }

    @After
    public void tearDown() {
        for (ActivityScenario<?> scenario : scenarios) {
            scenario.close();
        }
    }

    // =====================================================================================
    // Sending
    // =====================================================================================

    @Test
    public void remindMemberWhoOwes_confirmsAndDeliversToTheirInbox() throws Exception {
        createExpensePaidBy(TOMMY_EMAIL, tommyId);   // Alex owes Tommy $15.00

        launch(IndividualGroupBalanceActivity.class);
        waitForVisible(withText(context.getString(R.string.individual_owes_you, "$15.00")));
        onView(withId(R.id.remind_button)).perform(click());

        onView(withText(context.getString(R.string.reminder_confirm_message, "Alex Kim", "$15.00")))
                .inRoot(isDialog()).check(matches(isDisplayed()));
        onView(withText(R.string.reminder_send)).inRoot(isDialog()).perform(click());
        waitForVisible(withText(context.getString(R.string.reminder_sent, "Alex Kim", "$15.00")));

        loginAs(ALEX_EMAIL);
        Reminder received = newestReminder();
        assertEquals(groupId, received.getGroupId());
        assertEquals(tommyId, received.getSenderId());
        assertEquals(1500, received.getAmountCents());
        assertFalse(received.isRead());
    }

    @Test
    public void noRemindButtonForMembersWhoDontOweYou() throws Exception {
        createExpensePaidBy(ALEX_EMAIL, alexId);   // Tommy owes Alex, not the other way round

        launch(IndividualGroupBalanceActivity.class);
        waitForVisible(withText(context.getString(R.string.individual_you_owe, "$15.00")));
        onView(withId(R.id.settle_button)).check(matches(isDisplayed()));
        onView(withId(R.id.remind_button)).check(matches(not(isDisplayed())));
    }

    @Test
    public void remindingAfterTheyPaidUp_showsFriendlyMessageAndRefreshes() throws Exception {
        createExpensePaidBy(TOMMY_EMAIL, tommyId);

        launch(IndividualGroupBalanceActivity.class);
        waitForVisible(withText(context.getString(R.string.individual_owes_you, "$15.00")));

        // Alex pays Tommy back while Tommy's screen is still showing the old balance.
        loginAs(ALEX_EMAIL);
        this.<Settlement>call(cb -> AppServices.expenses().recordSettlement(groupId, alexId,
                tommyId, 1500, "", cb));
        loginAs(TOMMY_EMAIL);

        onView(withId(R.id.remind_button)).perform(click());
        onView(withText(R.string.reminder_send)).inRoot(isDialog()).perform(click());
        waitForVisible(withText(R.string.error_nothing_owed));
        waitForVisible(withText(R.string.individual_even));
    }

    // =====================================================================================
    // Receiving
    // =====================================================================================

    @Test
    public void inboxShowsSenderAmountAndGroup_andMarksReadAndUnread() throws Exception {
        createExpensePaidBy(TOMMY_EMAIL, tommyId);
        sendReminderAsTommy();
        loginAs(ALEX_EMAIL);
        markEverythingReadExcept(newestReminder().getId());

        // The home screen points Alex at the unread reminder.
        launch(GroupListActivity.class);
        waitForVisible(withText(context.getResources()
                .getQuantityString(R.plurals.reminders_unread_banner, 1, 1)));
        onView(withId(R.id.reminders_banner)).perform(click());

        waitForVisible(groupDetails());
        onView(rowView(R.id.reminder_message)).check(matches(withText(
                context.getString(R.string.reminder_message, "Tommy Trojan", "$15.00"))));
        onView(groupDetails()).check(matches(withText(
                context.getString(R.string.reminder_details, groupName,
                        "" + LocalDate.now()))));
        onView(rowView(R.id.reminder_unread)).check(matches(isDisplayed()));

        onView(rowView(R.id.reminder_toggle_read)).check(matches(withText(R.string.reminder_mark_read)))
                .perform(click());
        waitForVisible(allOf(rowView(R.id.reminder_toggle_read),
                withText(R.string.reminder_mark_unread)));
        onView(rowView(R.id.reminder_unread)).check(matches(not(isDisplayed())));
        assertTrue(newestReminder().isRead());

        onView(rowView(R.id.reminder_toggle_read)).perform(click());
        waitForVisible(allOf(rowView(R.id.reminder_toggle_read),
                withText(R.string.reminder_mark_read)));
        assertFalse(newestReminder().isRead());

        // Read state is stored, so a fresh inbox shows the same thing.
        launch(RemindersActivity.class);
        waitForVisible(allOf(rowView(R.id.reminder_toggle_read),
                withText(R.string.reminder_mark_read)));
        onView(rowView(R.id.reminder_unread)).check(matches(isDisplayed()));
    }

    @Test
    public void tappingReminder_marksItReadAndOpensThatGroupsBalances() throws Exception {
        createExpensePaidBy(TOMMY_EMAIL, tommyId);
        sendReminderAsTommy();
        loginAs(ALEX_EMAIL);

        launch(RemindersActivity.class);
        waitForVisible(rowView(R.id.reminder_message));
        onView(rowView(R.id.reminder_message)).perform(click());

        waitForVisible(withText(R.string.individual_title));
        waitForVisible(withText(context.getString(R.string.individual_you_owe, "$15.00")));
        assertTrue(newestReminder().isRead());
        pressBack();

        waitForVisible(allOf(rowView(R.id.reminder_toggle_read),
                withText(R.string.reminder_mark_unread)));
    }

    // =====================================================================================
    // UI helpers
    // =====================================================================================

    private <A extends Activity> void launch(Class<A> screen) {
        Intent intent = new Intent(context, screen).putExtra(Extras.GROUP_ID, groupId);
        scenarios.add(ActivityScenario.launch(intent));
    }

    /** A view inside this test group's inbox card, told apart from older runs by group name. */
    private Matcher<View> rowView(int id) {
        return allOf(withId(id), hasSibling(groupDetails()));
    }

    /** The line naming this test's group, which every other view in its card sits beside. */
    private Matcher<View> groupDetails() {
        return allOf(withId(R.id.reminder_details), withText(containsString(groupName)));
    }

    private static void waitForVisible(Matcher<View> matcher) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        Throwable last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(matcher).check(matches(
                        withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
                return;
            } catch (NoMatchingViewException | AssertionError e) {
                last = e;
                Thread.sleep(100);
            }
        }
        throw new AssertionError("Timed out waiting for view: " + matcher, last);
    }

    // =====================================================================================
    // TEST FIXTURES (the only part that touches app internals)
    // =====================================================================================

    private interface Starter<T> {
        void start(Callback<T> callback);
    }

    private <T> T call(Starter<T> starter) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> starter.start(new Callback<T>() {
            @Override public void onSuccess(T result) {
                value.set(result);
                latch.countDown();
            }

            @Override public void onError(Exception e) {
                error.set(e);
                latch.countDown();
            }
        }));
        assertTrue("handler call timed out", latch.await(WAIT_MS, TimeUnit.MILLISECONDS));
        if (error.get() != null) {
            throw error.get();
        }
        return value.get();
    }

    private User loginAs(String email) throws Exception {
        return call(cb -> AppServices.session().login(email, PASSWORD, cb));
    }

    /** A $30.00 equal split between Tommy and Alex, paid by {@code payerId}, as {@code email}. */
    private void createExpensePaidBy(String email, String payerId) throws Exception {
        loginAs(email);
        Map<String, Long> allocations = new LinkedHashMap<>();
        allocations.put(tommyId, 0L);
        allocations.put(alexId, 0L);
        ExpenseDraft draft = new ExpenseDraft(groupId, payerId, "Shared dinner", "", 3000L,
                Category.FOOD, SplitType.EQUAL, LocalDate.now(), allocations);
        this.<Expense>call(cb -> AppServices.expenses().createExpense(payerId, draft, cb));
        loginAs(TOMMY_EMAIL);
    }

    private void sendReminderAsTommy() throws Exception {
        loginAs(TOMMY_EMAIL);
        this.<Reminder>call(cb -> AppServices.reminders().sendReminder(groupId, alexId, cb));
    }

    /** The logged-in user's newest reminder, which must be from this test's group. */
    private Reminder newestReminder() throws Exception {
        List<Reminder> inbox = call(cb -> AppServices.reminders().getReceivedReminders(cb));
        Reminder newest = inbox.get(0);
        assertEquals(groupId, newest.getGroupId());
        return newest;
    }

    /** Clears unread reminders left by earlier runs, so the banner count is predictable. */
    private void markEverythingReadExcept(String keepUnreadId) throws Exception {
        List<Reminder> inbox = call(cb -> AppServices.reminders().getReceivedReminders(cb));
        for (Reminder reminder : inbox) {
            if (!reminder.isRead() && !reminder.getId().equals(keepUnreadId)) {
                this.<Void>call(cb -> AppServices.reminders().setRead(reminder.getId(), true, cb));
            }
        }
    }
}
