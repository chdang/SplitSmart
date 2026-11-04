package edu.usc.splitsmart.ui.balance;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
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
 * Records a payment made between the current user and another member outside the app (R16).
 * Either side can record it, so the direction is a choice rather than always "I paid them". Can
 * be opened with {@link Extras#RECEIVER_ID} and {@link Extras#AMOUNT_CENTS} to fill in the form.
 */
public class SettleUpActivity extends GroupScopedActivity {

    private final List<User> others = new ArrayList<>();
    private final Map<String, Long> balances = new HashMap<>();
    private User other;
    /** The amount this screen filled in, so a later suggestion can replace it but not typing. */
    private String suggestedAmount = "";

    private TextInputLayout otherLayout;
    private AutoCompleteTextView otherInput;
    private TextView balanceHint;
    private ColorStateList neutralColor;
    private MaterialButtonToggleGroup directionGroup;
    private TextInputLayout amountLayout;
    private TextInputEditText amountInput;
    private TextInputEditText noteInput;
    private Button recordButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settle_up);
        setUpToolbar(getString(R.string.settle_up_title), true);

        otherLayout = findViewById(R.id.other_layout);
        otherInput = findViewById(R.id.other_input);
        balanceHint = findViewById(R.id.balance_hint);
        neutralColor = balanceHint.getTextColors();
        directionGroup = findViewById(R.id.direction_group);
        amountLayout = findViewById(R.id.amount_layout);
        amountInput = findViewById(R.id.amount_input);
        noteInput = findViewById(R.id.note_input);
        recordButton = findViewById(R.id.record_button);

        otherInput.setOnItemClickListener((parent, view, position, id) ->
                selectOther(others.get(position)));
        directionGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                suggestAmount();
            }
        });
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
                        showMembers();
                    }, this::closeWithError));
        }, this::closeWithError));
    }

    private void showMembers() {
        if (others.isEmpty()) {
            closeWithMessage(getString(R.string.settle_up_no_one));
            return;
        }
        List<String> names = new ArrayList<>();
        for (User member : others) {
            names.add(member.getName());
        }
        otherInput.setAdapter(new DropdownAdapter(this, names));
        recordButton.setEnabled(true);

        String receiverId = getIntent().getStringExtra(Extras.RECEIVER_ID);
        for (User member : others) {
            if (member.getId().equals(receiverId)) {
                otherInput.setText(member.getName(), false);
                selectOther(member);
            }
        }
        long amount = getIntent().getLongExtra(Extras.AMOUNT_CENTS, 0);
        if (amount > 0) {
            suggestedAmount = Money.toInput(amount);
            amountInput.setText(suggestedAmount);
        }
    }

    private void selectOther(User selected) {
        other = selected;
        otherLayout.setError(null);
        long net = netWith(selected);
        String name = selected.getName();
        if (net < 0) {
            balanceHint.setText(getString(R.string.settle_up_you_owe, name, Money.format(-net)));
            balanceHint.setTextColor(ContextCompat.getColor(this, R.color.money_you_owe));
        } else if (net > 0) {
            balanceHint.setText(getString(R.string.settle_up_they_owe, name, Money.format(net)));
            balanceHint.setTextColor(ContextCompat.getColor(this, R.color.money_owed_to_you));
        } else {
            balanceHint.setText(getString(R.string.settle_up_even, name));
            balanceHint.setTextColor(neutralColor);
        }
        balanceHint.setVisibility(View.VISIBLE);
        suggestAmount();
    }

    /** Positive means the other member owes the current user; negative means the reverse. */
    private long netWith(User member) {
        Long net = balances.get(member.getId());
        return net == null ? 0 : net;
    }

    /**
     * Fills in the outstanding debt for the chosen direction. Anything the user typed is left
     * alone; only an empty box or this screen's own earlier suggestion gets replaced.
     */
    private void suggestAmount() {
        String typed = textOf(amountInput);
        if (other == null || (!typed.isEmpty() && !typed.equals(suggestedAmount))) {
            return;
        }
        long net = netWith(other);
        long owed = iPaid() ? Math.max(-net, 0) : Math.max(net, 0);
        suggestedAmount = owed > 0 ? Money.toInput(owed) : "";
        amountInput.setText(suggestedAmount);
    }

    /** True when the current user is the one who handed over the money. */
    private boolean iPaid() {
        return directionGroup.getCheckedButtonId() != R.id.direction_they_paid;
    }

    private void record() {
        otherLayout.setError(null);
        amountLayout.setError(null);
        Long amount = Money.parseCents(amountInput.getText());
        boolean valid = true;
        if (other == null) {
            otherLayout.setError(getString(R.string.settle_up_pick_other));
            valid = false;
        }
        if (amount == null || amount <= 0) {
            amountLayout.setError(getString(R.string.error_amount_invalid));
            valid = false;
        }
        if (!valid) {
            return;
        }

        String payerId = iPaid() ? currentUser.getId() : other.getId();
        String receiverId = iPaid() ? other.getId() : currentUser.getId();
        setLoading(true);
        recordButton.setEnabled(false);
        AppServices.expenses().recordSettlement(groupId, payerId, receiverId,
                amount, textOf(noteInput), onUi(settlement -> {
                    Toast.makeText(this, R.string.settle_up_recorded, Toast.LENGTH_SHORT).show();
                    finish();
                }, error -> {
                    setLoading(false);
                    recordButton.setEnabled(true);
                    showError(error);
                }));
    }
}
