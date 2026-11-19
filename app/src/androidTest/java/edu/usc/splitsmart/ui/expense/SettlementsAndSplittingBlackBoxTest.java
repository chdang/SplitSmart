package edu.usc.splitsmart.ui.expense;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.ViewAssertion;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.material.textfield.TextInputLayout;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.ui.auth.LoginActivity;

/**
 * Project 2.4, Kyuhong Lee: black-box tests 16-20 (R9-R11, R16).
 * All test inputs and assertions use visible UI controls. Only setup resets the database/session;
 * no handlers, repositories, split calculators, or private application fields supply an oracle.
 * Run on a disposable emulator: every test deletes this app's local database.
 */
@RunWith(AndroidJUnit4.class)
public class SettlementsAndSplittingBlackBoxTest {
    private static final long TIMEOUT_MS = 30_000;

    private static final class FreshAppRule extends ExternalResource {
        @Override
        protected void before() {
            Context context = ApplicationProvider.getApplicationContext();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                AppServices.session().logout();
                context.deleteDatabase("splitsmart.db");
                AppServices.init(context);
            });
        }
    }

    @Rule
    public final RuleChain rules = RuleChain.outerRule(new FreshAppRule())
            .around(new ActivityScenarioRule<>(LoginActivity.class));

    @After
    public void closeScreens() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Set<Activity> screens = new HashSet<>();
            for (Stage stage : Stage.values()) {
                screens.addAll(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage));
            }
            for (Activity screen : screens) if (!screen.isFinishing()) screen.finish();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /** BB16: $10 / 3, first-listed penny rule, all participants, and reopening saved shares. */
    @Test
    public void test16EqualSplitConservesPenniesAfterReopening() {
        openNewExpense("Equal rounding", "10.00");
        uncheck("Jordan Lee");
        assertEqualPreviews("$3.34", "$3.33", "$3.33");
        saveAndReopen("Equal rounding");
        onView(withId(R.id.split_equal)).check(matches(isChecked()));
        assertEqualPreviews("$3.34", "$3.33", "$3.33");
        onView(withId(R.id.amount_input)).perform(scrollTo()).check(matches(withText("10.00")));
    }

    /** BB17: just below/above 100%, a one-cent total, and original percentages on edit. */
    @Test
    public void test17PercentageRequiresOneHundredAndPreservesRounding() {
        openNewExpense("Percentage rounding", "0.01");
        keepTommyAndAlex();
        onView(withId(R.id.split_percent)).perform(scrollTo(), click());
        setShare("Tommy Trojan", "33.33");
        setShare("Alex Kim", "66.66");
        rejectSplit("The percentages need to add up to 100%");
        setShare("Alex Kim", "66.68");
        rejectSplit("The percentages need to add up to 100%");
        setShare("Alex Kim", "66.67");
        assertSharePreview("Tommy Trojan", "$0.00");
        assertSharePreview("Alex Kim", "$0.01");
        saveAndReopen("Percentage rounding");
        onView(withId(R.id.split_percent)).check(matches(isChecked()));
        onView(participantControl(R.id.participant_value_input, "Tommy Trojan"))
                .perform(scrollTo()).check(matches(withText("33.33")));
        onView(participantControl(R.id.participant_value_input, "Alex Kim"))
                .perform(scrollTo()).check(matches(withText("66.67")));
        assertSharePreview("Tommy Trojan", "$0.00");
        assertSharePreview("Alex Kim", "$0.01");
    }

    /** BB18: one cent under/over the total and valid, deliberately unequal exact shares. */
    @Test
    public void test18ExactAmountsMustMatchTheTotal() {
        openNewExpense("Exact amounts", "10.00");
        keepTommyAndAlex();
        onView(withId(R.id.split_exact)).perform(scrollTo(), click());
        setShare("Tommy Trojan", "4.00");
        setShare("Alex Kim", "5.99");
        rejectSplit("The amounts need to add up to $10.00");
        setShare("Alex Kim", "6.01");
        rejectSplit("The amounts need to add up to $10.00");
        setShare("Alex Kim", "6.00");
        saveAndReopen("Exact amounts");
        onView(withId(R.id.split_exact)).check(matches(isChecked()));
        onView(participantControl(R.id.participant_value_input, "Tommy Trojan"))
                .perform(scrollTo()).check(matches(withText("4.00")));
        onView(participantControl(R.id.participant_value_input, "Alex Kim"))
                .perform(scrollTo()).check(matches(withText("6.00")));
    }

    /** BB19: a partial cash payment, stored note/details, and an exact final payment. */
    @Test
    public void test19PartialAndFinalSettlementsAreSavedAndReduceDebt() {
        createFifteenDollarDebt();
        openPayment();
        onView(withId(R.id.amount_input)).check(matches(withText("15.00")));
        onView(withId(R.id.amount_input)).perform(scrollTo(), replaceText("5.00"), closeSoftKeyboard());
        onView(withId(R.id.note_input)).perform(scrollTo(), replaceText("Cash at lunch"), closeSoftKeyboard());
        onView(withId(R.id.record_button)).perform(scrollTo(), click());
        waitFor(withId(R.id.payable_amount), matches(withText("$10.00")));
        onView(withId(R.id.history_button)).perform(scrollTo(), click());
        waitFor(allOf(withId(R.id.history_amount), withText("$5.00")), matches(isDisplayed()));
        onView(allOf(withId(R.id.history_amount), withText("$5.00"))).perform(click());
        waitFor(withId(R.id.note_input), matches(withText("Cash at lunch")));
        onView(withId(R.id.amount_input)).perform(scrollTo()).check(matches(withText("5.00")));
        onView(withId(R.id.payer_input)).perform(scrollTo()).check(matches(withText(startsWith("Tommy Trojan"))));
        onView(withId(R.id.receiver_input)).check(matches(withText("Alex Kim")));
        pressBack();
        pressBack();
        openPayment();
        onView(withId(R.id.amount_input)).check(matches(withText("10.00")));
        onView(withId(R.id.record_button)).perform(scrollTo(), click());
        waitFor(withId(R.id.payable_amount), matches(withText("$0.00")));
        onView(withId(R.id.receivable_amount)).check(matches(withText("$0.00")));
    }

    /** BB20: missing recipient, zero amount, overpayment by one cent, and reversed direction. */
    @Test
    public void test20InvalidOrExcessSettlementsDoNotChangeDebt() {
        createFifteenDollarDebt();
        onView(withId(R.id.settle_up_button)).perform(scrollTo(), click());
        waitFor(withId(R.id.record_button), matches(isEnabled()));
        onView(withId(R.id.amount_input)).perform(scrollTo(), replaceText("0.00"), closeSoftKeyboard());
        onView(withId(R.id.record_button)).perform(scrollTo(), click());
        onView(withId(R.id.other_layout)).check(matches(hasError("Pick who the payment was with")));
        onView(withId(R.id.amount_layout)).check(matches(hasError("Enter an amount greater than $0, like 12.50")));
        chooseAlex(R.id.other_input);
        onView(withId(R.id.record_button)).perform(scrollTo(), click());
        onView(withId(R.id.amount_layout)).check(matches(hasError("Enter an amount greater than $0, like 12.50")));
        onView(withId(R.id.amount_input)).perform(scrollTo(), replaceText("15.01"), closeSoftKeyboard());
        onView(withId(R.id.record_button)).perform(scrollTo(), click());
        waitFor(withText("That's more than is owed right now. Enter $15.00 or less."), matches(isDisplayed()));
        waitFor(withText("That's more than is owed right now. Enter $15.00 or less."), doesNotExist());
        onView(withId(R.id.direction_they_paid)).perform(scrollTo(), click());
        onView(withId(R.id.amount_input)).perform(scrollTo(), replaceText("1.00"), closeSoftKeyboard());
        onView(withId(R.id.record_button)).perform(scrollTo(), click());
        waitFor(withText("That's more than is owed right now. Enter $0.00 or less."), matches(isDisplayed()));
        waitFor(withText("That's more than is owed right now. Enter $0.00 or less."), doesNotExist());
        pressBack();
        waitFor(withId(R.id.payable_amount), matches(withText("$15.00")));
        onView(withId(R.id.receivable_amount)).check(matches(withText("$0.00")));
    }

    private static void openNewExpense(String description, String amount) {
        onView(withId(R.id.email_input)).perform(replaceText("tommy@usc.edu"));
        onView(withId(R.id.password_input)).perform(replaceText("password"), closeSoftKeyboard());
        onView(withId(R.id.login_button)).perform(click());
        waitFor(withText("Apartment 4B"), matches(isDisplayed()));
        onView(withText("Apartment 4B")).perform(click());
        waitFor(withId(R.id.add_expense_button), matches(isDisplayed()));
        onView(withId(R.id.add_expense_button)).perform(click());
        waitFor(withId(R.id.save_button), matches(isEnabled()));
        onView(withId(R.id.description_input)).perform(scrollTo(), replaceText(description));
        onView(withId(R.id.amount_input)).perform(scrollTo(), replaceText(amount), closeSoftKeyboard());
    }

    private static void keepTommyAndAlex() {
        uncheck("Sam Rivera");
        uncheck("Jordan Lee");
    }

    private static void uncheck(String name) {
        onView(allOf(withId(R.id.participant_check), withText(startsWith(name))))
                .perform(scrollTo(), click());
    }

    private static void setShare(String name, String value) {
        onView(participantControl(R.id.participant_value_input, name))
                .perform(scrollTo(), replaceText(value), closeSoftKeyboard());
    }

    private static void assertSharePreview(String name, String value) {
        onView(participantControl(R.id.participant_preview, name))
                .perform(scrollTo()).check(matches(withText(value)));
    }

    private static void rejectSplit(String message) {
        onView(withId(R.id.save_button)).perform(scrollTo(), click());
        waitFor(withText(message), matches(isDisplayed()));
        onView(withId(R.id.save_button)).check(matches(isDisplayed()));
        // The transient error banner can cover Save. Wait for it to leave before the next tap.
        waitFor(withText(message), doesNotExist());
    }

    private static void saveAndReopen(String description) {
        onView(withId(R.id.save_button)).perform(scrollTo(), click());
        waitFor(allOf(withId(R.id.expense_description), withText(description)), matches(isDisplayed()));
        onView(allOf(withId(R.id.expense_description), withText(description))).perform(click());
        waitFor(withId(R.id.delete_button), matches(withEffectiveVisibility(Visibility.VISIBLE)));
    }

    private static void createFifteenDollarDebt() {
        openNewExpense("Settlement fixture", "30.00");
        chooseAlex(R.id.payer_input);
        keepTommyAndAlex();
        onView(withId(R.id.save_button)).perform(scrollTo(), click());
        waitFor(withId(R.id.payable_amount), matches(withText("$15.00")));
    }

    private static void openPayment() {
        waitFor(withId(R.id.settle_up_button), matches(isDisplayed()));
        onView(withId(R.id.settle_up_button)).perform(scrollTo(), click());
        waitFor(withId(R.id.record_button), matches(isEnabled()));
        chooseAlex(R.id.other_input);
        waitFor(withId(R.id.balance_hint), matches(withText(startsWith("You owe Alex Kim"))));
    }

    private static void chooseAlex(int inputId) {
        onView(withId(inputId)).perform(scrollTo(), click());
        onView(withText("Alex Kim")).inRoot(isPlatformPopup()).perform(click());
    }

    private static void assertEqualPreviews(String... expected) {
        onView(withId(R.id.participants_container)).check((view, missing) -> {
            if (missing != null) throw missing;
            LinearLayout rows = (LinearLayout) view;
            int selected = 0;
            for (int i = 0; i < rows.getChildCount(); i++) {
                View row = rows.getChildAt(i);
                CheckBox check = row.findViewById(R.id.participant_check);
                if (!check.isChecked()) continue;
                TextView preview = row.findViewById(R.id.participant_preview);
                assertEquals(check.getText().toString(), expected[selected++], preview.getText().toString());
            }
            assertEquals("Number of checked participants", expected.length, selected);
        });
    }

    /** Finds a repeated control by the visible checkbox label in its nearest participant row. */
    private static Matcher<View> participantControl(int id, String name) {
        return allOf(withId(id), new TypeSafeMatcher<View>() {
            public void describeTo(Description description) {
                description.appendText("participant row labelled " + name);
            }

            protected boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                while (parent instanceof View) {
                    CheckBox label = ((View) parent).findViewById(R.id.participant_check);
                    if (label != null) return label.getText().toString().startsWith(name);
                    parent = parent.getParent();
                }
                return false;
            }
        });
    }

    private static Matcher<View> hasError(String expected) {
        return new BoundedMatcher<View, TextInputLayout>(TextInputLayout.class) {
            public void describeTo(Description description) {
                description.appendText("field error " + expected);
            }

            protected boolean matchesSafely(TextInputLayout layout) {
                return layout.getError() != null && expected.contentEquals(layout.getError());
            }
        };
    }

    /** Waits for asynchronous screen results without fixed sleeps or application internals. */
    private static void waitFor(Matcher<View> view, ViewAssertion assertion) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (true) {
            try {
                onView(view).check(assertion);
                return;
            } catch (RuntimeException | AssertionError failure) {
                if (SystemClock.uptimeMillis() >= deadline) throw failure;
                SystemClock.sleep(100);
            }
        }
    }
}
