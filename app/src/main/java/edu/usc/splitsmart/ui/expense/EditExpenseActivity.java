package edu.usc.splitsmart.ui.expense;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.ExpenseDraft;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.Extras;

/**
 * EditExpenseForm: shows an expense. Its creator can edit or delete it; everyone else sees it
 * read-only (R7, R14).
 */
public class EditExpenseActivity extends ExpenseFormActivity {

    private String expenseId;
    private Expense expense;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        expenseId = getIntent().getStringExtra(Extras.EXPENSE_ID);
        super.onCreate(savedInstanceState);
        if (expenseId == null) {
            finish();
            return;
        }
        deleteButton.setOnClickListener(v -> confirmDelete());
    }

    @Override
    protected int titleRes() {
        return R.string.expense_details;
    }

    @Override
    protected void onFormReady(boolean restored) {
        // Read-only until the creator check below says otherwise.
        setEditable(false);
        AppServices.expenses().getExpense(expenseId, onUi(loaded -> {
            expense = loaded;
            if (!restored) {
                fill(loaded);
            }
            AppServices.expenses().authenticateCreator(expenseId, currentUser.getId(),
                    onUi(isCreator -> {
                        setLoading(false);
                        showPermissions(Boolean.TRUE.equals(isCreator));
                    }, this::closeWithError));
        }, this::closeWithError));
    }

    private void showPermissions(boolean isCreator) {
        setEditable(isCreator);
        deleteButton.setVisibility(isCreator ? View.VISIBLE : View.GONE);
        if (isCreator) {
            setToolbarTitle(getString(R.string.edit_expense));
            readOnlyBanner.setVisibility(View.GONE);
        } else {
            readOnlyBanner.setText(getString(R.string.expense_read_only, creatorName()));
            readOnlyBanner.setVisibility(View.VISIBLE);
        }
    }

    private String creatorName() {
        for (User member : members) {
            if (member.getId().equals(expense.getCreatorId())) {
                return member.getName();
            }
        }
        return getString(R.string.history_former_member);
    }

    @Override
    protected void submit(ExpenseDraft draft) {
        AppServices.expenses().updateExpense(expenseId, currentUser.getId(), draft,
                onUi(updated -> {
                    Toast.makeText(this, R.string.expense_updated, Toast.LENGTH_SHORT).show();
                    finish();
                }, error -> {
                    setSaving(false);
                    showError(error);
                }));
    }

    private void confirmDelete() {
        if (expense == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.expense_delete_title)
                .setMessage(getString(R.string.expense_delete_message, expense.getDescription()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> delete())
                .show();
    }

    private void delete() {
        setSaving(true);
        AppServices.expenses().deleteExpense(expenseId, currentUser.getId(), onUi(done -> {
            Toast.makeText(this, R.string.expense_deleted, Toast.LENGTH_SHORT).show();
            finish();
        }, error -> {
            setSaving(false);
            showError(error);
        }));
    }
}
