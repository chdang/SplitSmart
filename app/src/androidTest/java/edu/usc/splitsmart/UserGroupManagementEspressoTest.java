package edu.usc.splitsmart;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.ViewAssertion;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.textfield.TextInputLayout;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import edu.usc.splitsmart.ui.auth.LoginActivity;

/**
 * Black-box UI tests for user and group management (Project 2.4, Darius). Every test starts
 * logged out on the login screen with a freshly seeded database, and only uses what a user can
 * see and tap. Seeded accounts all use the password "password".
 */
@RunWith(AndroidJUnit4.class)
public class UserGroupManagementEspressoTest {

    /**
     * Generous on purpose: each test rebuilds the database, and seeding it plus each login runs
     * PBKDF2 (100,000 iterations), which takes several seconds on an emulator.
     */
    private static final long TIMEOUT_MS = 30_000;

    /** Logs out and rebuilds the seeded database before the login screen launches. */
    private static final class FreshAppRule extends ExternalResource {
        @Override
        protected void before() {
            Context context = ApplicationProvider.getApplicationContext();
            InstrumentationRegistry.getInstrumentation()
                    .runOnMainSync(() -> AppServices.session().logout());
            context.deleteDatabase("splitsmart.db");
            AppServices.init(context);
        }
    }

    @Rule
    public final RuleChain rules = RuleChain.outerRule(new FreshAppRule())
            .around(new ActivityScenarioRule<>(LoginActivity.class));

    /** BB-1: a valid-looking email outside @usc.edu is rejected and creates no account (R1). */
    @Test
    public void signUpWithNonUscEmailIsRejectedAndCreatesNoAccount() {
        onView(withId(R.id.sign_up_button)).perform(click());
        fillSignUp("Test User", "test@gmail.com", "1111111111", "password123", "password123");
        onView(withId(R.id.sign_up_button)).perform(scrollTo(), click());

        waitFor(withId(R.id.email_layout), matches(hasError("Use an @usc.edu email address")));
        onView(withId(R.id.sign_up_button)).check(matches(isDisplayed()));

        // Back on the login screen, that email does not log in.
        pressBack();
        logInExpectingFailure("test@gmail.com", "password123");
    }

    /** BB-2: duplicate email (in a different case) and duplicate USC ID, each on its field (R2). */
    @Test
    public void signUpWithTakenEmailOrUscIdShowsErrorOnThatField() {
        onView(withId(R.id.sign_up_button)).perform(click());

        fillSignUp("Copy Cat", "TOMMY@usc.edu", "9999999999", "password123", "password123");
        onView(withId(R.id.sign_up_button)).perform(scrollTo(), click());
        waitFor(withId(R.id.email_layout),
                matches(hasError("An account with this email already exists")));

        fillSignUp("Copy Cat", "fresh@usc.edu", "1234567890", "password123", "password123");
        onView(withId(R.id.sign_up_button)).perform(scrollTo(), click());
        waitFor(withId(R.id.usc_id_layout),
                matches(hasError("An account with this USC ID already exists")));
        onView(withId(R.id.email_layout)).check(matches(hasError(null)));
    }

    /**
     * BB-3: boundary values. A 9-digit USC ID and a 7-character password are rejected, an 11th
     * digit can't be typed, and exactly 10 digits and exactly 8 characters create an account
     * that can log in.
     */
    @Test
    public void signUpBoundaryValuesRejectJustOutsideAndAcceptJustInside() {
        onView(withId(R.id.sign_up_button)).perform(click());

        fillSignUp("Edge Case", "edge@usc.edu", "123456789", "1234567", "1234567");
        onView(withId(R.id.sign_up_button)).perform(scrollTo(), click());
        onView(withId(R.id.usc_id_layout)).check(matches(hasError("Enter your 10-digit USC ID")));
        onView(withId(R.id.password_layout)).check(matches(hasError("Use at least 8 characters")));

        // An 11th digit can't be entered at all: the field keeps only the first 10.
        fillSignUp("Edge Case", "edge@usc.edu", "12345678901", "12345678", "12345678");
        onView(withId(R.id.usc_id_input)).check(matches(withText("1234567890")));

        fillSignUp("Edge Case", "edge@usc.edu", "0000000000", "12345678", "12345678");
        onView(withId(R.id.sign_up_button)).perform(scrollTo(), click());

        // Back on login with the new email filled in; the account logs in to an empty list.
        waitFor(withId(R.id.login_button), matches(isDisplayed()));
        onView(withId(R.id.email_input)).check(matches(withText("edge@usc.edu")));
        onView(withId(R.id.password_input)).perform(replaceText("12345678"), closeSoftKeyboard());
        onView(withId(R.id.login_button)).perform(click());
        waitFor(withId(R.id.empty_view), matches(isDisplayed()));
    }

