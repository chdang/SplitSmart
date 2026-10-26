package edu.usc.splitsmart.ui.common;

import android.text.Editable;
import android.text.TextWatcher;

/** A TextWatcher that runs one action after the text changes. */
public final class SimpleTextWatcher implements TextWatcher {
    private final Runnable onChange;

    private SimpleTextWatcher(Runnable onChange) {
        this.onChange = onChange;
    }

    public static TextWatcher after(Runnable onChange) {
        return new SimpleTextWatcher(onChange);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        onChange.run();
    }
}
