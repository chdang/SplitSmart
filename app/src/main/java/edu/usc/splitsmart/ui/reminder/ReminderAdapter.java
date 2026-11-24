package edu.usc.splitsmart.ui.reminder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.Reminder;
import edu.usc.splitsmart.ui.common.Dates;
import edu.usc.splitsmart.ui.common.Money;

/** One card per reminder. Unread ones are bold with a "New" label. */
final class ReminderAdapter extends RecyclerView.Adapter<ReminderAdapter.Holder> {

    interface OnReminder {
        void handle(Reminder reminder);
    }

    private final List<Reminder> reminders = new ArrayList<>();
    private final OnReminder onOpen;
    private final OnReminder onToggleRead;

    ReminderAdapter(OnReminder onOpen, OnReminder onToggleRead) {
        this.onOpen = onOpen;
        this.onToggleRead = onToggleRead;
    }

    @SuppressLint("NotifyDataSetChanged")
    void submit(List<Reminder> newReminders) {
        reminders.clear();
        reminders.addAll(newReminders);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reminder, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Reminder reminder = reminders.get(position);
        Context context = holder.itemView.getContext();
        boolean unread = !reminder.isRead();

        holder.message.setText(context.getString(R.string.reminder_message,
                reminder.getSenderName(), Money.format(reminder.getAmountCents())));
        holder.message.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
        holder.details.setText(context.getString(R.string.reminder_details,
                reminder.getGroupName(), Dates.shortDate(reminder.getSentAt())));
        holder.unreadLabel.setVisibility(unread ? View.VISIBLE : View.GONE);
        holder.toggle.setText(unread ? R.string.reminder_mark_read : R.string.reminder_mark_unread);
        holder.toggle.setOnClickListener(v -> onToggleRead.handle(reminder));
        holder.itemView.setOnClickListener(v -> onOpen.handle(reminder));
    }

    @Override
    public int getItemCount() {
        return reminders.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView message;
        final TextView details;
        final TextView unreadLabel;
        final Button toggle;

        Holder(View view) {
            super(view);
            message = view.findViewById(R.id.reminder_message);
            details = view.findViewById(R.id.reminder_details);
            unreadLabel = view.findViewById(R.id.reminder_unread);
            toggle = view.findViewById(R.id.reminder_toggle_read);
        }
    }
}
