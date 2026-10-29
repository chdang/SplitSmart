package edu.usc.splitsmart.logic;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Comparator;

import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.GroupRepository;
import edu.usc.splitsmart.repository.UserRepository;

/** Creates and manages REAL groups (R8, R17-R19). */
public class DefaultGroupHandler implements GroupHandler{

    private final UserRepository users;

    private final GroupRepository groups;

    public DefaultGroupHandler(UserRepository users, GroupRepository groups) {
        this.users = users;
        this.groups = groups;
    }

    /**
     * Creates a group owned by {@code ownerId}, with the owner and every email as members.
     * Fails with MISSING_ACCOUNTS (listing the emails) if any email has no account, or
     * INVALID_INPUT if the name is blank.
     */
    @Override
    public void createGroup(String ownerId, String name, String description, List<String> emails,
                     Callback<Group> callback) {
        Async.run(() -> {
            // check all emails belong to existing accounts
            List<String> missing = new ArrayList<>();
            Set<String> memberIds = new LinkedHashSet<>();

            memberIds.add(ownerId);

            for (String email : emails) {
                User u = users.findByEmail(email);
                if (u == null) missing.add(email);
                else memberIds.add(u.getId());
            }

            if(!missing.isEmpty()){
                throw new SplitSmartException(SplitSmartException.Code.MISSING_ACCOUNTS, missing);
            }

            Instant createdAt = Instant.now();
            return groups.saveGroup(name, description, ownerId, createdAt, new ArrayList<>(memberIds));
        }, callback);
    }

    /** Fails with NOT_FOUND if the group doesn't exist. */
    @Override
    public void getGroup(String groupId, Callback<Group> callback){
        Async.run(() -> {
            Group g = groups.findGroupById(groupId);
            if(g == null) {
                throw new SplitSmartException(SplitSmartException.Code.NOT_FOUND);
            }
            else return g;
        }, callback);
    }

    /** Every group the user is a member of, newest first. */
    @Override
    public void getGroups(String userId, Callback<List<Group>> callback) {
        Async.run(() -> {
            List<Group> g = groups.findGroupsByUser(userId);
            g.sort(Comparator.comparing(Group::getCreatedAt).reversed());
            return g;
        }, callback);
    }

    /** The group's members, in the order they joined. */
    @Override
    public void getMembers(String groupId, Callback<List<User>> callback) {
        Async.run(() -> {
            return groups.findMembers(groupId);
        }, callback);
    }

    /** Every expense in the group, newest expense date first (R8). */
    @Override
    public void getGroupExpenses(String groupId, Callback<List<Expense>> callback) {
        throw notImplemented(4);
    }

    /** Expense count, total spending, and spending per category for one month (R17). */
    @Override
    public void getMonthlyStatistics(String groupId, YearMonth month,
                              Callback<MonthlyStatistics> callback){
        throw notImplemented(5);
    }

    /**
     * Changes the name and description and adds the members in {@code memberEmails}. Existing
     * members stay. Fails with NOT_OWNER if {@code actorId} isn't the owner, or MISSING_ACCOUNTS
     * if an email has no account (R18).
     */
    @Override
    public void updateGroup(String groupId, String actorId, String name, String description,
                     List<String> memberEmails, Callback<Group> callback){
        Async.run(() -> {
            Group g = groups.findGroupById(groupId);

            // verify actor is owner
            if(g != null && !g.getOwnerId().equals(actorId)) {
                throw new SplitSmartException(SplitSmartException.Code.NOT_FOUND);
            }

            // check member emails are linked to accounts
            List<String> missing = new ArrayList<>();
            Set<String> memberIds = new LinkedHashSet<>();

            for (String email : memberEmails) {
                User u = users.findByEmail(email);
                if (u == null) missing.add(email);
                else memberIds.add(u.getId());
            }

            if(!missing.isEmpty()){
                throw new SplitSmartException(SplitSmartException.Code.MISSING_ACCOUNTS, missing);
            }

            Group ug = groups.updateGroup(groupId, name, description);
            groups.addMembers(groupId, new ArrayList<>(memberIds));

            return ug;
        }, callback);
    }

    /**
     * Deletes the group with all its expenses and settlements. Fails with NOT_OWNER if
     * {@code actorId} isn't the owner (R18).
     */
    @Override
    public void deleteGroup(String groupId, String actorId, Callback<Void> callback) {
        Async.run(() -> {
            Group g = groups.findGroupById(groupId);

            // verify actor is owner
            if(g != null && !g.getOwnerId().equals(actorId)) {
                throw new SplitSmartException(SplitSmartException.Code.NOT_FOUND);
            }

            groups.deleteGroup(groupId);
            return null;
        }, callback);
    }

    /** Returns true if the user owns the group. Fails with NOT_FOUND if no group found.*/
    @Override
    public void authenticateOwner(String groupId, String userId, Callback<Boolean> callback){
        Async.run(() -> {
            Group g = groups.findGroupById(groupId);

            if(g == null) {
                throw new SplitSmartException(SplitSmartException.Code.NOT_FOUND);
            }

            // verify actor is owner
            return g.getOwnerId().equals(userId);
        }, callback);
    }

    private static UnsupportedOperationException notImplemented(int role) {
        return new UnsupportedOperationException("Role " + role + " still has to write this");
    }
}


