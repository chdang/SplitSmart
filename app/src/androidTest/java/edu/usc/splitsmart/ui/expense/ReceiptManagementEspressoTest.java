package edu.usc.splitsmart.ui.expense;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.isInternal;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.NoActivityResumedException;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.intent.rule.IntentsRule;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import edu.usc.splitsmart.model.Receipt;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.Extras;

/**
 * Black-box UI tests for receipt management: attaching receipts while creating or editing an
 * expense, viewing and deleting them, and other group members viewing them read-only.
 *
 * <p>The system photo picker belongs to another app, so Espresso-Intents answers it with a
 * test image written to the app's cache. Everything after that goes through the screens.
 *
 * <p>SETUP: same as ExpenseManagementEspressoTest. Relies on the sample accounts (Tommy Trojan
 * and Alex Kim, password "password"); each test makes its own group with both of them.
 */
@RunWith(AndroidJUnit4.class)
public class ReceiptManagementEspressoTest {

    /** Records and stubs outgoing intents (the photo picker) for each test. */
    @Rule
    public final IntentsRule intents = new IntentsRule();

    private static final String TOMMY_EMAIL = "tommy@usc.edu";
    private static final String ALEX_EMAIL = "alex@usc.edu";
    private static final String PASSWORD = "password";
    // Generous: the first test also pays for opening the database and hashing passwords.
    private static final long WAIT_MS = 20000;
    /**
     * Handler calls get longer: the first login after a fresh install creates the database and
     * runs the deliberately slow password hashing, which can take well over 20 s on a busy host.
     */
    private static final long CALL_WAIT_MS = 60000;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<ActivityScenario<?>> scenarios = new ArrayList<>();
    private final List<File> testFiles = new ArrayList<>();

    private String groupId;
    private String tommyId;
    private String alexId;

    @Before
    public void setUp() throws Exception {
        // Other test classes delete the database file between tests, which leaves the app's
        // open connection pointing at a removed file. Reopen it so this class works in any order.
        instrumentation.runOnMainSync(() -> AppServices.init(context));
        alexId = loginAs(ALEX_EMAIL).getId();
        tommyId = loginAs(TOMMY_EMAIL).getId();   // Tommy is the user under test.
        Group group = call(cb -> AppServices.groups().createGroup(tommyId,
                "Receipt UI test " + System.nanoTime(), "created by UI test",
                Collections.singletonList(ALEX_EMAIL), cb));
        groupId = group.getId();
    }

    @After
    public void tearDown() {
        for (ActivityScenario<?> scenario : scenarios) {
            scenario.close();
        }
        for (File file : testFiles) {
            file.delete();
        }
    }

    // =====================================================================================
    // Attach while creating
    // =====================================================================================

    @Test
    public void attachReceiptWhileCreating_savedWithExpenseAndShownWhenReopened() throws Exception {
        String description = "Groceries " + System.nanoTime();
        pickerReturns(testImage(Color.RED));

        ActivityScenario<NewExpenseActivity> form = launch(NewExpenseActivity.class, null);
        waitForVisible(allOf(withId(R.id.save_button), isEnabled()));
        onView(withId(R.id.description_input)).perform(typeText(description), closeSoftKeyboard());
        onView(withId(R.id.amount_input)).perform(typeText("24.00"), closeSoftKeyboard());
        onView(withId(R.id.receipts_empty)).perform(scrollTo())
                .check(matches(withText(R.string.receipts_empty)));

        onView(withId(R.id.add_receipt_button)).perform(scrollTo(), click());
        waitForVisible(withContentDescription(thumbnail(1)));
        onView(withId(R.id.receipts_empty)).check(matches(not(isDisplayed())));

        onView(withId(R.id.save_button)).perform(scrollTo(), click());
        waitUntilClosed(form);

        // Reopen the saved expense: the receipt came along with it.
        launch(EditExpenseActivity.class, onlyExpenseId());
        waitForVisible(withContentDescription(thumbnail(1)));
        onView(withContentDescription(thumbnail(2))).check(doesNotExist());
    }

