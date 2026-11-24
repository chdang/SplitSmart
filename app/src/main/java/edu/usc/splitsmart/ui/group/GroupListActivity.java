package edu.usc.splitsmart.ui.group;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.ui.common.BaseActivity;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.profile.ProfileActivity;
import edu.usc.splitsmart.ui.reminder.RemindersActivity;

/** The home screen after login: every group the user is in. */
public class GroupListActivity extends BaseActivity {

    private GroupListAdapter adapter;
    private View emptyView;
    private View remindersBanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_list);
        if (!requireLogin()) {
            return;
        }
        setUpToolbar(getString(R.string.your_groups), false);

        emptyView = findViewById(R.id.empty_view);
        adapter = new GroupListAdapter(this::openGroup);
        RecyclerView list = findViewById(R.id.group_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.new_group_button).setOnClickListener(v ->
                startActivity(new Intent(this, NewGroupActivity.class)));

        remindersBanner = findViewById(R.id.reminders_banner);
        remindersBanner.setOnClickListener(v -> openReminders());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload every time, since groups can be created, renamed, or deleted elsewhere.
        if (currentUser == null) {
            return;
        }
        setLoading(true);
        AppServices.groups().getGroups(currentUser.getId(), onUi(groups -> {
            setLoading(false);
            adapter.submit(groups);
            emptyView.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
        }));
        showUnreadReminders();
    }

    /** Shows the banner while there are unread reminders. A failed count just hides it. */
    private void showUnreadReminders() {
        AppServices.reminders().getUnreadCount(onUi(unread -> {
            remindersBanner.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
            TextView text = findViewById(R.id.reminders_banner_text);
            text.setText(getResources().getQuantityString(R.plurals.reminders_unread_banner,
                    unread, unread));
        }, error -> remindersBanner.setVisibility(View.GONE)));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_group_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_reminders) {
            openReminders();
            return true;
        }
        if (item.getItemId() == R.id.action_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openReminders() {
        startActivity(new Intent(this, RemindersActivity.class));
    }

    private void openGroup(Group group) {
        startActivity(new Intent(this, GroupDetailActivity.class)
                .putExtra(Extras.GROUP_ID, group.getId()));
    }
}
