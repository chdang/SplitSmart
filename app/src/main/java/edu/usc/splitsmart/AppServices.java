package edu.usc.splitsmart;

import android.content.Context;

import edu.usc.splitsmart.fake.FakeExpenseHandler;
import edu.usc.splitsmart.fake.FakeGroupHandler;
import edu.usc.splitsmart.fake.FakeMemberAuthenticationHandler;
import edu.usc.splitsmart.fake.FakeStore;
import edu.usc.splitsmart.fake.FakeUserGroupBalanceHandler;
import edu.usc.splitsmart.logic.DefaultSessionHandler;
import edu.usc.splitsmart.logic.DefaultUpdateUserNameHandler;
import edu.usc.splitsmart.logic.DefaultUserSignUpHandler;
import edu.usc.splitsmart.logic.ExpenseHandler;
import edu.usc.splitsmart.logic.GroupHandler;
import edu.usc.splitsmart.logic.MemberAuthenticationHandler;
import edu.usc.splitsmart.logic.SessionHandler;
import edu.usc.splitsmart.logic.UpdateUserNameHandler;
import edu.usc.splitsmart.logic.UserGroupBalanceHandler;
import edu.usc.splitsmart.logic.UserSignUpHandler;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.SplitSmartDatabase;
import edu.usc.splitsmart.repository.SqliteUserRepository;
import edu.usc.splitsmart.repository.UserRepository;

/**
 * The one place screens get their handlers from. Accounts are real and backed by the database.
 * The rest are still fakes on in-memory sample data; when an owner merges a real handler, they
 * change only its line in {@link #init} and every screen starts using it.
 *
 * <p>While some handlers are real and others are fake, the two sides line up only because the
 * seeded accounts use the same ids on both. An account created through sign-up exists in the
 * database but not in the fake store, so its group screens stay empty until roles 3 to 5 land.
 * Log in as one of the sample accounts to see populated screens.
 */
public final class AppServices {

    // Role 2: real
    private static SessionHandler session;
    private static UserSignUpHandler signUp;
    private static UpdateUserNameHandler userNames;

    // Role 3
    private static GroupHandler groups;
    private static MemberAuthenticationHandler membership;

    // Role 4
    private static ExpenseHandler expenses;

    // Role 5
    private static UserGroupBalanceHandler balances;

    private AppServices() {
    }

    /** Called once from {@link SplitSmartApplication}, before any screen opens. */
    public static void init(Context context) {
        SplitSmartDatabase database = new SplitSmartDatabase(context);
        UserRepository users = new SqliteUserRepository(database);
        SessionStore sessions = new SessionStore(context);

        session = new DefaultSessionHandler(users, sessions);
        signUp = new DefaultUserSignUpHandler(users);
        userNames = new DefaultUpdateUserNameHandler(users, sessions);

        FakeStore fakeStore = new FakeStore();
        groups = new FakeGroupHandler(fakeStore);
        membership = new FakeMemberAuthenticationHandler(fakeStore);
        expenses = new FakeExpenseHandler(fakeStore);
        balances = new FakeUserGroupBalanceHandler(fakeStore);
    }

    public static SessionHandler session() {
        return session;
    }

    public static UserSignUpHandler signUp() {
        return signUp;
    }

    public static UpdateUserNameHandler userNames() {
        return userNames;
    }

    public static GroupHandler groups() {
        return groups;
    }

    public static MemberAuthenticationHandler membership() {
        return membership;
    }

    public static ExpenseHandler expenses() {
        return expenses;
    }

    public static UserGroupBalanceHandler balances() {
        return balances;
    }

    /**
     * True while the app still runs on seeded sample data, so the login screen can show the
     * demo accounts. Goes false once the last fake handler is replaced.
     */
    public static boolean usingFakeAccounts() {
        return groups instanceof FakeGroupHandler;
    }
}
