package edu.usc.splitsmart.ui.expense;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtraWithKey;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
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
import java.io.OutputStream;
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
 * Black-box UI tests for scanning receipts with the camera, while creating or editing an
 * expense.
 *
 * <p>Google's document scanner runs inside Play services, which Espresso can't drive, so these
 * tests switch {@link ReceiptScanner} to its camera fallback. Espresso-Intents then plays the
 * camera app: it writes a test photo to the location the app asked for, as a real camera would.
 *
 * <p>SETUP: same as ReceiptManagementEspressoTest.
 */
@RunWith(AndroidJUnit4.class)
public class ReceiptScanningEspressoTest {

    @Rule
    public final IntentsRule intents = new IntentsRule();

    private static final String TOMMY_EMAIL = "tommy@usc.edu";
    private static final String ALEX_EMAIL = "alex@usc.edu";
    private static final String PASSWORD = "password";
    private static final long WAIT_MS = 20000;
    /**
     * Handler calls get longer: the first login after a fresh install creates the database and
     * runs the deliberately slow password hashing, which can take well over 20 s on a busy host.
     */
    private static final long CALL_WAIT_MS = 60000;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<ActivityScenario<?>> scenarios = new ArrayList<>();

    private String groupId;
    private String tommyId;
    private String alexId;

    @Before
    public void setUp() throws Exception {
        // Other test classes delete the database file between tests; reopen it.
        instrumentation.runOnMainSync(() -> AppServices.init(context));
        ReceiptScanner.useDocumentScanner = false;
        alexId = loginAs(ALEX_EMAIL).getId();
        tommyId = loginAs(TOMMY_EMAIL).getId();
        Group group = call(cb -> AppServices.groups().createGroup(tommyId,
                "Scan UI test " + System.nanoTime(), "created by UI test",
                Collections.singletonList(ALEX_EMAIL), cb));
        groupId = group.getId();
    }

    @After
    public void tearDown() {
        for (ActivityScenario<?> scenario : scenarios) {
            scenario.close();
        }
        ReceiptScanner.useDocumentScanner = true;
    }

