package edu.usc.splitsmart.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.SplitSmartException;
import edu.usc.splitsmart.ui.common.BaseActivity;
import edu.usc.splitsmart.ui.common.ErrorMessages;
import edu.usc.splitsmart.ui.common.Extras;

/**
 * Creates an account with name, USC email, USC ID, and password (R1, R2). On success it returns
 * to the login screen with the email filled in.
 */
public class SignUpActivity extends BaseActivity {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private TextInputLayout nameLayout;
    private TextInputEditText nameInput;
    private TextInputLayout emailLayout;
    private TextInputEditText emailInput;
    private TextInputLayout uscIdLayout;
    private TextInputEditText uscIdInput;
    private TextInputLayout passwordLayout;
    private TextInputEditText passwordInput;
    private TextInputLayout confirmLayout;
    private TextInputEditText confirmInput;
    private Button signUpButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        setUpToolbar(getString(R.string.sign_up_title), true);

        nameLayout = findViewById(R.id.name_layout);
        nameInput = findViewById(R.id.name_input);
        emailLayout = findViewById(R.id.email_layout);
        emailInput = findViewById(R.id.email_input);
        uscIdLayout = findViewById(R.id.usc_id_layout);
        uscIdInput = findViewById(R.id.usc_id_input);
        passwordLayout = findViewById(R.id.password_layout);
        passwordInput = findViewById(R.id.password_input);
        confirmLayout = findViewById(R.id.confirm_layout);
        confirmInput = findViewById(R.id.confirm_input);
        signUpButton = findViewById(R.id.sign_up_button);

        signUpButton.setOnClickListener(v -> signUp());
    }

    private void signUp() {
        clearErrors();
        String name = textOf(nameInput);
        String email = textOf(emailInput).toLowerCase(Locale.US);
        String uscId = textOf(uscIdInput);
        String password = rawText(passwordInput);
        String confirm = rawText(confirmInput);

        boolean valid = true;
        if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.error_required));
            valid = false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.error_email_invalid));
            valid = false;
        } else if (!email.endsWith("@usc.edu")) {
            // R1: accounts are for verified USC students.
            emailLayout.setError(getString(R.string.error_usc_email));
            valid = false;
        }
        if (!uscId.matches("\\d{10}")) {
            uscIdLayout.setError(getString(R.string.error_usc_id));
            valid = false;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            passwordLayout.setError(getString(R.string.error_password_short));
            valid = false;
        } else if (!password.equals(confirm)) {
            confirmLayout.setError(getString(R.string.error_password_mismatch));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setLoading(true);
        signUpButton.setEnabled(false);
        AppServices.signUp().register(name, email, uscId, password, onUi(user -> {
            setResult(RESULT_OK, new Intent().putExtra(Extras.EMAIL, user.getEmail()));
            finish();
        }, error -> {
            setLoading(false);
            signUpButton.setEnabled(true);
            // R2: show duplicate accounts on the field that caused them.
            if (ErrorMessages.is(error, SplitSmartException.Code.DUPLICATE_EMAIL)) {
                emailLayout.setError(ErrorMessages.from(this, error));
            } else if (ErrorMessages.is(error, SplitSmartException.Code.DUPLICATE_USC_ID)) {
                uscIdLayout.setError(ErrorMessages.from(this, error));
            } else {
                showError(error);
            }
        }));
    }

    private void clearErrors() {
        nameLayout.setError(null);
        emailLayout.setError(null);
        uscIdLayout.setError(null);
        passwordLayout.setError(null);
        confirmLayout.setError(null);
    }

    private static String rawText(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }
}
