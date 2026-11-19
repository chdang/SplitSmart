package edu.usc.splitsmart.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.SplitSmartDatabase;
import edu.usc.splitsmart.repository.SqliteGroupRepository;
import edu.usc.splitsmart.repository.SqliteUserRepository;

/**
 * White-box tests for user and group management (Project 2.4, Darius). Inputs are picked from
 * the branches in {@link DefaultUserSignUpHandler#register} and {@link DefaultGroupHandler}'s
 * createGroup / updateGroup, and run against the real seeded SQLite repositories.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class UserGroupManagementUnitTest {
    /** Seeded accounts and group, from SampleData. */
    private static final String TOMMY = "u-tommy";
    private static final String ALEX = "u-alex";
    private static final String APARTMENT = "g-apartment";

    private Context context;
    private SplitSmartDatabase database;
    private SqliteUserRepository users;
    private SqliteGroupRepository groups;
    private SessionStore sessions;
    private DefaultUserSignUpHandler signUp;
    private DefaultGroupHandler groupHandler;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("splitsmart.db");
        database = new SplitSmartDatabase(context);
        users = new SqliteUserRepository(database);
        groups = new SqliteGroupRepository(database);
        sessions = new SessionStore(context);
        sessions.clear();
        signUp = new DefaultUserSignUpHandler(users);
        groupHandler = new DefaultGroupHandler(users, groups,
                new DefaultSessionHandler(users, sessions));
    }

    @After
    public void tearDown() {
        database.close();
        context.deleteDatabase("splitsmart.db");
    }

    /**
     * Covers the trim/lowercase normalization and both duplicate branches, including that the
     * email check runs before the USC ID check when both are taken.
     */
    @Test
    public void registerNormalizesInputAndChecksDuplicateEmailBeforeUscId() {
        // Both the email (in another case, padded) and the USC ID belong to Tommy.
        assertError(SplitSmartException.Code.DUPLICATE_EMAIL, UserGroupManagementUnitTest.<User>await(
                cb -> signUp.register("Someone", "  Tommy@USC.EDU ", "1234567890", "password", cb)));

        // Only the USC ID (padded with spaces) is taken.
        assertError(SplitSmartException.Code.DUPLICATE_USC_ID, UserGroupManagementUnitTest.<User>await(
                cb -> signUp.register("Someone", "someone@usc.edu", " 1234567890 ", "password", cb)));

        // Neither is taken: the account is saved trimmed and lowercased, with a hashed password.
        User created = UserGroupManagementUnitTest.<User>await(
                cb -> signUp.register("  New Person ", " NEW@Usc.Edu ", " 1111111111 ", "secret123",
                        cb)).value();
        assertEquals("New Person", created.getName());
        assertEquals("new@usc.edu", created.getEmail());
        assertEquals("1111111111", created.getUscId());
        assertNotNull(users.findByEmail("new@usc.edu"));
        assertNotEquals("secret123", users.findPasswordHash(created.getId()));
    }

    /**
     * MC/DC on the four-part INVALID_INPUT guard: each condition is made true on its own while
     * the other three hold, so each one is shown to reject by itself.
     */
    @Test
    public void registerRejectsEachInvalidFieldOnItsOwn() {
        String[][] cases = {
                // name, email, uscId, password
                {"   ", "a@usc.edu", "2222222222", "password"},        // blank name
                {"Ann", "a@usc.edu", "   ", "password"},              // blank USC ID
                {"Ann", "a@usc.edu", "2222222222", ""},               // empty password
                {"Ann", "a@usc.edu.com", "2222222222", "password"},   // not an @usc.edu email
                {"Ann", "a@gmail.com", "2222222222", "password"},     // not an @usc.edu email
        };
        for (String[] c : cases) {
            assertError(SplitSmartException.Code.INVALID_INPUT, UserGroupManagementUnitTest.<User>await(
                    cb -> signUp.register(c[0], c[1], c[2], c[3], cb)));
        }
        assertNull("No case may create an account", users.findByEmail("a@usc.edu"));
    }

    /**
     * The member loop with 0, 1, and many emails. The owner is always a member, and repeated
     * emails (including the owner's own, in another case) are only added once.
     */
    @Test
    public void createGroupAddsOwnerOnceAndDeduplicatesEmails() {
        Group solo = UserGroupManagementUnitTest.<Group>await(cb -> groupHandler.createGroup(
                TOMMY, "Solo", "", Collections.emptyList(), cb)).value();
        assertEquals(TOMMY, solo.getOwnerId());
        assertEquals(set(TOMMY), memberIds(solo.getId()));

        Group pair = UserGroupManagementUnitTest.<Group>await(cb -> groupHandler.createGroup(
                TOMMY, "Pair", "", Collections.singletonList("alex@usc.edu"), cb)).value();
        assertEquals(set(TOMMY, ALEX), memberIds(pair.getId()));

        Group many = UserGroupManagementUnitTest.<Group>await(cb -> groupHandler.createGroup(
                TOMMY, "Many", "desc", Arrays.asList("TOMMY@usc.edu", "alex@usc.edu",
                        "Alex@USC.edu", "sam@usc.edu"), cb)).value();
        assertEquals(3, groups.findMembers(many.getId()).size());
        assertEquals(set(TOMMY, ALEX, "u-sam"), memberIds(many.getId()));
    }

    /**
     * Exception paths of createGroup: every unknown email is reported (known ones are not),
     * nothing is saved, and a blank or whitespace-only name is rejected.
     */
    @Test
    public void createGroupReportsAllMissingEmailsAndRejectsBlankNames() {
        int before = groups.findGroupsByUser(TOMMY).size();

        SplitSmartException missing = assertError(SplitSmartException.Code.MISSING_ACCOUNTS,
                UserGroupManagementUnitTest.<Group>await(cb -> groupHandler.createGroup(TOMMY,
                        "Trip", "", Arrays.asList("ghost@usc.edu", "alex@usc.edu",
                                "nobody@usc.edu"), cb)));
        assertEquals(Arrays.asList("ghost@usc.edu", "nobody@usc.edu"), missing.getDetails());

        assertError(SplitSmartException.Code.INVALID_INPUT, UserGroupManagementUnitTest.<Group>await(
                cb -> groupHandler.createGroup(TOMMY, "", "", Collections.emptyList(), cb)));
        assertError(SplitSmartException.Code.INVALID_INPUT, UserGroupManagementUnitTest.<Group>await(
                cb -> groupHandler.createGroup(TOMMY, "   ", "", Collections.emptyList(), cb)));

        assertEquals("No failed call may save a group", before,
                groups.findGroupsByUser(TOMMY).size());
    }

    /**
     * updateGroup's add-members branch when an email already belongs to a member. The doc says
     * existing members stay, so this must succeed and leave each member listed once.
     */
    @Test
    public void updateGroupWithExistingMemberEmailKeepsEachMemberOnce() {
        sessions.setCurrentUser(users.findByEmail("tommy@usc.edu"));
        Set<String> before = memberIds(APARTMENT);

        Group updated = UserGroupManagementUnitTest.<Group>await(cb -> groupHandler.updateGroup(
                APARTMENT, TOMMY, "Apartment 4B (2026)", "Rent",
                Arrays.asList("alex@usc.edu", "priya@usc.edu"), cb)).value();

        assertEquals("Apartment 4B (2026)", updated.getName());
        Set<String> expected = new HashSet<>(before);
        expected.add("u-priya");
        assertEquals(expected, memberIds(APARTMENT));
        assertEquals(expected.size(), groups.findMembers(APARTMENT).size());
    }

    private Set<String> memberIds(String groupId) {
        Set<String> ids = new HashSet<>();
        for (User member : groups.findMembers(groupId)) {
            ids.add(member.getId());
        }
        return ids;
    }

    private static Set<String> set(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static SplitSmartException assertError(SplitSmartException.Code code, Result<?> result) {
        assertTrue("Expected " + code + " but got " + result.error,
                result.error instanceof SplitSmartException);
        SplitSmartException error = (SplitSmartException) result.error;
        assertEquals(code, error.getCode());
        return error;
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
