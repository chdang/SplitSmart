package edu.usc.splitsmart.logic;

import java.time.YearMonth;
import java.util.List;

import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.model.User;

/** Creates and manages groups (R8, R17-R19). */
public interface GroupHandler {

    /**
     * Creates a group owned by {@code ownerId}, with the owner and every email as members.
     * Fails with MISSING_ACCOUNTS (listing the emails) if any email has no account, or
     * INVALID_INPUT if the name is blank.
     */
    void createGroup(String ownerId, String name, String description, List<String> emails,
                     Callback<Group> callback);

    /** Fails with NOT_FOUND if the group doesn't exist. */
    void getGroup(String groupId, Callback<Group> callback);

    /** Every group the user is a member of, newest first. */
    void getGroups(String userId, Callback<List<Group>> callback);

    /** The group's members, in the order they joined. */
    void getMembers(String groupId, Callback<List<User>> callback);

    /** Every expense in the group, newest expense date first (R8). */
    void getGroupExpenses(String groupId, Callback<List<Expense>> callback);

    /** Expense count, total spending, and spending per category for one month (R17). */
    void getMonthlyStatistics(String groupId, YearMonth month,
                              Callback<MonthlyStatistics> callback);

    /**
     * Changes the name and description and adds the members in {@code memberEmails}. Existing
     * members stay. Fails with NOT_OWNER if {@code actorId} isn't the owner, or MISSING_ACCOUNTS
     * if an email has no account (R18).
     */
    void updateGroup(String groupId, String actorId, String name, String description,
                     List<String> memberEmails, Callback<Group> callback);

    /**
     * Deletes the group with all its expenses and settlements. Fails with NOT_OWNER if
     * {@code actorId} isn't the owner (R18).
     */
    void deleteGroup(String groupId, String actorId, Callback<Void> callback);

    /** Returns true if the user owns the group. */
    void authenticateOwner(String groupId, String userId, Callback<Boolean> callback);
}
