package edu.usc.splitsmart.ui.balance;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.DropdownAdapter;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.common.GroupScopedActivity;
import edu.usc.splitsmart.ui.common.Money;

/**
 * Records that the current user paid another member outside the app (R16). Can be opened with
 * {@link Extras#RECEIVER_ID} and {@link Extras#AMOUNT_CENTS} to fill in the form.
 */
public class SettleUpActivity extends GroupScopedActivity {

    private final List<User> others = new ArrayList<>();
    private final Map<String, Long> balances = new HashMap<>();
    private User receiver;

    private TextInputLayout receiverLayout;
    private AutoCompleteTextView receiverInput;
    private TextView balanceHint;
    private ColorStateList neutralColor;
    private TextInputLayout amountLayout;
    private TextInputEditText amountInput;
    private Button recordButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settle_up);
        setUpToolbar(getString(R.string.settle_up_title), true);

        receiverLayout = findViewById(R.id.receiver_layout);
        receiverInput = findViewById(R.id.receiver_input);
        balanceHint = findViewById(R.id.balance_hint);
        neutralColor = balanceHint.getTextColors();
        amountLayout = findViewById(R.id.amount_layout);
        amountInput = findViewById(R.id.amount_input);
        recordButton = findViewById(R.id.record_button);

        receiverInput.setOnItemClickListener((parent, view, position, id) ->
                selectReceiver(others.get(position)));
        recordButton.setOnClickListener(v -> record());
        recordButton.setEnabled(false);

        startGroupScreen();
    }

    @Override
    protected void onMemberVerified() {
        AppServices.groups().getMembers(groupId, onUi(members -> {
            others.clear();
            for (User member : members) {
                if (!member.getId().equals(currentUser.getId())) {
                    others.add(member);
                }
            }
            AppServices.balances().getIndividualBalances(groupId, currentUser.getId(),
                    onUi(result -> {
                        setLoading(false);
                        balances.clear();
                        balances.putAll(result);
                        showReceivers();
                    }, this::closeWithError));
        }, this::closeWithError));
    }

    private void showReceivers() {
        if (others.isEmpty()) {
            closeWithMessage(getString(R.string.settle_up_no_one));
            return;
        }
        List<String> names = new ArrayList<>();
        for (User other : others) {
            names.add(other.getName());
        }
        receiverInput.setAdapter(new DropdownAdapter(this, names));
        recordButton.setEnabled(true);

        String receiverId = getIntent().getStringExtra(Extras.RECEIVER_ID);
        for (User other : others) {
            if (other.getId().equals(receiverId)) {
                receiverInput.setText(other.getName(), false);
                selectReceiver(other);
            }
        }
        long amount = getIntent().getLongExtra(Extras.AMOUNT_CENTS, 0);
        if (amount > 0) {
            amountInput.setText(Money.toInput(amount));
        }
    }

    private void selectReceiver(User selected) {
        receiver = selected;
        receiverLayout.setError(null);
        long net = balances.containsKey(selected.getId()) ? balances.get(selected.getId()) : 0;
        String name = selected.getName();
        if (net < 0) {
            balanceHint.setText(getString(R.string.settle_up_you_owe, name, Money.format(-net)));
            balanceHint.setTextColor(ContextCompat.getColor(this, R.color.money_you_owe));
            if (textOf(amountInput).isEmpty()) {
                amountInput.setText(Money.toInput(-net));
            }
        } else if (net > 0) {
            balanceHint.setText(getString(R.string.settle_up_they_owe, name, Money.format(net)));
            balanceHint.setTextColor(ContextCompat.getColor(this, R.color.money_owed_to_you));
        } else {
            balanceHint.setText(getString(R.string.settle_up_even, name));
            balanceHint.setTextColor(neutralColor);
        }
        balanceHint.setVisibility(View.VISIBLE);
    }

    private void record() {
        receiverLayout.setError(null);
        amountLayout.setError(null);
        Long amount = Money.parseCents(amountInput.getText());
        boolean valid = true;
        if (receiver == null) {
            receiverLayout.setError(getString(R.string.settle_up_pick_receiver));
            valid = false;
        }
        if (amount == null || amount <= 0) {
            amountLayout.setError(getString(R.string.error_amount_invalid));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setLoading(true);
        recordButton.setEnabled(false);
        AppServices.expenses().recordSettlement(groupId, currentUser.getId(), receiver.getId(),
                amount, onUi(settlement -> {
                    Toast.makeText(this, R.string.settle_up_recorded, Toast.LENGTH_SHORT).show();
                    finish();
                }, error -> {
                    setLoading(false);
                    recordButton.setEnabled(true);
                    showError(error);
                }));
    }
}
