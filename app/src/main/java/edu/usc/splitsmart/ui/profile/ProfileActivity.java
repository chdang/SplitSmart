package edu.usc.splitsmart.ui.profile;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.ui.common.BaseActivity;
import edu.usc.splitsmart.ui.common.SimpleTextWatcher;

/** Shows the account, lets the user change their name (R5), and logs them out (R4). */
public class ProfileActivity extends BaseActivity {

    private TextInputLayout nameLayout;
    private TextInputEditText nameInput;
    private Button saveNameButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        if (!requireLogin()) {
            return;
        }
        setUpToolbar(getString(R.string.profile_title), true);

        nameLayout = findViewById(R.id.name_layout);
        nameInput = findViewById(R.id.name_input);
        saveNameButton = findViewById(R.id.save_name_button);
        TextView email = findViewById(R.id.email_value);
        TextView uscId = findViewById(R.id.usc_id_value);

        if (savedInstanceState == null) {
            nameInput.setText(currentUser.getName());
        }
        email.setText(currentUser.getEmail());
        uscId.setText(currentUser.getUscId());

        nameInput.addTextChangedListener(SimpleTextWatcher.after(this::updateSaveButton));
        saveNameButton.setOnClickListener(v -> saveName());
        findViewById(R.id.log_out_button).setOnClickListener(v -> confirmLogOut());
        updateSaveButton();
    }

    private void updateSaveButton() {
        String name = textOf(nameInput);
        nameLayout.setError(null);
        saveNameButton.setEnabled(!name.isEmpty() && !name.equals(currentUser.getName()));
    }

    private void saveName() {
        String name = textOf(nameInput);
        if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.error_required));
            return;
        }
        setLoading(true);
        saveNameButton.setEnabled(false);
        AppServices.userNames().updateName(currentUser.getId(), name, onUi(updated -> {
            setLoading(false);
            currentUser = updated;
            updateSaveButton();
            showMessage(getString(R.string.profile_name_updated));
        }, error -> {
            setLoading(false);
            updateSaveButton();
            showError(error);
        }));
    }

    private void confirmLogOut() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.log_out_confirm_title)
                .setMessage(R.string.log_out_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.log_out, (dialog, which) -> {
                    AppServices.session().logout();
                    restartAtLaunchScreen();
                })
                .show();
    }
}
