package com.open.intenter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "INTENTER";

    // ── Component ──────────────────────────────────────────────────────────
    private MaterialSwitch useComponent;
    private LinearLayout componentLayout;
    private TextInputEditText packageInput, componentInput;
    private TextInputLayout tilPackage, tilComponent;

    // ── Action ─────────────────────────────────────────────────────────────
    private MaterialSwitch useActions;
    private LinearLayout actionsLayout;
    private MaterialAutoCompleteTextView actionInput;

    // ── Data ───────────────────────────────────────────────────────────────
    private MaterialSwitch useData;
    private LinearLayout dataLayout;
    private TextInputEditText dataUriInput;
    private MaterialAutoCompleteTextView dataTypeInput;

    // ── Categories ─────────────────────────────────────────────────────────
    private MaterialSwitch useCategory;
    private LinearLayout categoriesLayout, categoriesContainer;
    private List<CategoryItem> categoriesList = new ArrayList<>();

    // ── Extras ─────────────────────────────────────────────────────────────
    private MaterialSwitch useExtras;
    private LinearLayout extrasLayout, extrasContainer;
    private List<ExtraItem> extrasList = new ArrayList<>();

    // ── Bundles ────────────────────────────────────────────────────────────
    private MaterialSwitch useBundle;
    private LinearLayout bundlesLayout, bundlesContainer;
    private List<BundleItem> bundlesList = new ArrayList<>();

    // ── Flags ──────────────────────────────────────────────────────────────
    private MaterialSwitch useFlags;
    private LinearLayout flagsLayout;
    private ChipGroup flagsChipGroup;
    private TextInputEditText customFlagsInput;

    // ── Chooser ────────────────────────────────────────────────────────────
    private MaterialSwitch useChooser;
    private LinearLayout chooserLayout;
    private TextInputEditText chooserTitleInput;

    // ── Launch Type ────────────────────────────────────────────────────────
    private ChipGroup launchTypeChipGroup;

    // ── Preview ────────────────────────────────────────────────────────────
    private TextView intentPreviewText;
    private NestedScrollView nestedScrollView;
    private View bottomActionBar;
    private int bottomActionBarBasePaddingBottom;
    private int nestedScrollBasePaddingBottom;

    // ── History ────────────────────────────────────────────────────────────
    private HistoryManager historyManager;
    private ActivityResultLauncher<Intent> historyLauncher;

    // ── Activity for Result ────────────────────────────────────────────────
    private ActivityResultLauncher<Intent> activityResultLauncher;

    // Flag name → flag value mapping
    private static final Map<String, Integer> FLAG_MAP = new HashMap<>();
    static {
        FLAG_MAP.put("FLAG_ACTIVITY_NEW_TASK",             Intent.FLAG_ACTIVITY_NEW_TASK);
        FLAG_MAP.put("FLAG_ACTIVITY_CLEAR_TOP",            Intent.FLAG_ACTIVITY_CLEAR_TOP);
        FLAG_MAP.put("FLAG_ACTIVITY_SINGLE_TOP",           Intent.FLAG_ACTIVITY_SINGLE_TOP);
        FLAG_MAP.put("FLAG_ACTIVITY_NO_HISTORY",           Intent.FLAG_ACTIVITY_NO_HISTORY);
        FLAG_MAP.put("FLAG_ACTIVITY_REORDER_TO_FRONT",     Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        FLAG_MAP.put("FLAG_ACTIVITY_CLEAR_TASK",           Intent.FLAG_ACTIVITY_CLEAR_TASK);
        FLAG_MAP.put("FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS", Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        FLAG_MAP.put("FLAG_ACTIVITY_FORWARD_RESULT",       Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        FLAG_MAP.put("FLAG_ACTIVITY_BROUGHT_TO_FRONT",     Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        FLAG_MAP.put("FLAG_ACTIVITY_MULTIPLE_TASK",        Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        FLAG_MAP.put("FLAG_ACTIVITY_NEW_DOCUMENT",         Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        FLAG_MAP.put("FLAG_ACTIVITY_NO_ANIMATION",         Intent.FLAG_ACTIVITY_NO_ANIMATION);
        FLAG_MAP.put("FLAG_ACTIVITY_NO_USER_ACTION",       Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        FLAG_MAP.put("FLAG_ACTIVITY_PREVIOUS_IS_TOP",      Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        FLAG_MAP.put("FLAG_ACTIVITY_TASK_ON_HOME",         Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        FLAG_MAP.put("FLAG_ACTIVITY_RETAIN_IN_RECENTS",    Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
        FLAG_MAP.put("FLAG_ACTIVITY_LAUNCH_ADJACENT",      Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        FLAG_MAP.put("FLAG_GRANT_READ_URI_PERMISSION",     Intent.FLAG_GRANT_READ_URI_PERMISSION);
        FLAG_MAP.put("FLAG_GRANT_WRITE_URI_PERMISSION",    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        FLAG_MAP.put("FLAG_GRANT_PERSISTABLE_URI_PERMISSION", Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        FLAG_MAP.put("FLAG_GRANT_PREFIX_URI_PERMISSION",   Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        FLAG_MAP.put("FLAG_RECEIVER_FOREGROUND",           Intent.FLAG_RECEIVER_FOREGROUND);
        FLAG_MAP.put("FLAG_RECEIVER_NO_ABORT",             Intent.FLAG_RECEIVER_NO_ABORT);
        FLAG_MAP.put("FLAG_INCLUDE_STOPPED_PACKAGES",      Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        FLAG_MAP.put("FLAG_EXCLUDE_STOPPED_PACKAGES",      Intent.FLAG_EXCLUDE_STOPPED_PACKAGES);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        historyManager = HistoryManager.getInstance(this);

        // Register activity-for-result launcher
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    String msg = "Activity result: code=" + result.getResultCode();
                    if (result.getData() != null) {
                        msg += "\nData: " + result.getData().toUri(Intent.URI_INTENT_SCHEME);
                    }
                    Log.d(TAG, msg);
                    new AlertDialog.Builder(this)
                            .setTitle("Activity Result")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show();
                });

        // Register history launcher
        historyLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String json = result.getData().getStringExtra(HistoryActivity.EXTRA_SELECTED_MODEL_JSON);
                        if (json != null) {
                            try {
                                IntentModel model = IntentModel.fromJson(new JSONObject(json));
                                loadFromModel(model);
                                Snackbar.make(findViewById(android.R.id.content),
                                        "Loaded from history", Snackbar.LENGTH_SHORT).show();
                            } catch (JSONException e) {
                                showError("Failed to load history entry: " + e.getMessage());
                            }
                        }
                    }
                });

        initViews();
        setupToolbar();
        setupSwitchListeners();
        setupFlagsChips();
        setupActionAutocomplete();
        setupMimeTypeAutocomplete();
        setupPreviewWatchers();
        setupKeyboardAwareLayout();

        // Default: Component on
        useComponent.setChecked(true);
        componentLayout.setVisibility(View.VISIBLE);

        // Buttons
        findViewById(R.id.addCategoryButton).setOnClickListener(v -> addNewCategory());
        findViewById(R.id.addExtraButton).setOnClickListener(v -> addNewExtra());
        findViewById(R.id.addBundleButton).setOnClickListener(v -> addNewBundle());
        findViewById(R.id.launchButton).setOnClickListener(v -> launchIntent());
        findViewById(R.id.copyIntentButton).setOnClickListener(v -> copyIntentUri());
        findViewById(R.id.browsePackageButton).setOnClickListener(v -> showPackagePicker());
        updatePreview();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Initialisation
    // ══════════════════════════════════════════════════════════════════════

    private void initViews() {
        useComponent    = findViewById(R.id.useComponent);
        componentLayout = findViewById(R.id.componentLayout);
        packageInput    = findViewById(R.id.packageInput);
        componentInput  = findViewById(R.id.componentInput);
        tilPackage      = findViewById(R.id.tilPackage);
        tilComponent    = findViewById(R.id.tilComponent);

        useActions   = findViewById(R.id.useActions);
        actionsLayout = findViewById(R.id.actionsLayout);
        actionInput  = findViewById(R.id.actionInput);

        useData      = findViewById(R.id.useData);
        dataLayout   = findViewById(R.id.dataLayout);
        dataUriInput = findViewById(R.id.dataUriInput);
        dataTypeInput = findViewById(R.id.dataTypeInput);

        useCategory         = findViewById(R.id.useCategory);
        categoriesLayout    = findViewById(R.id.categoriesLayout);
        categoriesContainer = findViewById(R.id.categoriesContainer);

        useExtras       = findViewById(R.id.useExtras);
        extrasLayout    = findViewById(R.id.extrasLayout);
        extrasContainer = findViewById(R.id.extrasContainer);

        useBundle        = findViewById(R.id.useBundle);
        bundlesLayout    = findViewById(R.id.bundlesLayout);
        bundlesContainer = findViewById(R.id.bundlesContainer);

        useFlags         = findViewById(R.id.useFlags);
        flagsLayout      = findViewById(R.id.flagsLayout);
        flagsChipGroup   = findViewById(R.id.flagsChipGroup);
        customFlagsInput = findViewById(R.id.customFlagsInput);

        useChooser       = findViewById(R.id.useChooser);
        chooserLayout    = findViewById(R.id.chooserLayout);
        chooserTitleInput = findViewById(R.id.chooserTitleInput);

        launchTypeChipGroup = findViewById(R.id.launchTypeChipGroup);
        intentPreviewText   = findViewById(R.id.intentPreviewText);
        nestedScrollView    = findViewById(R.id.nestedScrollView);
        bottomActionBar     = findViewById(R.id.bottomActionBar);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setupSwitchListeners() {
        useComponent.setOnCheckedChangeListener((b, on) ->
                componentLayout.setVisibility(on ? View.VISIBLE : View.GONE));
        useActions.setOnCheckedChangeListener((b, on) ->
                actionsLayout.setVisibility(on ? View.VISIBLE : View.GONE));
        useData.setOnCheckedChangeListener((b, on) ->
                dataLayout.setVisibility(on ? View.VISIBLE : View.GONE));
        useCategory.setOnCheckedChangeListener((b, on) ->
                categoriesLayout.setVisibility(on ? View.VISIBLE : View.GONE));
        useExtras.setOnCheckedChangeListener((b, on) ->
                extrasLayout.setVisibility(on ? View.VISIBLE : View.GONE));
        useBundle.setOnCheckedChangeListener((b, on) ->
                bundlesLayout.setVisibility(on ? View.VISIBLE : View.GONE));
        useFlags.setOnCheckedChangeListener((b, on) ->
                flagsLayout.setVisibility(on ? View.VISIBLE : View.GONE));
        useChooser.setOnCheckedChangeListener((b, on) ->
                chooserLayout.setVisibility(on ? View.VISIBLE : View.GONE));
    }

    private void setupFlagsChips() {
        String[] flagNames = getResources().getStringArray(R.array.intent_flags);
        for (String flagName : flagNames) {
            Chip chip = new Chip(this);
            chip.setText(flagName.replace("FLAG_", "").replace("_", " "));
            chip.setTag(flagName);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            flagsChipGroup.addView(chip);
        }
    }

    private void setupActionAutocomplete() {
        String[] actions = getResources().getStringArray(R.array.intent_actions);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, actions);
        actionInput.setAdapter(adapter);
        actionInput.setThreshold(1);
    }

    private void setupMimeTypeAutocomplete() {
        String[] mimeTypes = getResources().getStringArray(R.array.mime_types);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, mimeTypes);
        dataTypeInput.setAdapter(adapter);
        dataTypeInput.setThreshold(1);
    }

    private void setupPreviewWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updatePreview(); }
        };
        packageInput.addTextChangedListener(watcher);
        componentInput.addTextChangedListener(watcher);
        actionInput.addTextChangedListener(watcher);
        dataUriInput.addTextChangedListener(watcher);
        dataTypeInput.addTextChangedListener(watcher);
        chooserTitleInput.addTextChangedListener(watcher);

        // Override switch listeners to also trigger preview update
        useComponent.setOnCheckedChangeListener((b, on) -> { componentLayout.setVisibility(on ? View.VISIBLE : View.GONE); updatePreview(); });
        useActions.setOnCheckedChangeListener((b, on) -> { actionsLayout.setVisibility(on ? View.VISIBLE : View.GONE); updatePreview(); });
        useData.setOnCheckedChangeListener((b, on) -> { dataLayout.setVisibility(on ? View.VISIBLE : View.GONE); updatePreview(); });
        useCategory.setOnCheckedChangeListener((b, on) -> { categoriesLayout.setVisibility(on ? View.VISIBLE : View.GONE); updatePreview(); });
        useExtras.setOnCheckedChangeListener((b, on) -> { extrasLayout.setVisibility(on ? View.VISIBLE : View.GONE); updatePreview(); });
        useBundle.setOnCheckedChangeListener((b, on) -> { bundlesLayout.setVisibility(on ? View.VISIBLE : View.GONE); updatePreview(); });
        useFlags.setOnCheckedChangeListener((b, on) -> { flagsLayout.setVisibility(on ? View.VISIBLE : View.GONE); updatePreview(); });
        useChooser.setOnCheckedChangeListener((b, on) -> { chooserLayout.setVisibility(on ? View.VISIBLE : View.GONE); updatePreview(); });
    }

    private void setupKeyboardAwareLayout() {
        View root = findViewById(R.id.rootLayout);
        bottomActionBarBasePaddingBottom = bottomActionBar.getPaddingBottom();
        nestedScrollBasePaddingBottom = nestedScrollView.getPaddingBottom();

        bottomActionBar.post(() -> {
            nestedScrollBasePaddingBottom = Math.max(
                    nestedScrollBasePaddingBottom,
                    bottomActionBar.getHeight() + dp(16));
            nestedScrollView.setPadding(
                    nestedScrollView.getPaddingLeft(),
                    nestedScrollView.getPaddingTop(),
                    nestedScrollView.getPaddingRight(),
                    nestedScrollBasePaddingBottom);
            ViewCompat.requestApplyInsets(root);
        });

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            bottomActionBar.setVisibility(imeVisible ? View.GONE : View.VISIBLE);
            bottomActionBar.setPadding(
                    bottomActionBar.getPaddingLeft(),
                    bottomActionBar.getPaddingTop(),
                    bottomActionBar.getPaddingRight(),
                    bottomActionBarBasePaddingBottom + systemBars.bottom);

            int bottomPadding = imeVisible
                    ? ime.bottom + dp(24)
                    : bottomActionBar.getHeight() + systemBars.bottom + dp(16);
            nestedScrollView.setPadding(
                    nestedScrollView.getPaddingLeft(),
                    nestedScrollView.getPaddingTop(),
                    nestedScrollView.getPaddingRight(),
                    Math.max(bottomPadding, nestedScrollBasePaddingBottom));

            if (imeVisible) {
                scrollFocusedInputIntoView(root.findFocus());
            }
            return insets;
        });

        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (isTextEntryView(newFocus)) {
                scrollFocusedInputIntoView(newFocus);
            }
        });
    }

    private boolean isTextEntryView(View view) {
        return view instanceof TextInputEditText || view instanceof MaterialAutoCompleteTextView;
    }

    private void scrollFocusedInputIntoView(View focused) {
        if (focused == null || nestedScrollView == null) return;
        nestedScrollView.postDelayed(() -> {
            if (!focused.isShown()) return;

            int[] scrollLocation = new int[2];
            int[] focusedLocation = new int[2];
            nestedScrollView.getLocationOnScreen(scrollLocation);
            focused.getLocationOnScreen(focusedLocation);

            int viewportTop = nestedScrollView.getPaddingTop();
            int viewportBottom = nestedScrollView.getHeight() - nestedScrollView.getPaddingBottom();
            int focusedTop = focusedLocation[1] - scrollLocation[1] - dp(12);
            int focusedBottom = focusedTop + focused.getHeight() + dp(24);

            if (focusedBottom > viewportBottom) {
                nestedScrollView.smoothScrollBy(0, focusedBottom - viewportBottom);
            } else if (focusedTop < viewportTop) {
                nestedScrollView.smoothScrollBy(0, focusedTop - viewportTop);
            }
        }, 120);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Package Picker
    // ══════════════════════════════════════════════════════════════════════

    private void showPackagePicker() {
        PackagePickerDialog dialog = new PackagePickerDialog();
        dialog.setOnPackageSelectedListener((packageName, activityClassName) -> {
            packageInput.setText(packageName);
            if (activityClassName != null && !activityClassName.isEmpty()) {
                componentInput.setText(activityClassName);
            }
            updatePreview();
        });
        dialog.show(getSupportFragmentManager(), "package_picker");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Menu
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_history) {
            openHistory();
            return true;
        } else if (id == R.id.action_clear) {
            clearAll();
            return true;
        } else if (id == R.id.action_about) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openHistory() {
        Intent intent = new Intent(this, HistoryActivity.class);
        historyLauncher.launch(intent);
    }

    private void clearAll() {
        packageInput.setText("");
        componentInput.setText("");
        actionInput.setText("");
        dataUriInput.setText("");
        dataTypeInput.setText("");
        customFlagsInput.setText("");
        chooserTitleInput.setText("");
        categoriesContainer.removeAllViews();
        categoriesList.clear();
        extrasContainer.removeAllViews();
        extrasList.clear();
        bundlesContainer.removeAllViews();
        bundlesList.clear();
        for (int i = 0; i < flagsChipGroup.getChildCount(); i++) {
            ((Chip) flagsChipGroup.getChildAt(i)).setChecked(false);
        }
        useComponent.setChecked(true);
        useActions.setChecked(false);
        useData.setChecked(false);
        useCategory.setChecked(false);
        useExtras.setChecked(false);
        useBundle.setChecked(false);
        useFlags.setChecked(false);
        useChooser.setChecked(false);
        Chip chipActivity = findViewById(R.id.chipActivity);
        if (chipActivity != null) chipActivity.setChecked(true);
        updatePreview();
        Snackbar.make(findViewById(android.R.id.content), "Cleared", Snackbar.LENGTH_SHORT).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("Intenter")
                .setMessage("A developer tool to build and fire Android Intents.\n\n" +
                        "Supports:\n" +
                        "- Explicit and implicit intents\n" +
                        "- Start Activity / Service / Foreground Service / Broadcast\n" +
                        "- Activity for Result\n" +
                        "- Chooser dialog wrapping\n" +
                        "- Extras and bundle extras\n" +
                        "- Preset and custom intent flags\n" +
                        "- URI preview and clipboard copy\n" +
                        "- Launch history with replay\n" +
                        "- Installed package/activity browser")
                .setPositiveButton("OK", null)
                .show();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Dynamic item builders
    // ══════════════════════════════════════════════════════════════════════

    private void addNewCategory() {
        View view = getLayoutInflater().inflate(R.layout.category_item, categoriesContainer, false);
        MaterialAutoCompleteTextView input = view.findViewById(R.id.categoryInput);
        String[] cats = getResources().getStringArray(R.array.intent_categories);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, cats);
        input.setAdapter(adapter);
        input.setThreshold(0);
        input.addTextChangedListener(simpleWatcher());

        CategoryItem item = new CategoryItem(view, input);
        categoriesList.add(item);
        categoriesContainer.addView(view);

        view.findViewById(R.id.removeCategoryButton).setOnClickListener(v -> {
            categoriesList.remove(item);
            categoriesContainer.removeView(view);
            updatePreview();
        });
        updatePreview();
    }

    private void addNewExtra() {
        addExtraToContainer(extrasContainer, extrasList);
        updatePreview();
    }

    private void addExtraToContainer(LinearLayout container, List<ExtraItem> list) {
        View view = getLayoutInflater().inflate(R.layout.extra_item, container, false);
        TextInputEditText keyInput   = view.findViewById(R.id.extraKeyInput);
        TextInputEditText valueInput = view.findViewById(R.id.extraValueInput);
        MaterialAutoCompleteTextView typeView = view.findViewById(R.id.extraTypeSpinner);

        String[] types = getResources().getStringArray(R.array.extra_types);
        // Use a non-filterable adapter so ALL types always show in the dropdown
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line, types) {
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = java.util.Arrays.asList(types);
                        results.count = types.length;
                        return results;
                    }
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
        typeView.setAdapter(adapter);
        typeView.setText(types[0], false);
        // Prevent keyboard — this is a pure dropdown picker
        typeView.setInputType(0);
        typeView.setKeyListener(null);
        // Force show all items on click
        typeView.setOnClickListener(v -> typeView.showDropDown());
        typeView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) typeView.showDropDown();
        });

        keyInput.addTextChangedListener(simpleWatcher());
        valueInput.addTextChangedListener(simpleWatcher());

        ExtraItem item = new ExtraItem(view, typeView, keyInput, valueInput);
        list.add(item);
        container.addView(view);

        view.findViewById(R.id.removeExtraButton).setOnClickListener(v -> {
            list.remove(item);
            container.removeView(view);
            updatePreview();
        });
    }

    /** Adds an extra with pre-filled values (for history loading). */
    private void addExtraToContainerWithValues(LinearLayout container, List<ExtraItem> list,
                                               String key, String value, String type) {
        addExtraToContainer(container, list);
        ExtraItem item = list.get(list.size() - 1);
        item.keyInput.setText(key);
        item.valueInput.setText(value);
        item.typeView.setText(type, false);
    }

    private void addNewBundle() {
        View view = getLayoutInflater().inflate(R.layout.bundle_item, bundlesContainer, false);
        TextInputEditText keyInput = view.findViewById(R.id.bundleKeyInput);
        LinearLayout extrasInBundle = view.findViewById(R.id.bundleExtrasContainer);
        keyInput.addTextChangedListener(simpleWatcher());

        BundleItem item = new BundleItem(view, keyInput, new ArrayList<>(), extrasInBundle);
        bundlesList.add(item);
        bundlesContainer.addView(view);

        view.findViewById(R.id.removeBundleButton).setOnClickListener(v -> {
            bundlesList.remove(item);
            bundlesContainer.removeView(view);
            updatePreview();
        });
        view.findViewById(R.id.addBundleExtraButton).setOnClickListener(v ->
                addExtraToContainer(extrasInBundle, item.extras));
        updatePreview();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Build IntentModel from UI (for history saving)
    // ══════════════════════════════════════════════════════════════════════

    private IntentModel buildModel() {
        IntentModel m = new IntentModel();

        m.useComponent = useComponent.isChecked();
        m.packageName = text(packageInput);
        m.componentName = text(componentInput);

        m.useAction = useActions.isChecked();
        m.action = actionInput.getText() == null ? "" : actionInput.getText().toString().trim();

        m.useData = useData.isChecked();
        m.dataUri = text(dataUriInput);
        m.mimeType = text(dataTypeInput);

        m.useCategory = useCategory.isChecked();
        for (CategoryItem cat : categoriesList) {
            String c = cat.categoryInput.getText() == null ? "" : cat.categoryInput.getText().toString().trim();
            if (!c.isEmpty()) m.categories.add(c);
        }

        m.useExtras = useExtras.isChecked();
        for (ExtraItem e : extrasList) {
            String k = text(e.keyInput);
            String v = text(e.valueInput);
            String t = e.typeView.getText() == null ? "String" : e.typeView.getText().toString();
            if (!k.isEmpty()) m.extras.add(new IntentModel.ExtraEntry(k, v, t));
        }

        m.useBundle = useBundle.isChecked();
        for (BundleItem bi : bundlesList) {
            IntentModel.BundleEntry be = new IntentModel.BundleEntry();
            be.key = text(bi.keyInput);
            for (ExtraItem e : bi.extras) {
                String k = text(e.keyInput);
                String v = text(e.valueInput);
                String t = e.typeView.getText() == null ? "String" : e.typeView.getText().toString();
                if (!k.isEmpty()) be.extras.add(new IntentModel.ExtraEntry(k, v, t));
            }
            m.bundles.add(be);
        }

        m.useFlags = useFlags.isChecked();
        for (int i = 0; i < flagsChipGroup.getChildCount(); i++) {
            Chip chip = (Chip) flagsChipGroup.getChildAt(i);
            if (chip.isChecked()) m.flagNames.add((String) chip.getTag());
        }
        m.customFlags = text(customFlagsInput);

        // Launch type
        int checkedId = launchTypeChipGroup.getCheckedChipId();
        if (checkedId == R.id.chipActivity) m.launchType = "chipActivity";
        else if (checkedId == R.id.chipService) m.launchType = "chipService";
        else if (checkedId == R.id.chipFgService) m.launchType = "chipFgService";
        else if (checkedId == R.id.chipBroadcast) m.launchType = "chipBroadcast";
        else if (checkedId == R.id.chipActivityResult) m.launchType = "chipActivityResult";

        m.useChooser = useChooser.isChecked();
        m.chooserTitle = text(chooserTitleInput);

        m.label = m.generateLabel();
        return m;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Load IntentModel into UI (from history)
    // ══════════════════════════════════════════════════════════════════════

    private void loadFromModel(IntentModel m) {
        // Clear dynamic lists first
        categoriesContainer.removeAllViews(); categoriesList.clear();
        extrasContainer.removeAllViews(); extrasList.clear();
        bundlesContainer.removeAllViews(); bundlesList.clear();
        for (int i = 0; i < flagsChipGroup.getChildCount(); i++) {
            ((Chip) flagsChipGroup.getChildAt(i)).setChecked(false);
        }

        // Component
        useComponent.setChecked(m.useComponent);
        packageInput.setText(m.packageName);
        componentInput.setText(m.componentName);

        // Action
        useActions.setChecked(m.useAction);
        actionInput.setText(m.action);

        // Data
        useData.setChecked(m.useData);
        dataUriInput.setText(m.dataUri);
        dataTypeInput.setText(m.mimeType);

        // Categories
        useCategory.setChecked(m.useCategory);
        for (String cat : m.categories) {
            addNewCategory();
            CategoryItem ci = categoriesList.get(categoriesList.size() - 1);
            ci.categoryInput.setText(cat);
        }

        // Extras
        useExtras.setChecked(m.useExtras);
        for (IntentModel.ExtraEntry e : m.extras) {
            addExtraToContainerWithValues(extrasContainer, extrasList, e.key, e.value, e.type);
        }

        // Bundles
        useBundle.setChecked(m.useBundle);
        for (IntentModel.BundleEntry be : m.bundles) {
            addNewBundle();
            BundleItem bi = bundlesList.get(bundlesList.size() - 1);
            bi.keyInput.setText(be.key);
            for (IntentModel.ExtraEntry e : be.extras) {
                addExtraToContainerWithValues(bi.extrasContainer, bi.extras, e.key, e.value, e.type);
            }
        }

        // Flags
        useFlags.setChecked(m.useFlags);
        for (int i = 0; i < flagsChipGroup.getChildCount(); i++) {
            Chip chip = (Chip) flagsChipGroup.getChildAt(i);
            chip.setChecked(m.flagNames.contains(chip.getTag()));
        }
        customFlagsInput.setText(m.customFlags);

        // Launch type
        switch (m.launchType) {
            case "chipService":        ((Chip) findViewById(R.id.chipService)).setChecked(true); break;
            case "chipFgService":      ((Chip) findViewById(R.id.chipFgService)).setChecked(true); break;
            case "chipBroadcast":      ((Chip) findViewById(R.id.chipBroadcast)).setChecked(true); break;
            case "chipActivityResult": ((Chip) findViewById(R.id.chipActivityResult)).setChecked(true); break;
            default:                   ((Chip) findViewById(R.id.chipActivity)).setChecked(true); break;
        }

        // Chooser
        useChooser.setChecked(m.useChooser);
        chooserTitleInput.setText(m.chooserTitle);

        updatePreview();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Intent building
    // ══════════════════════════════════════════════════════════════════════

    private Intent buildIntent() {
        Intent intent = new Intent();

        // Component
        if (useComponent.isChecked()) {
            String pkg  = text(packageInput);
            String comp = text(componentInput);
            if (!pkg.isEmpty() && !comp.isEmpty()) {
                intent.setClassName(pkg, comp);
            } else if (!pkg.isEmpty()) {
                intent.setPackage(pkg);
            }
        }

        // Action
        if (useActions.isChecked()) {
            String action = actionInput.getText() == null ? "" : actionInput.getText().toString().trim();
            if (!action.isEmpty()) intent.setAction(action);
        }

        // Data / MIME
        if (useData.isChecked()) {
            String uri  = text(dataUriInput);
            String mime = text(dataTypeInput);
            if (!uri.isEmpty() && !mime.isEmpty()) {
                intent.setDataAndType(Uri.parse(uri), mime);
            } else if (!uri.isEmpty()) {
                intent.setData(Uri.parse(uri));
            } else if (!mime.isEmpty()) {
                intent.setType(mime);
            }
        }

        // Categories
        if (useCategory.isChecked()) {
            for (CategoryItem cat : categoriesList) {
                String c = cat.categoryInput.getText() == null ? "" : cat.categoryInput.getText().toString().trim();
                if (!c.isEmpty()) intent.addCategory(c);
            }
        }

        // Extras
        if (useExtras.isChecked()) {
            applyExtrasToIntent(intent, extrasList);
        }

        // Bundles
        if (useBundle.isChecked()) {
            for (BundleItem bi : bundlesList) {
                Bundle b = new Bundle();
                applyExtrasToBundle(b, bi.extras);
                String key = text(bi.keyInput);
                if (!key.isEmpty()) {
                    intent.putExtra(key, b);
                } else {
                    intent.putExtras(b);
                }
            }
        }

        // Flags
        if (useFlags.isChecked()) {
            applyFlags(intent);
        }

        return intent;
    }

    private void applyExtrasToIntent(Intent intent, List<ExtraItem> list) {
        for (ExtraItem e : list) {
            String key   = text(e.keyInput);
            String value = text(e.valueInput);
            String type  = e.typeView.getText() == null ? "String" : e.typeView.getText().toString();
            if (key.isEmpty() || value.isEmpty()) continue;
            try {
                switch (type) {
                    case "Integer":  intent.putExtra(key, Integer.parseInt(value)); break;
                    case "Boolean":  intent.putExtra(key, Boolean.parseBoolean(value)); break;
                    case "Float":    intent.putExtra(key, Float.parseFloat(value)); break;
                    case "Long":     intent.putExtra(key, Long.parseLong(value)); break;
                    case "Double":   intent.putExtra(key, Double.parseDouble(value)); break;
                    case "Uri":      intent.putExtra(key, Uri.parse(value)); break;
                    case "String[]": intent.putExtra(key, value.split(",")); break;
                    case "int[]": {
                        String[] parts = value.split(",");
                        int[] arr = new int[parts.length];
                        for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i].trim());
                        intent.putExtra(key, arr);
                        break;
                    }
                    case "long[]": {
                        String[] parts = value.split(",");
                        long[] arr = new long[parts.length];
                        for (int i = 0; i < parts.length; i++) arr[i] = Long.parseLong(parts[i].trim());
                        intent.putExtra(key, arr);
                        break;
                    }
                    case "double[]": {
                        String[] parts = value.split(",");
                        double[] arr = new double[parts.length];
                        for (int i = 0; i < parts.length; i++) arr[i] = Double.parseDouble(parts[i].trim());
                        intent.putExtra(key, arr);
                        break;
                    }
                    case "boolean[]": {
                        String[] parts = value.split(",");
                        boolean[] arr = new boolean[parts.length];
                        for (int i = 0; i < parts.length; i++) arr[i] = Boolean.parseBoolean(parts[i].trim());
                        intent.putExtra(key, arr);
                        break;
                    }
                    case "ArrayList<String>": {
                        String[] parts = value.split(",");
                        ArrayList<String> al = new ArrayList<>();
                        for (String p : parts) al.add(p.trim());
                        intent.putStringArrayListExtra(key, al);
                        break;
                    }
                    case "ArrayList<Integer>": {
                        String[] parts = value.split(",");
                        ArrayList<Integer> al = new ArrayList<>();
                        for (String p : parts) al.add(Integer.parseInt(p.trim()));
                        intent.putIntegerArrayListExtra(key, al);
                        break;
                    }
                    default: intent.putExtra(key, value); break;
                }
            } catch (NumberFormatException ex) {
                showError("Invalid value for key '" + key + "': " + ex.getMessage());
            }
        }
    }

    private void applyExtrasToBundle(Bundle bundle, List<ExtraItem> list) {
        for (ExtraItem e : list) {
            String key   = text(e.keyInput);
            String value = text(e.valueInput);
            String type  = e.typeView.getText() == null ? "String" : e.typeView.getText().toString();
            if (key.isEmpty() || value.isEmpty()) continue;
            try {
                switch (type) {
                    case "Integer":  bundle.putInt(key, Integer.parseInt(value)); break;
                    case "Boolean":  bundle.putBoolean(key, Boolean.parseBoolean(value)); break;
                    case "Float":    bundle.putFloat(key, Float.parseFloat(value)); break;
                    case "Long":     bundle.putLong(key, Long.parseLong(value)); break;
                    case "Double":   bundle.putDouble(key, Double.parseDouble(value)); break;
                    case "Uri":      bundle.putParcelable(key, Uri.parse(value)); break;
                    case "String[]": bundle.putStringArray(key, value.split(",")); break;
                    case "int[]": {
                        String[] parts = value.split(",");
                        int[] arr = new int[parts.length];
                        for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i].trim());
                        bundle.putIntArray(key, arr);
                        break;
                    }
                    case "long[]": {
                        String[] parts = value.split(",");
                        long[] arr = new long[parts.length];
                        for (int i = 0; i < parts.length; i++) arr[i] = Long.parseLong(parts[i].trim());
                        bundle.putLongArray(key, arr);
                        break;
                    }
                    case "double[]": {
                        String[] parts = value.split(",");
                        double[] arr = new double[parts.length];
                        for (int i = 0; i < parts.length; i++) arr[i] = Double.parseDouble(parts[i].trim());
                        bundle.putDoubleArray(key, arr);
                        break;
                    }
                    case "boolean[]": {
                        String[] parts = value.split(",");
                        boolean[] arr = new boolean[parts.length];
                        for (int i = 0; i < parts.length; i++) arr[i] = Boolean.parseBoolean(parts[i].trim());
                        bundle.putBooleanArray(key, arr);
                        break;
                    }
                    case "ArrayList<String>": {
                        String[] parts = value.split(",");
                        ArrayList<String> al = new ArrayList<>();
                        for (String p : parts) al.add(p.trim());
                        bundle.putStringArrayList(key, al);
                        break;
                    }
                    case "ArrayList<Integer>": {
                        String[] parts = value.split(",");
                        ArrayList<Integer> al = new ArrayList<>();
                        for (String p : parts) al.add(Integer.parseInt(p.trim()));
                        bundle.putIntegerArrayList(key, al);
                        break;
                    }
                    default: bundle.putString(key, value); break;
                }
            } catch (NumberFormatException ex) {
                showError("Invalid value for key '" + key + "': " + ex.getMessage());
            }
        }
    }

    private void applyFlags(Intent intent) {
        for (int i = 0; i < flagsChipGroup.getChildCount(); i++) {
            Chip chip = (Chip) flagsChipGroup.getChildAt(i);
            if (chip.isChecked()) {
                String name = (String) chip.getTag();
                Integer val = FLAG_MAP.get(name);
                if (val != null) intent.addFlags(val);
            }
        }
        String custom = text(customFlagsInput);
        if (!custom.isEmpty()) {
            try {
                int flag;
                if (custom.startsWith("0x") || custom.startsWith("0X")) {
                    flag = (int) Long.parseLong(custom.substring(2), 16);
                } else {
                    flag = (int) Long.parseLong(custom);
                }
                intent.addFlags(flag);
            } catch (NumberFormatException ex) {
                showError("Invalid custom flag value: " + custom);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Launch
    // ══════════════════════════════════════════════════════════════════════

    private void launchIntent() {
        try {
            Intent intent = buildIntent();
            int selectedId = launchTypeChipGroup.getCheckedChipId();

            if (isIntentBlank(intent)) {
                showSuccess("Add a target, action, data, extra, or flag before launching");
                return;
            }

            // Wrap in chooser if enabled
            if (useChooser.isChecked()) {
                String title = text(chooserTitleInput);
                intent = Intent.createChooser(intent, title.isEmpty() ? null : title);
            }

            Log.d(TAG, "Launching: " + intent.toUri(Intent.URI_INTENT_SCHEME));

            if (selectedId == R.id.chipActivity) {
                startActivity(intent);
                showSuccess("Activity launched");
            } else if (selectedId == R.id.chipService) {
                startService(intent);
                showSuccess("Service started");
            } else if (selectedId == R.id.chipFgService) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                showSuccess("Foreground service started");
            } else if (selectedId == R.id.chipBroadcast) {
                sendBroadcast(intent);
                showSuccess("Broadcast sent");
            } else if (selectedId == R.id.chipActivityResult) {
                activityResultLauncher.launch(intent);
                showSuccess("Activity for result launched");
            }

            // Save to history on successful launch
            saveToHistory();

        } catch (android.content.ActivityNotFoundException e) {
            showError("No app found to handle this intent:\n" + e.getMessage());
        } catch (SecurityException e) {
            showError("Permission denied:\n" + e.getMessage());
        } catch (IllegalStateException e) {
            showError("Illegal state (foreground service issue?):\n" + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Launch error", e);
            showError("Error: " + e.getMessage());
        }
    }

    private boolean isIntentBlank(Intent intent) {
        return intent.getComponent() == null
                && intent.getPackage() == null
                && intent.getAction() == null
                && intent.getData() == null
                && intent.getType() == null
                && intent.getCategories() == null
                && (intent.getExtras() == null || intent.getExtras().isEmpty())
                && intent.getFlags() == 0;
    }

    private void saveToHistory() {
        try {
            IntentModel model = buildModel();
            historyManager.save(model);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save history", e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Preview
    // ══════════════════════════════════════════════════════════════════════

    private void updatePreview() {
        try {
            Intent intent = buildIntent();
            String uri = intent.toUri(Intent.URI_INTENT_SCHEME);
            intentPreviewText.setText(uri);
        } catch (Exception e) {
            intentPreviewText.setText("(invalid intent: " + e.getMessage() + ")");
        }
    }

    private void copyIntentUri() {
        try {
            Intent intent = buildIntent();
            String uri = intent.toUri(Intent.URI_INTENT_SCHEME);
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Intent URI", uri));
            showSuccess("Intent URI copied to clipboard");
        } catch (Exception e) {
            showError("Could not copy: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════

    private String text(android.widget.EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void showSuccess(String msg) {
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show();
    }

    private void showError(String msg) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private TextWatcher simpleWatcher() {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updatePreview(); }
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Data classes
    // ══════════════════════════════════════════════════════════════════════

    private static class CategoryItem {
        View view;
        MaterialAutoCompleteTextView categoryInput;
        CategoryItem(View v, MaterialAutoCompleteTextView i) { view = v; categoryInput = i; }
    }

    private static class ExtraItem {
        View view;
        MaterialAutoCompleteTextView typeView;
        TextInputEditText keyInput, valueInput;
        ExtraItem(View v, MaterialAutoCompleteTextView t, TextInputEditText k, TextInputEditText val) {
            view = v; typeView = t; keyInput = k; valueInput = val;
        }
    }

    private static class BundleItem {
        View view;
        TextInputEditText keyInput;
        List<ExtraItem> extras;
        LinearLayout extrasContainer;
        BundleItem(View v, TextInputEditText k, List<ExtraItem> e, LinearLayout ec) {
            view = v; keyInput = k; extras = e; extrasContainer = ec;
        }
    }
}
