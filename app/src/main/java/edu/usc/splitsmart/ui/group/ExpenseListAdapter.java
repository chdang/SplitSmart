package edu.usc.splitsmart.ui.group;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.Dates;
import edu.usc.splitsmart.ui.common.Labels;
import edu.usc.splitsmart.ui.common.Money;

/** GroupExpenseList rows: what it was, who paid, the total, and what it means for the user (R8). */
final class ExpenseListAdapter extends RecyclerView.Adapter<ExpenseListAdapter.Holder> {

    interface OnExpenseClick {
        void open(Expense expense);
    }

    private final List<Expense> expenses = new ArrayList<>();
    private final Map<String, User> members = new HashMap<>();
    private final String currentUserId;
    private final OnExpenseClick onClick;

    ExpenseListAdapter(String currentUserId, OnExpenseClick onClick) {
        this.currentUserId = currentUserId;
        this.onClick = onClick;
    }

    @SuppressLint("NotifyDataSetChanged")
    void submit(List<User> groupMembers, List<Expense> newExpenses) {
        members.clear();
        for (User member : groupMembers) {
            members.put(member.getId(), member);
        }
        expenses.clear();
        expenses.addAll(newExpenses);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_expense, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Expense expense = expenses.get(position);
        Context context = holder.itemView.getContext();

        holder.description.setText(expense.getDescription());
        holder.total.setText(Money.format(expense.getTotalCents()));
        holder.meta.setText(context.getString(R.string.expense_meta, paidBy(context, expense),
                Dates.shortDate(expense.getExpenseDate()),
                Labels.category(context, expense.getCategory())));

        long yourShare = expense.owedBy(currentUserId);
        if (expense.getPayerId().equals(currentUserId)) {
            long lent = expense.getTotalCents() - yourShare;
            if (lent > 0) {
                holder.yourShare.setText(context.getString(R.string.expense_you_lent,
                        Money.format(lent)));
                holder.yourShare.setTextColor(
                        ContextCompat.getColor(context, R.color.money_owed_to_you));
            } else {
                holder.yourShare.setText(R.string.expense_you_paid);
                holder.yourShare.setTextColor(holder.neutralColor);
            }
        } else if (yourShare > 0) {
            holder.yourShare.setText(context.getString(R.string.expense_you_owe,
                    Money.format(yourShare)));
            holder.yourShare.setTextColor(ContextCompat.getColor(context, R.color.money_you_owe));
        } else {
            holder.yourShare.setText(R.string.expense_not_involved);
            holder.yourShare.setTextColor(holder.neutralColor);
        }

        holder.itemView.setOnClickListener(v -> onClick.open(expense));
    }

    @Override
    public int getItemCount() {
        return expenses.size();
    }

    private String paidBy(Context context, Expense expense) {
        if (expense.getPayerId().equals(currentUserId)) {
            return context.getString(R.string.expense_paid_by_you);
        }
        User payer = members.get(expense.getPayerId());
        String name = payer != null ? payer.getName()
                : context.getString(R.string.history_former_member);
        return context.getString(R.string.expense_paid_by_name, name);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView description;
        final TextView meta;
        final TextView total;
        final TextView yourShare;
        final ColorStateList neutralColor;

        Holder(View view) {
            super(view);
            description = view.findViewById(R.id.expense_description);
            meta = view.findViewById(R.id.expense_meta);
            total = view.findViewById(R.id.expense_total);
            yourShare = view.findViewById(R.id.expense_your_share);
            neutralColor = meta.getTextColors();
        }
    }
}
