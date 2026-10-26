package edu.usc.splitsmart.ui.balance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.common.GroupScopedActivity;

/** IndividualGroupBalanceView: the user's balance with each other member (R13). */
public class IndividualGroupBalanceActivity extends GroupScopedActivity {

    private MemberBalanceAdapter adapter;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_individual_balance);
        setUpToolbar(getString(R.string.individual_title), true);

        emptyView = findViewById(R.id.empty_view);
        adapter = new MemberBalanceAdapter(this::settleUpWith);
        RecyclerView list = findViewById(R.id.balance_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        startGroupScreen();
    }

    @Override
    protected void onMemberVerified() {
        loadBalances();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (memberVerified) {
            setLoading(true);
            loadBalances();
        }
    }

    private void loadBalances() {
        AppServices.groups().getMembers(groupId, onUi(members ->
                AppServices.balances().getIndividualBalances(groupId, currentUser.getId(),
                        onUi(balances -> {
                            setLoading(false);
                            showBalances(members, balances);
                        }))));
    }

    private void showBalances(List<User> members, Map<String, Long> balances) {
        List<MemberBalanceAdapter.Row> rows = new ArrayList<>();
        for (User member : members) {
            if (!member.getId().equals(currentUser.getId())) {
                Long net = balances.get(member.getId());
                rows.add(new MemberBalanceAdapter.Row(member, net == null ? 0 : net));
            }
        }
        adapter.submit(rows);
        emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void settleUpWith(User member, long owedCents) {
        startActivity(new Intent(this, SettleUpActivity.class)
                .putExtra(Extras.GROUP_ID, groupId)
                .putExtra(Extras.RECEIVER_ID, member.getId())
                .putExtra(Extras.AMOUNT_CENTS, owedCents));
    }
}
