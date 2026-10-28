package edu.usc.splitsmart.repository;

import androidx.annotation.Nullable;

import java.time.YearMonth;
import java.util.List;

import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.User;

/**
 * Where roles 3, 4, and 5 write their SQL. Every method is a stub until its owner fills it in,
 * so the project compiles while the three of them work in parallel.
 *
 * <p>Fill in your own block and leave the others alone. The table and column names are in
 * {@link SplitSmartDatabase}. Use {@code database.getWritableDatabase()} for writes, and wrap
 * anything that touches two tables in {@code beginTransaction} / {@code endTransaction} so a
 * half-saved expense can't happen.
 */
public final class SqliteGroupRepository implements GroupRepository {

    private final SplitSmartDatabase database;

    public SqliteGroupRepository(SplitSmartDatabase database) {
        this.database = database;
    }

    // -------------------------------------------------------------------------------------
    // Role 3: groups, membership, settlements
    // -------------------------------------------------------------------------------------

    @Override
    public void saveGroup(Group group, List<String> memberIds) {
        throw notImplemented(3);
    }

    @Override
    public void updateGroup(String groupId, String name, String description) {
        throw notImplemented(3);
    }

    @Override
    public void addMembers(String groupId, List<String> userIds) {
        throw notImplemented(3);
    }

    @Override
    public void deleteGroup(String groupId) {
        throw notImplemented(3);
    }

    @Nullable
    @Override
    public Group findGroupById(String groupId) {
        throw notImplemented(3);
    }

    @Override
    public List<Group> findGroupsByUser(String userId) {
        throw notImplemented(3);
    }

    @Override
    public List<User> findMembers(String groupId) {
        throw notImplemented(3);
    }

    @Override
    public boolean isMember(String groupId, String userId) {
        throw notImplemented(3);
    }

    @Override
    public boolean isOwner(String groupId, String userId) {
        throw notImplemented(3);
    }

    @Override
    public void saveSettlement(Settlement settlement) {
        throw notImplemented(3);
    }

    @Override
    public List<Settlement> findSettlementsByGroup(String groupId) {
        throw notImplemented(3);
    }

    // -------------------------------------------------------------------------------------
    // Role 4: expenses and shares
    // -------------------------------------------------------------------------------------

    @Override
    public void saveExpense(Expense expense) {
        throw notImplemented(4);
    }

    @Override
    public void replaceExpense(Expense expense) {
        throw notImplemented(4);
    }

    @Override
    public void deleteExpense(String expenseId) {
        throw notImplemented(4);
    }

    @Nullable
    @Override
    public Expense findExpenseById(String expenseId) {
        throw notImplemented(4);
    }

    @Override
    public List<Expense> findExpensesByGroup(String groupId) {
        throw notImplemented(4);
    }

    // -------------------------------------------------------------------------------------
    // Role 5: reporting reads
    // -------------------------------------------------------------------------------------

    @Override
    public List<Expense> findExpensesByGroupAndMonth(String groupId, YearMonth month) {
        throw notImplemented(5);
    }

    private static UnsupportedOperationException notImplemented(int role) {
        return new UnsupportedOperationException("Role " + role + " still has to write this");
    }
}
