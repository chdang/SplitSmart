package edu.usc.splitsmart.ui.expense;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.SplitType;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.Money;
import edu.usc.splitsmart.ui.common.SimpleTextWatcher;

/**
 * One member in the "Split between" list: a checkbox, a preview of their share, and, for
 * percent and amount splits, a field for their value.
 */
final class ParticipantRow {
    final User user;
    final View view;

    private final Context context;
    private final CheckBox check;
    private final TextView preview;
    private final TextInputLayout valueLayout;
    private final TextInputEditText valueInput;
    private SplitType splitType = SplitType.EQUAL;
    private boolean editable = true;

    ParticipantRow(LayoutInflater inflater, ViewGroup parent, User user, String label,
                   Runnable onChange) {
        this.user = user;
        this.context = parent.getContext();
        view = inflater.inflate(R.layout.item_participant, parent, false);
        check = view.findViewById(R.id.participant_check);
        preview = view.findViewById(R.id.participant_preview);
        valueLayout = view.findViewById(R.id.participant_value_layout);
        valueInput = view.findViewById(R.id.participant_value_input);

        // Every row reuses the same view ids, so Android's automatic state saving would copy one
        // row's value into all of them after rotation. The form saves row state itself instead.
        check.setSaveEnabled(false);
        valueLayout.setSaveEnabled(false);
        valueInput.setSaveEnabled(false);

        check.setText(label);
        valueLayout.setEnabled(false);
        check.setOnCheckedChangeListener((button, isChecked) -> {
            valueLayout.setEnabled(editable && isChecked);
            if (!isChecked) {
                valueLayout.setError(null);
            }
            onChange.run();
        });
        valueInput.addTextChangedListener(SimpleTextWatcher.after(onChange));
    }

    /** Clears the value and shows the field that fits the split type. */
    void setSplitType(SplitType type) {
        splitType = type;
        valueInput.setText(null);
        valueLayout.setError(null);
        valueLayout.setVisibility(type == SplitType.EQUAL ? View.GONE : View.VISIBLE);
        valueLayout.setPrefixText(type == SplitType.EXACT_AMOUNT
                ? context.getString(R.string.currency_prefix) : null);
        valueLayout.setSuffixText(type == SplitType.PERCENTAGE
                ? context.getString(R.string.percent_suffix) : null);
    }

    boolean isChecked() {
        return check.isChecked();
    }

    void setChecked(boolean checked) {
        check.setChecked(checked);
    }

    /** Basis points for a percent split, cents for an amount split, or null if not valid. */
    @Nullable
    Long value() {
        return splitType == SplitType.PERCENTAGE
                ? Money.parseBasisPoints(valueInput.getText())
                : Money.parseCents(valueInput.getText());
    }

    String valueText() {
        CharSequence text = valueInput.getText();
        return text == null ? "" : text.toString();
    }

    void setValueText(String text) {
        valueInput.setText(text);
    }

    /** Shows the person's share next to their name, or hides it when null. */
    void setPreview(@Nullable String text) {
        preview.setText(text);
        preview.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    void showError(@Nullable String message) {
        valueLayout.setError(message);
    }

    void setEditable(boolean editable) {
        this.editable = editable;
        check.setEnabled(editable);
        valueLayout.setEnabled(editable && check.isChecked());
    }
}
