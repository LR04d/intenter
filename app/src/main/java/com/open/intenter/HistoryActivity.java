package com.open.intenter;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays launch history. Tapping an item returns it to MainActivity for re-loading.
 */
public class HistoryActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_MODEL_JSON = "selected_model_json";

    private HistoryManager historyManager;
    private HistoryAdapter adapter;
    private List<IntentModel> historyList = new ArrayList<>();
    private LinearLayout emptyStateLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        historyManager = HistoryManager.getInstance(this);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.historyToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_history) {
                confirmClearAll();
                return true;
            }
            return false;
        });

        emptyStateLayout = findViewById(R.id.emptyStateLayout);

        RecyclerView rv = findViewById(R.id.historyRecyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        rv.setAdapter(adapter);

        loadHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        historyList.clear();
        historyList.addAll(historyManager.getAll());
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (historyList.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Delete all history entries?")
                .setPositiveButton("Clear", (d, w) -> {
                    historyManager.clearAll();
                    loadHistory();
                    Snackbar.make(findViewById(android.R.id.content), "History cleared", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteEntry(int position) {
        historyManager.delete(position);
        historyList.remove(position);
        adapter.notifyItemRemoved(position);
        updateEmptyState();
    }

    private void selectEntry(int position) {
        IntentModel model = historyList.get(position);
        try {
            Intent data = new Intent();
            data.putExtra(EXTRA_SELECTED_MODEL_JSON, model.toJson().toString());
            setResult(RESULT_OK, data);
            finish();
        } catch (JSONException e) {
            Snackbar.make(findViewById(android.R.id.content), "Error: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
        }
    }

    // ─── Adapter ─────────────────────────────────────────────────────────

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.history_item, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            IntentModel model = historyList.get(position);
            holder.label.setText(model.label.isEmpty() ? model.generateLabel() : model.label);

            // Relative timestamp
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    model.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            holder.timestamp.setText(relativeTime);

            // Launch type display
            String lt = model.launchType;
            String ltDisplay;
            String icon;
            switch (lt) {
                case "chipService":
                    ltDisplay = "Service";
                    icon = "⚙️";
                    break;
                case "chipFgService":
                    ltDisplay = "FG Service";
                    icon = "🔧";
                    break;
                case "chipBroadcast":
                    ltDisplay = "Broadcast";
                    icon = "📡";
                    break;
                case "chipActivityResult":
                    ltDisplay = "For Result";
                    icon = "🔄";
                    break;
                default:
                    ltDisplay = "Activity";
                    icon = "🚀";
                    break;
            }
            holder.launchType.setText(ltDisplay);
            holder.icon.setText(icon);

            holder.itemView.setOnClickListener(v -> selectEntry(holder.getAdapterPosition()));
            holder.deleteButton.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    deleteEntry(pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return historyList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView label, timestamp, launchType, icon;
            MaterialButton deleteButton;

            VH(View v) {
                super(v);
                label = v.findViewById(R.id.historyLabel);
                timestamp = v.findViewById(R.id.historyTimestamp);
                launchType = v.findViewById(R.id.historyLaunchType);
                icon = v.findViewById(R.id.historyIcon);
                deleteButton = v.findViewById(R.id.historyDeleteButton);
            }
        }
    }
}

