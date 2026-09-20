package com.open.intenter;

import android.app.Dialog;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bottom sheet that lists installed apps and, for a chosen app, all of its
 * activities, services, receivers and providers with their exported state
 * and permissions.
 */
public class ComponentPickerDialog extends BottomSheetDialogFragment {

    public enum Kind {
        ACTIVITY("ACT", "Activity"), SERVICE("SVC", "Service"), RECEIVER("RCV", "Receiver"), PROVIDER("PRV", "Provider");
        public final String badge;
        public final String label;
        Kind(String badge, String label) { this.badge = badge; this.label = label; }
    }

    public static class ComponentEntry {
        public Kind kind;
        public String packageName;
        public String className;
        public boolean exported;
        public boolean enabled = true;
        public String permission;        // permission / readPermission
        public String writePermission;   // providers
        public String authority;         // providers
        public boolean grantUriPermissions;
        public String label;

        public String shortName() {
            if (className == null) return "";
            int dot = className.lastIndexOf('.');
            return dot >= 0 ? className.substring(dot + 1) : className;
        }
    }

    public interface Listener {
        void onPackageOnly(String packageName);
        void onComponentSelected(ComponentEntry entry);
    }

    private static final String ARG_PROVIDERS_ONLY = "providersOnly";

    public static ComponentPickerDialog newInstance(boolean providersOnly) {
        ComponentPickerDialog d = new ComponentPickerDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_PROVIDERS_ONLY, providersOnly);
        d.setArguments(args);
        return d;
    }

    static class AppEntry {
        String packageName;
        String label;
        Drawable icon;
        boolean system;
    }

    private Listener listener;
    private PackageManager pm;
    private boolean providersOnly;

    private RecyclerView recyclerView;
    private TextInputEditText searchInput;
    private LinearProgressIndicator loadingIndicator;
    private View backButton;
    private TextView title, subtitle;
    private Chip chipActivity, chipService, chipReceiver, chipProvider, chipExportedOnly, chipHideSystem;

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<ComponentEntry> allComponents = new ArrayList<>();
    private String currentPackage;
    private final PickerAdapter adapter = new PickerAdapter();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            View sheet = ((BottomSheetDialog) dialog).findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                sheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_component_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pm = requireContext().getPackageManager();
        providersOnly = getArguments() != null && getArguments().getBoolean(ARG_PROVIDERS_ONLY, false);

        searchInput = view.findViewById(R.id.searchInput);
        loadingIndicator = view.findViewById(R.id.loadingIndicator);
        recyclerView = view.findViewById(R.id.packageRecyclerView);
        backButton = view.findViewById(R.id.pickerBackButton);
        title = view.findViewById(R.id.pickerTitle);
        subtitle = view.findViewById(R.id.pickerSubtitle);
        chipActivity = view.findViewById(R.id.chipKindActivity);
        chipService = view.findViewById(R.id.chipKindService);
        chipReceiver = view.findViewById(R.id.chipKindReceiver);
        chipProvider = view.findViewById(R.id.chipKindProvider);
        chipExportedOnly = view.findViewById(R.id.chipExportedOnly);
        chipHideSystem = view.findViewById(R.id.chipHideSystem);

        if (providersOnly) {
            title.setText("Choose provider");
            chipActivity.setVisibility(View.GONE);
            chipService.setVisibility(View.GONE);
            chipReceiver.setVisibility(View.GONE);
            chipProvider.setVisibility(View.GONE);
            chipActivity.setChecked(false);
            chipService.setChecked(false);
            chipReceiver.setChecked(false);
            chipProvider.setChecked(true);
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(UiUtil.watcher(this::refresh));
        for (Chip c : new Chip[]{chipActivity, chipService, chipReceiver, chipProvider, chipExportedOnly, chipHideSystem}) {
            c.setOnCheckedChangeListener((b, on) -> refresh());
        }
        backButton.setOnClickListener(v -> showApps());
        loadApps();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // ─── Loading ─────────────────────────────────────────────────────────

    private void loadApps() {
        loadingIndicator.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<AppEntry> entries = new ArrayList<>();
            try {
                for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                    AppEntry e = new AppEntry();
                    e.packageName = info.packageName;
                    e.label = String.valueOf(pm.getApplicationLabel(info));
                    e.system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    try { e.icon = info.loadIcon(pm); } catch (RuntimeException ignored) {}
                    entries.add(e);
                }
            } catch (RuntimeException ignored) {}
            Collections.sort(entries, (a, b) -> a.label.compareToIgnoreCase(b.label));
            View v = getView();
            if (v != null) v.post(() -> {
                allApps.clear();
                allApps.addAll(entries);
                loadingIndicator.setVisibility(View.GONE);
                refresh();
            });
        });
    }

    private void loadComponents(String packageName) {
        loadingIndicator.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<ComponentEntry> list = new ArrayList<>();
            int base = PackageManager.MATCH_DISABLED_COMPONENTS;
            PackageInfo pi = null;
            try {
                pi = pm.getPackageInfo(packageName, base | PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                        | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS);
                collect(list, pi);
            } catch (Exception tooLarge) {
                // Huge apps can overflow the binder transaction; fetch each kind separately.
                int[] flags = {PackageManager.GET_ACTIVITIES, PackageManager.GET_SERVICES,
                        PackageManager.GET_RECEIVERS, PackageManager.GET_PROVIDERS};
                for (int f : flags) {
                    try { collect(list, pm.getPackageInfo(packageName, base | f)); } catch (Exception ignored) {}
                }
            }
            Collections.sort(list, (a, b) -> {
                int k = a.kind.compareTo(b.kind);
                if (k != 0) return k;
                if (a.exported != b.exported) return a.exported ? -1 : 1;
                return a.className.compareToIgnoreCase(b.className);
            });
            View v = getView();
            if (v != null) v.post(() -> {
                allComponents.clear();
                allComponents.addAll(list);
                loadingIndicator.setVisibility(View.GONE);
                refresh();
            });
        });
    }

    private void collect(List<ComponentEntry> out, PackageInfo pi) {
        if (pi.activities != null) for (ActivityInfo ai : pi.activities) {
            ComponentEntry e = base(Kind.ACTIVITY, ai.packageName, ai.name, ai.exported, ai.enabled);
            e.permission = ai.permission;
            out.add(e);
        }
        if (pi.services != null) for (ServiceInfo si : pi.services) {
            ComponentEntry e = base(Kind.SERVICE, si.packageName, si.name, si.exported, si.enabled);
            e.permission = si.permission;
            out.add(e);
        }
        if (pi.receivers != null) for (ActivityInfo ri : pi.receivers) {
            ComponentEntry e = base(Kind.RECEIVER, ri.packageName, ri.name, ri.exported, ri.enabled);
            e.permission = ri.permission;
            out.add(e);
        }
        if (pi.providers != null) for (ProviderInfo prv : pi.providers) {
            ComponentEntry e = base(Kind.PROVIDER, prv.packageName, prv.name, prv.exported, prv.enabled);
            e.permission = prv.readPermission;
            e.writePermission = prv.writePermission;
            e.authority = prv.authority;
            e.grantUriPermissions = prv.grantUriPermissions;
            out.add(e);
        }
    }

    private static ComponentEntry base(Kind kind, String pkg, String name, boolean exported, boolean enabled) {
        ComponentEntry e = new ComponentEntry();
        e.kind = kind;
        e.packageName = pkg;
        e.className = name;
        e.exported = exported;
        e.enabled = enabled;
        return e;
    }

    // ─── Navigation / filtering ──────────────────────────────────────────

    private void showApps() {
        currentPackage = null;
        allComponents.clear();
        backButton.setVisibility(View.GONE);
        title.setText(providersOnly ? "Choose provider" : "Choose target");
        subtitle.setText(providersOnly ? "Pick the app that owns the provider" : "Pick an app, then one of its components");
        searchInput.setText("");
        refresh();
    }

    private void onAppTapped(AppEntry app) {
        currentPackage = app.packageName;
        backButton.setVisibility(View.VISIBLE);
        title.setText(app.label);
        subtitle.setText(app.packageName);
        searchInput.setText("");
        loadComponents(app.packageName);
    }

    private void refresh() {
        String q = UiUtil.text(searchInput).toLowerCase(Locale.ROOT);
        List<Object> items = new ArrayList<>();
        if (currentPackage == null) {
            for (AppEntry e : allApps) {
                if (chipHideSystem.isChecked() && e.system) continue;
                if (!q.isEmpty() && !e.label.toLowerCase(Locale.ROOT).contains(q)
                        && !e.packageName.toLowerCase(Locale.ROOT).contains(q)) continue;
                items.add(e);
            }
        } else {
            if (!providersOnly) items.add(PACKAGE_ONLY);
            for (ComponentEntry c : allComponents) {
                if (!kindEnabled(c.kind)) continue;
                if (chipExportedOnly.isChecked() && !c.exported) continue;
                if (!q.isEmpty() && !c.className.toLowerCase(Locale.ROOT).contains(q)
                        && (c.authority == null || !c.authority.toLowerCase(Locale.ROOT).contains(q))) continue;
                items.add(c);
            }
            if (items.size() <= (providersOnly ? 0 : 1) && allComponents.isEmpty() && loadingIndicator.getVisibility() != View.VISIBLE) {
                items.add(EMPTY);
            }
        }
        adapter.submit(items);
    }

    private boolean kindEnabled(Kind k) {
        switch (k) {
            case ACTIVITY: return chipActivity.isChecked();
            case SERVICE: return chipService.isChecked();
            case RECEIVER: return chipReceiver.isChecked();
            default: return chipProvider.isChecked();
        }
    }

    private static final Object PACKAGE_ONLY = new Object();
    private static final Object EMPTY = new Object();

    // ─── Adapter ─────────────────────────────────────────────────────────

    private static final int TYPE_APP = 0, TYPE_COMPONENT = 1, TYPE_SPECIAL = 2;

    private class PickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Object> items = new ArrayList<>();

        void submit(List<Object> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object o = items.get(position);
            if (o instanceof AppEntry) return TYPE_APP;
            if (o instanceof ComponentEntry) return TYPE_COMPONENT;
            return TYPE_SPECIAL;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_APP) return new AppVH(inf.inflate(R.layout.package_item, parent, false));
            return new ComponentVH(inf.inflate(R.layout.component_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object o = items.get(position);
            if (holder instanceof AppVH) {
                AppEntry e = (AppEntry) o;
                AppVH h = (AppVH) holder;
                h.label.setText(e.label);
                h.pkg.setText(e.packageName + (e.system ? "  · system" : ""));
                h.icon.setImageDrawable(e.icon);
                h.itemView.setOnClickListener(v -> onAppTapped(e));
                return;
            }
            ComponentVH h = (ComponentVH) holder;
            if (o == PACKAGE_ONLY) {
                h.kind.setText("PKG");
                h.kind.setBackgroundResource(R.drawable.bg_badge_primary);
                h.shortName.setText("Use package only");
                h.fullName.setText(currentPackage);
                h.meta.setText("Implicit intent limited to this package (setPackage)");
                h.exported.setVisibility(View.GONE);
                h.itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onPackageOnly(currentPackage);
                    dismiss();
                });
                return;
            }
            if (o == EMPTY) {
                h.kind.setText("—");
                h.kind.setBackgroundResource(R.drawable.bg_badge_neutral);
                h.shortName.setText("No components found");
                h.fullName.setText("");
                h.meta.setText("The package may be restricted or have no matching components");
                h.exported.setVisibility(View.GONE);
                h.itemView.setOnClickListener(null);
                return;
            }
            ComponentEntry c = (ComponentEntry) o;
            h.kind.setText(c.kind.badge);
            switch (c.kind) {
                case ACTIVITY: h.kind.setBackgroundResource(R.drawable.bg_badge_primary); break;
                case SERVICE: h.kind.setBackgroundResource(R.drawable.bg_badge_secondary); break;
                case RECEIVER: h.kind.setBackgroundResource(R.drawable.bg_badge_tertiary); break;
                default: h.kind.setBackgroundResource(R.drawable.bg_badge_neutral); break;
            }
            h.shortName.setText(c.shortName());
            h.fullName.setText(c.kind == Kind.PROVIDER && c.authority != null ? c.authority : c.className);
            StringBuilder meta = new StringBuilder();
            if (!c.enabled) meta.append("disabled · ");
            if (c.kind == Kind.PROVIDER) {
                meta.append("read: ").append(c.permission == null ? "none" : c.permission);
                meta.append(" · write: ").append(c.writePermission == null ? "none" : c.writePermission);
                if (c.grantUriPermissions) meta.append(" · grants URI");
            } else if (c.permission != null) {
                meta.append("permission: ").append(c.permission);
            } else {
                meta.append(c.exported ? "reachable from other apps" : "not reachable from other apps");
            }
            h.meta.setText(meta.toString());
            h.exported.setVisibility(View.VISIBLE);
            h.exported.setText(c.exported ? "exported" : "private");
            h.exported.setBackgroundResource(c.exported ? R.drawable.bg_badge_success : R.drawable.bg_badge_neutral);
            h.exported.setTextColor(requireContext().getColor(c.exported ? R.color.app_success : R.color.app_on_surface_variant));
            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onComponentSelected(c);
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    static class AppVH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView label, pkg;
        AppVH(View v) {
            super(v);
            icon = v.findViewById(R.id.packageIcon);
            label = v.findViewById(R.id.packageLabel);
            pkg = v.findViewById(R.id.packageName);
        }
    }

    static class ComponentVH extends RecyclerView.ViewHolder {
        TextView kind, shortName, fullName, meta, exported;
        ComponentVH(View v) {
            super(v);
            kind = v.findViewById(R.id.componentKind);
            shortName = v.findViewById(R.id.componentShortName);
            fullName = v.findViewById(R.id.componentFullName);
            meta = v.findViewById(R.id.componentMeta);
            exported = v.findViewById(R.id.componentExported);
        }
    }
}
