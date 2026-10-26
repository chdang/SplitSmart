package edu.usc.splitsmart.ui.group;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.Group;
import edu.usc.splitsmart.ui.common.BaseActivity;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.profile.ProfileActivity;

/** The home screen after login: every group the user is in. */
public class GroupListActivity extends BaseActivity {

    private GroupListAdapter adapter;
    private View emptyView;

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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_group_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openGroup(Group group) {
        startActivity(new Intent(this, GroupDetailActivity.class)
                .putExtra(Extras.GROUP_ID, group.getId()));
    }
}
