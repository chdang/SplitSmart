package edu.usc.splitsmart.ui.report;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.ActivityItem;
import edu.usc.splitsmart.model.Expense;
import edu.usc.splitsmart.model.Settlement;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.Dates;
import edu.usc.splitsmart.ui.common.Labels;
import edu.usc.splitsmart.ui.common.Money;

/** Rows for the group history: expenses and payments, newest first. */
final class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {

    private final List<ActivityItem> items = new ArrayList<>();
    private final Map<String, User> members = new HashMap<>();
    private final String currentUserId;

    HistoryAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    @SuppressLint("NotifyDataSetChanged")
    void submit(List<User> groupMembers, List<ActivityItem> newItems) {
        members.clear();
        for (User member : groupMembers) {
            members.put(member.getId(), member);
        }
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ActivityItem item = items.get(position);
        Context context = holder.itemView.getContext();
        String date = Dates.shortDate(item.getOccurredAt());
        if (item.getType() == ActivityItem.Type.EXPENSE) {
            Expense expense = item.getExpense();
            holder.type.setText(R.string.history_type_expense);
            holder.title.setText(expense.getDescription());
            String payer = expense.getPayerId().equals(currentUserId)
                    ? context.getString(R.string.history_you_object)
                    : nameOf(context, expense.getPayerId());
            holder.subtitle.setText(context.getString(R.string.history_expense_meta, payer, date));
            holder.amount.setText(Money.format(expense.getTotalCents()));
        } else {
            Settlement settlement = item.getSettlement();
            holder.type.setText(R.string.history_type_payment);
            String receiver = settlement.getReceiverId().equals(currentUserId)
                    ? context.getString(R.string.history_you_object)
                    : nameOf(context, settlement.getReceiverId());
            holder.title.setText(context.getString(R.string.history_payment,
                    nameOf(context, settlement.getPayerId()), receiver));
            holder.subtitle.setText(date);
            holder.amount.setText(Money.format(settlement.getAmountCents()));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** "You" for the current user, the member's name otherwise. */
    private String nameOf(Context context, String userId) {
        User user = members.get(userId);
        if (user == null) {
            return context.getString(R.string.history_former_member);
        }
        return Labels.personOrYou(context, user, currentUserId);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView type;
        final TextView title;
        final TextView subtitle;
        final TextView amount;

        Holder(View view) {
            super(view);
            type = view.findViewById(R.id.history_type);
            title = view.findViewById(R.id.history_title);
            subtitle = view.findViewById(R.id.history_subtitle);
            amount = view.findViewById(R.id.history_amount);
        }
    }
}
