package edu.usc.splitsmart.ui.common;

import android.app.Activity;

/** Keys for the values screens pass to each other when one opens another. */
public final class Extras {
    public static final String GROUP_ID = "edu.usc.splitsmart.extra.GROUP_ID";
    public static final String EXPENSE_ID = "edu.usc.splitsmart.extra.EXPENSE_ID";
    public static final String RECEIVER_ID = "edu.usc.splitsmart.extra.RECEIVER_ID";
    public static final String AMOUNT_CENTS = "edu.usc.splitsmart.extra.AMOUNT_CENTS";
    public static final String EMAIL = "edu.usc.splitsmart.extra.EMAIL";

    /** Sent back by the edit group screen so the group page knows to close. */
    public static final int RESULT_GROUP_DELETED = Activity.RESULT_FIRST_USER + 1;

    private Extras() {
    }
}
