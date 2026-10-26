package edu.usc.splitsmart.ui.balance;

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
import java.util.List;

import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.User;
import edu.usc.splitsmart.ui.common.Money;

/** One row per other member, showing the user's net balance with them. */
final class MemberBalanceAdapter extends RecyclerView.Adapter<MemberBalanceAdapter.Holder> {

    /** A member and the user's net balance with them. Positive means they owe the user. */
    static final class Row {
        final User member;
        final long netCents;

        Row(User member, long netCents) {
            this.member = member;
            this.netCents = netCents;
        }
    }

    interface OnSettleUp {
        void settleUp(User member, long owedCents);
    }

    private final List<Row> rows = new ArrayList<>();
    private final OnSettleUp onSettleUp;

    MemberBalanceAdapter(OnSettleUp onSettleUp) {
        this.onSettleUp = onSettleUp;
    }

    @SuppressLint("NotifyDataSetChanged")
    void submit(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_member_balance, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = rows.get(position);
        Context context = holder.itemView.getContext();
        holder.name.setText(row.member.getName());
        if (row.netCents > 0) {
            holder.status.setText(context.getString(R.string.individual_owes_you,
                    Money.format(row.netCents)));
            holder.status.setTextColor(ContextCompat.getColor(context, R.color.money_owed_to_you));
            holder.settleButton.setVisibility(View.GONE);
        } else if (row.netCents < 0) {
            holder.status.setText(context.getString(R.string.individual_you_owe,
                    Money.format(-row.netCents)));
            holder.status.setTextColor(ContextCompat.getColor(context, R.color.money_you_owe));
            holder.settleButton.setVisibility(View.VISIBLE);
            holder.settleButton.setOnClickListener(v ->
                    onSettleUp.settleUp(row.member, -row.netCents));
        } else {
            holder.status.setText(R.string.individual_even);
            holder.status.setTextColor(holder.neutralColor);
            holder.settleButton.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView status;
        final View settleButton;
        final ColorStateList neutralColor;

        Holder(View view) {
            super(view);
            name = view.findViewById(R.id.member_name);
            status = view.findViewById(R.id.balance_status);
            settleButton = view.findViewById(R.id.settle_button);
            neutralColor = status.getTextColors();
        }
    }
}
