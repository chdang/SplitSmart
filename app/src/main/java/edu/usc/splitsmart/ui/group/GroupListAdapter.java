package edu.usc.splitsmart.ui.group;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import edu.usc.splitsmart.R;
import edu.usc.splitsmart.model.Group;

/** A card for each group the user belongs to. */
final class GroupListAdapter extends RecyclerView.Adapter<GroupListAdapter.Holder> {

    interface OnGroupClick {
        void open(Group group);
    }

    private final List<Group> groups = new ArrayList<>();
    private final OnGroupClick onClick;

    GroupListAdapter(OnGroupClick onClick) {
        this.onClick = onClick;
    }

    @SuppressLint("NotifyDataSetChanged")
    void submit(List<Group> newGroups) {
        groups.clear();
        groups.addAll(newGroups);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Group group = groups.get(position);
        holder.name.setText(group.getName());
        holder.description.setText(group.getDescription());
        holder.description.setVisibility(
                group.getDescription().isEmpty() ? View.GONE : View.VISIBLE);
        holder.itemView.setOnClickListener(v -> onClick.open(group));
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView description;

        Holder(View view) {
            super(view);
            name = view.findViewById(R.id.group_name);
            description = view.findViewById(R.id.group_description);
        }
    }
}
