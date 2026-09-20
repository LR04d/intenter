package com.open.intenter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The intent builder: sections for every part of an Intent, a live preview
 * sheet with export formats and device handlers, and the launch controls.
 */
public class BuildFragment extends Fragment {

    private static final String TAG = "INTENTER";

    // Target
    private MaterialSwitch useComponent;
    private LinearLayout componentLayout;
    private TextInputEditText packageInput, componentInput;
    private TextView summaryComponent, componentInfo;

    // Action
    private MaterialSwitch useActions;
    private LinearLayout actionsLayout, actionsContainer;
    private TextView summaryAction;
    private final List<MaterialAutoCompleteTextView> actionInputs = new ArrayList<>();

    // Data
    private MaterialSwitch useData;
    private LinearLayout dataLayout;
    private TextInputEditText dataUriInput;
    private MaterialAutoCompleteTextView dataTypeInput;
    private ChipGroup schemeChips;
    private TextView summaryData;

    // Categories
    private MaterialSwitch useCategory;
    private LinearLayout categoriesLayout, categoriesContainer;
    private TextView summaryCategory;
    private final List<MaterialAutoCompleteTextView> categoryInputs = new ArrayList<>();

    // Extras
    private MaterialSwitch useExtras;
    private LinearLayout extrasLayout;
    private ExtrasEditor extrasEditor;
    private TextView summaryExtras;

    // ClipData
    private MaterialSwitch useClipData;
    private LinearLayout clipDataLayout, clipDataItemsContainer;
    private TextInputEditText clipDataLabelInput, clipDataMimeTypesInput;
    private TextView summaryClipData;
    private final List<ClipRow> clipRows = new ArrayList<>();

    // Flags
    private MaterialSwitch useFlags;
    private LinearLayout flagsLayout, flagsGroupsContainer;
    private TextInputEditText customFlagsInput;
    private TextView flagsEffective, summaryFlags;
    private final List<Chip> flagChips = new ArrayList<>();

    // Options
    private MaterialSwitch useChooser, useAdvanced;
    private LinearLayout chooserLayout, advancedLayout;
    private TextInputEditText chooserTitleInput, receiverPermissionInput, identifierInput;
    private TextView summaryChooser, summaryAdvanced;

    // Sheet
    private View bottomSheet, sheetPeek;
    private BottomSheetBehavior<View> sheetBehavior;
    private NestedScrollView nestedScrollView;
    private TextView previewLine, resolveLine, intentPreviewText, handlersText;
    private ChipGroup formatChips;
    private com.google.android.material.button.MaterialButton modeButton, launchButton, bindingsButton;
    private String currentMode = IntentModel.MODE_ACTIVITY;
    private IntentCodec.Format currentFormat = IntentCodec.Format.URI;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Runnable previewRunnable = this::updatePreview;
    private int resolveGeneration = 0;
    private boolean keyboardVisible = false;
    private boolean restoring = false;

    private ActivityResultLauncher<Intent> activityResultLauncher;
    private HistoryManager historyManager;
    private EventStore events;
    private final ServiceBinder.Listener bindingsListener = this::updateBindingsButton;

    // ══════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    String code = describeResultCode(result.getResultCode());
                    String details = "Result code: " + code + "\n\n" +
                            (result.getData() == null ? "No data intent" : IntentDumper.dump(result.getData()));
                    String modelJson = result.getData() == null ? null : IntentCodec.fromIntent(result.getData()).toJsonString();
                    events.add(EventStore.Kind.RESULT, "Activity result " + code,
                            result.getData() == null ? "no data" : IntentDumper.summary(result.getData()),
                            details, modelJson);
                    UiUtil.showText(requireContext(), "Activity result", details);
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_build, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        historyManager = HistoryManager.getInstance(requireContext());
        events = EventStore.get(requireContext());

        bindViews(view);
        setupSections();
        setupFlags();
        setupSheet(view);
        setupKeyboardWatcher(view);

        view.findViewById(R.id.browsePackageButton).setOnClickListener(v -> showComponentPicker());
        view.findViewById(R.id.addActionButton).setOnClickListener(v -> addAction(null));
        view.findViewById(R.id.addCategoryButton).setOnClickListener(v -> addCategory(null));
        view.findViewById(R.id.addExtraButton).setOnClickListener(v -> extrasEditor.add());
        view.findViewById(R.id.extrasFromJsonButton).setOnClickListener(v -> showExtrasFromJsonDialog());
        view.findViewById(R.id.addClipDataItemButton).setOnClickListener(v -> addClipItem(null, null));
        launchButton.setOnClickListener(v -> launch());
        modeButton.setOnClickListener(v -> showModeMenu());
        view.findViewById(R.id.copyIntentButton).setOnClickListener(v ->
                UiUtil.copy(requireContext(), "Intent", intentPreviewText.getText().toString()));
        view.findViewById(R.id.shareIntentButton).setOnClickListener(v ->
                UiUtil.share(requireContext(), "Intent (" + currentFormat.name().toLowerCase() + ")", intentPreviewText.getText().toString()));
        bindingsButton.setOnClickListener(v -> showBindingsDialog());

        IntentModel pending = SavedStore.get(requireContext()).takePendingLoad();
        IntentModel draft = pending != null ? pending : SavedStore.get(requireContext()).draft();
        if (draft != null) {
            loadModel(draft);
        } else {
            useComponent.setChecked(true);
            setMode(IntentModel.MODE_ACTIVITY);
            scheduleUpdate();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ServiceBinder.get(requireContext()).addListener(bindingsListener);
        updateBindingsButton();
        IntentModel pending = SavedStore.get(requireContext()).takePendingLoad();
        if (pending != null) loadModel(pending);
    }

