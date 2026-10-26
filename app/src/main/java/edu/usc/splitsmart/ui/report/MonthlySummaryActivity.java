package edu.usc.splitsmart.ui.report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.MonthlyStatistics;
import edu.usc.splitsmart.model.Category;
import edu.usc.splitsmart.ui.common.Dates;
import edu.usc.splitsmart.ui.common.GroupScopedActivity;
import edu.usc.splitsmart.ui.common.Labels;
import edu.usc.splitsmart.ui.common.Money;

/** Number of expenses, total spending, and spending per category for a chosen month (R17). */
public class MonthlySummaryActivity extends GroupScopedActivity {

    private static final String STATE_MONTH = "month";

    private YearMonth month = YearMonth.now();

    private TextView monthLabel;
    private View previousButton;
    private View nextButton;
    private TextView expenseCount;
    private TextView totalSpent;
    private LinearLayout categoryContainer;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monthly_summary);
        setUpToolbar(getString(R.string.summary_title), true);

        if (savedInstanceState != null && savedInstanceState.getString(STATE_MONTH) != null) {
            month = YearMonth.parse(savedInstanceState.getString(STATE_MONTH));
        }
        monthLabel = findViewById(R.id.month_label);
        previousButton = findViewById(R.id.previous_month);
        nextButton = findViewById(R.id.next_month);
        expenseCount = findViewById(R.id.expense_count);
        totalSpent = findViewById(R.id.total_spent);
        categoryContainer = findViewById(R.id.category_container);
        emptyView = findViewById(R.id.empty_view);

        previousButton.setOnClickListener(v -> {
            if (memberVerified) {
                showMonth(month.minusMonths(1));
            }
        });
        nextButton.setOnClickListener(v -> {
            if (memberVerified) {
                showMonth(month.plusMonths(1));
            }
        });

        startGroupScreen();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MONTH, month.toString());
    }

    @Override
    protected void onMemberVerified() {
        showMonth(month);
    }

    private void showMonth(YearMonth newMonth) {
        month = newMonth;
        monthLabel.setText(Dates.month(month));
        // There's nothing to show for months that haven't happened yet.
        nextButton.setEnabled(month.isBefore(YearMonth.now()));
        nextButton.setAlpha(nextButton.isEnabled() ? 1f : 0.38f);

        setLoading(true);
        YearMonth requested = month;
        AppServices.groups().getMonthlyStatistics(groupId, requested, onUi(stats -> {
            // Ignore an older answer if the user already moved to another month.
            if (requested.equals(month)) {
                setLoading(false);
                showStatistics(stats);
            }
        }));
    }

    private void showStatistics(MonthlyStatistics stats) {
        expenseCount.setText(String.valueOf(stats.getExpenseCount()));
        totalSpent.setText(Money.format(stats.getTotalCents()));

        List<Map.Entry<Category, Long>> spending = new ArrayList<>();
        for (Map.Entry<Category, Long> entry : stats.getSpendingByCategory().entrySet()) {
            if (entry.getValue() > 0) {
                spending.add(entry);
            }
        }
        spending.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        categoryContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Map.Entry<Category, Long> entry : spending) {
            View row = inflater.inflate(R.layout.item_category_row, categoryContainer, false);
            ((TextView) row.findViewById(R.id.category_name))
                    .setText(Labels.category(this, entry.getKey()));
            ((TextView) row.findViewById(R.id.category_amount))
                    .setText(Money.format(entry.getValue()));
            LinearProgressIndicator bar = row.findViewById(R.id.category_bar);
            bar.setProgress((int) Math.round(entry.getValue() * 100.0 / stats.getTotalCents()));
            categoryContainer.addView(row);
        }
        emptyView.setVisibility(spending.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
