package com.open.intenter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Saved tab: favorites, launch history and built-in presets, with paste
 * import and JSON file export / import.
 */
public class SavedFragment extends Fragment {

    private static final int TAB_FAVORITES = 0, TAB_HISTORY = 1, TAB_PRESETS = 2;

    private TabLayout tabs;
    private RecyclerView recyclerView;
    private TextView empty;
    private MaterialButton clearButton, exportButton, importButton;
    private final SavedAdapter adapter = new SavedAdapter();
    private int currentTab = TAB_FAVORITES;

    private SavedStore savedStore;
    private HistoryManager history;
    private ActivityResultLauncher<Intent> exportLauncher, importLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exportLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
            writeExport(result.getData().getData());
        });
        importLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
            readImport(result.getData().getData());
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_saved, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        savedStore = SavedStore.get(requireContext());
        history = HistoryManager.getInstance(requireContext());
        tabs = v.findViewById(R.id.savedTabs);
        recyclerView = v.findViewById(R.id.savedRecyclerView);
        empty = v.findViewById(R.id.savedEmpty);
        clearButton = v.findViewById(R.id.clearListButton);
        exportButton = v.findViewById(R.id.exportButton);
        importButton = v.findViewById(R.id.importButton);

        tabs.addTab(tabs.newTab().setText("Favorites"));
        tabs.addTab(tabs.newTab().setText("History"));
        tabs.addTab(tabs.newTab().setText("Presets"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { currentTab = tab.getPosition(); refresh(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        v.findViewById(R.id.pasteButton).setOnClickListener(x -> {
            BuildFragment b = ((MainActivity) requireActivity()).buildFragment();
            ((MainActivity) requireActivity()).selectTab(R.id.nav_build);
            if (b != null) b.showImportDialog();
        });
        exportButton.setOnClickListener(x -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "intenter-export.json");
            try { exportLauncher.launch(i); } catch (RuntimeException e) { UiUtil.showError(requireContext(), UiUtil.describeThrowable(e)); }
        });
        importButton.setOnClickListener(x -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            try { importLauncher.launch(i); } catch (RuntimeException e) { UiUtil.showError(requireContext(), UiUtil.describeThrowable(e)); }
        });
        clearButton.setOnClickListener(x -> confirmClear());
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) refresh();
    }

    private List<IntentModel> currentList() {
        switch (currentTab) {
            case TAB_HISTORY: return history.getAll();
            case TAB_PRESETS: return SavedStore.presets();
            default: return savedStore.favorites();
        }
    }

    private void refresh() {
        if (!isAdded()) return;
        List<IntentModel> list = currentList();
        adapter.submit(list);
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(currentTab == TAB_FAVORITES ? "No favorites yet.\nUse ★ in the toolbar of the Build tab, or star a history entry."
                : currentTab == TAB_HISTORY ? "No launches yet.\nEvery successful launch is recorded here." : "");
        clearButton.setVisibility(currentTab == TAB_PRESETS ? View.GONE : View.VISIBLE);
    }

    private void confirmClear() {
        String what = currentTab == TAB_HISTORY ? "history" : "favorites";
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear " + what)
                .setMessage("Delete all " + what + " entries?")
                .setPositiveButton("Clear", (d, w) -> {
                    if (currentTab == TAB_HISTORY) history.clearAll();
                    else savedStore.replaceFavorites(new ArrayList<>());
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void load(IntentModel m) {
        ((MainActivity) requireActivity()).openBuildWithModel(m.copy());
    }

    private void saveAsFavorite(IntentModel m) {
        View v = getLayoutInflater().inflate(R.layout.dialog_save_favorite, null, false);
        TextInputEditText name = v.findViewById(R.id.favoriteNameInput);
        TextInputEditText note = v.findViewById(R.id.favoriteNoteInput);
        name.setText(m.name.isEmpty() ? m.generateLabel() : m.name);
        note.setText(m.description);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(currentTab == TAB_FAVORITES ? "Rename favorite" : "Save as favorite")
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    IntentModel copy = m.copy();
                    String oldName = m.name;
                    copy.name = UiUtil.text(name).isEmpty() ? m.generateLabel() : UiUtil.text(name);
                    copy.description = UiUtil.text(note);
                    if (currentTab == TAB_FAVORITES && !oldName.equals(copy.name)) {
                        List<IntentModel> favs = savedStore.favorites();
                        for (int i = 0; i < favs.size(); i++) if (favs.get(i).name.equals(oldName)) { favs.remove(i); break; }
                        savedStore.replaceFavorites(favs);
                    }
                    savedStore.saveFavorite(copy);
                    Snackbar.make(requireView(), "Saved to favorites", Snackbar.LENGTH_SHORT).show();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void delete(int position) {
        if (currentTab == TAB_HISTORY) history.delete(position);
        else if (currentTab == TAB_FAVORITES) savedStore.deleteFavorite(position);
        refresh();
    }

    // ─── File export / import ────────────────────────────────────────────

    private void writeExport(Uri uri) {
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IllegalStateException("Could not open file");
            out.write(savedStore.exportAll(history.getAll()).getBytes(StandardCharsets.UTF_8));
            Snackbar.make(requireView(), "Exported favorites and history", Snackbar.LENGTH_SHORT).show();
        } catch (Exception e) {
            UiUtil.showError(requireContext(), "Export failed: " + UiUtil.describeThrowable(e));
        }
    }

    private void readImport(Uri uri) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Could not open file");
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            int count = savedStore.importAll(bos.toString("UTF-8"), history);
            Snackbar.make(requireView(), "Imported " + count + " entr" + (count == 1 ? "y" : "ies"), Snackbar.LENGTH_SHORT).show();
            refresh();
        } catch (Exception e) {
            UiUtil.showError(requireContext(), "Import failed: " + UiUtil.describeThrowable(e));
        }
    }

    // ─── Adapter ─────────────────────────────────────────────────────────

    private class SavedAdapter extends RecyclerView.Adapter<SavedVH> {
        private final List<IntentModel> items = new ArrayList<>();

        void submit(List<IntentModel> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SavedVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SavedVH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SavedVH h, int position) {
            IntentModel m = items.get(position);
            h.badge.setText(IntentModel.modeShortLabel(m.launchType).toUpperCase());
            h.badge.setBackgroundResource(IntentModel.isBroadcastMode(m.launchType) ? R.drawable.bg_badge_tertiary
                    : IntentModel.isServiceMode(m.launchType) ? R.drawable.bg_badge_secondary
                    : IntentModel.MODE_RESOLVE.equals(m.launchType) ? R.drawable.bg_badge_neutral : R.drawable.bg_badge_primary);
            String label = m.label.isEmpty() ? m.generateLabel() : m.label;
            if (currentTab == TAB_HISTORY) {
                h.title.setText(label);
                h.subtitle.setText(UiUtil.relativeTime(m.timestamp) + " · " + summarize(m));
                h.primary.setVisibility(View.VISIBLE);
                h.primary.setIconResource(R.drawable.ic_star_outline);
                h.primary.setContentDescription("Save as favorite");
                h.delete.setVisibility(View.VISIBLE);
            } else if (currentTab == TAB_FAVORITES) {
                h.title.setText(m.name.isEmpty() ? label : m.name);
                h.subtitle.setText(m.description.isEmpty() ? label : m.description + " · " + label);
                h.primary.setVisibility(View.VISIBLE);
                h.primary.setIconResource(R.drawable.ic_edit);
                h.primary.setContentDescription("Rename");
                h.delete.setVisibility(View.VISIBLE);
            } else {
                h.title.setText(m.name);
                h.subtitle.setText(m.description);
                h.primary.setVisibility(View.VISIBLE);
                h.primary.setIconResource(R.drawable.ic_star_outline);
                h.primary.setContentDescription("Save as favorite");
                h.delete.setVisibility(View.GONE);
            }
            h.itemView.setOnClickListener(v -> load(m));
            h.primary.setOnClickListener(v -> saveAsFavorite(m));
            h.delete.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) delete(pos);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static String summarize(IntentModel m) {
        List<String> parts = new ArrayList<>();
        if (m.useExtras && m.extraCount() > 0) parts.add(m.extraCount() + " extras");
        if (m.useFlags && (!m.flagNames.isEmpty() || !m.customFlags.isEmpty())) parts.add("flags");
        if (m.useClipData && !m.clipDataItems.isEmpty()) parts.add("clip");
        if (m.useChooser) parts.add("chooser");
        if (parts.isEmpty()) return IntentModel.modeLabel(m.launchType);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) { if (i > 0) sb.append(", "); sb.append(parts.get(i)); }
        return sb.toString();
    }

    static class SavedVH extends RecyclerView.ViewHolder {
        TextView badge, title, subtitle;
        MaterialButton primary, delete;
        SavedVH(View v) {
            super(v);
            badge = v.findViewById(R.id.savedBadge);
            title = v.findViewById(R.id.savedTitle);
            subtitle = v.findViewById(R.id.savedSubtitle);
            primary = v.findViewById(R.id.savedPrimaryButton);
            delete = v.findViewById(R.id.savedDeleteButton);
        }
    }
}