    @Override
    public void onPause() {
        super.onPause();
        ServiceBinder.get(requireContext()).removeListener(bindingsListener);
        try {
            SavedStore.get(requireContext()).saveDraft(buildModel());
        } catch (RuntimeException e) {
            Log.w(TAG, "draft save failed", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Setup
    // ══════════════════════════════════════════════════════════════════════

    private void bindViews(View v) {
        useComponent = v.findViewById(R.id.useComponent);
        componentLayout = v.findViewById(R.id.componentLayout);
        packageInput = v.findViewById(R.id.packageInput);
        componentInput = v.findViewById(R.id.componentInput);
        summaryComponent = v.findViewById(R.id.summaryComponent);
        componentInfo = v.findViewById(R.id.componentInfo);

        useActions = v.findViewById(R.id.useActions);
        actionsLayout = v.findViewById(R.id.actionsLayout);
        actionsContainer = v.findViewById(R.id.actionsContainer);
        summaryAction = v.findViewById(R.id.summaryAction);

        useData = v.findViewById(R.id.useData);
        dataLayout = v.findViewById(R.id.dataLayout);
        dataUriInput = v.findViewById(R.id.dataUriInput);
        dataTypeInput = v.findViewById(R.id.dataTypeInput);
        schemeChips = v.findViewById(R.id.schemeChips);
        summaryData = v.findViewById(R.id.summaryData);

        useCategory = v.findViewById(R.id.useCategory);
        categoriesLayout = v.findViewById(R.id.categoriesLayout);
        categoriesContainer = v.findViewById(R.id.categoriesContainer);
        summaryCategory = v.findViewById(R.id.summaryCategory);

        useExtras = v.findViewById(R.id.useExtras);
        extrasLayout = v.findViewById(R.id.extrasLayout);
        summaryExtras = v.findViewById(R.id.summaryExtras);
        extrasEditor = new ExtrasEditor(requireContext(), v.findViewById(R.id.extrasContainer), ExtraTypes.ALL, this::scheduleUpdate);

        useClipData = v.findViewById(R.id.useClipData);
        clipDataLayout = v.findViewById(R.id.clipDataLayout);
        clipDataItemsContainer = v.findViewById(R.id.clipDataItemsContainer);
        clipDataLabelInput = v.findViewById(R.id.clipDataLabelInput);
        clipDataMimeTypesInput = v.findViewById(R.id.clipDataMimeTypesInput);
        summaryClipData = v.findViewById(R.id.summaryClipData);

        useFlags = v.findViewById(R.id.useFlags);
        flagsLayout = v.findViewById(R.id.flagsLayout);
        flagsGroupsContainer = v.findViewById(R.id.flagsGroupsContainer);
        customFlagsInput = v.findViewById(R.id.customFlagsInput);
        flagsEffective = v.findViewById(R.id.flagsEffective);
        summaryFlags = v.findViewById(R.id.summaryFlags);

        useChooser = v.findViewById(R.id.useChooser);
        chooserLayout = v.findViewById(R.id.chooserLayout);
        chooserTitleInput = v.findViewById(R.id.chooserTitleInput);
        summaryChooser = v.findViewById(R.id.summaryChooser);
        useAdvanced = v.findViewById(R.id.useAdvanced);
        advancedLayout = v.findViewById(R.id.advancedLayout);
        receiverPermissionInput = v.findViewById(R.id.receiverPermissionInput);
        identifierInput = v.findViewById(R.id.identifierInput);
        summaryAdvanced = v.findViewById(R.id.summaryAdvanced);

        bottomSheet = v.findViewById(R.id.bottomSheet);
        sheetPeek = v.findViewById(R.id.sheetPeek);
        nestedScrollView = v.findViewById(R.id.nestedScrollView);
        previewLine = v.findViewById(R.id.previewLine);
        resolveLine = v.findViewById(R.id.resolveLine);
        intentPreviewText = v.findViewById(R.id.intentPreviewText);
        handlersText = v.findViewById(R.id.handlersText);
        formatChips = v.findViewById(R.id.formatChips);
        modeButton = v.findViewById(R.id.modeButton);
        launchButton = v.findViewById(R.id.launchButton);
        bindingsButton = v.findViewById(R.id.bindingsButton);
    }

    private void setupSections() {
        bindSection(R.id.headerComponent, useComponent, componentLayout);
        bindSection(R.id.headerAction, useActions, actionsLayout);
        bindSection(R.id.headerData, useData, dataLayout);
        bindSection(R.id.headerCategory, useCategory, categoriesLayout);
        bindSection(R.id.headerExtras, useExtras, extrasLayout);
        bindSection(R.id.headerClipData, useClipData, clipDataLayout);
        bindSection(R.id.headerFlags, useFlags, flagsLayout);
        bindSection(R.id.headerChooser, useChooser, chooserLayout);
        bindSection(R.id.headerAdvanced, useAdvanced, advancedLayout);

        useActions.setOnCheckedChangeListener((b, on) -> {
            actionsLayout.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on && actionInputs.isEmpty() && !restoring) addAction(null);
            scheduleUpdate();
        });
        useCategory.setOnCheckedChangeListener((b, on) -> {
            categoriesLayout.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on && categoryInputs.isEmpty() && !restoring) addCategory(null);
            scheduleUpdate();
        });
        useExtras.setOnCheckedChangeListener((b, on) -> {
            extrasLayout.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on && extrasEditor.count() == 0 && !restoring) extrasEditor.add();
            scheduleUpdate();
        });
        useClipData.setOnCheckedChangeListener((b, on) -> {
            clipDataLayout.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on && clipRows.isEmpty() && !restoring) addClipItem(null, null);
            scheduleUpdate();
        });

        for (TextInputEditText et : new TextInputEditText[]{packageInput, componentInput, dataUriInput,
                clipDataLabelInput, clipDataMimeTypesInput, customFlagsInput, chooserTitleInput,
                receiverPermissionInput, identifierInput}) {
            et.addTextChangedListener(UiUtil.watcher(this::scheduleUpdate));
        }
        dataTypeInput.addTextChangedListener(UiUtil.watcher(this::scheduleUpdate));
        UiUtil.setupSuggestions(dataTypeInput, getResources().getStringArray(R.array.mime_types));

