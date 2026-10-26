package edu.usc.splitsmart.ui.common;

import android.widget.Toast;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;

/**
 * A screen that belongs to one group. It reads the group id from the intent, checks that the
 * current user is a member, and only then calls {@link #onMemberVerified()} to load data.
 */
public abstract class GroupScopedActivity extends BaseActivity {

    protected String groupId;
    protected boolean memberVerified;

    /**
     * Call at the end of onCreate, after the views are set up. Closes the screen if nobody is
     * logged in, no group was passed, or the user isn't a member.
     */
    protected void startGroupScreen() {
        if (!requireLogin()) {
            return;
        }
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        if (groupId == null) {
            finish();
            return;
        }
        setLoading(true);
        AppServices.membership().authenticateMember(groupId, currentUser.getId(), onUi(isMember -> {
            if (Boolean.TRUE.equals(isMember)) {
                memberVerified = true;
                onMemberVerified();
            } else {
                closeWithMessage(getString(R.string.error_not_a_member));
            }
        }, error -> closeWithMessage(ErrorMessages.from(this, error))));
    }

    /** Load the screen's data here. The loading bar is showing; hide it when done. */
    protected abstract void onMemberVerified();

    /** Shows a short message that survives the screen closing, then closes it. */
    protected void closeWithMessage(CharSequence message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    /** Error handler for loads the screen can't work without. */
    protected void closeWithError(Exception error) {
        closeWithMessage(ErrorMessages.from(this, error));
    }
}
