package edu.usc.splitsmart.ui.report;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.ActivityItem;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.balance.EditSettlementActivity;
import edu.usc.splitsmart.ui.common.DropdownAdapter;
import edu.usc.splitsmart.ui.common.Extras;
import edu.usc.splitsmart.ui.common.GroupScopedActivity;
import edu.usc.splitsmart.ui.common.SimpleTextWatcher;
import edu.usc.splitsmart.ui.expense.EditExpenseActivity;

/**
 * Every expense and payment in the group, with a search box, a kind filter, and a sort order
 * (R15). The group's history is loaded once and narrowed down on screen, so typing in the
 * search box doesn't go back to the database.
 */
public class HistoryActivity extends GroupScopedActivity {

    private static final String STATE_SORT = "sort";

    /** Sort options in the order they appear in the dropdown. */
    private static final HistoryFilter.Sort[] SORTS = {
            HistoryFilter.Sort.NEWEST,
            HistoryFilter.Sort.OLDEST,
            HistoryFilter.Sort.LARGEST,
            HistoryFilter.Sort.SMALLEST
    };

    private final List<ActivityItem> loaded = new ArrayList<>();
    private final List<User> members = new ArrayList<>();

    private HistoryFilter filter;
    private HistoryAdapter adapter;
    private View filters;
    private TextInputEditText searchInput;
    private MaterialButtonToggleGroup kindGroup;
    private AutoCompleteTextView sortInput;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        setUpToolbar(getString(R.string.history_title), true);

        filter = new HistoryFilter(this);
        filters = findViewById(R.id.filters);
        searchInput = findViewById(R.id.search_input);
        kindGroup = findViewById(R.id.kind_group);
        sortInput = findViewById(R.id.sort_input);
        emptyView = findViewById(R.id.empty_view);

        // The search box and the kind buttons restore themselves and are read back on every
        // refresh, but the sort order lives in the filter, so this class restores it.
        sortInput.setSaveEnabled(false);
        sortInput.setAdapter(new DropdownAdapter(this, sortLabels()));
        if (savedInstanceState != null) {
            filter.setSort(HistoryFilter.Sort.valueOf(
                    savedInstanceState.getString(STATE_SORT, HistoryFilter.Sort.NEWEST.name())));
        }
        showSort();

        searchInput.addTextChangedListener(SimpleTextWatcher.after(this::refresh));
        kindGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                refresh();
            }
        });
        sortInput.setOnItemClickListener((parent, view, position, id) -> {
            filter.setSort(SORTS[position]);
            refresh();
        });

        startGroupScreen();
    }

    @Override
    protected void onMemberVerified() {
        adapter = new HistoryAdapter(currentUser.getId(), this::openItem);
        RecyclerView list = findViewById(R.id.history_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        AppServices.groups().getMembers(groupId, onUi(groupMembers ->
                AppServices.balances().getHistory(groupId, onUi(items -> {
                    setLoading(false);
                    members.clear();
                    members.addAll(groupMembers);
                    loaded.clear();
                    loaded.addAll(items);
                    filter.setMembers(members);
                    refresh();
                }))));
    }

    /** Re-applies the search, filter, and sort to the loaded history. */
    private void refresh() {
        if (adapter == null) {
            return;
        }
        filter.setQuery(textOf(searchInput));
        filter.setKind(checkedKind());
        List<ActivityItem> visible = filter.apply(loaded);
        adapter.submit(members, visible);
        // Nothing to search in an empty group, so the controls stay out of the way.
        filters.setVisibility(loaded.isEmpty() ? View.GONE : View.VISIBLE);
        emptyView.setText(loaded.isEmpty()
                ? R.string.history_empty : R.string.history_no_matches);
        emptyView.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private HistoryFilter.Kind checkedKind() {
        int checked = kindGroup.getCheckedButtonId();
        if (checked == R.id.kind_expenses) {
            return HistoryFilter.Kind.EXPENSES;
        }
        if (checked == R.id.kind_payments) {
            return HistoryFilter.Kind.PAYMENTS;
        }
        return HistoryFilter.Kind.ALL;
    }

    private List<String> sortLabels() {
        return Arrays.asList(
                getString(R.string.history_sort_newest),
                getString(R.string.history_sort_oldest),
                getString(R.string.history_sort_largest),
                getString(R.string.history_sort_smallest));
    }

    private void showSort() {
        int index = Arrays.asList(SORTS).indexOf(filter.getSort());
        sortInput.setText(sortLabels().get(index), false);
    }

    /** Opens the full details of a row, where its creator or recorder can edit or delete it. */
    private void openItem(ActivityItem item) {
        Intent intent = item.getType() == ActivityItem.Type.EXPENSE
                ? new Intent(this, EditExpenseActivity.class)
                        .putExtra(Extras.EXPENSE_ID, item.getExpense().getId())
                : new Intent(this, EditSettlementActivity.class)
                        .putExtra(Extras.SETTLEMENT_ID, item.getSettlement().getId());
        startActivity(intent.putExtra(Extras.GROUP_ID, groupId));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SORT, filter.getSort().name());
    }
}
