
package edu.usc.splitsmart;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.NoActivityResumedException;
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
import java.util.Arrays;
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
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.group.GroupDetailActivity;
import edu.usc.splitsmart.ui.expense.EditExpenseActivity;
import edu.usc.splitsmart.ui.expense.NewExpenseActivity;


/**
 * Black-box UI tests for expense management (R6, R7).
 *
 * Every action and every assertion goes through the screens (typing, tapping, reading what is
 * displayed). The only non-UI code is the "TEST FIXTURES" section at the bottom, which creates a
 * group with two members (and one expense made by the other member) before a test starts.
 *
 * R8 (view all expenses in a group) is not here yet: it needs the group's expense list screen.
 * R9-R11 (split types) are covered by another role's tests.
 *
 * SETUP: relies on the sample accounts SampleData inserts into a new database (Tommy Trojan and
 * Alex Kim, password "password"). Each test creates its own two-member group with Tommy as owner
 * and logged-in user.
 * If the sample accounts are missing, clear the app's data (or reinstall) and run again.
 * Run on an emulator or device with window/transition/animator animation scales set to off.
 */
@RunWith(AndroidJUnit4.class)
public class ExpenseManagementEspressoTest {

    // Sample accounts from SampleData.
    private static final String TOMMY_EMAIL = "tommy@usc.edu";
    private static final String TOMMY_PASSWORD = "password";
    private static final String ALEX_EMAIL = "alex@usc.edu";
    private static final String ALEX_PASSWORD = "password";

    private static final String GROUP_DESCRIPTION = "created by UI test";

    private static final long WAIT_MS = 8000;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<ActivityScenario<?>> scenarios = new ArrayList<>();

    private String groupId;
    private String tommyId;
    private String alexId;
    private String alexName;

    @Before
    public void setUp() throws Exception {
        User alex = loginAs(ALEX_EMAIL, ALEX_PASSWORD);
        alexId = alex.getId();
        alexName = alex.getName();
        User tommy = loginAs(TOMMY_EMAIL, TOMMY_PASSWORD);   // Tommy is the user under test.
        tommyId = tommy.getId();
        groupId = createGroup(tommyId, "Expense UI test " + System.nanoTime(),
                Arrays.asList(ALEX_EMAIL));                   // Tommy (owner) first, Alex second.
    }

    @After
    public void tearDown() {
        for (ActivityScenario<?> scenario : scenarios) {
            scenario.close();
        }
    }

    // =====================================================================================
    // R6: create an expense in a group for a payer
    // =====================================================================================

    @Test
    public void r6_memberCreatesExpenseForPayer_expenseAppearsInGroupList() throws Exception {
        String description = unique("Dinner");

        // Start on the group page, as a user would, and wait for it to finish loading.
        launchGroupDetail();
        waitForVisible(allOf(withId(R.id.group_description), withText(GROUP_DESCRIPTION)));

        // Add expense -> fill in the form.
        onView(withId(R.id.add_expense_button)).perform(click());
        waitForVisible(allOf(withId(R.id.save_button), isEnabled()));
        onView(withId(R.id.description_input)).perform(typeText(description), closeSoftKeyboard());
        onView(withId(R.id.amount_input)).perform(typeText("12.00"), closeSoftKeyboard());

        // Pick Alex (not the logged-in user) as the payer.
        onView(withId(R.id.payer_input)).perform(scrollTo(), click());
        onView(withText(containsString(alexName))).inRoot(isPlatformPopup()).perform(click());
        onView(withId(R.id.payer_input)).check(matches(withText(containsString(alexName))));

        onView(withId(R.id.save_button)).perform(scrollTo(), click());

        // Back on the group page, the new expense is in the list (it reloads when we return).
        // This is a brand-new group, so it is the only row.
        waitForVisible(allOf(withId(R.id.expense_description), withText(description)));
        onView(withId(R.id.expense_total)).check(matches(withText(containsString("12.00"))));
        onView(withId(R.id.expense_meta)).check(matches(withText(containsString(alexName))));
        // Equal split of $12.00 between two people: Tommy, who didn't pay, owes $6.00.
        onView(withId(R.id.expense_your_share)).check(matches(withText(containsString("6.00"))));

        // Open it from the list and check what was saved.
        onView(withText(description)).perform(click());
        waitForVisible(allOf(withId(R.id.description_input), withText(description)));
        onView(withId(R.id.payer_input)).check(matches(withText(containsString(alexName))));
        onView(withId(R.id.amount_input)).check(matches(withText(containsString("12"))));
    }

    // =====================================================================================
    // R7: only the creator can edit or delete an expense
    // =====================================================================================

