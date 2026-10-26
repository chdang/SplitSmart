package edu.usc.splitsmart.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An expected failure that the screens show a specific message for. */
public class SplitSmartException extends Exception {
    private static final long serialVersionUID = 1L;

    public enum Code {
        /** Sign-up: the email already belongs to an account (R2). */
        DUPLICATE_EMAIL,
        /** Sign-up: the USC ID already belongs to an account (R2). */
        DUPLICATE_USC_ID,
        /** Login: no account matches this email and password. */
        INVALID_CREDENTIALS,
        /** Nobody is logged in. */
        NOT_LOGGED_IN,
        /** The user isn't a member of the group. */
        NOT_A_MEMBER,
        /** Only the group owner can do this (R18). */
        NOT_OWNER,
        /** Only the expense creator can do this (R7). */
        NOT_CREATOR,
        /** Some member emails don't have accounts. {@link #getDetails()} lists them. */
        MISSING_ACCOUNTS,
        /** The amount is zero, negative, or not a valid amount of money. */
        INVALID_AMOUNT,
        /** The shares don't add up to the total, or the participants aren't valid. */
        INVALID_SPLIT,
        /** The group, expense, or user doesn't exist. */
        NOT_FOUND,
        /** A required field is blank or malformed. */
        INVALID_INPUT
    }

    private final Code code;
    private final List<String> details;

    public SplitSmartException(Code code) {
        this(code, Collections.emptyList());
    }

    public SplitSmartException(Code code, List<String> details) {
        super(code.name());
        this.code = code;
        this.details = Collections.unmodifiableList(new ArrayList<>(details));
    }

    public Code getCode() {
        return code;
    }

    /** Extra information for some codes, such as the missing emails for MISSING_ACCOUNTS. */
    public List<String> getDetails() {
        return details;
    }
}