    // =====================================================================================
    // Attach, view, and delete while editing
    // =====================================================================================

    @Test
    public void creatorAttachesViewsAndDeletesReceipt_changesPersist() throws Exception {
        String expenseId = createExpenseAs(TOMMY_EMAIL, tommyId);
        pickerReturns(testImage(Color.BLUE));

        ActivityScenario<EditExpenseActivity> edit = launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withId(R.id.delete_button));   // creator controls are showing
        waitForVisible(withId(R.id.receipts_empty));

        onView(withId(R.id.add_receipt_button)).perform(scrollTo(), click());
        waitForVisible(withContentDescription(thumbnail(1)));

        // Close without pressing Save: the receipt was saved as soon as it was attached.
        edit.close();
        ActivityScenario<EditExpenseActivity> reopened =
                launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withId(R.id.delete_button));
        waitForVisible(withContentDescription(thumbnail(1)));

        // View it full size, then delete it from the viewer.
        onView(withContentDescription(thumbnail(1))).perform(scrollTo(), click());
        waitForVisible(withId(R.id.receipt_image));
        onView(withText(R.string.receipt_delete)).inRoot(isDialog()).perform(click());
        onView(withText(R.string.delete)).inRoot(isDialog()).perform(click());
        waitUntilGone(withContentDescription(thumbnail(1)));
        waitForVisible(withId(R.id.receipts_empty));

        reopened.close();
        launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withId(R.id.delete_button));
        waitForVisible(withId(R.id.receipts_empty));
        onView(withContentDescription(thumbnail(1))).check(doesNotExist());
    }

    @Test
    public void cancellingDeleteKeepsReceipt() throws Exception {
        String expenseId = createExpenseAs(TOMMY_EMAIL, tommyId);
        attachReceiptAs(TOMMY_EMAIL, tommyId, expenseId);

        launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withId(R.id.delete_button));
        waitForVisible(withContentDescription(thumbnail(1)));
        onView(withContentDescription(thumbnail(1))).perform(scrollTo(), click());
        onView(withText(R.string.receipt_delete)).inRoot(isDialog()).perform(click());
        onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click());

        onView(withContentDescription(thumbnail(1))).check(matches(isDisplayed()));
        assertEquals(1, receiptsOf(expenseId).size());
    }

    @Test
    public void cancellingPicker_attachesNothing() throws Exception {
        String expenseId = createExpenseAs(TOMMY_EMAIL, tommyId);
        intending(not(isInternal())).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null));

        launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withId(R.id.delete_button));
        onView(withId(R.id.add_receipt_button)).perform(scrollTo(), click());

        onView(withId(R.id.receipts_empty)).check(matches(isDisplayed()));
        assertTrue(receiptsOf(expenseId).isEmpty());
    }

    @Test
    public void pickingSomethingThatIsNotAnImage_showsFriendlyMessage() throws Exception {
        String expenseId = createExpenseAs(TOMMY_EMAIL, tommyId);
        File notAnImage = cacheFile("not-an-image.jpg", "hello".getBytes());
        pickerReturns(Uri.fromFile(notAnImage));

        launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withId(R.id.delete_button));
        onView(withId(R.id.add_receipt_button)).perform(scrollTo(), click());

        waitForVisible(withText(R.string.receipt_unreadable));
        onView(withId(R.id.receipts_empty)).check(matches(isDisplayed()));
        assertTrue(receiptsOf(expenseId).isEmpty());
    }

    // =====================================================================================
    // Other group members view receipts read-only
    // =====================================================================================

    @Test
    public void groupMemberViewsReceiptOnSharedExpense_cannotAddOrDelete() throws Exception {
        // Alex adds an expense and a receipt; Tommy (another member) opens it.
        String expenseId = createExpenseAs(ALEX_EMAIL, alexId);
        attachReceiptAs(ALEX_EMAIL, alexId, expenseId);

        launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withId(R.id.read_only_banner));
        waitForVisible(withContentDescription(thumbnail(1)));
        onView(withId(R.id.add_receipt_button)).check(matches(
                withEffectiveVisibility(ViewMatchers.Visibility.GONE)));

        onView(withContentDescription(thumbnail(1))).perform(scrollTo(), click());
        waitForVisible(withId(R.id.receipt_image));
        onView(withText(R.string.receipt_delete)).check(doesNotExist());
        onView(withText(R.string.receipt_close)).inRoot(isDialog()).perform(click());

        onView(withContentDescription(thumbnail(1))).check(matches(isDisplayed()));
        assertEquals(1, receiptsOf(expenseId).size());
    }

    // =====================================================================================
    // UI helpers
    // =====================================================================================

    private <A extends Activity> ActivityScenario<A> launch(Class<A> screen, String expenseId) {
        Intent intent = new Intent(context, screen).putExtra(Extras.GROUP_ID, groupId);
        if (expenseId != null) {
            intent.putExtra(Extras.EXPENSE_ID, expenseId);
        }
        ActivityScenario<A> scenario = ActivityScenario.launch(intent);
        scenarios.add(scenario);
        return scenario;
    }

    /** Answers the next photo-picker launch with {@code uri}, as if the user chose that photo. */
    private void pickerReturns(Uri uri) {
        intending(not(isInternal())).respondWith(new Instrumentation.ActivityResult(
                Activity.RESULT_OK, new Intent().setData(uri)));
    }

    private String thumbnail(int position) {
        return context.getString(R.string.receipt_thumbnail, position);
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

    private static void waitUntilGone(Matcher<View> matcher) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        Throwable last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(matcher).check(doesNotExist());
                return;
            } catch (AssertionError | NoActivityResumedException e) {
                last = e;
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
        throw new AssertionError("Screen did not close after saving");
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
        assertTrue("handler call timed out", latch.await(CALL_WAIT_MS, TimeUnit.MILLISECONDS));
        if (error.get() != null) {
            throw error.get();
        }
        return value.get();
    }

    private User loginAs(String email) throws Exception {
        return call(cb -> AppServices.session().login(email, PASSWORD, cb));
    }

    /** Logs in as {@code email}, adds an equal-split expense, then logs back in as Tommy. */
    private String createExpenseAs(String email, String userId) throws Exception {
        loginAs(email);
        Map<String, Long> allocations = new LinkedHashMap<>();
        allocations.put(tommyId, 0L);
        allocations.put(alexId, 0L);
        ExpenseDraft draft = new ExpenseDraft(groupId, userId, "Shared dinner", "", 3000L,
                Category.FOOD, SplitType.EQUAL, LocalDate.now(), allocations);
        Expense expense = call(cb -> AppServices.expenses().createExpense(userId, draft, cb));
        loginAs(TOMMY_EMAIL);
        return expense.getId();
    }

    private void attachReceiptAs(String email, String userId, String expenseId) throws Exception {
        loginAs(email);
        byte[] jpeg = jpeg(Color.GREEN);
        this.<List<Receipt>>call(cb -> AppServices.receipts().addReceipts(expenseId, userId,
                Collections.singletonList(jpeg), cb));
        loginAs(TOMMY_EMAIL);
    }

    private List<Receipt> receiptsOf(String expenseId) throws Exception {
        return call(cb -> AppServices.receipts().getReceipts(expenseId, cb));
    }

    /** The id of the group's only expense (the one the test just created through the UI). */
    private String onlyExpenseId() throws Exception {
        List<Expense> expenses = call(cb -> AppServices.expenses().getExpenses(groupId, cb));
        assertEquals(1, expenses.size());
        return expenses.get(0).getId();
    }

    private Uri testImage(int color) throws IOException {
        return Uri.fromFile(cacheFile("receipt-" + System.nanoTime() + ".jpg", jpeg(color)));
    }

    private static byte[] jpeg(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        return out.toByteArray();
    }

    private File cacheFile(String name, byte[] bytes) throws IOException {
        File file = new File(context.getCacheDir(), name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
        testFiles.add(file);
        return file;
    }
}