    @Test
    public void r7a_nonCreatorSeesExpenseReadOnly() throws Exception {
        // Alex creates an expense; Tommy (a different member) opens it.
        String expenseId = createExpenseAs(ALEX_EMAIL, ALEX_PASSWORD, alexId,
                unique("Alex's groceries"));

        launchEditExpense(expenseId);
        waitForVisible(allOf(withId(R.id.read_only_banner), isDisplayed()));
        onView(withId(R.id.read_only_banner)).check(matches(withText(containsString(alexName))));
        onView(withId(R.id.save_button)).check(matches(
                withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
        onView(withId(R.id.delete_button)).check(matches(
                withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
    }

    @Test
    public void r7b_creatorSeesEditAndDeleteControls() throws Exception {
        String description = unique("My lunch");
        String expenseId = createExpenseAs(TOMMY_EMAIL, TOMMY_PASSWORD, tommyId, description);

        launchEditExpense(expenseId);
        waitForVisible(allOf(withId(R.id.description_input), withText(description)));
        waitForVisible(withId(R.id.delete_button));
        onView(withId(R.id.save_button)).check(matches(
                withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        onView(withId(R.id.read_only_banner)).check(matches(
                withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
    }

    @Test
    public void r7c_creatorCanEditOwnExpense() throws Exception {
        String description = unique("My lunch");
        String expenseId = createExpenseAs(TOMMY_EMAIL, TOMMY_PASSWORD, tommyId, description);

        ActivityScenario<EditExpenseActivity> edit = launchEditExpense(expenseId);
        waitForVisible(allOf(withId(R.id.description_input), withText(description)));
        waitForVisible(withId(R.id.delete_button));   // shown once the creator check finishes

        String newDescription = unique("My updated lunch");
        onView(withId(R.id.description_input)).perform(replaceText(newDescription));
        onView(withId(R.id.save_button)).perform(scrollTo(), click());
        waitUntilClosed(edit);

        // Reopen it: the change was saved.
        launchEditExpense(expenseId);
        waitForVisible(allOf(withId(R.id.description_input), withText(newDescription)));
    }

    @Test
    public void r7d_creatorCanDeleteOwnExpense_expenseDisappearsFromGroupList() throws Exception {
        String description = unique("My lunch");
        createExpenseAs(TOMMY_EMAIL, TOMMY_PASSWORD, tommyId, description);

        // Find it in the group's expense list and open it.
        launchGroupDetail();
        waitForVisible(allOf(withId(R.id.expense_description), withText(description)));
        onView(withText(description)).perform(click());

        // Delete it and confirm in the dialog.
        waitForVisible(withId(R.id.delete_button));
        onView(withId(R.id.delete_button)).perform(scrollTo(), click());
        onView(withText(R.string.delete)).inRoot(isDialog()).perform(click());

        // Back on the group page the list reloads, and the expense is gone.
        waitUntilGone(withText(description));
        waitForVisible(withId(R.id.empty_view));
    }

    // =====================================================================================
    // UI helpers
    // =====================================================================================

    private ActivityScenario<GroupDetailActivity> launchGroupDetail() {
        Intent intent = new Intent(context, GroupDetailActivity.class)
                .putExtra(Extras.GROUP_ID, groupId);
        ActivityScenario<GroupDetailActivity> scenario = ActivityScenario.launch(intent);
        scenarios.add(scenario);
        return scenario;
    }

    private ActivityScenario<NewExpenseActivity> launchNewExpense() {
        Intent intent = new Intent(context, NewExpenseActivity.class)
                .putExtra(Extras.GROUP_ID, groupId);
        ActivityScenario<NewExpenseActivity> scenario = ActivityScenario.launch(intent);
        scenarios.add(scenario);
        return scenario;
    }

    private ActivityScenario<EditExpenseActivity> launchEditExpense(String expenseId) {
        Intent intent = new Intent(context, EditExpenseActivity.class)
                .putExtra(Extras.GROUP_ID, groupId)
                .putExtra(Extras.EXPENSE_ID, expenseId);
        ActivityScenario<EditExpenseActivity> scenario = ActivityScenario.launch(intent);
        scenarios.add(scenario);
        return scenario;
    }

    /**
     * The handlers do their work on a background thread, which Espresso doesn't know about, so
     * tests poll instead. (An IdlingResource would be tidier but needs a hook in Async.)
     */
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

    /** Polls until no view matches. (The list reloads in the background after a delete.) */
    private static void waitUntilGone(Matcher<View> matcher) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        Throwable last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(matcher).check(doesNotExist());
                return;
            } catch (AssertionError | NoActivityResumedException e) {
                last = e;   // still there, or a screen is mid-transition
                Thread.sleep(100);
            }
        }
        throw new AssertionError("View never went away: " + matcher, last);
    }

    private static void waitUntilClosed(ActivityScenario<?> scenario) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (scenario.getState() == Lifecycle.State.DESTROYED) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Screen did not close after saving/deleting");
    }

    private static String unique(String base) {
        return base + " " + System.nanoTime();
    }

    // =====================================================================================
    // TEST FIXTURES (the only part that touches app internals)
    // =====================================================================================

    private interface Starter<T> {
        void start(Callback<T> callback);
    }

    /** Runs a handler call on the main thread and blocks the test thread until it finishes. */
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

    /** Logs in through the real session handler, the same one the login screen uses. */
    private User loginAs(String email, String password) throws Exception {
        return call(cb -> AppServices.session().login(email, password, cb));
    }

    /** The owner is added automatically; {@code otherMemberEmails} are added after them. */
    private String createGroup(String ownerId, String name, List<String> otherMemberEmails)
            throws Exception {
        Group group = call(cb -> AppServices.groups().createGroup(
                ownerId, name, GROUP_DESCRIPTION, otherMemberEmails, cb));
        return group.getId();
    }

    /** Logs in as {@code email}, creates an equal-split expense, then logs back in as Tommy. */
    private String createExpenseAs(String email, String password, String userId,
                                   String description) throws Exception {
        loginAs(email, password);
        Map<String, Long> allocations = new LinkedHashMap<>();
        allocations.put(tommyId, 0L);
        allocations.put(alexId, 0L);
        ExpenseDraft draft = new ExpenseDraft(groupId, userId, description, 1000L, Category.FOOD,
                SplitType.EQUAL, LocalDate.now(), allocations);
        Expense expense = call(cb -> AppServices.expenses().createExpense(userId, draft, cb));
        loginAs(TOMMY_EMAIL, TOMMY_PASSWORD);
        return expense.getId();
    }
}
 