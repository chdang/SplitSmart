package edu.usc.splitsmart.ui.group;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.ui.balance.SettleUpActivity;
import edu.usc.splitsmart.ui.balance.TotalGroupBalanceActivity;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.common.GroupScopedActivity;
import edu.usc.splitsmart.ui.common.Money;
import edu.usc.splitsmart.ui.expense.EditExpenseActivity;
import edu.usc.splitsmart.ui.expense.NewExpenseActivity;
import edu.usc.splitsmart.ui.report.HistoryActivity;
import edu.usc.splitsmart.ui.report.MonthlySummaryActivity;

/**
 * The group page: the user's balance, the expense list (R8), and the way to every other group
 * screen. The owner also gets an Edit group button (R18).
 */
public class GroupDetailActivity extends GroupScopedActivity {

    private ExpenseListAdapter adapter;
    private boolean isOwner;

    private TextView description;
    private TextView payableAmount;
    private TextView receivableAmount;
    private View emptyView;

    private final ActivityResultLauncher<Intent> editGroupLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Extras.RESULT_GROUP_DELETED) {
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);
        setUpToolbar(null, true);

        description = findViewById(R.id.group_description);
        payableAmount = findViewById(R.id.payable_amount);
        receivableAmount = findViewById(R.id.receivable_amount);
        emptyView = findViewById(R.id.empty_view);

        findViewById(R.id.balance_card).setOnClickListener(v ->
                open(TotalGroupBalanceActivity.class));
        findViewById(R.id.settle_up_button).setOnClickListener(v ->
                open(SettleUpActivity.class));
        findViewById(R.id.history_button).setOnClickListener(v -> open(HistoryActivity.class));
        findViewById(R.id.summary_button).setOnClickListener(v ->
                open(MonthlySummaryActivity.class));
        findViewById(R.id.add_expense_button).setOnClickListener(v ->
                open(NewExpenseActivity.class));

        startGroupScreen();
    }

    @Override
    protected void onMemberVerified() {
        adapter = new ExpenseListAdapter(currentUser.getId(), this::openExpense);
        RecyclerView list = findViewById(R.id.expense_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        loadGroup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Expenses, balances, or the group itself may have changed on another screen (R14).
        if (memberVerified) {
            setLoading(true);
            loadGroup();
        }
    }

    private void loadGroup() {
        String userId = currentUser.getId();
        AppServices.groups().getGroup(groupId, onUi(group -> {
            showGroup(group);
            AppServices.groups().authenticateOwner(groupId, userId, onUi(owner -> {
                isOwner = Boolean.TRUE.equals(owner);
                invalidateOptionsMenu();
            }));
            AppServices.balances().getTotalPayable(groupId, userId, onUi(cents ->
                    payableAmount.setText(Money.format(cents))));
            AppServices.balances().getTotalReceivable(groupId, userId, onUi(cents ->
                    receivableAmount.setText(Money.format(cents))));
            AppServices.groups().getMembers(groupId, onUi(members ->
                    AppServices.groups().getGroupExpenses(groupId, onUi(expenses -> {
                        setLoading(false);
                        adapter.submit(members, expenses);
                        emptyView.setVisibility(expenses.isEmpty() ? View.VISIBLE : View.GONE);
                    }))));
        }, this::closeWithError));
    }

    private void showGroup(Group group) {
        setToolbarTitle(group.getName());
        description.setText(group.getDescription());
        description.setVisibility(group.getDescription().isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_group_detail, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_edit_group).setVisible(isOwner);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_edit_group) {
            editGroupLauncher.launch(new Intent(this, EditGroupActivity.class)
                    .putExtra(Extras.GROUP_ID, groupId));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void open(Class<?> screen) {
        if (memberVerified) {
            startActivity(new Intent(this, screen).putExtra(Extras.GROUP_ID, groupId));
        }
    }

    private void openExpense(Expense expense) {
        startActivity(new Intent(this, EditExpenseActivity.class)
                .putExtra(Extras.GROUP_ID, groupId)
                .putExtra(Extras.EXPENSE_ID, expense.getId()));
    }
}
