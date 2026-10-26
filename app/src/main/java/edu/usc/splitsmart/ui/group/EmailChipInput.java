package edu.usc.splitsmart.ui.group;

import android.app.Activity;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import edu.usc.splitsmart.R;

/**
 * The "add members by email" control on the group forms (layout view_email_input). Typed emails
 * become removable chips. It checks the format here; the handler checks the accounts exist.
 */
final class EmailChipInput {
    private final Activity activity;
    private final TextInputLayout layout;
    private final EditText input;
    private final ChipGroup chips;
    private final TextView missingAccounts;
    private final String ownEmail;
    private final Set<String> existingMembers = new HashSet<>();
    private final List<String> emails = new ArrayList<>();

    /** Finds the control's views in the activity. {@code ownEmail} is the current user's. */
    EmailChipInput(Activity activity, String ownEmail) {
        this.activity = activity;
        this.ownEmail = normalize(ownEmail);
        layout = activity.findViewById(R.id.member_email_layout);
        input = activity.findViewById(R.id.member_email_input);
        chips = activity.findViewById(R.id.member_chips);
        missingAccounts = activity.findViewById(R.id.missing_accounts);

        activity.findViewById(R.id.add_member_button).setOnClickListener(v -> addTypedEmail());
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTypedEmail();
                return true;
            }
            return false;
        });
    }

    /** Emails of people already in the group, so they can't be added twice. */
    void setExistingMembers(Collection<String> memberEmails) {
        existingMembers.clear();
        for (String email : memberEmails) {
            existingMembers.add(normalize(email));
        }
    }

    /**
     * Turns whatever is typed into a chip. Returns false and shows an error if the typed text
     * isn't a new, valid email. An empty field counts as fine.
     */
    boolean addTypedEmail() {
        layout.setError(null);
        String email = normalize(input.getText().toString());
        if (email.isEmpty()) {
            return true;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layout.setError(activity.getString(R.string.error_email_invalid));
            return false;
        }
        if (email.equals(ownEmail)) {
            layout.setError(activity.getString(R.string.error_member_is_you));
            return false;
        }
        if (existingMembers.contains(email)) {
            layout.setError(activity.getString(R.string.error_already_member));
            return false;
        }
        addEmail(email);
        input.setText(null);
        return true;
    }

    /** Adds chips for emails saved before a rotation. */
    void restore(Collection<String> savedEmails) {
        for (String email : savedEmails) {
            addEmail(email);
        }
    }

    ArrayList<String> getEmails() {
        return new ArrayList<>(emails);
    }

    /** Lists emails the handler said have no account, or hides the message when null. */
    void showMissing(List<String> missing) {
        if (missing == null || missing.isEmpty()) {
            missingAccounts.setVisibility(View.GONE);
            return;
        }
        missingAccounts.setText(activity.getString(R.string.error_missing_accounts,
                TextUtils.join(", ", missing)));
        missingAccounts.setVisibility(View.VISIBLE);
    }

    void setEnabled(boolean enabled) {
        layout.setEnabled(enabled);
        activity.findViewById(R.id.add_member_button).setEnabled(enabled);
        for (int i = 0; i < chips.getChildCount(); i++) {
            chips.getChildAt(i).setEnabled(enabled);
        }
    }

    private void addEmail(String email) {
        if (emails.contains(email)) {
            return;
        }
        emails.add(email);
        Chip chip = (Chip) LayoutInflater.from(activity)
                .inflate(R.layout.view_email_chip, chips, false);
        chip.setText(email);
        chip.setCloseIconVisible(true);
        chip.setCloseIconContentDescription(activity.getString(R.string.group_remove_email, email));
        chip.setOnCloseIconClickListener(v -> {
            emails.remove(email);
            chips.removeView(chip);
        });
        chips.addView(chip);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.US);
    }
}
