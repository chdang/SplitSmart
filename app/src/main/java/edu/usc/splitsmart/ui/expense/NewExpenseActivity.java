package edu.usc.splitsmart.ui.expense;

import android.widget.Toast;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.ExpenseDraft;

/** NewExpenseForm: adds an expense to the group (R6, R9-R11). */
public class NewExpenseActivity extends ExpenseFormActivity {

    @Override
    protected int titleRes() {
        return R.string.add_expense;
    }

    @Override
    protected void onFormReady(boolean restored) {
        setLoading(false);
        if (!restored) {
            // Most expenses are paid by whoever enters them and shared by everyone.
            selectPayer(currentUser);
            checkEveryone();
        }
    }

    @Override
    protected void submit(ExpenseDraft draft) {
        AppServices.expenses().createExpense(currentUser.getId(), draft, onUi(expense -> {
            Toast.makeText(this, R.string.expense_added, Toast.LENGTH_SHORT).show();
            finish();
        }, error -> {
            setSaving(false);
            showError(error);
        }));
    }
}
