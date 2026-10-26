package edu.usc.splitsmart.ui.group;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.SplitSmartException;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.ErrorMessages;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.common.GroupScopedActivity;

/**
 * EditGroupForm: the owner changes the name and description, adds members, or deletes the
 * group (R18). Anyone else is turned away.
 */
public class EditGroupActivity extends GroupScopedActivity {

    private static final String STATE_EMAILS = "new_member_emails";

    private Group group;
    private EmailChipInput emailInput;
    private ArrayList<String> restoredEmails;

    private TextInputLayout nameLayout;
    private TextInputEditText nameInput;
    private TextInputEditText descriptionInput;
    private ChipGroup currentMembers;
    private Button saveButton;
    private Button deleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_group);
        setUpToolbar(getString(R.string.edit_group), true);
        if (savedInstanceState != null) {
            restoredEmails = savedInstanceState.getStringArrayList(STATE_EMAILS);
        }

        nameLayout = findViewById(R.id.name_layout);
        nameInput = findViewById(R.id.name_input);
        descriptionInput = findViewById(R.id.description_input);
        currentMembers = findViewById(R.id.current_members);
        saveButton = findViewById(R.id.save_button);
        deleteButton = findViewById(R.id.delete_button);
        saveButton.setOnClickListener(v -> save());
        deleteButton.setOnClickListener(v -> confirmDelete());
        setButtonsEnabled(false);

        startGroupScreen();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_EMAILS,
                emailInput != null ? emailInput.getEmails() : restoredEmails);
    }

    @Override
    protected void onMemberVerified() {
        emailInput = new EmailChipInput(this, currentUser.getEmail());
        if (restoredEmails != null) {
            emailInput.restore(restoredEmails);
        }
        AppServices.groups().authenticateOwner(groupId, currentUser.getId(), onUi(isOwner -> {
            if (!Boolean.TRUE.equals(isOwner)) {
                closeWithMessage(getString(R.string.only_owner_can_edit));
                return;
            }
            AppServices.groups().getGroup(groupId, onUi(loaded -> {
                group = loaded;
                // A rotation keeps typed text, so only fill the fields the first time.
                if (textOf(nameInput).isEmpty()) {
                    nameInput.setText(loaded.getName());
                    descriptionInput.setText(loaded.getDescription());
                }
                AppServices.groups().getMembers(groupId, onUi(members -> {
                    setLoading(false);
                    showMembers(members);
                    setButtonsEnabled(true);
                }, this::closeWithError));
            }, this::closeWithError));
        }, this::closeWithError));
    }

    private void showMembers(List<User> members) {
        currentMembers.removeAllViews();
        List<String> emails = new ArrayList<>();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (User member : members) {
            emails.add(member.getEmail());
            Chip chip = (Chip) inflater.inflate(R.layout.view_email_chip, currentMembers, false);
            chip.setText(member.getId().equals(group.getOwnerId())
                    ? getString(R.string.group_owner_label, member.getName())
                    : member.getName());
            currentMembers.addView(chip);
        }
        emailInput.setExistingMembers(emails);
    }

    private void save() {
        nameLayout.setError(null);
        emailInput.showMissing(null);
        String name = textOf(nameInput);
        boolean valid = emailInput.addTypedEmail();
        if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setLoading(true);
        setButtonsEnabled(false);
        AppServices.groups().updateGroup(groupId, currentUser.getId(), name,
                textOf(descriptionInput), emailInput.getEmails(), onUi(updated -> {
                    Toast.makeText(this, R.string.group_updated, Toast.LENGTH_SHORT).show();
                    finish();
                }, this::saveFailed));
    }

    private void saveFailed(Exception error) {
        setLoading(false);
        setButtonsEnabled(true);
        if (ErrorMessages.is(error, SplitSmartException.Code.MISSING_ACCOUNTS)) {
            emailInput.showMissing(((SplitSmartException) error).getDetails());
        } else {
            showError(error);
        }
    }

    private void confirmDelete() {
        if (group == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.group_delete_title, group.getName()))
                .setMessage(R.string.group_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> delete())
                .show();
    }

    private void delete() {
        setLoading(true);
        setButtonsEnabled(false);
        AppServices.groups().deleteGroup(groupId, currentUser.getId(), onUi(done -> {
            Toast.makeText(this, R.string.group_deleted, Toast.LENGTH_SHORT).show();
            // Tell the group page to close too, since its group no longer exists.
            setResult(Extras.RESULT_GROUP_DELETED);
            finish();
        }, error -> {
            setLoading(false);
            setButtonsEnabled(true);
            showError(error);
        }));
    }

    private void setButtonsEnabled(boolean enabled) {
        saveButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
        if (emailInput != null) {
            emailInput.setEnabled(enabled);
        }
    }
}