        for (String scheme : getResources().getStringArray(R.array.uri_scheme_chips)) {
            Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
            chip.setText(scheme);
            chip.setChipMinHeight(UiUtil.dp(requireContext(), 30));
            chip.setTextAppearanceResource(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setOnClickListener(c -> {
                String current = UiUtil.raw(dataUriInput);
                int colon = current.indexOf(':');
                String rest = colon >= 0 ? current.substring(colon + 1).replaceFirst("^//", "") : current;
                dataUriInput.setText(scheme + rest);
                dataUriInput.setSelection(dataUriInput.getText() == null ? 0 : dataUriInput.getText().length());
                dataUriInput.requestFocus();
            });
            schemeChips.addView(chip);
        }
    }

    private void bindSection(int headerId, MaterialSwitch sw, View body) {
        View header = requireView().findViewById(headerId);
        header.setOnClickListener(v -> sw.toggle());
        sw.setOnCheckedChangeListener((b, on) -> {
            body.setVisibility(on ? View.VISIBLE : View.GONE);
            scheduleUpdate();
        });
        body.setVisibility(sw.isChecked() ? View.VISIBLE : View.GONE);
    }

    private void setupFlags() {
        flagsGroupsContainer.removeAllViews();
        flagChips.clear();
        for (FlagRegistry.Group group : FlagRegistry.groups()) {
            TextView label = new TextView(requireContext());
            label.setText(group.title);
            label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            label.setTextColor(requireContext().getColor(R.color.app_on_surface));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = UiUtil.dp(requireContext(), 6);
            flagsGroupsContainer.addView(label, lp);

            ChipGroup cg = new ChipGroup(requireContext());
            cg.setChipSpacingHorizontal(UiUtil.dp(requireContext(), 6));
            cg.setChipSpacingVertical(UiUtil.dp(requireContext(), 0));
            for (String name : group.flags.keySet()) {
                Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
                chip.setText(FlagRegistry.shortName(name));
                chip.setTag(name);
                chip.setCheckable(true);
                chip.setCheckedIconVisible(true);
                chip.setChipMinHeight(UiUtil.dp(requireContext(), 32));
                chip.setEnsureMinTouchTargetSize(false);
                chip.setTextAppearanceResource(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
                chip.setOnCheckedChangeListener((b, on) -> scheduleUpdate());
                cg.addView(chip);
                flagChips.add(chip);
            }
            flagsGroupsContainer.addView(cg);
        }
    }

    private void setupSheet(View root) {
        sheetBehavior = BottomSheetBehavior.from(bottomSheet);
        sheetBehavior.setHideable(false);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        sheetPeek.post(this::applyPeekHeight);

        formatChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipFmtAdb) currentFormat = IntentCodec.Format.ADB;
            else if (id == R.id.chipFmtJava) currentFormat = IntentCodec.Format.JAVA;
            else if (id == R.id.chipFmtKotlin) currentFormat = IntentCodec.Format.KOTLIN;
            else if (id == R.id.chipFmtJson) currentFormat = IntentCodec.Format.JSON;
            else currentFormat = IntentCodec.Format.URI;
            updatePreview();
        });
        previewLine.setOnClickListener(v -> sheetBehavior.setState(
                sheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED
                        ? BottomSheetBehavior.STATE_COLLAPSED : BottomSheetBehavior.STATE_EXPANDED));
    }

    private void applyPeekHeight() {
        int peek = sheetPeek.getHeight();
        if (peek <= 0) return;
        sheetBehavior.setPeekHeight(peek, true);
        nestedScrollView.setPadding(nestedScrollView.getPaddingLeft(), nestedScrollView.getPaddingTop(),
                nestedScrollView.getPaddingRight(), peek + UiUtil.dp(requireContext(), 12));
    }

    private void setupKeyboardWatcher(View root) {
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (!isAdded()) return;
                Rect r = new Rect();
                root.getWindowVisibleDisplayFrame(r);
                int screen = root.getRootView().getHeight();
                boolean visible = screen - r.bottom > screen * 0.2;
                if (visible != keyboardVisible) {
                    keyboardVisible = visible;
                    if (visible) {
                        sheetBehavior.setHideable(true);
                        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    } else {
                        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        bottomSheet.postDelayed(() -> sheetBehavior.setHideable(false), 300);
                    }
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Dynamic rows
    // ══════════════════════════════════════════════════════════════════════

    private void addAction(String value) {
        View view = getLayoutInflater().inflate(R.layout.action_item, actionsContainer, false);
        MaterialAutoCompleteTextView input = view.findViewById(R.id.actionInput);
        UiUtil.setupSuggestions(input, getResources().getStringArray(R.array.intent_actions));
        input.addTextChangedListener(UiUtil.watcher(this::scheduleUpdate));
        if (value != null) input.setText(value);
        actionInputs.add(input);
        actionsContainer.addView(view);
        view.findViewById(R.id.removeActionButton).setOnClickListener(v -> {
            actionInputs.remove(input);
            actionsContainer.removeView(view);
            scheduleUpdate();
        });
        scheduleUpdate();
    }

    private void addCategory(String value) {
        View view = getLayoutInflater().inflate(R.layout.category_item, categoriesContainer, false);
        MaterialAutoCompleteTextView input = view.findViewById(R.id.categoryInput);
        UiUtil.setupSuggestions(input, getResources().getStringArray(R.array.intent_categories));
        input.addTextChangedListener(UiUtil.watcher(this::scheduleUpdate));
        if (value != null) input.setText(value);
        categoryInputs.add(input);
        categoriesContainer.addView(view);
        view.findViewById(R.id.removeCategoryButton).setOnClickListener(v -> {
            categoryInputs.remove(input);
            categoriesContainer.removeView(view);
            scheduleUpdate();
        });
        scheduleUpdate();
    }

    private static class ClipRow {
        MaterialAutoCompleteTextView type;
        TextInputEditText value;
    }

    private static final String[] CLIP_TYPES = {"Text", "Html", "Uri", "Intent"};

    private void addClipItem(String type, String value) {
        View view = getLayoutInflater().inflate(R.layout.clip_data_item, clipDataItemsContainer, false);
        ClipRow row = new ClipRow();
        row.type = view.findViewById(R.id.clipItemTypeSpinner);
        row.value = view.findViewById(R.id.clipItemValueInput);
        UiUtil.setupPicker(row.type, CLIP_TYPES, type == null ? CLIP_TYPES[0] : type);
        row.type.setOnItemClickListener((p, v, pos, id) -> scheduleUpdate());
        row.value.addTextChangedListener(UiUtil.watcher(this::scheduleUpdate));
        if (value != null) row.value.setText(value);
        clipRows.add(row);
        clipDataItemsContainer.addView(view);
        view.findViewById(R.id.removeClipItemButton).setOnClickListener(v -> {
            clipRows.remove(row);
            clipDataItemsContainer.removeView(view);
            scheduleUpdate();
        });
        scheduleUpdate();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Model <-> UI
    // ══════════════════════════════════════════════════════════════════════

    public IntentModel buildModel() {
        IntentModel m = new IntentModel();
        m.useComponent = useComponent.isChecked();
        m.packageName = UiUtil.text(packageInput);
        m.componentName = UiUtil.text(componentInput);

        m.useAction = useActions.isChecked();
        for (MaterialAutoCompleteTextView in : actionInputs) {
            String a = UiUtil.text(in);
            if (!a.isEmpty()) m.actions.add(a);
        }
        m.action = m.actions.isEmpty() ? "" : m.actions.get(0);

        m.useData = useData.isChecked();
        m.dataUri = UiUtil.text(dataUriInput);
        m.mimeType = UiUtil.text(dataTypeInput);

        m.useCategory = useCategory.isChecked();
        for (MaterialAutoCompleteTextView in : categoryInputs) {
            String c = UiUtil.text(in);
            if (!c.isEmpty()) m.categories.add(c);
        }

        m.useExtras = useExtras.isChecked();
        m.extras.addAll(extrasEditor.entries());

        m.useClipData = useClipData.isChecked();
        m.clipDataLabel = UiUtil.text(clipDataLabelInput);
        for (String part : UiUtil.text(clipDataMimeTypesInput).split(",")) {
            if (!part.trim().isEmpty()) m.clipDataMimeTypes.add(part.trim());
        }
        for (ClipRow row : clipRows) {
            String val = UiUtil.raw(row.value);
            if (!val.isEmpty()) m.clipDataItems.add(new IntentModel.ClipDataItem(row.type.getText().toString(), val));
        }

        m.useFlags = useFlags.isChecked();
        for (Chip chip : flagChips) if (chip.isChecked()) m.flagNames.add((String) chip.getTag());
        m.customFlags = UiUtil.text(customFlagsInput);

        m.launchType = currentMode;
        m.useChooser = useChooser.isChecked();
        m.chooserTitle = UiUtil.text(chooserTitleInput);
        m.useAdvanced = useAdvanced.isChecked();
        m.permission = UiUtil.text(receiverPermissionInput);
        m.identifier = UiUtil.text(identifierInput);
        m.label = m.generateLabel();
        return m;
    }

    public void loadModel(IntentModel m) {
        restoring = true;
        handler.removeCallbacks(previewRunnable);

        actionsContainer.removeAllViews(); actionInputs.clear();
        categoriesContainer.removeAllViews(); categoryInputs.clear();
        clipDataItemsContainer.removeAllViews(); clipRows.clear();
        for (Chip chip : flagChips) chip.setChecked(false);

        useComponent.setChecked(m.useComponent);
        packageInput.setText(m.packageName);
        componentInput.setText(m.componentName);

        useActions.setChecked(m.useAction);
        for (String a : m.actions) addAction(a);

        useData.setChecked(m.useData);
        dataUriInput.setText(m.dataUri);
        dataTypeInput.setText(m.mimeType, false);

        useCategory.setChecked(m.useCategory);
        for (String c : m.categories) addCategory(c);

        useExtras.setChecked(m.useExtras);
        extrasEditor.set(m.extras);

        useClipData.setChecked(m.useClipData);
        clipDataLabelInput.setText(m.clipDataLabel);
        clipDataMimeTypesInput.setText(joinComma(m.clipDataMimeTypes));
        for (IntentModel.ClipDataItem item : m.clipDataItems) addClipItem(item.type, item.value);

        useFlags.setChecked(m.useFlags);
        for (Chip chip : flagChips) chip.setChecked(m.flagNames.contains((String) chip.getTag()));
        customFlagsInput.setText(m.customFlags);

        useChooser.setChecked(m.useChooser);
        chooserTitleInput.setText(m.chooserTitle);
        useAdvanced.setChecked(m.useAdvanced);
        receiverPermissionInput.setText(m.permission);
        identifierInput.setText(m.identifier);

        setMode(IntentModel.normalizeMode(m.launchType));
        restoring = false;
        scheduleUpdate();
    }

    public void clearAll() {
        loadModel(new IntentModel());
        useComponent.setChecked(true);
        SavedStore.get(requireContext()).clearDraft();
        Snackbar.make(requireView(), "Form cleared", Snackbar.LENGTH_SHORT).show();
    }

    private static String joinComma(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Mode
    // ══════════════════════════════════════════════════════════════════════

    private void setMode(String mode) {
        currentMode = mode;
        modeButton.setText(IntentModel.modeShortLabel(mode));
        switch (mode) {
            case IntentModel.MODE_BROADCAST: launchButton.setText("Send broadcast"); break;
            case IntentModel.MODE_ORDERED_BROADCAST: launchButton.setText("Send ordered"); break;
            case IntentModel.MODE_BIND_SERVICE: launchButton.setText("Bind"); break;
            case IntentModel.MODE_STOP_SERVICE: launchButton.setText("Stop service"); break;
            case IntentModel.MODE_RESOLVE: launchButton.setText("Resolve"); break;
            case IntentModel.MODE_SERVICE:
            case IntentModel.MODE_FG_SERVICE: launchButton.setText("Start service"); break;
            default: launchButton.setText(getString(R.string.launch_intent)); break;
        }
        launchButton.setIconResource(IntentModel.MODE_RESOLVE.equals(mode) ? R.drawable.ic_search : R.drawable.ic_play);
        scheduleUpdate();
    }

    private void showModeMenu() {
        PopupMenu menu = new PopupMenu(requireContext(), modeButton);
        for (int i = 0; i < IntentModel.MODES.length; i++) {
            menu.getMenu().add(0, i, i, IntentModel.modeLabel(IntentModel.MODES[i]));
        }
        menu.setOnMenuItemClickListener(item -> {
            setMode(IntentModel.MODES[item.getItemId()]);
            return true;
        });
        menu.show();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Preview
    // ══════════════════════════════════════════════════════════════════════

    private void scheduleUpdate() {
        if (restoring) return;
        handler.removeCallbacks(previewRunnable);
        handler.postDelayed(previewRunnable, 120);
    }

    private void updatePreview() {
        if (!isAdded()) return;
        IntentModel m = buildModel();
        updateSummaries(m);
        List<Intent> intents;
        try {
            intents = IntentBuilder.build(m);
        } catch (RuntimeException e) {
            String msg = "⚠ " + UiUtil.describeThrowable(e);
            previewLine.setText(msg);
            intentPreviewText.setText(msg);
            resolveLine.setText("Fix the value above to preview");
            handlersText.setText("—");
            return;
        }
        Intent first = intents.get(0);
        previewLine.setText(first.toUri(Intent.URI_INTENT_SCHEME));
        try {
            intentPreviewText.setText(IntentCodec.export(currentFormat, m, intents));
        } catch (RuntimeException e) {
            intentPreviewText.setText("Export failed: " + UiUtil.describeThrowable(e));
        }
        resolveHandlers(m, first);
    }

    private void updateSummaries(IntentModel m) {
        String pkg = m.packageName, cls = m.componentName;
        summaryComponent.setText(!m.useComponent ? "Off — implicit intent"
                : pkg.isEmpty() ? "Package and class for explicit intents"
                : cls.isEmpty() ? pkg + " (package only)" : pkg + "/" + (cls.startsWith(pkg) ? cls.substring(pkg.length()) : cls));
        if (m.useAction && !m.actions.isEmpty()) {
            summaryAction.setText(IntentDumper.shortAction(m.actions.get(0)) + (m.actions.size() > 1 ? " +" + (m.actions.size() - 1) + " more" : ""));
        } else {
            summaryAction.setText(m.useAction ? "Add an action" : "Standard or custom action; several run in sequence");
        }
        if (m.useData && (!m.dataUri.isEmpty() || !m.mimeType.isEmpty())) {
            summaryData.setText((m.dataUri.isEmpty() ? "" : m.dataUri) + (m.mimeType.isEmpty() ? "" : "  · " + m.mimeType));
        } else {
            summaryData.setText("URI and/or MIME type for the receiver");
        }
        summaryCategory.setText(m.useCategory && !m.categories.isEmpty()
                ? m.categories.size() + (m.categories.size() == 1 ? " category" : " categories")
                : "Standard or custom category constants");
        int extraCount = m.useExtras ? m.extraCount() : 0;
        summaryExtras.setText(extraCount > 0 ? extraCount + (extraCount == 1 ? " extra" : " extras")
                : "Typed key–value payload, nested bundles, intents, arrays");
        summaryClipData.setText(m.useClipData && !m.clipDataItems.isEmpty()
                ? m.clipDataItems.size() + (m.clipDataItems.size() == 1 ? " item" : " items")
                : "Attach text, HTML, URIs or intents (use grant flags for URIs)");
        String flagsDesc;
        try {
            int flags = FlagRegistry.combine(m.flagNames, m.customFlags);
            flagsDesc = FlagRegistry.describe(flags);
            flagsEffective.setText("Effective: " + flagsDesc);
        } catch (IllegalArgumentException e) {
            flagsDesc = "invalid";
            flagsEffective.setText("Effective: " + e.getMessage());
        }
        summaryFlags.setText(m.useFlags && !"0".equals(flagsDesc) ? flagsDesc : "Task, URI grant and receiver flags, plus raw values");
        summaryChooser.setText(m.useChooser ? (m.chooserTitle.isEmpty() ? "Chooser without title" : "Chooser: " + m.chooserTitle)
                : "Wrap in Intent.createChooser (activity modes)");
        StringBuilder adv = new StringBuilder();
        if (m.useAdvanced && !m.permission.isEmpty()) adv.append("permission ").append(m.permission);
        if (m.useAdvanced && !m.identifier.isEmpty()) adv.append(adv.length() > 0 ? " · " : "").append("id ").append(m.identifier);
        summaryAdvanced.setText(adv.length() > 0 ? adv.toString() : "Receiver permission, intent identifier");
    }

    private void resolveHandlers(IntentModel m, Intent intent) {
        final int gen = ++resolveGeneration;
        final Context ctx = requireContext().getApplicationContext();
        final String mode = currentMode;
        final boolean blank = IntentBuilder.isBlank(intent);
        executor.execute(() -> {
            String line, details, info;
            if (blank) {
                line = "Empty intent — nothing to resolve";
                details = "—";
                info = null;
            } else {
                PackageManager pm = ctx.getPackageManager();
                StringBuilder sb = new StringBuilder();
                List<String> found = new ArrayList<>();
                try {
                    if (IntentModel.isServiceMode(mode)) {
                        appendResolved(sb, found, "Services", pm.queryIntentServices(intent, PackageManager.MATCH_ALL));
                    } else if (IntentModel.isBroadcastMode(mode)) {
                        appendResolved(sb, found, "Manifest receivers", pm.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL));
                        sb.append("(dynamically registered receivers are not listed)\n");
                    } else if (IntentModel.MODE_RESOLVE.equals(mode)) {
                        appendResolved(sb, found, "Activities", pm.queryIntentActivities(intent, PackageManager.MATCH_ALL));
                        appendResolved(sb, found, "Services", pm.queryIntentServices(intent, PackageManager.MATCH_ALL));
                        appendResolved(sb, found, "Manifest receivers", pm.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL));
                    } else {
                        int flags = intent.getComponent() != null ? PackageManager.MATCH_ALL : PackageManager.MATCH_DEFAULT_ONLY;
                        appendResolved(sb, found, "Activities", pm.queryIntentActivities(intent, flags));
                        ResolveInfo def = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                        if (def != null && def.activityInfo != null && found.size() > 1) {
                            sb.append("Default: ").append(def.activityInfo.packageName).append('/').append(def.activityInfo.name).append('\n');
                        }
                    }
                } catch (RuntimeException e) {
                    sb.append("Resolve failed: ").append(UiUtil.describeThrowable(e)).append('\n');
                }
                if (found.isEmpty()) {
                    line = "No handler found on this device";
                } else if (found.size() == 1) {
                    line = "Handler: " + found.get(0);
                } else {
                    line = found.size() + " handlers: " + found.get(0) + ", …";
                }
                details = sb.toString().trim();
                info = describeComponent(pm, m);
            }
            final String fLine = line, fDetails = details, fInfo = info;
            handler.post(() -> {
                if (gen != resolveGeneration || !isAdded()) return;
                resolveLine.setText(fLine);
                handlersText.setText(fDetails.isEmpty() ? "—" : fDetails);
                if (fInfo == null) {
                    componentInfo.setVisibility(View.GONE);
                } else {
                    componentInfo.setVisibility(View.VISIBLE);
                    componentInfo.setText(fInfo);
                }
            });
        });
    }

    private static void appendResolved(StringBuilder sb, List<String> found, String title, List<ResolveInfo> list) {
        if (list == null || list.isEmpty()) return;
        sb.append(title).append(" (").append(list.size()).append("):\n");
        for (ResolveInfo ri : list) {
            String name, extra;
            boolean exported;
            String perm;
            if (ri.activityInfo != null) {
                name = ri.activityInfo.packageName + "/" + ri.activityInfo.name;
                exported = ri.activityInfo.exported;
                perm = ri.activityInfo.permission;
            } else if (ri.serviceInfo != null) {
                name = ri.serviceInfo.packageName + "/" + ri.serviceInfo.name;
                exported = ri.serviceInfo.exported;
                perm = ri.serviceInfo.permission;
            } else if (ri.providerInfo != null) {
                name = ri.providerInfo.packageName + "/" + ri.providerInfo.name;
                exported = ri.providerInfo.exported;
                perm = ri.providerInfo.readPermission;
            } else continue;
            extra = (exported ? "exported" : "NOT exported") + (perm != null ? ", permission " + perm : "")
                    + (ri.priority != 0 ? ", priority " + ri.priority : "");
            found.add(name);
            sb.append("  ").append(name).append("\n      ").append(extra).append('\n');
        }
    }

    /** Looks up the explicitly named component and describes its kind / export state. */
    private static String describeComponent(PackageManager pm, IntentModel m) {
        if (!m.useComponent || m.packageName.isEmpty()) return null;
        String pkg = m.packageName, cls = m.componentName;
        if (cls.isEmpty()) {
            try {
                pm.getPackageInfo(pkg, 0);
                return "Package installed · implicit intent limited to it";
            } catch (PackageManager.NameNotFoundException e) {
                return "Package not installed";
            }
        }
        if (cls.startsWith(".")) cls = pkg + cls;
        ComponentName cn = new ComponentName(pkg, cls);
        int flags = PackageManager.MATCH_DISABLED_COMPONENTS;
        try {
            ActivityInfo ai = pm.getActivityInfo(cn, flags);
            return "Activity · " + (ai.exported ? "exported" : "NOT exported") + (ai.permission != null ? " · needs " + ai.permission : "") + (ai.enabled ? "" : " · disabled");
        } catch (PackageManager.NameNotFoundException ignored) {}
        try {
            ServiceInfo si = pm.getServiceInfo(cn, flags);
            return "Service · " + (si.exported ? "exported" : "NOT exported") + (si.permission != null ? " · needs " + si.permission : "") + (si.enabled ? "" : " · disabled");
        } catch (PackageManager.NameNotFoundException ignored) {}
        try {
            ActivityInfo ri = pm.getReceiverInfo(cn, flags);
            return "Receiver · " + (ri.exported ? "exported" : "NOT exported") + (ri.permission != null ? " · needs " + ri.permission : "") + (ri.enabled ? "" : " · disabled");
        } catch (PackageManager.NameNotFoundException ignored) {}
        try {
            android.content.pm.ProviderInfo pi = pm.getProviderInfo(cn, flags);
            return "Provider · " + (pi.exported ? "exported" : "NOT exported") + " · authority " + pi.authority;
        } catch (PackageManager.NameNotFoundException ignored) {}
        try {
            pm.getPackageInfo(pkg, 0);
            return "Component not found in " + pkg;
        } catch (PackageManager.NameNotFoundException e) {
            return "Package not installed";
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Launch
    // ══════════════════════════════════════════════════════════════════════

    private void launch() {
        IntentModel model = buildModel();
        List<Intent> intents;
        try {
            intents = IntentBuilder.build(model);
        } catch (RuntimeException e) {
            UiUtil.showError(requireContext(), e.getMessage());
            return;
        }
        if (intents.size() == 1 && IntentBuilder.isBlank(intents.get(0))) {
            Snackbar.make(requireView(), "Set a target, action, data, extra or flag first", Snackbar.LENGTH_SHORT).show();
            return;
        }
        String modelJson = model.toJsonString();
        int launched = 0;
        for (Intent intent : intents) {
            try {
                Intent toSend = intent;
                if (model.useChooser && IntentModel.isActivityMode(currentMode)) {
                    toSend = Intent.createChooser(intent, model.chooserTitle.isEmpty() ? null : model.chooserTitle);
                }
                String outcome = fire(toSend, intent, model);
                events.add(EventStore.Kind.LAUNCH, IntentModel.modeLabel(currentMode) + ": " + IntentDumper.summary(intent),
                        outcome, "Mode: " + IntentModel.modeLabel(currentMode) + "\n" + outcome + "\n\n" + IntentDumper.dump(intent), modelJson);
                launched++;
            } catch (ActivityNotFoundException e) {
                fail(intent, model, "No activity found to handle this intent", e);
            } catch (SecurityException e) {
                fail(intent, model, "Permission denied", e);
            } catch (IllegalStateException e) {
                fail(intent, model, "Illegal state (background start / foreground service rules?)", e);
            } catch (Exception e) {
                fail(intent, model, "Launch failed", e);
            }
        }
        if (launched > 0) {
            try { historyManager.save(model); } catch (RuntimeException e) { Log.e(TAG, "history", e); }
            if (!IntentModel.MODE_RESOLVE.equals(currentMode)) {
                Snackbar.make(requireView(), launched > 1 ? launched + " intents sent" : IntentModel.modeLabel(currentMode) + " sent",
                        Snackbar.LENGTH_SHORT).setAnchorView(bottomSheet).show();
            }
        }
    }

    private void fail(Intent intent, IntentModel model, String title, Exception e) {
        String details = title + "\n" + UiUtil.describeThrowable(e) + "\n\n" + IntentDumper.dump(intent);
        Log.e(TAG, title, e);
        events.add(EventStore.Kind.ERROR, title, UiUtil.describeThrowable(e), details, model.toJsonString());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(UiUtil.describeThrowable(e))
                .setPositiveButton("OK", null)
                .show();
    }

    /** Fires the intent according to the current mode and returns a one-line outcome. */
    private String fire(Intent toSend, Intent original, IntentModel model) {
        Context ctx = requireContext();
        Log.d(TAG, "Launching (" + currentMode + "): " + original.toUri(Intent.URI_INTENT_SCHEME));
        String permission = model.useAdvanced && !model.permission.isEmpty() ? model.permission : null;
        switch (currentMode) {
            case IntentModel.MODE_ACTIVITY_RESULT:
                activityResultLauncher.launch(toSend);
                return "Started for result; waiting for the callee";
            case IntentModel.MODE_SERVICE: {
                ComponentName cn = ctx.startService(toSend);
                return cn == null ? "startService returned null (no service matched)" : "Started " + cn.flattenToShortString();
            }
            case IntentModel.MODE_FG_SERVICE: {
                ComponentName cn;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) cn = ctx.startForegroundService(toSend);
                else cn = ctx.startService(toSend);
                return cn == null ? "startForegroundService returned null (no service matched)" : "Started " + cn.flattenToShortString();
            }
            case IntentModel.MODE_STOP_SERVICE: {
                boolean stopped = ctx.stopService(toSend);
                return stopped ? "stopService returned true (service was running)" : "stopService returned false (nothing to stop)";
            }
            case IntentModel.MODE_BIND_SERVICE: {
                ServiceBinder.Binding b = ServiceBinder.get(ctx).bind(toSend);
                return "bindService accepted; waiting for onServiceConnected (binding #" + Long.toHexString(b.id) + ")";
            }
            case IntentModel.MODE_BROADCAST:
                if (permission != null) ctx.sendBroadcast(toSend, permission);
                else ctx.sendBroadcast(toSend);
                return permission != null ? "Broadcast sent (receivers need " + permission + ")" : "Broadcast sent";
            case IntentModel.MODE_ORDERED_BROADCAST: {
                final Intent sent = original;
                ctx.sendOrderedBroadcast(toSend, permission, new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent i) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Result code: ").append(getResultCode()).append('\n');
                        sb.append("Result data: ").append(getResultData()).append('\n');
                        Bundle extras = getResultExtras(false);
                        sb.append("Result extras:\n").append(extras == null ? "  (none)" : IntentDumper.dumpBundle(extras));
                        sb.append("\n\nSent intent:\n").append(IntentDumper.dump(sent));
                        events.add(EventStore.Kind.BROADCAST_RESULT, "Ordered result code=" + getResultCode(),
                                getResultData() == null ? IntentDumper.summary(sent) : getResultData(), sb.toString());
                        if (isAdded()) UiUtil.showText(requireContext(), "Ordered broadcast result", sb.toString());
                    }
                }, null, Activity.RESULT_OK, null, null);
                return "Ordered broadcast sent; result arrives in Inbox";
            }
            case IntentModel.MODE_RESOLVE: {
                String report = resolveReport(ctx.getPackageManager(), original);
                events.add(EventStore.Kind.RESOLVE, "Resolve: " + IntentDumper.summary(original),
                        report.split("\n")[0], report + "\n\n" + IntentDumper.dump(original), model.toJsonString());
                UiUtil.showText(ctx, "Resolve (dry run)", report);
                return report.split("\n")[0];
            }
            default:
                ctx.startActivity(toSend);
                return "Activity started";
        }
    }

    private static String resolveReport(PackageManager pm, Intent intent) {
        StringBuilder sb = new StringBuilder();
        List<String> found = new ArrayList<>();
        appendResolved(sb, found, "Activities", pm.queryIntentActivities(intent, PackageManager.MATCH_ALL));
        appendResolved(sb, found, "Services", pm.queryIntentServices(intent, PackageManager.MATCH_ALL));
        appendResolved(sb, found, "Manifest receivers", pm.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL));
        appendResolved(sb, found, "Providers", pm.queryIntentContentProviders(intent, PackageManager.MATCH_ALL));
        String head = found.isEmpty() ? "No component on this device matches this intent"
                : found.size() + " matching component" + (found.size() == 1 ? "" : "s");
        ResolveInfo def = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && def.activityInfo != null) {
            sb.append("startActivity would pick: ").append(def.activityInfo.packageName).append('/').append(def.activityInfo.name);
            if ("android".equals(def.activityInfo.packageName)) sb.append(" (system chooser)");
            sb.append('\n');
        }
        return head + "\n\n" + sb.toString().trim();
    }

    private static String describeResultCode(int code) {
        if (code == Activity.RESULT_OK) return "RESULT_OK (-1)";
        if (code == Activity.RESULT_CANCELED) return "RESULT_CANCELED (0)";
        if (code == Activity.RESULT_FIRST_USER) return "RESULT_FIRST_USER (1)";
        return String.valueOf(code);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Bindings
    // ══════════════════════════════════════════════════════════════════════

    private void updateBindingsButton() {
        if (!isAdded()) return;
        int n = ServiceBinder.get(requireContext()).count();
        bindingsButton.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        bindingsButton.setText(n == 1 ? "1 bound" : n + " bound");
    }

    private void showBindingsDialog() {
        ServiceBinder binder = ServiceBinder.get(requireContext());
        List<ServiceBinder.Binding> list = binder.bindings();
        if (list.isEmpty()) return;
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            ServiceBinder.Binding b = list.get(i);
            labels[i] = b.label() + "\n" + (b.connected ? "connected · " + b.descriptor : "connecting…");
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Bound services")
                .setItems(labels, (d, which) -> {
                    binder.unbind(list.get(which).id);
                    Snackbar.make(requireView(), "Unbound", Snackbar.LENGTH_SHORT).show();
                })
                .setPositiveButton("Close", null)
                .setNegativeButton("Unbind all", (d, w) -> binder.unbindAll())
                .show();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Dialogs
    // ══════════════════════════════════════════════════════════════════════

    private void showComponentPicker() {
        ComponentPickerDialog dialog = ComponentPickerDialog.newInstance(false);
        dialog.setListener(new ComponentPickerDialog.Listener() {
            @Override
            public void onPackageOnly(String packageName) {
                useComponent.setChecked(true);
                packageInput.setText(packageName);
                componentInput.setText("");
            }

            @Override
            public void onComponentSelected(ComponentPickerDialog.ComponentEntry e) {
                if (e.kind == ComponentPickerDialog.Kind.PROVIDER) {
                    ((MainActivity) requireActivity()).openProviderWith("content://" + e.authority + "/");
                    return;
                }
                useComponent.setChecked(true);
                packageInput.setText(e.packageName);
                componentInput.setText(e.className);
                if (e.kind == ComponentPickerDialog.Kind.SERVICE && !IntentModel.isServiceMode(currentMode)) {
                    setMode(IntentModel.MODE_SERVICE);
                    Snackbar.make(requireView(), "Switched to Start service mode", Snackbar.LENGTH_SHORT).setAnchorView(bottomSheet).show();
                } else if (e.kind == ComponentPickerDialog.Kind.RECEIVER && !IntentModel.isBroadcastMode(currentMode)) {
                    setMode(IntentModel.MODE_BROADCAST);
                    Snackbar.make(requireView(), "Switched to Broadcast mode", Snackbar.LENGTH_SHORT).setAnchorView(bottomSheet).show();
                } else if (e.kind == ComponentPickerDialog.Kind.ACTIVITY && !IntentModel.isActivityMode(currentMode)
                        && !IntentModel.MODE_RESOLVE.equals(currentMode)) {
                    setMode(IntentModel.MODE_ACTIVITY);
                    Snackbar.make(requireView(), "Switched to Activity mode", Snackbar.LENGTH_SHORT).setAnchorView(bottomSheet).show();
                }
            }
        });
        dialog.show(getParentFragmentManager(), "component_picker");
    }

    public void showImportDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_import, null, false);
        TextInputEditText input = v.findViewById(R.id.importInput);
        android.content.ClipboardManager cm = (android.content.ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence clip = cm.getPrimaryClip().getItemAt(0).coerceToText(requireContext());
            if (clip != null) {
                String s = clip.toString().trim();
                if (s.startsWith("intent:") || s.startsWith("android-app:") || s.startsWith("{") || s.contains("am ")) input.setText(s);
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Import intent")
                .setView(v)
                .setPositiveButton("Load", (d, w) -> {
                    try {
                        IntentModel m = IntentCodec.importAny(UiUtil.raw(input));
                        loadModel(m);
                        Snackbar.make(requireView(), "Imported", Snackbar.LENGTH_SHORT).setAnchorView(bottomSheet).show();
                    } catch (Exception e) {
                        UiUtil.showError(requireContext(), "Could not parse: " + UiUtil.describeThrowable(e));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void showSaveFavoriteDialog() {
        IntentModel m = buildModel();
        View v = getLayoutInflater().inflate(R.layout.dialog_save_favorite, null, false);
        TextInputEditText name = v.findViewById(R.id.favoriteNameInput);
        TextInputEditText note = v.findViewById(R.id.favoriteNoteInput);
        name.setText(m.generateLabel());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Save as favorite")
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    m.name = UiUtil.text(name).isEmpty() ? m.generateLabel() : UiUtil.text(name);
                    m.description = UiUtil.text(note);
                    SavedStore.get(requireContext()).saveFavorite(m);
                    Snackbar.make(requireView(), "Saved to favorites", Snackbar.LENGTH_SHORT).setAnchorView(bottomSheet).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showExtrasFromJsonDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_import, null, false);
        TextInputEditText input = v.findViewById(R.id.importInput);
        input.setHint("{\"id\": 42, \"name\": \"x\", \"nested\": {\"ok\": true}}");
        ((TextView) ((ViewGroup) v).getChildAt(0)).setText("Each JSON value becomes a typed extra: whole numbers → Integer/Long, decimals → Double, objects → nested Bundle, arrays → typed arrays.");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Extras from JSON")
                .setView(v)
                .setPositiveButton("Add", (d, w) -> {
                    try {
                        List<IntentModel.ExtraEntry> entries = ExtrasEditor.entriesFromJson(UiUtil.raw(input));
                        useExtras.setChecked(true);
                        extrasEditor.addAll(entries);
                    } catch (Exception e) {
                        UiUtil.showError(requireContext(), "Invalid JSON: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
