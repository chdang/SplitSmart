package edu.usc.splitsmart;

import android.content.Context;

import edu.usc.splitsmart.logic.DefaultGroupHandler;
import edu.usc.splitsmart.logic.DefaultExpenseHandler;
import edu.usc.splitsmart.logic.DefaultMemberAuthenticationHandler;
import edu.usc.splitsmart.logic.DefaultSessionHandler;
import edu.usc.splitsmart.logic.DefaultUpdateUserNameHandler;
import edu.usc.splitsmart.logic.DefaultUserGroupBalanceHandler;
import edu.usc.splitsmart.logic.DefaultUserSignUpHandler;
import edu.usc.splitsmart.logic.ExpenseHandler;
import edu.usc.splitsmart.logic.GroupHandler;
import edu.usc.splitsmart.logic.MemberAuthenticationHandler;
import edu.usc.splitsmart.logic.SessionHandler;
import edu.usc.splitsmart.logic.UpdateUserNameHandler;
import edu.usc.splitsmart.logic.UserGroupBalanceHandler;
import edu.usc.splitsmart.logic.UserSignUpHandler;
import edu.usc.splitsmart.repository.GroupRepository;
import edu.usc.splitsmart.repository.SessionStore;
import edu.usc.splitsmart.repository.SplitSmartDatabase;
import edu.usc.splitsmart.repository.SqliteGroupRepository;
import edu.usc.splitsmart.repository.SqliteUserRepository;
import edu.usc.splitsmart.repository.UserRepository;

/**
 * The one place screens get their handlers from. Accounts, groups, expenses, and settlements
 * use SQLite. Role 5's balances and history still use a separate in-memory sample store and
 * do not yet reflect real expense changes.
 */
public final class AppServices {

    // Role 2: real
    private static SessionHandler session;
    private static UserSignUpHandler signUp;
    private static UpdateUserNameHandler userNames;

    // Role 3
    private static GroupHandler groupHandler;
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
        GroupRepository groups = new SqliteGroupRepository(database);
        SessionStore sessions = new SessionStore(context);

        session = new DefaultSessionHandler(users, sessions);
        signUp = new DefaultUserSignUpHandler(users);
        userNames = new DefaultUpdateUserNameHandler(users, sessions);

        groupHandler = new DefaultGroupHandler(users, groups, session);
        membership = new DefaultMemberAuthenticationHandler(groups);

        expenses = new DefaultExpenseHandler(groups, session);
        balances = new DefaultUserGroupBalanceHandler(groupHandler, membership, expenses, groups);
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
        return groupHandler;
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
        return groupHandler instanceof DefaultGroupHandler;
    }
}
