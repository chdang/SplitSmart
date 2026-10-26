package edu.usc.splitsmart.ui.report;

import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.ui.common.GroupScopedActivity;

/** Every expense and payment in the group, newest first (R15). */
public class HistoryActivity extends GroupScopedActivity {

    private HistoryAdapter adapter;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        setUpToolbar(getString(R.string.history_title), true);
        emptyView = findViewById(R.id.empty_view);
        startGroupScreen();
    }

    @Override
    protected void onMemberVerified() {
        adapter = new HistoryAdapter(currentUser.getId());
        RecyclerView list = findViewById(R.id.history_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        AppServices.groups().getMembers(groupId, onUi(members ->
                AppServices.balances().getHistory(groupId, onUi(items -> {
                    setLoading(false);
                    adapter.submit(members, items);
                    emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                }))));
    }
}
