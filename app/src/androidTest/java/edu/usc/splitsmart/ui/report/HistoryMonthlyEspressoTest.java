package edu.usc.splitsmart.ui.report;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.ui.common.Extras;

/**
 * Black-box tests for history, search, and monthly summaries (Project 2.4, Ariana), written as
 * Espresso instrumented tests. Expected values come from R15 and R17 and from the text the
 * screens show, not from the history or statistics code. Each test signs in as Tommy, enters
 * the expenses and payments it needs through the app's handlers, then opens the History or
 * Monthly summary screen and checks what a user would see.
 *
 * <p>Needs an emulator or device. Each test deletes the app's local data and starts from a
 * fresh copy of the sample data: Apartment 4B with Tommy, Alex, Sam, and Jordan, and no
 * expenses. Run with {@code ./gradlew connectedDebugAndroidTest} or from Android Studio.
 */
@RunWith(AndroidJUnit4.class)
public class HistoryMonthlyEspressoTest {

    private static final String GROUP = "g-apartment";
    private static final String TOMMY = "u-tommy";
    private static final String ALEX = "u-alex";
    private static final long SETUP_TIMEOUT_SECONDS = 60;

    private static final User TOMMY_TROJAN =
            new User(TOMMY, "Tommy Trojan", "tommy@usc.edu", "1234567890");

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
     * BB-21 (R15). Two expenses with a payment between them, so the three rows have one right
     * order. History must list them newest first, label each as an Expense or a Payment, and
     * show the payment from Tommy's point of view ("Alex Kim paid you").
     */
    @Test
    public void historyListsExpensesAndPaymentsNewestFirst() {
        addExpense("Groceries", TOMMY, 4210, Category.FOOD, today());
        addPayment(ALEX, TOMMY, 1500);
        addExpense("Utilities", TOMMY, 9000, Category.HOUSING, today());

        try (ActivityScenario<HistoryActivity> history = openHistory()) {
            onView(withId(R.id.history_list)).check(titlesInOrder(
                    "Utilities", "Alex Kim paid you", "Groceries"));
            assertRow("Utilities", "Expense", "$90.00");
            assertRow("Alex Kim paid you", "Payment", "$15.00");
            assertRow("Groceries", "Expense", "$42.10");
        }
    }

    /**
     * BB-22 (search). Equivalence classes for the search text: a member's name in another
     * case, text that matches nothing, and an empty box. "ALEX" must find the expense Alex paid
     * for and the payment Tommy made to Alex, but not Tommy's own expense. Unknown text shows
     * the no-match message, and clearing the box brings every row back.
     */
    @Test
    public void searchFindsRowsByMemberIgnoringCaseAndSaysWhenNothingMatches() {
        addExpense("Groceries", TOMMY, 4210, Category.FOOD, today());
        addExpense("Concert tickets", ALEX, 6000, Category.ENTERTAINMENT, today());
        // Tommy now owes Alex $8.95 overall, so he can pay part of it back.
        addPayment(TOMMY, ALEX, 500);

        try (ActivityScenario<HistoryActivity> history = openHistory()) {
            search("ALEX");
            onView(withId(R.id.history_list)).check(titlesInOrder(
                    "You paid Alex Kim", "Concert tickets"));

            search("plane tickets to mars");
            onView(withId(R.id.history_list)).check(titlesInOrder());
            onView(withId(R.id.empty_view)).check(matches(
                    withText("No expenses or payments match your search.")));

            search("");
            onView(withId(R.id.history_list)).check(titlesInOrder(
                    "You paid Alex Kim", "Concert tickets", "Groceries"));
            onView(withId(R.id.empty_view)).check(matches(
                    withEffectiveVisibility(Visibility.GONE)));
        }
    }

