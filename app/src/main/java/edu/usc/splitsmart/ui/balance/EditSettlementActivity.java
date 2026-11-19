package edu.usc.splitsmart.ui.balance;

import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.Dates;
import edu.usc.splitsmart.ui.common.DropdownAdapter;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.common.GroupScopedActivity;
import edu.usc.splitsmart.ui.common.Labels;
import edu.usc.splitsmart.ui.common.Money;

/**
 * Shows one recorded payment in full. The member who recorded it can edit or delete it;
 * everyone else sees it read-only (R16).
 */
public class EditSettlementActivity extends GroupScopedActivity {

    private static final String STATE_PAYER = "payer";
    private static final String STATE_RECEIVER = "receiver";

    private final List<User> members = new ArrayList<>();
    /** Non-null only while coming back from a rotation, so stored values don't overwrite edits. */
    private Bundle restoredState;
    private String settlementId;
    private Settlement settlement;
    private User payer;
    private User receiver;

    private TextView readOnlyBanner;
    private TextInputLayout payerLayout;
    private AutoCompleteTextView payerInput;
    private TextInputLayout receiverLayout;
    private AutoCompleteTextView receiverInput;
    private TextInputLayout amountLayout;
    private TextInputEditText amountInput;
    private TextInputLayout noteLayout;
    private TextInputEditText noteInput;
    private TextView recordedOn;
    private Button saveButton;
    private Button deleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoredState = savedInstanceState;
        setContentView(R.layout.activity_edit_settlement);
        setUpToolbar(getString(R.string.settlement_details), true);

        settlementId = getIntent().getStringExtra(Extras.SETTLEMENT_ID);
        if (settlementId == null) {
            finish();
            return;
        }

        readOnlyBanner = findViewById(R.id.read_only_banner);
        payerLayout = findViewById(R.id.payer_layout);
        payerInput = findViewById(R.id.payer_input);
        receiverLayout = findViewById(R.id.receiver_layout);
        receiverInput = findViewById(R.id.receiver_input);
        amountLayout = findViewById(R.id.amount_layout);
        amountInput = findViewById(R.id.amount_input);
        noteLayout = findViewById(R.id.note_layout);
        noteInput = findViewById(R.id.note_input);
        recordedOn = findViewById(R.id.recorded_on);
        saveButton = findViewById(R.id.save_button);
        deleteButton = findViewById(R.id.delete_button);

        // These show values this class tracks itself, so it restores them after rotation.
        payerInput.setSaveEnabled(false);
        receiverInput.setSaveEnabled(false);

        payerInput.setOnItemClickListener((parent, view, position, id) ->
                selectPayer(members.get(position)));
        receiverInput.setOnItemClickListener((parent, view, position, id) ->
                selectReceiver(members.get(position)));
        saveButton.setOnClickListener(v -> save());
        deleteButton.setOnClickListener(v -> confirmDelete());

