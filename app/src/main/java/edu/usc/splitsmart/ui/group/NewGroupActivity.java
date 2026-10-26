package edu.usc.splitsmart.ui.group;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.SplitSmartException;
import edu.usc.splitsmart.ui.common.BaseActivity;
import edu.usc.splitsmart.ui.common.ErrorMessages;
import edu.usc.splitsmart.ui.common.Extras;

/** NewGroupForm: creates a group with a name, description, and members (R19). */
public class NewGroupActivity extends BaseActivity {

    private static final String STATE_EMAILS = "member_emails";

    private EmailChipInput emailInput;
    private TextInputLayout nameLayout;
    private TextInputEditText nameInput;
    private TextInputEditText descriptionInput;
    private Button createButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_group);
        if (!requireLogin()) {
            return;
        }
        setUpToolbar(getString(R.string.new_group), true);

        nameLayout = findViewById(R.id.name_layout);
        nameInput = findViewById(R.id.name_input);
        descriptionInput = findViewById(R.id.description_input);
        createButton = findViewById(R.id.create_button);
        emailInput = new EmailChipInput(this, currentUser.getEmail());
        if (savedInstanceState != null) {
            ArrayList<String> saved = savedInstanceState.getStringArrayList(STATE_EMAILS);
            if (saved != null) {
                emailInput.restore(saved);
            }
        }
        createButton.setOnClickListener(v -> create());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (emailInput != null) {
            outState.putStringArrayList(STATE_EMAILS, emailInput.getEmails());
        }
    }

    private void create() {
        nameLayout.setError(null);
        emailInput.showMissing(null);
        String name = textOf(nameInput);
        // Picks up an email that was typed but not added with the Add button.
        boolean valid = emailInput.addTypedEmail();
        if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setLoading(true);
        createButton.setEnabled(false);
        AppServices.groups().createGroup(currentUser.getId(), name, textOf(descriptionInput),
                emailInput.getEmails(), onUi(group -> {
                    startActivity(new Intent(this, GroupDetailActivity.class)
                            .putExtra(Extras.GROUP_ID, group.getId()));
                    finish();
                }, error -> {
                    setLoading(false);
                    createButton.setEnabled(true);
                    if (ErrorMessages.is(error, SplitSmartException.Code.MISSING_ACCOUNTS)) {
                        emailInput.showMissing(((SplitSmartException) error).getDetails());
                    } else {
                        showError(error);
                    }
                }));
    }
}