    @Test
    public void scanWhileCreating_savedWithExpenseAndShownWhenReopened() throws Exception {
        cameraTakes(jpeg(Color.YELLOW));

        ActivityScenario<NewExpenseActivity> form = launch(NewExpenseActivity.class, null);
        waitForVisible(allOf(withId(R.id.save_button), isEnabled()));
        onView(withId(R.id.description_input)).perform(typeText("Scanned lunch"),
                closeSoftKeyboard());
        onView(withId(R.id.amount_input)).perform(typeText("18.00"), closeSoftKeyboard());

        onView(withId(R.id.scan_receipt_button)).perform(scrollTo(), click());
        waitForVisible(withContentDescription(thumbnail(1)));
        intended(allOf(hasAction(MediaStore.ACTION_IMAGE_CAPTURE),
                hasExtraWithKey(MediaStore.EXTRA_OUTPUT)));

        onView(withId(R.id.save_button)).perform(scrollTo(), click());
        waitUntilClosed(form);

        String expenseId = onlyExpenseId();
        assertEquals(1, receiptsOf(expenseId).size());
        launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withContentDescription(thumbnail(1)));
        onView(withContentDescription(thumbnail(2))).check(doesNotExist());
    }

    @Test
    public void scanWhileEditing_savesImmediatelyAsAViewableJpeg() throws Exception {
        String expenseId = createExpense();
        cameraTakes(jpeg(Color.CYAN));

        ActivityScenario<EditExpenseActivity> edit = launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withId(R.id.delete_button));
        onView(withId(R.id.scan_receipt_button)).perform(scrollTo(), click());
        waitForVisible(withContentDescription(thumbnail(1)));

        // Saved without pressing Save, and stored as a JPEG the viewer can open.
        List<Receipt> saved = receiptsOf(expenseId);
        assertEquals(1, saved.size());
        byte[] image = call(cb -> AppServices.receipts().getReceiptImage(saved.get(0).getId(), cb));
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xD8},
                new byte[]{image[0], image[1]});

        edit.close();
        launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withContentDescription(thumbnail(1)));
        onView(withContentDescription(thumbnail(1))).perform(scrollTo(), click());
        waitForVisible(withId(R.id.receipt_image));
    }

    @Test
    public void cancellingTheCamera_attachesNothing() throws Exception {
        String expenseId = createExpense();
        intending(hasAction(MediaStore.ACTION_IMAGE_CAPTURE)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null));

        launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withId(R.id.delete_button));
        onView(withId(R.id.scan_receipt_button)).perform(scrollTo(), click());

        onView(withId(R.id.receipts_empty)).check(matches(isDisplayed()));
        assertTrue(receiptsOf(expenseId).isEmpty());
    }

    @Test
    public void cameraThatSavesNothing_attachesNothing() throws Exception {
        String expenseId = createExpense();
        // Reports success but never writes the photo, which some camera apps do.
        intending(hasAction(MediaStore.ACTION_IMAGE_CAPTURE)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_OK, null));

        launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withId(R.id.delete_button));
        onView(withId(R.id.scan_receipt_button)).perform(scrollTo(), click());

        onView(withId(R.id.receipts_empty)).check(matches(isDisplayed()));
        assertTrue(receiptsOf(expenseId).isEmpty());
    }

    @Test
    public void memberWhoDidNotCreateTheExpense_cannotScan() throws Exception {
        loginAs(ALEX_EMAIL);
        String expenseId = createExpense();
        loginAs(TOMMY_EMAIL);

        launch(EditExpenseActivity.class, expenseId);
        waitForVisible(withId(R.id.read_only_banner));
        onView(withId(R.id.scan_receipt_button)).check(matches(
                withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
    }

    // =====================================================================================
    // UI helpers
    // =====================================================================================

    /** Plays the camera app: writes {@code photo} where the app asked, then reports success. */
    private void cameraTakes(byte[] photo) {
        intending(hasAction(MediaStore.ACTION_IMAGE_CAPTURE)).respondWithFunction(intent -> {
            Uri output = intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
            try (OutputStream out = context.getContentResolver().openOutputStream(output)) {
                out.write(photo);
            } catch (Exception e) {
                throw new AssertionError("Could not write the camera photo", e);
            }
            return new Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent());
        });
    }

    private <A extends Activity> ActivityScenario<A> launch(Class<A> screen, String expenseId) {
        Intent intent = new Intent(context, screen).putExtra(Extras.GROUP_ID, groupId);
        if (expenseId != null) {
            intent.putExtra(Extras.EXPENSE_ID, expenseId);
        }
        ActivityScenario<A> scenario = ActivityScenario.launch(intent);
        scenarios.add(scenario);
        return scenario;
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

    /** An equal split between Tommy and Alex, created by whoever is logged in. */
    private String createExpense() throws Exception {
        String creatorId = AppServices.session().getCurrentUser().getId();
        Map<String, Long> allocations = new LinkedHashMap<>();
        allocations.put(tommyId, 0L);
        allocations.put(alexId, 0L);
        ExpenseDraft draft = new ExpenseDraft(groupId, creatorId, "Shared dinner", "", 3000L,
                Category.FOOD, SplitType.EQUAL, LocalDate.now(), allocations);
        Expense expense = call(cb -> AppServices.expenses().createExpense(creatorId, draft, cb));
        return expense.getId();
    }

    private List<Receipt> receiptsOf(String expenseId) throws Exception {
        return call(cb -> AppServices.receipts().getReceipts(expenseId, cb));
    }

    private String onlyExpenseId() throws Exception {
        List<Expense> expenses = call(cb -> AppServices.expenses().getExpenses(groupId, cb));
        assertEquals(1, expenses.size());
        return expenses.get(0).getId();
    }

    private static byte[] jpeg(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        return out.toByteArray();
    }
}