    /**
     * BB-23 (filter and sort). Amounts are chosen so that newest-first and largest-first give
     * different orders. Payments shows only the payment; Expenses with Largest amount shows the
     * older $90.00 expense above the newer $42.10 one; All with Oldest first reverses the list.
     */
    @Test
    public void kindFilterAndSortNarrowAndReorderTheList() {
        addExpense("Furniture", TOMMY, 9000, Category.HOUSING, today());
        // Alex owes Tommy $45.00 for the furniture, so this pays part of it back.
        addPayment(ALEX, TOMMY, 2500);
        addExpense("Groceries", TOMMY, 4210, Category.FOOD, today());

        try (ActivityScenario<HistoryActivity> history = openHistory()) {
            onView(withId(R.id.kind_payments)).perform(click());
            onView(withId(R.id.history_list)).check(titlesInOrder("Alex Kim paid you"));

            onView(withId(R.id.kind_expenses)).perform(click());
            chooseSort("Largest amount");
            onView(withId(R.id.history_list)).check(titlesInOrder("Furniture", "Groceries"));

            onView(withId(R.id.kind_all)).perform(click());
            chooseSort("Oldest first");
            onView(withId(R.id.history_list)).check(titlesInOrder(
                    "Furniture", "Alex Kim paid you", "Groceries"));
        }
    }

    /**
     * BB-24 (R17). This month has two Food expenses and one Housing expense, plus a payment.
     * The summary must count 3 expenses totalling $820.00, list Housing $800.00 and Food $20.00,
     * and leave the $50.00 payment out, because paying someone back isn't new spending.
     */
    @Test
    public void monthlySummaryShowsCountTotalAndSpendingPerCategory() {
        addExpense("Pizza", TOMMY, 1250, Category.FOOD, today());
        addExpense("Bagels", ALEX, 750, Category.FOOD, today());
        addExpense("Rent share", TOMMY, 80000, Category.HOUSING, today());
        addPayment(ALEX, TOMMY, 5000);

        try (ActivityScenario<MonthlySummaryActivity> summary = openSummary()) {
            onView(withId(R.id.month_label)).check(matches(withText(label(YearMonth.now()))));
            onView(withId(R.id.expense_count)).check(matches(withText("3")));
            onView(withId(R.id.total_spent)).check(matches(withText("$820.00")));
            assertCategory("Housing", "$800.00");
            assertCategory("Food", "$20.00");
            onView(withId(R.id.category_container)).check(childCount(2));
            onView(withId(R.id.empty_view)).check(matches(
                    withEffectiveVisibility(Visibility.GONE)));
        }
    }

    /**
     * BB-25 (R17, boundary values). Expenses on the first day of this month, the last day of
     * last month, and the first day of last month: each must count only in its own month.
     * Going back one more month reaches a month with no expenses, which shows 0, $0.00, and
     * the empty message. Next is off for the current month, since the future has no spending.
     */
    @Test
    public void monthBoundariesCountInTheirOwnMonthAndEmptyMonthsSaySo() {
        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);
        addExpense("First of this month", TOMMY, 1000, Category.FOOD, thisMonth.atDay(1));
        addExpense("Last of last month", TOMMY, 2000, Category.FOOD, lastMonth.atEndOfMonth());
        addExpense("First of last month", TOMMY, 4000, Category.OTHER, lastMonth.atDay(1));

