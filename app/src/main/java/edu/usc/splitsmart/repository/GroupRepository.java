package edu.usc.splitsmart.repository;

import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.User;

/**
 * Reads and writes everything inside a group: the group itself, its members, its expenses and
 * shares, and its settlements. Roles 3, 4, and 5 each own the block of methods marked for them,
 * so they can work in {@link SqliteGroupRepository} without colliding.
 *
 * <p>Like {@link UserRepository}, every method blocks on the database and is called off the
 * main thread. Methods here do no permission checking at all; handlers decide who is allowed to
 * do what before calling in.
 *
 * <p>Changing a signature here affects two other roles, so raise it with them first.
 */
public interface GroupRepository {


    // -------------------------------------------------------------------------------------
    // Role 3: groups, membership, settlements
    // -------------------------------------------------------------------------------------

    /** Saves a new group along with a membership row for each id, in the order given. */
    Group saveGroup(String name, String description, String ownerId, Instant createdAt, List<String> memberIds);

    /** Changes the name and description of an existing group (R18). */
    void updateGroup(String groupId, String name, String description);

    /** Adds members to a group. Ids that are already members are left alone (R18). */
    void addMembers(String groupId, List<String> userIds);

    /** Deletes the group with its members, expenses, shares, and settlements (R18). */
    void deleteGroup(String groupId);

    @Nullable
    Group findGroupById(String groupId);

    /** Every group the user belongs to, newest first. */
    List<Group> findGroupsByUser(String userId);

    /** The group's members, in the order they joined. */
    List<User> findMembers(String groupId);

    boolean isMember(String groupId, String userId);

    boolean isOwner(String groupId, String userId);

    /** Records a payment one member made to another outside the app (R16). */
    Settlement saveSettlement(String groupId, String recorderId, String payerId, String receiverId, Long amountCents, Instant settledAt);

    /** Every settlement in the group. Role 5 nets these against the expenses. */
    List<Settlement> findSettlementsByGroup(String groupId);

    // -------------------------------------------------------------------------------------
    // Role 4: expenses and shares
    // -------------------------------------------------------------------------------------

    /** Saves an expense and its shares together, so a failure leaves neither behind (R6). */
    void saveExpense(Expense expense);

    /** Replaces an existing expense and swaps its old shares for the new ones (R7). */
    void replaceExpense(Expense expense);

    /** Deletes the expense and its shares (R7). */
    void deleteExpense(String expenseId);

    /** The expense with its shares attached, or null if it doesn't exist. */
    @Nullable
    Expense findExpenseById(String expenseId);

    /** Every expense in the group with its shares, newest expense date first (R8). */
    List<Expense> findExpensesByGroup(String groupId);

    // -------------------------------------------------------------------------------------
    // Role 5: reporting reads
    // -------------------------------------------------------------------------------------

    /**
     * The group's expenses whose expense date falls in {@code month}, newest first. Used by the
     * monthly summary (R17), which goes by when the money was spent, not when it was entered.
     */
    List<Expense> findExpensesByGroupAndMonth(String groupId, YearMonth month);
}
