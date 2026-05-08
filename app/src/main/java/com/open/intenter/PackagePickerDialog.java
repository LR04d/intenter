package com.open.intenter;

import android.app.Dialog;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bottom-sheet dialog to pick an installed package and optionally an activity within it.
 */
public class PackagePickerDialog extends BottomSheetDialogFragment {

    public interface OnPackageSelectedListener {
        void onPackageSelected(String packageName, @Nullable String activityClassName);
    }

    private OnPackageSelectedListener listener;
    private PackageManager pm;

    private RecyclerView recyclerView;
    private TextInputEditText searchInput;
    private LinearProgressIndicator loadingIndicator;

    private List<AppEntry> allApps = new ArrayList<>();
    private List<AppEntry> filteredApps = new ArrayList<>();
    private PackageAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void setOnPackageSelectedListener(OnPackageSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Expand the bottom sheet fully
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetDialog bsd = (BottomSheetDialog) dialog;
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_package_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pm = requireContext().getPackageManager();
        searchInput = view.findViewById(R.id.searchInput);
        loadingIndicator = view.findViewById(R.id.loadingIndicator);
        recyclerView = view.findViewById(R.id.packageRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new PackageAdapter();
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                filter(s.toString());
            }
        });

        loadPackages();
    }

    private void loadPackages() {
        loadingIndicator.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppEntry> entries = new ArrayList<>();
            for (ApplicationInfo info : apps) {
                AppEntry entry = new AppEntry();
                entry.packageName = info.packageName;
                entry.label = pm.getApplicationLabel(info).toString();
                entry.icon = info.loadIcon(pm);
                entries.add(entry);
            }
            Collections.sort(entries, (a, b) -> a.label.compareToIgnoreCase(b.label));
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    allApps.clear();
                    allApps.addAll(entries);
                    filteredApps.clear();
                    filteredApps.addAll(entries);
                    adapter.notifyDataSetChanged();
                    loadingIndicator.setVisibility(View.GONE);
                });
            }
        });
    }

    private void filter(String query) {
        filteredApps.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            filteredApps.addAll(allApps);
        } else {
            for (AppEntry e : allApps) {
                if (e.label.toLowerCase(Locale.ROOT).contains(q) || e.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                    filteredApps.add(e);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void onPackageTapped(String packageName) {
        // Try to list activities for this package and let user pick
        try {
            PackageInfo pkgInfo = pm.getPackageInfo(packageName,
                    PackageManager.GET_ACTIVITIES | PackageManager.MATCH_DISABLED_COMPONENTS);
            ActivityInfo[] activities = pkgInfo.activities;
            if (activities != null && activities.length > 0) {
                showActivityPicker(packageName, activities);
                return;
            }
        } catch (PackageManager.NameNotFoundException ignored) {}

        // No activities found — just return the package
        if (listener != null) {
            listener.onPackageSelected(packageName, null);
        }
        dismiss();
    }

    private void showActivityPicker(String packageName, ActivityInfo[] activities) {
        // Replace the current list with activity names
        searchInput.setText("");
        searchInput.setHint("Pick activity from " + packageName);

        // Build list
        List<String> activityNames = new ArrayList<>();
        for (ActivityInfo ai : activities) {
            activityNames.add(ai.name);
        }
        Collections.sort(activityNames);

        // Swap adapter to activity list
        ActivityAdapter actAdapter = new ActivityAdapter(activityNames, activityName -> {
            if (listener != null) {
                listener.onPackageSelected(packageName, activityName);
            }
            dismiss();
        });
        recyclerView.setAdapter(actAdapter);

        // Also add option to select package only
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().toLowerCase(Locale.ROOT).trim();
                List<String> filtered = new ArrayList<>();
                for (String name : activityNames) {
                    if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q)) {
                        filtered.add(name);
                    }
                }
                recyclerView.setAdapter(new ActivityAdapter(filtered, activityName -> {
                    if (listener != null) {
                        listener.onPackageSelected(packageName, activityName);
                    }
                    dismiss();
                }));
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // ─── Data class ──────────────────────────────────────────────────────

    static class AppEntry {
        String packageName;
        String label;
        Drawable icon;
    }

    // ─── Package Adapter ─────────────────────────────────────────────────

    private class PackageAdapter extends RecyclerView.Adapter<PackageAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.package_item, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            AppEntry entry = filteredApps.get(position);
            holder.label.setText(entry.label);
            holder.pkgName.setText(entry.packageName);
            holder.icon.setImageDrawable(entry.icon);
            holder.itemView.setOnClickListener(v -> onPackageTapped(entry.packageName));
        }

        @Override
        public int getItemCount() {
            return filteredApps.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView label, pkgName;

            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.packageIcon);
                label = v.findViewById(R.id.packageLabel);
                pkgName = v.findViewById(R.id.packageName);
            }
        }
    }

    // ─── Activity Adapter ────────────────────────────────────────────────

    interface OnActivitySelected {
        void onSelected(String activityClassName);
    }

    private static class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.VH> {
        private final List<String> activities;
        private final OnActivitySelected callback;

        ActivityAdapter(List<String> activities, OnActivitySelected callback) {
            this.activities = activities;
            this.callback = callback;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_picker_item, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String name = activities.get(position);
            holder.className.setText(name);
            holder.itemView.setOnClickListener(v -> callback.onSelected(name));
        }

        @Override
        public int getItemCount() {
            return activities.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView className;

            VH(View v) {
                super(v);
                className = v.findViewById(R.id.activityClassName);
            }
        }
    }
}

