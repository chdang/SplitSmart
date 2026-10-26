package edu.usc.splitsmart.ui.common;

import android.content.Context;

import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.model.User;

/** Turns model values into text for the screen. */
public final class Labels {
    private Labels() {
    }

    public static String category(Context context, Category category) {
        switch (category) {
            case FOOD:
                return context.getString(R.string.category_food);
            case HOUSING:
                return context.getString(R.string.category_housing);
            case TRANSPORTATION:
                return context.getString(R.string.category_transportation);
            case ENTERTAINMENT:
                return context.getString(R.string.category_entertainment);
            case OTHER:
            default:
                return context.getString(R.string.category_other);
        }
    }

    /** The user's name, or "You" for the current user. */
    public static String personOrYou(Context context, User user, String currentUserId) {
        return user.getId().equals(currentUserId) ? context.getString(R.string.you)
                : user.getName();
    }

    /** The user's name, with "(you)" after it for the current user. Used in pickers. */
    public static String nameMarkingYou(Context context, User user, String currentUserId) {
        return user.getId().equals(currentUserId)
                ? context.getString(R.string.name_with_you, user.getName())
                : user.getName();
    }
}
