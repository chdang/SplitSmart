package edu.usc.splitsmart;

import edu.usc.splitsmart.fake.FakeExpenseHandler;
import edu.usc.splitsmart.fake.FakeGroupHandler;
import edu.usc.splitsmart.fake.FakeMemberAuthenticationHandler;
import edu.usc.splitsmart.fake.FakeSessionHandler;
import edu.usc.splitsmart.fake.FakeStore;
import edu.usc.splitsmart.fake.FakeUpdateUserNameHandler;
import edu.usc.splitsmart.fake.FakeUserGroupBalanceHandler;
import edu.usc.splitsmart.fake.FakeUserSignUpHandler;
import edu.usc.splitsmart.logic.ExpenseHandler;
import edu.usc.splitsmart.logic.GroupHandler;
import edu.usc.splitsmart.logic.MemberAuthenticationHandler;
import edu.usc.splitsmart.logic.SessionHandler;
import edu.usc.splitsmart.logic.UpdateUserNameHandler;
import edu.usc.splitsmart.logic.UserGroupBalanceHandler;
import edu.usc.splitsmart.logic.UserSignUpHandler;

/**
 * The one place screens get their handlers from. Each handler starts as a fake backed by
 * in-memory sample data. When an owner merges a real handler, they change only its line here,
 * and every screen starts using it.
 *
 * <p>The fakes share one store, so a fake handler won't know about users or groups that a real
 * handler created. Expect odd results while some handlers are real and others are still fake.
 */
public final class AppServices {
    private static final FakeStore FAKE_STORE = new FakeStore();

    // Role 2
    private static final SessionHandler SESSION = new FakeSessionHandler(FAKE_STORE);
    private static final UserSignUpHandler SIGN_UP = new FakeUserSignUpHandler(FAKE_STORE);
    private static final UpdateUserNameHandler USER_NAMES =
            new FakeUpdateUserNameHandler(FAKE_STORE);

    // Role 3
    private static final GroupHandler GROUPS = new FakeGroupHandler(FAKE_STORE);
    private static final MemberAuthenticationHandler MEMBERSHIP =
            new FakeMemberAuthenticationHandler(FAKE_STORE);

    // Role 4
    private static final ExpenseHandler EXPENSES = new FakeExpenseHandler(FAKE_STORE);

    // Role 5
    private static final UserGroupBalanceHandler BALANCES =
            new FakeUserGroupBalanceHandler(FAKE_STORE);

    private AppServices() {
    }

    public static SessionHandler session() {
        return SESSION;
    }

    public static UserSignUpHandler signUp() {
        return SIGN_UP;
    }

    public static UpdateUserNameHandler userNames() {
        return USER_NAMES;
    }

    public static GroupHandler groups() {
        return GROUPS;
    }

    public static MemberAuthenticationHandler membership() {
        return MEMBERSHIP;
    }

    public static ExpenseHandler expenses() {
        return EXPENSES;
    }

    public static UserGroupBalanceHandler balances() {
        return BALANCES;
    }

    /** True while login still uses fake accounts, so the login screen can show the demo login. */
    public static boolean usingFakeAccounts() {
        return SESSION instanceof FakeSessionHandler;
    }
}
