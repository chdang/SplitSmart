package edu.usc.splitsmart.ui.common;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Adapter for Material dropdown fields. A plain ArrayAdapter filters by the field's current
 * text, so after a value is picked (or the screen rotates) the list would show only that value.
 * This one always shows every option.
 */
public final class DropdownAdapter extends ArrayAdapter<String> {
    private final List<String> options;

    public DropdownAdapter(Context context, List<String> options) {
        super(context, android.R.layout.simple_list_item_1, options);
        this.options = options;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                results.values = options;
                results.count = options.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };
    }
}