    /**
     * BB-4: invalid group input. A malformed email, the user's own email, a blank name, and an
     * email with no account each block creation, and no group is created.
     */
    @Test
    public void createGroupWithInvalidInputIsRejectedAndNotSaved() {
        logIn("tommy@usc.edu", "password");
        onView(withId(R.id.new_group_button)).perform(click());

        addMemberEmail("not-an-email");
        onView(withId(R.id.member_email_layout))
                .check(matches(hasError("Enter a valid email address")));

        addMemberEmail("tommy@usc.edu");
        onView(withId(R.id.member_email_layout))
                .check(matches(hasError("You're already in the group")));

        addMemberEmail("alex@usc.edu");
        addMemberEmail("ghost@usc.edu");
        onView(withId(R.id.create_button)).perform(scrollTo(), click());
        onView(withId(R.id.name_layout)).check(matches(hasError("Required")));

        onView(withId(R.id.name_input)).perform(replaceText("Trip"), closeSoftKeyboard());
        onView(withId(R.id.create_button)).perform(scrollTo(), click());
        waitFor(withId(R.id.missing_accounts),
                matches(withText("No SplitSmart account for: ghost@usc.edu")));

        pressBack();
        waitFor(withText("Apartment 4B"), matches(isDisplayed()));
        onView(withText("Trip")).check(doesNotExist());
    }

    /**
     * BB-5: happy path for R18/R19. Tommy creates a group with Alex and renames it as owner;
     * Alex sees the new name but gets no Edit group action.
     */
    @Test
    public void ownerCreatesAndRenamesGroupAndMemberCannotEditIt() {
        logIn("tommy@usc.edu", "password");
        onView(withId(R.id.new_group_button)).perform(click());
        onView(withId(R.id.name_input)).perform(replaceText("Road Trip"));
        onView(withId(R.id.description_input)).perform(replaceText("Spring break"));
        addMemberEmail("alex@usc.edu");
        onView(withId(R.id.create_button)).perform(scrollTo(), click());

        waitFor(withText("Road Trip"), matches(isDisplayed()));
        waitFor(withId(R.id.action_edit_group), matches(isDisplayed()));
        onView(withId(R.id.action_edit_group)).perform(click());

        waitFor(withId(R.id.save_button), matches(isEnabled()));
        onView(withId(R.id.name_input)).perform(replaceText("Road Trip 2026"), closeSoftKeyboard());
        onView(withId(R.id.save_button)).perform(scrollTo(), click());
        // Wait to be back on the group page, so the match below is its title, not the form field.
        waitFor(withId(R.id.add_expense_button), matches(isDisplayed()));
        waitFor(withText("Road Trip 2026"), matches(isDisplayed()));

        // Log out and back in as Alex, a member who is not the owner.
        pressBack();
        waitFor(withId(R.id.action_profile), matches(isDisplayed()));
        onView(withId(R.id.action_profile)).perform(click());
        onView(withId(R.id.log_out_button)).perform(scrollTo(), click());
        onView(withId(android.R.id.button1)).perform(click());
        logIn("alex@usc.edu", "password");

        waitFor(withText("Road Trip 2026"), matches(isDisplayed()));
        onView(withText("Road Trip 2026")).perform(click());
        waitFor(withText("Spring break"), matches(isDisplayed()));
        // Give the owner check time to come back before asserting the action stays hidden.
        SystemClock.sleep(1000);
        onView(withId(R.id.action_edit_group)).check(doesNotExist());
    }

    private static void fillSignUp(String name, String email, String uscId, String password,
                                   String confirm) {
        onView(withId(R.id.name_input)).perform(scrollTo(), replaceText(name));
        onView(withId(R.id.email_input)).perform(scrollTo(), replaceText(email));
        onView(withId(R.id.usc_id_input)).perform(scrollTo(), replaceText(uscId));
        onView(withId(R.id.password_input)).perform(scrollTo(), replaceText(password));
        onView(withId(R.id.confirm_input)).perform(scrollTo(), replaceText(confirm),
                closeSoftKeyboard());
    }

    private static void logIn(String email, String password) {
        waitFor(withId(R.id.login_button), matches(isDisplayed()));
        onView(withId(R.id.email_input)).perform(replaceText(email));
        onView(withId(R.id.password_input)).perform(replaceText(password), closeSoftKeyboard());
        onView(withId(R.id.login_button)).perform(click());
        waitFor(withId(R.id.new_group_button), matches(isDisplayed()));
    }

    private static void logInExpectingFailure(String email, String password) {
        waitFor(withId(R.id.login_button), matches(isDisplayed()));
        onView(withId(R.id.email_input)).perform(replaceText(email));
        onView(withId(R.id.password_input)).perform(replaceText(password), closeSoftKeyboard());
        onView(withId(R.id.login_button)).perform(click());
        waitFor(withId(R.id.password_layout), matches(hasError("Wrong email or password")));
    }

    private static void addMemberEmail(String email) {
        onView(withId(R.id.member_email_input)).perform(scrollTo(), replaceText(email),
                closeSoftKeyboard());
        onView(withId(R.id.add_member_button)).perform(scrollTo(), click());
    }

    /**
     * Retries an assertion until it passes or times out. The app does its work on a background
     * thread that Espresso doesn't know to wait for, so results land a moment after a tap.
     */
    private static void waitFor(Matcher<View> view, ViewAssertion assertion) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (true) {
            try {
                onView(view).check(assertion);
                return;
            } catch (RuntimeException | AssertionError e) {
                if (SystemClock.uptimeMillis() > deadline) {
                    throw e;
                }
                SystemClock.sleep(100);
            }
        }
    }

    /** Matches a TextInputLayout showing exactly this error, or no error when null. */
    private static Matcher<View> hasError(String expected) {
        return new BoundedMatcher<View, TextInputLayout>(TextInputLayout.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("TextInputLayout with error: " + expected);
            }

            @Override
            protected boolean matchesSafely(TextInputLayout layout) {
                CharSequence error = layout.getError();
                return expected == null ? error == null : error != null
                        && expected.contentEquals(error);
            }
        };
    }
}
