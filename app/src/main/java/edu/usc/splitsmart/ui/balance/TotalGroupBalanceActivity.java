package edu.usc.splitsmart.ui.balance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.common.GroupScopedActivity;
import edu.usc.splitsmart.ui.common.Money;

/** TotalGroupBalanceView: how much the user owes and is owed in the group overall (R12). */
public class TotalGroupBalanceActivity extends GroupScopedActivity {

    private TextView payableAmount;
    private TextView receivableAmount;
    private TextView netSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_total_balance);
        setUpToolbar(getString(R.string.balances_title), true);

        payableAmount = findViewById(R.id.payable_amount);
        receivableAmount = findViewById(R.id.receivable_amount);
        netSummary = findViewById(R.id.net_summary);
        findViewById(R.id.individual_button).setOnClickListener(v ->
                openGroupScreen(IndividualGroupBalanceActivity.class));
        findViewById(R.id.settle_button).setOnClickListener(v ->
                openGroupScreen(SettleUpActivity.class));

        startGroupScreen();
    }

    @Override
    protected void onMemberVerified() {
        loadTotals();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Balances change after recording a payment on another screen (R14).
        if (memberVerified) {
            setLoading(true);
            loadTotals();
        }
    }

    private void loadTotals() {
        String userId = currentUser.getId();
        AppServices.balances().getTotalPayable(groupId, userId, onUi(payable ->
                AppServices.balances().getTotalReceivable(groupId, userId, onUi(receivable -> {
                    setLoading(false);
                    showTotals(payable, receivable);
                }))));
    }

    private void showTotals(long payable, long receivable) {
        payableAmount.setText(Money.format(payable));
        receivableAmount.setText(Money.format(receivable));
        long net = receivable - payable;
        if (net > 0) {
            netSummary.setText(getString(R.string.balances_net_owed, Money.format(net)));
        } else if (net < 0) {
            netSummary.setText(getString(R.string.balances_net_owe, Money.format(-net)));
        } else {
            netSummary.setText(R.string.balances_net_even);
        }
    }

    private void openGroupScreen(Class<?> screen) {
        startActivity(new Intent(this, screen).putExtra(Extras.GROUP_ID, groupId));
    }
}
