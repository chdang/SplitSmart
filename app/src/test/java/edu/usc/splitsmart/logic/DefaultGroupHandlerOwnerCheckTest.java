package edu.usc.splitsmart.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;

import java.util.Collections;
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
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.SplitSmartDatabase;
import edu.usc.splitsmart.repository.SqliteGroupRepository;
import edu.usc.splitsmart.repository.SqliteUserRepository;

/** Owner checks for editing and deleting groups (R18), against the seeded SQLite data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class DefaultGroupHandlerOwnerCheckTest {
    /** Seeded group owned by Tommy; Alex is a member but not the owner. */
    private static final String GROUP = "g-apartment";
    private static final String TOMMY = "u-tommy";
    private static final String ALEX = "u-alex";
    private static final String MISSING = "g-does-not-exist";

    private Context context;
    private SplitSmartDatabase database;
    private SqliteGroupRepository groups;
    private DefaultGroupHandler handler;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("splitsmart.db");
        database = new SplitSmartDatabase(context);
        groups = new SqliteGroupRepository(database);
        SqliteUserRepository users = new SqliteUserRepository(database);
        handler = new DefaultGroupHandler(users, groups,
                new DefaultSessionHandler(users, new SessionStore(context)));
    }

    @After
    public void tearDown() {
        database.close();
        context.deleteDatabase("splitsmart.db");
    }

    @Test
    public void updateByNonOwnerFailsWithNotOwnerAndChangesNothing() {
        Group before = groups.findGroupById(GROUP);

        assertError(SplitSmartException.Code.NOT_OWNER, DefaultGroupHandlerOwnerCheckTest.<Group>await(
                cb -> handler.updateGroup(GROUP, ALEX, "Renamed", "Changed",
                        Collections.emptyList(), cb)));

        Group after = groups.findGroupById(GROUP);
        assertEquals(before.getName(), after.getName());
        assertEquals(before.getDescription(), after.getDescription());
    }

    @Test
    public void updateOfMissingGroupFailsWithNotFound() {
        assertError(SplitSmartException.Code.NOT_FOUND, DefaultGroupHandlerOwnerCheckTest.<Group>await(
                cb -> handler.updateGroup(MISSING, TOMMY, "Name", "Description",
                        Collections.emptyList(), cb)));
    }

    @Test
    public void updateByOwnerStillSucceeds() {
        Group updated = DefaultGroupHandlerOwnerCheckTest.<Group>await(
                cb -> handler.updateGroup(GROUP, TOMMY, "Renamed", "Changed",
                        Collections.emptyList(), cb)).value();

        assertEquals("Renamed", updated.getName());
        assertEquals("Renamed", groups.findGroupById(GROUP).getName());
    }

    @Test
    public void deleteByNonOwnerFailsWithNotOwnerAndKeepsGroup() {
        assertError(SplitSmartException.Code.NOT_OWNER, DefaultGroupHandlerOwnerCheckTest.<Void>await(
                cb -> handler.deleteGroup(GROUP, ALEX, cb)));

        assertNotNull(groups.findGroupById(GROUP));
    }

    @Test
    public void deleteOfMissingGroupFailsWithNotFound() {
        assertError(SplitSmartException.Code.NOT_FOUND, DefaultGroupHandlerOwnerCheckTest.<Void>await(
                cb -> handler.deleteGroup(MISSING, TOMMY, cb)));
    }

    @Test
    public void deleteByOwnerStillSucceeds() {
        DefaultGroupHandlerOwnerCheckTest.<Void>await(
                cb -> handler.deleteGroup(GROUP, TOMMY, cb)).value();

        assertNull(groups.findGroupById(GROUP));
    }

    private static void assertError(SplitSmartException.Code code, Result<?> result) {
        assertTrue("Expected " + code + " but got " + result.error,
                result.error instanceof SplitSmartException);
        assertEquals(code, ((SplitSmartException) result.error).getCode());
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
