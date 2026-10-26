package edu.usc.splitsmart.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.SplitSmartException;
import edu.usc.splitsmart.ui.common.BaseActivity;
import edu.usc.splitsmart.ui.common.ErrorMessages;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.group.GroupListActivity;

/** LoginForm: the app's launch screen. Logs the user in, or opens sign-up (R1, R3). */
public class LoginActivity extends BaseActivity {

    private TextInputLayout emailLayout;
    private TextInputEditText emailInput;
    private TextInputLayout passwordLayout;
    private TextInputEditText passwordInput;
    private Button loginButton;

    /** After sign-up, fill in the new account's email so the user only types the password. */
    private final ActivityResultLauncher<Intent> signUpLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    emailInput.setText(result.getData().getStringExtra(Extras.EMAIL));
                    passwordInput.setText(null);
                    passwordInput.requestFocus();
                    showMessage(getString(R.string.account_created));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (AppServices.session().getCurrentUser() != null) {
            openGroups();
            return;
        }
        setContentView(R.layout.activity_login);

        emailLayout = findViewById(R.id.email_layout);
        emailInput = findViewById(R.id.email_input);
        passwordLayout = findViewById(R.id.password_layout);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);

        loginButton.setOnClickListener(v -> logIn());
        passwordInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                logIn();
                return true;
            }
            return false;
        });
        findViewById(R.id.sign_up_button).setOnClickListener(v ->
                signUpLauncher.launch(new Intent(this, SignUpActivity.class)));

        if (AppServices.usingFakeAccounts()) {
            findViewById(R.id.demo_hint).setVisibility(View.VISIBLE);
        }
    }

    private void logIn() {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        String email = textOf(emailInput);
        String password = passwordInput.getText() == null ? "" : passwordInput.getText().toString();

        boolean valid = true;
        if (email.isEmpty()) {
            emailLayout.setError(getString(R.string.error_required));
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.error_email_invalid));
            valid = false;
        }
        if (password.isEmpty()) {
            passwordLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setLoading(true);
        loginButton.setEnabled(false);
        AppServices.session().login(email, password, onUi(user -> openGroups(), error -> {
            setLoading(false);
            loginButton.setEnabled(true);
            if (ErrorMessages.is(error, SplitSmartException.Code.INVALID_CREDENTIALS)) {
                passwordLayout.setError(ErrorMessages.from(this, error));
            } else {
                showError(error);
            }
        }));
    }

    private void openGroups() {
        startActivity(new Intent(this, GroupListActivity.class));
        finish();
    }
}
