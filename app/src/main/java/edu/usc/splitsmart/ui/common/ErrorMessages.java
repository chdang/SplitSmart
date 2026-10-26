package edu.usc.splitsmart.ui.common;

import android.content.Context;
import android.text.TextUtils;

import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.SplitSmartException;

/** Turns a handler error into a message the user can act on. */
public final class ErrorMessages {
    private ErrorMessages() {
    }

    public static String from(Context context, Exception error) {
        if (!(error instanceof SplitSmartException)) {
            return context.getString(R.string.error_generic);
        }
        SplitSmartException known = (SplitSmartException) error;
        switch (known.getCode()) {
            case DUPLICATE_EMAIL:
                return context.getString(R.string.error_duplicate_email);
            case DUPLICATE_USC_ID:
                return context.getString(R.string.error_duplicate_usc_id);
            case INVALID_CREDENTIALS:
                return context.getString(R.string.error_invalid_credentials);
            case NOT_LOGGED_IN:
                return context.getString(R.string.error_not_logged_in);
            case NOT_A_MEMBER:
                return context.getString(R.string.error_not_a_member);
            case NOT_OWNER:
                return context.getString(R.string.error_not_owner);
            case NOT_CREATOR:
                return context.getString(R.string.error_not_creator);
            case MISSING_ACCOUNTS:
                return context.getString(R.string.error_missing_accounts,
                        TextUtils.join(", ", known.getDetails()));
            case INVALID_AMOUNT:
                return context.getString(R.string.error_invalid_amount);
            case INVALID_SPLIT:
                return context.getString(R.string.error_invalid_split);
            case NOT_FOUND:
                return context.getString(R.string.error_not_found);
            case INVALID_INPUT:
                return context.getString(R.string.error_invalid_input);
            default:
                return context.getString(R.string.error_generic);
        }
    }

    /** True if the error is a SplitSmartException with this code. */
    public static boolean is(Exception error, SplitSmartException.Code code) {
        return error instanceof SplitSmartException
                && ((SplitSmartException) error).getCode() == code;
    }
}
