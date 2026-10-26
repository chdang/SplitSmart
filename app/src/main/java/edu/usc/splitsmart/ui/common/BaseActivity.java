package edu.usc.splitsmart.ui.common;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

import java.util.function.Consumer;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.model.User;

/** Shared behavior for every screen: login check, toolbar, loading bar, and error messages. */
public abstract class BaseActivity extends AppCompatActivity {

    /** The logged-in user. Set by {@link #requireLogin()}. */
    protected User currentUser;

    /**
     * Loads the logged-in user, or sends them back to the login screen if nobody is logged in.
     * Call this first in onCreate on every screen after login, and stop if it returns false.
     */
    protected boolean requireLogin() {
        currentUser = AppServices.session().getCurrentUser();
        if (currentUser == null) {
            restartAtLaunchScreen();
            return false;
        }
        return true;
    }

    /**
     * Closes every open screen and opens the app's launch screen, which is the login screen.
     * Used for logging out and for sessions that ended.
     */
    protected void restartAtLaunchScreen() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(launch);
        }
        finish();
    }

    /** Uses the layout's toolbar as the action bar. Call after setContentView. */
    protected void setUpToolbar(@Nullable CharSequence title, boolean showBackArrow) {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
            actionBar.setDisplayHomeAsUpEnabled(showBackArrow);
        }
    }

    protected void setToolbarTitle(CharSequence title) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    /** Shows or hides the loading bar under the toolbar. */
    protected void setLoading(boolean loading) {
        View progress = findViewById(R.id.progress);
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    protected void showMessage(CharSequence message) {
        View anchor = findViewById(R.id.root);
        if (anchor == null) {
            anchor = findViewById(android.R.id.content);
        }
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show();
    }

    protected void showError(Exception error) {
        showMessage(ErrorMessages.from(this, error));
    }

    /** True once the screen is closing, so late results should be ignored. */
    protected boolean isClosing() {
        return isFinishing() || isDestroyed();
    }

    /**
     * Wraps screen code as a handler callback. Results that arrive after the screen closed are
     * dropped, and errors are shown as a message at the bottom of the screen.
     */
    protected <T> Callback<T> onUi(Consumer<T> onSuccess) {
        return onUi(onSuccess, error -> {
            setLoading(false);
            showError(error);
        });
    }

    /** Like {@link #onUi(Consumer)}, with custom error handling. */
    protected <T> Callback<T> onUi(Consumer<T> onSuccess, Consumer<Exception> onError) {
        return new Callback<T>() {
            @Override
            public void onSuccess(T result) {
                if (!isClosing()) {
                    onSuccess.accept(result);
                }
            }

            @Override
            public void onError(Exception error) {
                if (!isClosing()) {
                    onError.accept(error);
                }
            }
        };
    }

    /** The trimmed text of a field. */
    protected static String textOf(TextView field) {
        CharSequence text = field.getText();
        return text == null ? "" : text.toString().trim();
    }
}