        try (ActivityScenario<MonthlySummaryActivity> summary = openSummary()) {
            onView(withId(R.id.expense_count)).check(matches(withText("1")));
            onView(withId(R.id.total_spent)).check(matches(withText("$10.00")));
            onView(withId(R.id.next_month)).check(matches(not(isEnabled())));

            onView(withId(R.id.previous_month)).perform(click());
            onView(withId(R.id.month_label)).check(matches(withText(label(lastMonth))));
            onView(withId(R.id.expense_count)).check(matches(withText("2")));
            onView(withId(R.id.total_spent)).check(matches(withText("$60.00")));
            assertCategory("Other", "$40.00");
            assertCategory("Food", "$20.00");
            onView(withId(R.id.next_month)).check(matches(isEnabled()));

            onView(withId(R.id.previous_month)).perform(click());
            onView(withId(R.id.month_label)).check(matches(withText(label(lastMonth.minusMonths(1)))));
            onView(withId(R.id.expense_count)).check(matches(withText("0")));
            onView(withId(R.id.total_spent)).check(matches(withText("$0.00")));
            onView(withId(R.id.empty_view)).check(matches(withText("No expenses this month.")));
            onView(withId(R.id.category_container)).check(childCount(0));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Screens and what they show
    // ---------------------------------------------------------------------------------------

    private ActivityScenario<HistoryActivity> openHistory() {
        return ActivityScenario.launch(new Intent(context, HistoryActivity.class)
                .putExtra(Extras.GROUP_ID, GROUP));
    }

    private ActivityScenario<MonthlySummaryActivity> openSummary() {
        return ActivityScenario.launch(new Intent(context, MonthlySummaryActivity.class)
                .putExtra(Extras.GROUP_ID, GROUP));
    }

    private static void search(String text) {
        onView(withId(R.id.search_input)).perform(replaceText(text), closeSoftKeyboard());
    }

    private static void chooseSort(String option) {
        onView(withId(R.id.sort_input)).perform(click());
        onView(withText(option)).inRoot(isPlatformPopup()).perform(click());
    }

    /** One history row, found by its title: its Expense/Payment label and its amount. */
    private static void assertRow(String title, String type, String amount) {
        onView(allOf(withId(R.id.history_type), withParent(hasDescendant(withText(title)))))
                .check(matches(withText(type)));
        onView(allOf(withId(R.id.history_amount), withParent(hasDescendant(withText(title)))))
                .check(matches(withText(amount)));
    }

    /** One row of the summary's By category list. */
    private static void assertCategory(String name, String amount) {
        onView(allOf(withId(R.id.category_amount), withParent(hasDescendant(withText(name)))))
                .check(matches(withText(amount)));
    }

    /** The history list shows exactly these row titles, top to bottom. */
    private static ViewAssertion titlesInOrder(String... expected) {
        return (view, noView) -> {
            if (noView != null) {
                throw noView;
            }
            RecyclerView list = (RecyclerView) view;
            List<String> titles = new ArrayList<>();
            for (int i = 0; i < list.getChildCount(); i++) {
                TextView title = list.getChildAt(i).findViewById(R.id.history_title);
                titles.add(title.getText().toString());
            }
            assertEquals("Rows on the History screen", Arrays.asList(expected), titles);
            assertEquals(expected.length, list.getAdapter().getItemCount());
        };
    }

    private static ViewAssertion childCount(int expected) {
        return (view, noView) -> {
            if (noView != null) {
                throw noView;
            }
            assertEquals("Category rows", expected, ((android.view.ViewGroup) view).getChildCount());
        };
    }

    /** The month as the summary's header names it, e.g. "October 2026". */
    private static String label(YearMonth month) {
        return month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()));
    }

    private static LocalDate today() {
        return LocalDate.now();
    }

    // ---------------------------------------------------------------------------------------
    // Helpers: setting up data the way the app's own screens do
    // ---------------------------------------------------------------------------------------

    /**
     * Makes {@code user} the signed-in user by saving the session directly, then rebuilds
     * AppServices so its handlers pick the session up. Logging in with a password would run the
     * app's deliberately slow password check, which is not what these tests are about.
     */
    private void signIn(User user) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            new SessionStore(context).setCurrentUser(user);
            AppServices.init(context);
        });
    }

    /**
     * Adds an expense to Apartment 4B, split equally between Tommy and Alex. Waits a moment
     * afterwards so each entry gets its own "recorded at" time and history order is fixed.
     */
    private static Expense addExpense(String description, String payerId, long totalCents,
                                      Category category, LocalDate date) {
        Map<String, Long> split = new LinkedHashMap<>();
        split.put(TOMMY, 0L);
        split.put(ALEX, 0L);
        ExpenseDraft draft = new ExpenseDraft(GROUP, payerId, description, "", totalCents,
                category, SplitType.EQUAL, date, split);
        Expense saved = HistoryMonthlyEspressoTest.<Expense>await(cb -> AppServices.expenses()
                .createExpense(AppServices.session().getCurrentUser().getId(), draft, cb));
        pause();
        return saved;
    }

    private static Settlement addPayment(String payerId, String receiverId, long amountCents) {
        Settlement saved = HistoryMonthlyEspressoTest.<Settlement>await(cb -> AppServices
                .expenses().recordSettlement(GROUP, payerId, receiverId, amountCents, "", cb));
        pause();
        return saved;
    }

    private static void pause() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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
