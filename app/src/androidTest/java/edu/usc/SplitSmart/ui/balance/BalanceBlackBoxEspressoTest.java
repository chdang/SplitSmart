package edu.usc.splitsmart.ui.balance;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.ViewAssertion;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.logic.ExpenseDraft;
import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.ui.common.Extras;

/**
 * Black-box tests for Balance Calculation and Display (R12-R14), written as Espresso
 * instrumented tests. Every expected value comes from the requirements and the wording the
 * screens are specified to show, not from the balance code. Each test signs in as a sample
 * user and enters the expenses it needs, then opens the Balances or Who owes whom screen and
 * checks what a user would see. BB4 records its payments by tapping through the Settle up
 * screen.
 *
 * <p>Needs an emulator or device. Each test deletes the app's local data and starts from a
 * fresh copy of the sample data: Apartment 4B with Tommy, Alex, Sam, and Jordan, and no
 * expenses. Run with {@code ./gradlew connectedDebugAndroidTest} or from Android Studio.
 */
@RunWith(AndroidJUnit4.class)
public class BalanceBlackBoxEspressoTest {

    private static final String GROUP = "g-apartment";
    private static final String TOMMY = "u-tommy";
    private static final String ALEX = "u-alex";
    private static final String SAM = "u-sam";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 15);
    private static final String SETTLED = "You're all settled up in this group.";

    /** Sample accounts, exactly as the app seeds them into every new database. */
    private static final User TOMMY_TROJAN =
            new User(TOMMY, "Tommy Trojan", "tommy@usc.edu", "1234567890");
    private static final User ALEX_KIM =
            new User(ALEX, "Alex Kim", "alex@usc.edu", "2345678901");

    /**
     * Generous because the first step of each test creates a fresh database, and seeding it
     * runs the app's deliberately slow password hashing. On a busy emulator that can take
     * well over 10 seconds.
     */
    private static final long SETUP_TIMEOUT_SECONDS = 60;

    private final LoadingBarIdlingResource loading = new LoadingBarIdlingResource();
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("splitsmart.db");
        signIn(TOMMY_TROJAN);
        IdlingRegistry.getInstance().register(loading);
    }

    @After
    public void tearDown() {
        IdlingRegistry.getInstance().unregister(loading);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> AppServices.session().logout());
        context.deleteDatabase("splitsmart.db");
    }

    /**
     * BB1 (equivalence class: the user is only owed money). Tommy pays $30.00 for dinner, split
     * equally between Tommy, Alex, and Sam. By R12, Tommy owes nothing and is owed $20.00, $10.00
     * from each of the others; his own $10.00 share is not a debt to anyone.
     */
    @Test
    public void payerIsOwedEveryoneElsesShareButNotTheirOwn() {
        addExpense(TOMMY, 3000, SplitType.EQUAL, allocations(TOMMY, 0L, ALEX, 0L, SAM, 0L));

        try (ActivityScenario<TotalGroupBalanceActivity> balances = openBalances()) {
            assertTotals("$0.00", "$20.00", "Overall, you're owed $20.00");
        }
    }

    /**
     * BB2 (equivalence class: debts in both directions with one person). Alex pays $40.00 split
     * equally with Tommy, so Tommy owes Alex $20.00. Tommy pays $10.00 that is entirely Alex's,
     * so Alex owes Tommy $10.00. R12 says totals come after netting each pair, so Tommy owes
     * $10.00 and is owed nothing (not "owe $20.00, owed $10.00"). Alex must see the mirror image.
     */
    @Test
    public void debtsBetweenTheSameTwoPeopleAreNettedForBoth() {
        addExpense(ALEX, 4000, SplitType.EQUAL, allocations(TOMMY, 0L, ALEX, 0L));
        addExpense(TOMMY, 1000, SplitType.EXACT_AMOUNT, allocations(ALEX, 1000L));

        try (ActivityScenario<TotalGroupBalanceActivity> balances = openBalances()) {
            assertTotals("$10.00", "$0.00", "Overall, you owe $10.00");
        }

        signIn(ALEX_KIM);
        try (ActivityScenario<TotalGroupBalanceActivity> balances = openBalances()) {
            assertTotals("$0.00", "$10.00", "Overall, you're owed $10.00");
        }
    }

    /**
     * BB3 (R13, one row per other member, covering all three states). Alex pays $60.00 split
     * equally between Tommy, Alex, and Sam, so Tommy owes Alex $20.00. Tommy pays $30.00 as exact
     * amounts, $12.50 for Sam and $17.50 for himself, so Sam owes Tommy $12.50. Jordan shares no
     * expenses with Tommy. The Who owes whom screen must list exactly Alex, Sam, and Jordan
     * (never Tommy himself), say who owes whom and how much, and offer Settle up only on the row
     * where Tommy is the one who owes.
     */
    @Test
    public void whoOwesWhomShowsEachOtherMembersDirectionAndAmount() {
        addExpense(ALEX, 6000, SplitType.EQUAL, allocations(TOMMY, 0L, ALEX, 0L, SAM, 0L));
        addExpense(TOMMY, 3000, SplitType.EXACT_AMOUNT, allocations(SAM, 1250L, TOMMY, 1750L));

        Intent intent = new Intent(context, IndividualGroupBalanceActivity.class)
                .putExtra(Extras.GROUP_ID, GROUP);
        try (ActivityScenario<IndividualGroupBalanceActivity> whoOwesWhom =
                     ActivityScenario.launch(intent)) {
            onView(withId(R.id.balance_list)).check(hasRows(3));
            onView(withText("Tommy Trojan")).check(doesNotExist());
            assertRow("Alex Kim", "You owe $20.00", true);
            assertRow("Sam Rivera", "Owes you $12.50", false);
            assertRow("Jordan Lee", "Settled up", false);
        }
    }

    /**
     * BB4 (boundary: paying a debt down to exactly zero, and R14). Alex pays $30.00 split
     * equally with Tommy, so Tommy owes $15.00. Tommy goes to Who owes whom, taps Settle up on
     * Alex's row, and pays $5.00, then comes back and pays the suggested remainder. After each
     * payment both screens must update as soon as Tommy returns to them, and paying exactly what
     * is owed must leave Alex's row "Settled up" with no Settle up button, $0.00 on both sides of
     * the Balances screen, and the settled-up message.
     */
    @Test
    public void settlingUpThroughTheAppUpdatesBalancesUntilExactlySettled() {
        addExpense(ALEX, 3000, SplitType.EQUAL, allocations(TOMMY, 0L, ALEX, 0L));

        try (ActivityScenario<TotalGroupBalanceActivity> balances = openBalances()) {
            assertTotals("$15.00", "$0.00", "Overall, you owe $15.00");

            // Pay part of it: $5.00 of $15.00.
            onView(withId(R.id.individual_button)).perform(scrollTo(), click());
            onView(settleUpButtonFor("Alex Kim")).perform(click());
            onView(withId(R.id.balance_hint)).check(matches(withText("You owe Alex Kim $15.00")));
            onView(withId(R.id.amount_input)).check(matches(withText("15.00")));
            onView(withId(R.id.amount_input))
                    .perform(scrollTo(), replaceText("5.00"), closeSoftKeyboard());
            onView(withId(R.id.record_button)).perform(scrollTo(), click());

            assertRow("Alex Kim", "You owe $10.00", true);
            pressBack();
            assertTotals("$10.00", "$0.00", "Overall, you owe $10.00");

            // Pay exactly what is left, which the screen suggests.
            onView(withId(R.id.individual_button)).perform(scrollTo(), click());
            onView(settleUpButtonFor("Alex Kim")).perform(click());
            onView(withId(R.id.balance_hint)).check(matches(withText("You owe Alex Kim $10.00")));
            onView(withId(R.id.amount_input)).check(matches(withText("10.00")));
            onView(withId(R.id.record_button)).perform(scrollTo(), click());

            assertRow("Alex Kim", "Settled up", false);
            pressBack();
            assertTotals("$0.00", "$0.00", SETTLED);
        }
    }

    /**
     * BB5 (R14, edits and deletes). Tommy pays $30.00 split equally between Tommy, Alex, and Sam,
     * so he is owed $20.00. While he is away from the Balances screen the expense is edited to
     * $60.00, so he should be owed $40.00, and then deleted, so nobody should owe anything.
     * Balances must follow each change the next time the screen is shown.
     */
    @Test
    public void editingAndDeletingAnExpenseUpdatesBalances() {
        Map<String, Long> threeWays = allocations(TOMMY, 0L, ALEX, 0L, SAM, 0L);
        Expense dinner = addExpense(TOMMY, 3000, SplitType.EQUAL, threeWays);

        try (ActivityScenario<TotalGroupBalanceActivity> balances = openBalances()) {
            assertTotals("$0.00", "$20.00", "Overall, you're owed $20.00");

            BalanceBlackBoxEspressoTest.<Expense>await(cb -> AppServices.expenses().updateExpense(
                    dinner.getId(), TOMMY, draft(TOMMY, 6000, SplitType.EQUAL, threeWays), cb));
            leaveAndComeBack(balances);
            assertTotals("$0.00", "$40.00", "Overall, you're owed $40.00");

            BalanceBlackBoxEspressoTest.<Void>await(cb ->
                    AppServices.expenses().deleteExpense(dinner.getId(), TOMMY, cb));
            leaveAndComeBack(balances);
            assertTotals("$0.00", "$0.00", SETTLED);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers: reading the screens
    // ---------------------------------------------------------------------------------------

    private ActivityScenario<TotalGroupBalanceActivity> openBalances() {
        Intent intent = new Intent(context, TotalGroupBalanceActivity.class)
                .putExtra(Extras.GROUP_ID, GROUP);
        return ActivityScenario.launch(intent);
    }

    /** Pauses the screen and brings it back, as when the user visits another screen. */
    private static void leaveAndComeBack(ActivityScenario<?> screen) {
        screen.moveToState(Lifecycle.State.STARTED);
        screen.moveToState(Lifecycle.State.RESUMED);
    }

    /** The Balances screen: "You owe", "You're owed", and the summary under them. */
    private static void assertTotals(String youOwe, String youreOwed, String summary) {
        onView(withId(R.id.payable_amount)).check(matches(withText(youOwe)));
        onView(withId(R.id.receivable_amount)).check(matches(withText(youreOwed)));
        onView(withId(R.id.net_summary)).check(matches(withText(summary)));
    }

    /** One row of the Who owes whom screen, found by the member's name. */
    private static void assertRow(String name, String status, boolean offersSettleUp) {
        onView(allOf(withId(R.id.balance_status), hasSibling(withText(name))))
                .check(matches(withText(status)));
        onView(settleUpButtonFor(name)).check(matches(
                withEffectiveVisibility(offersSettleUp ? Visibility.VISIBLE : Visibility.GONE)));
    }

    private static Matcher<View> settleUpButtonFor(String name) {
        return allOf(withId(R.id.settle_button), withParent(hasDescendant(withText(name))));
    }

    private static ViewAssertion hasRows(int expected) {
        return (view, noView) -> {
            if (noView != null) {
                throw noView;
            }
            assertEquals("Rows on the screen", expected,
                    ((RecyclerView) view).getAdapter().getItemCount());
        };
    }

    // ---------------------------------------------------------------------------------------
    // Helpers: setting up data the way the app's own screens do
    // ---------------------------------------------------------------------------------------

    /**
     * Makes {@code user} the signed-in user by saving the session directly, the same shortcut
     * ExpenseIntegrationTest uses, then rebuilds AppServices so its handlers pick the session
     * up. Logging in with a password would run the app's deliberately slow password check,
     * which is not what these tests are about and can take many seconds on an emulator.
     */
    private void signIn(User user) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            new SessionStore(context).setCurrentUser(user);
            AppServices.init(context);
        });
    }

    /** Adds an expense to Apartment 4B, entered by whoever is signed in. */
    private static Expense addExpense(String payerId, long totalCents, SplitType type,
                                      Map<String, Long> allocations) {
        return BalanceBlackBoxEspressoTest.<Expense>await(cb -> AppServices.expenses()
                .createExpense(AppServices.session().getCurrentUser().getId(),
                        draft(payerId, totalCents, type, allocations), cb));
    }

    private static ExpenseDraft draft(String payerId, long totalCents, SplitType type,
                                      Map<String, Long> allocations) {
        return new ExpenseDraft(GROUP, payerId, "Test expense", "", totalCents, Category.FOOD,
                type, DATE, allocations);
    }

    /** Participants in the order given. Values are ignored for EQUAL splits. */
    private static Map<String, Long> allocations(Object... idValuePairs) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < idValuePairs.length; i += 2) {
            result.put((String) idValuePairs[i], (Long) idValuePairs[i + 1]);
        }
        return result;
    }

    /**
     * Calls a handler on the main thread, as the app's screens do, and waits on the test thread
     * for its answer. Fails the test if the handler reports an error.
     */
    private static <T> T await(Consumer<Callback<T>> call) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        Callback<T> callback = new Callback<T>() {
            @Override
            public void onSuccess(T result) {
                value.set(result);
                done.countDown();
            }

            @Override
            public void onError(Exception failure) {
                error.set(failure);
                done.countDown();
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> call.accept(callback));
        try {
            assertTrue("Timed out after " + SETUP_TIMEOUT_SECONDS + " s waiting for test setup."
                            + " If the emulator just started, wait a minute and run again.",
                    done.await(SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
        if (error.get() != null) {
            throw new AssertionError("Test setup failed", error.get());
        }
        return value.get();
    }

    /**
     * Makes Espresso wait while the screen in front shows its loading bar. SplitSmart's handlers
     * work on a background thread that Espresso can't see, and every screen shows this bar
     * (R.id.progress) until its results are on screen, so the bar is the signal to wait for.
     */
    private static final class LoadingBarIdlingResource implements IdlingResource {
        private final Handler mainThread = new Handler(Looper.getMainLooper());
        private volatile ResourceCallback callback;
        private boolean recheckScheduled;

        @Override
        public String getName() {
            return "SplitSmart loading bar";
        }

        @Override
        public boolean isIdleNow() {
            boolean idle = true;
            for (Activity screen : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                View bar = screen.findViewById(R.id.progress);
                if (bar != null && bar.getVisibility() == View.VISIBLE) {
                    idle = false;
                }
            }
            if (idle) {
                ResourceCallback current = callback;
                if (current != null) {
                    current.onTransitionToIdle();
                }
            } else if (!recheckScheduled) {
                // Nothing else tells Espresso when the bar hides, so look again shortly.
                recheckScheduled = true;
                mainThread.postDelayed(() -> {
                    recheckScheduled = false;
                    isIdleNow();
                }, 50);
            }
            return idle;
        }

        @Override
        public void registerIdleTransitionCallback(ResourceCallback callback) {
            this.callback = callback;
        }
    }
}
