package edu.usc.splitsmart.logic;

import java.util.List;
import java.util.Map;

/**
 * Works out who owes whom from a group's expenses and settlements (R12-R15). Balances are
 * calculated fresh on every call, so edits, deletes, and settlements show up right away (R14).
 */
public interface UserGroupBalanceHandler {

    /**
     * The user's net balance with every other member, keyed by member id. Positive means that
     * member owes the user; negative means the user owes them; 0 means they're settled up (R13).
     */
    void getIndividualBalances(String groupId, String userId,
                               Callback<Map<String, Long>> callback);

    /** Total cents the user owes, after netting each pair (R12). */
    void getTotalPayable(String groupId, String userId, Callback<Long> callback);

    /** Total cents owed to the user, after netting each pair (R12). */
    void getTotalReceivable(String groupId, String userId, Callback<Long> callback);

    /** Every expense and settlement in the group, newest first (R15). */
    void getHistory(String groupId, Callback<List<ActivityItem>> callback);
}
