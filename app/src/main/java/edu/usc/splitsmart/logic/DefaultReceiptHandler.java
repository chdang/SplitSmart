package edu.usc.splitsmart.logic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Receipt;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.repository.GroupRepository;
import edu.usc.splitsmart.repository.ReceiptRepository;

/** Checks who may touch an expense's receipts, then reads or writes them through the repository. */
public final class DefaultReceiptHandler implements ReceiptHandler {
    private final GroupRepository groups;
    private final ReceiptRepository receipts;
    private final SessionHandler session;

    public DefaultReceiptHandler(GroupRepository groups, ReceiptRepository receipts,
                                 SessionHandler session) {
        this.groups = groups;
        this.receipts = receipts;
        this.session = session;
    }

    @Override
    public void addReceipts(String expenseId, String actorId, List<byte[]> images,
                            Callback<List<Receipt>> callback) {
        // Copy now, on the caller's thread, so later changes to the list can't race the save.
        List<byte[]> copy = images == null ? null : new ArrayList<>(images);
        withCurrentUser(currentId -> {
            Expense expense = requireExpense(expenseId);
            requireCreator(expense, currentId, actorId);
            if (copy == null || copy.isEmpty()) {
                throw error(SplitSmartException.Code.INVALID_RECEIPT);
            }
            for (byte[] image : copy) {
                if (image == null || image.length == 0 || image.length > MAX_IMAGE_BYTES) {
                    throw error(SplitSmartException.Code.INVALID_RECEIPT);
                }
            }
            return receipts.saveReceipts(expenseId, currentId, copy, Instant.now());
        }, callback);
    }

    @Override
    public void getReceipts(String expenseId, Callback<List<Receipt>> callback) {
        withCurrentUser(currentId -> {
            Expense expense = requireExpense(expenseId);
            requireMember(expense.getGroupId(), currentId);
            return receipts.findReceiptsByExpense(expenseId);
        }, callback);
    }

    @Override
    public void getReceiptImage(String receiptId, Callback<byte[]> callback) {
        withCurrentUser(currentId -> {
            Receipt receipt = requireReceipt(receiptId);
            requireMember(requireExpense(receipt.getExpenseId()).getGroupId(), currentId);
            byte[] image = receipts.findImage(receiptId);
            if (image == null) {
                throw error(SplitSmartException.Code.NOT_FOUND);
            }
            return image;
        }, callback);
    }

    @Override
    public void deleteReceipt(String receiptId, String actorId, Callback<Void> callback) {
        withCurrentUser(currentId -> {
            Receipt receipt = requireReceipt(receiptId);
            requireCreator(requireExpense(receipt.getExpenseId()), currentId, actorId);
            receipts.deleteReceipt(receiptId);
            return null;
        }, callback);
    }

    private Expense requireExpense(String expenseId) throws SplitSmartException {
        Expense expense = expenseId == null ? null : groups.findExpenseById(expenseId);
        if (expense == null) {
            throw error(SplitSmartException.Code.NOT_FOUND);
        }
        return expense;
    }

    private Receipt requireReceipt(String receiptId) throws SplitSmartException {
        Receipt receipt = receiptId == null ? null : receipts.findReceiptById(receiptId);
        if (receipt == null) {
            throw error(SplitSmartException.Code.NOT_FOUND);
        }
        return receipt;
    }

    private void requireMember(String groupId, String userId) throws SplitSmartException {
        if (!groups.isMember(groupId, userId)) {
            throw error(SplitSmartException.Code.NOT_A_MEMBER);
        }
    }

    /** Same rule as editing the expense: the actor must be the logged-in creator, still a member. */
    private void requireCreator(Expense expense, String currentId, String actorId)
            throws SplitSmartException {
        if (!currentId.equals(actorId) || !expense.getCreatorId().equals(currentId)) {
            throw error(SplitSmartException.Code.NOT_CREATOR);
        }
        requireMember(expense.getGroupId(), currentId);
    }

    private <T> void withCurrentUser(UserWork<T> work, Callback<T> callback) {
        // Read the session on the caller's thread, as DefaultExpenseHandler does.
        User current = session.getCurrentUser();
        Async.run(() -> {
            if (current == null) {
                throw error(SplitSmartException.Code.NOT_LOGGED_IN);
            }
            return work.run(current.getId());
        }, callback);
    }

    private interface UserWork<T> {
        T run(String currentId) throws Exception;
    }

    private static SplitSmartException error(SplitSmartException.Code code) {
        return new SplitSmartException(code);
    }
}
