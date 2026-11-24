package edu.usc.splitsmart.ui.reminder;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.Reminder;
import edu.usc.splitsmart.ui.balance.IndividualGroupBalanceActivity;
import edu.usc.splitsmart.ui.common.BaseActivity;
import edu.usc.splitsmart.ui.common.Extras;

/**
 * The payment reminders other members have sent the user, newest first. Each can be marked read
 * or unread; tapping one marks it read and opens that group's balances so the user can settle up.
 */
public class RemindersActivity extends BaseActivity {

    private ReminderAdapter adapter;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminders);
        if (!requireLogin()) {
            return;
        }
        setUpToolbar(getString(R.string.reminders_title), true);

        emptyView = findViewById(R.id.empty_view);
        adapter = new ReminderAdapter(this::open, this::toggleRead);
        RecyclerView list = findViewById(R.id.reminder_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentUser != null) {
            load();
        }
    }

    private void load() {
        setLoading(true);
        AppServices.reminders().getReceivedReminders(onUi(reminders -> {
            setLoading(false);
            adapter.submit(reminders);
            emptyView.setVisibility(reminders.isEmpty() ? View.VISIBLE : View.GONE);
        }));
    }

    private void toggleRead(Reminder reminder) {
        boolean read = !reminder.isRead();
        setLoading(true);
        AppServices.reminders().setRead(reminder.getId(), read, onUi(done -> {
            showMessage(getString(read ? R.string.reminder_marked_read
                    : R.string.reminder_marked_unread));
            load();
        }));
    }

    private void open(Reminder reminder) {
        Intent balances = new Intent(this, IndividualGroupBalanceActivity.class)
                .putExtra(Extras.GROUP_ID, reminder.getGroupId());
        if (reminder.isRead()) {
            startActivity(balances);
            return;
        }
        // Opening a reminder reads it. Navigate even if that fails; the inbox reloads on return.
        AppServices.reminders().setRead(reminder.getId(), true,
                onUi(done -> startActivity(balances), error -> startActivity(balances)));
    }
}