        startGroupScreen();
    }

    @Override
    protected void onMemberVerified() {
        // Read-only until the recorder check below says otherwise.
        setEditable(false);
        AppServices.groups().getMembers(groupId, onUi(list -> {
            members.clear();
            members.addAll(list);
            List<String> names = new ArrayList<>();
            for (User member : members) {
                names.add(Labels.nameMarkingYou(this, member, currentUser.getId()));
            }
            payerInput.setAdapter(new DropdownAdapter(this, names));
            receiverInput.setAdapter(new DropdownAdapter(this, names));
            loadSettlement();
        }, this::closeWithError));
    }

    private void loadSettlement() {
        AppServices.expenses().getSettlement(settlementId, onUi(loaded -> {
            settlement = loaded;
            fill(loaded);
            AppServices.expenses().authenticateRecorder(settlementId, currentUser.getId(),
                    onUi(isRecorder -> {
                        setLoading(false);
                        showPermissions(Boolean.TRUE.equals(isRecorder));
                    }, this::closeWithError));
        }, this::closeWithError));
    }

    private void fill(Settlement loaded) {
        recordedOn.setText(getString(R.string.settlement_recorded_on,
                Dates.shortDate(loaded.getSettledAt())));
        if (restoredState != null) {
            // The text fields restored themselves; only the two pickers need their value back.
            selectPayer(memberById(restoredState.getString(STATE_PAYER)));
            selectReceiver(memberById(restoredState.getString(STATE_RECEIVER)));
            return;
        }
        selectPayer(memberById(loaded.getPayerId()));
        selectReceiver(memberById(loaded.getReceiverId()));
        amountInput.setText(Money.toInput(loaded.getAmountCents()));
        noteInput.setText(loaded.getNote());
    }

    private void showPermissions(boolean isRecorder) {
        setEditable(isRecorder);
        deleteButton.setVisibility(isRecorder ? View.VISIBLE : View.GONE);
        if (isRecorder) {
            setToolbarTitle(getString(R.string.settlement_edit));
            readOnlyBanner.setVisibility(View.GONE);
        } else {
            readOnlyBanner.setText(getString(R.string.settlement_read_only, recorderName()));
            readOnlyBanner.setVisibility(View.VISIBLE);
        }
    }

    private String recorderName() {
        User recorder = memberById(settlement.getRecorderId());
        return recorder != null ? recorder.getName()
                : getString(R.string.history_former_member);
    }

    private void setEditable(boolean canEdit) {
        payerLayout.setEnabled(canEdit);
        receiverLayout.setEnabled(canEdit);
        amountLayout.setEnabled(canEdit);
        noteLayout.setEnabled(canEdit);
        saveButton.setVisibility(canEdit ? View.VISIBLE : View.GONE);
    }

    private void setSaving(boolean saving) {
        setLoading(saving);
        saveButton.setEnabled(!saving);
        deleteButton.setEnabled(!saving);
    }

    private void selectPayer(User selected) {
        payer = selected;
        payerLayout.setError(null);
        if (selected != null) {
            payerInput.setText(Labels.nameMarkingYou(this, selected, currentUser.getId()), false);
        }
    }

    private void selectReceiver(User selected) {
        receiver = selected;
        receiverLayout.setError(null);
        if (selected != null) {
            receiverInput.setText(Labels.nameMarkingYou(this, selected, currentUser.getId()),
                    false);
        }
    }

    private User memberById(String userId) {
        for (User member : members) {
            if (member.getId().equals(userId)) {
                return member;
            }
        }
        return null;
    }

    private void save() {
        payerLayout.setError(null);
        receiverLayout.setError(null);
        amountLayout.setError(null);
        Long amount = Money.parseCents(amountInput.getText());
        boolean valid = true;
        if (payer == null) {
            payerLayout.setError(getString(R.string.settlement_pick_payer));
            valid = false;
        }
        if (receiver == null) {
            receiverLayout.setError(getString(R.string.settlement_pick_receiver));
            valid = false;
        }
        if (payer != null && receiver != null && payer.getId().equals(receiver.getId())) {
            receiverLayout.setError(getString(R.string.settlement_same_person));
            valid = false;
        }
        if (amount == null || amount <= 0) {
            amountLayout.setError(getString(R.string.error_amount_invalid));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setSaving(true);
        AppServices.expenses().updateSettlement(settlementId, currentUser.getId(), payer.getId(),
                receiver.getId(), amount, textOf(noteInput), onUi(updated -> {
                    Toast.makeText(this, R.string.settlement_updated, Toast.LENGTH_SHORT).show();
                    finish();
                }, error -> {
                    setSaving(false);
                    showError(error);
                }));
    }

    private void confirmDelete() {
        if (settlement == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settlement_delete_title)
                .setMessage(getString(R.string.settlement_delete_message,
                        Money.format(settlement.getAmountCents())))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> delete())
                .show();
    }

    private void delete() {
        setSaving(true);
        AppServices.expenses().deleteSettlement(settlementId, currentUser.getId(), onUi(done -> {
            Toast.makeText(this, R.string.settlement_deleted, Toast.LENGTH_SHORT).show();
            finish();
        }, error -> {
            setSaving(false);
            showError(error);
        }));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (payer != null) {
            outState.putString(STATE_PAYER, payer.getId());
        }
        if (receiver != null) {
            outState.putString(STATE_RECEIVER, receiver.getId());
        }
    }
}
