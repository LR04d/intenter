package com.open.intenter;

import android.content.ComponentName;
import com.google.android.material.button.MaterialButton;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private EditText packageInput, componentInput, categoryInput;
    private LinearLayout componentLayout, extrasLayout;
    private CheckBox useComponent, useCategory, useExtras;
    private List<ExtraItem> extrasList;
    private LinearLayout extrasContainer;
    private List<ActionItem> actionsList;
    private List<BundleItem> bundlesList;

    private List<CategoryItem> categoriesList;
    private LinearLayout actionsContainer;
    private LinearLayout bundlesContainer;
    private CheckBox useBundle;

    private CheckBox useData;
    private EditText dataUriInput;
    private EditText dataTypeInput;
    private LinearLayout dataLayout;

    private CheckBox useActions;
    private LinearLayout actionsLayout;

    private RadioGroup launchTypeRadioGroup;

    private LinearLayout categoriesContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setupCheckboxListeners();

        launchTypeRadioGroup = findViewById(R.id.launchTypeRadioGroup);

        // Set component as default
        useComponent.setChecked(true);
        componentLayout.setVisibility(View.VISIBLE);

        findViewById(R.id.addExtraButton).setOnClickListener(v -> addNewExtra());
        findViewById(R.id.addActionButton).setOnClickListener(v -> addNewAction());
        findViewById(R.id.addBundleButton).setOnClickListener(v -> addNewBundle());
        findViewById(R.id.addCategoryButton).setOnClickListener(v -> addNewCategory());
        findViewById(R.id.launchButton).setOnClickListener(v -> launchActivity());

        extrasList = new ArrayList<>();
        actionsList = new ArrayList<>();
        bundlesList = new ArrayList<>();
        categoriesList = new ArrayList<>();
    }

    private void initializeViews() {
        packageInput = findViewById(R.id.packageInput);
        componentInput = findViewById(R.id.componentInput);
        // Remove this line - it's looking for a single categoryInput that no longer exists
        // categoryInput = findViewById(R.id.categoryInput);

        useComponent = findViewById(R.id.useComponent);
        useCategory = findViewById(R.id.useCategory);
        useExtras = findViewById(R.id.useExtras);
        categoriesContainer = findViewById(R.id.categoriesContainer);

        componentLayout = findViewById(R.id.componentLayout);
        extrasLayout = findViewById(R.id.extrasLayout);
        extrasContainer = findViewById(R.id.extrasContainer);

        useActions = findViewById(R.id.useActions);
        actionsLayout = findViewById(R.id.actionsLayout);

        actionsContainer = findViewById(R.id.actionsContainer);
        bundlesContainer = findViewById(R.id.bundlesContainer);
        useBundle = findViewById(R.id.useBundle);

        useData = findViewById(R.id.useData);
        dataUriInput = findViewById(R.id.dataUriInput);
        dataTypeInput = findViewById(R.id.dataTypeInput);
        dataLayout = findViewById(R.id.dataLayout);

        setupEditTextFocusListener(packageInput);
        setupEditTextFocusListener(componentInput);
        // Remove this line as categoryInput is null
        // setupEditTextFocusListener(categoryInput);
        setupEditTextFocusListener(dataUriInput);
        setupEditTextFocusListener(dataTypeInput);
    }


    private void addNewAction() {
        View actionView = getLayoutInflater().inflate(R.layout.action_item, actionsContainer, false);
        EditText actionInput = actionView.findViewById(R.id.actionInput);
        ImageButton removeButton = actionView.findViewById(R.id.removeActionButton);

        ActionItem actionItem = new ActionItem(actionView, actionInput);
        actionsList.add(actionItem);
        actionsContainer.addView(actionView);

        removeButton.setOnClickListener(v -> {
            actionsList.remove(actionItem);
            actionsContainer.removeView(actionView);
        });
    }

    private void addNewBundle() {
        View bundleView = getLayoutInflater().inflate(R.layout.bundle_item, bundlesContainer, false);
        EditText bundleKeyInput = bundleView.findViewById(R.id.bundleKeyInput);
        LinearLayout bundleExtrasContainer = bundleView.findViewById(R.id.bundleExtrasContainer);
        // Change this line from ImageButton to MaterialButton
        com.google.android.material.button.MaterialButton removeButton = bundleView.findViewById(R.id.removeBundleButton);
        Button addBundleExtraButton = bundleView.findViewById(R.id.addBundleExtraButton);

        BundleItem bundleItem = new BundleItem(bundleView, bundleKeyInput, new ArrayList<>());
        bundlesList.add(bundleItem);
        bundlesContainer.addView(bundleView);

        removeButton.setOnClickListener(v -> {
            bundlesList.remove(bundleItem);
            bundlesContainer.removeView(bundleView);
        });

        addBundleExtraButton.setOnClickListener(v -> addNewBundleExtra(bundleItem, bundleExtrasContainer));
    }

    private void addNewBundleExtra(BundleItem bundleItem, LinearLayout container) {
        View extraView = getLayoutInflater().inflate(R.layout.extra_item, container, false);
        Spinner typeSpinner = extraView.findViewById(R.id.extraTypeSpinner);
        EditText keyInput = extraView.findViewById(R.id.extraKeyInput);
        EditText valueInput = extraView.findViewById(R.id.extraValueInput);
        ImageButton removeButton = extraView.findViewById(R.id.removeExtraButton);

        setupEditTextFocusListener(keyInput);
        setupEditTextFocusListener(valueInput);

        ExtraItem extraItem = new ExtraItem(extraView, typeSpinner, keyInput, valueInput);
        bundleItem.extras.add(extraItem);
        container.addView(extraView);

        removeButton.setOnClickListener(v -> {
            bundleItem.extras.remove(extraItem);
            container.removeView(extraView);
        });
    }

    private void setupCheckboxListeners() {
        CheckBox useComponent = findViewById(R.id.useComponent);
        CheckBox useData = findViewById(R.id.useData);
        CheckBox useCategory = findViewById(R.id.useCategory);
        CheckBox useExtras = findViewById(R.id.useExtras);
        CheckBox useBundle = findViewById(R.id.useBundle);
        CheckBox useActions = findViewById(R.id.useActions);
        View actionsLayout = findViewById(R.id.actionsLayout);

        View componentLayout = findViewById(R.id.componentLayout);
        View dataLayout = findViewById(R.id.dataLayout);
        TextInputEditText categoryInput = findViewById(R.id.categoryInput);
        View extrasLayout = findViewById(R.id.extrasLayout);
        View bundlesLayout = findViewById(R.id.bundlesLayout);

        // Component checkbox listener
        useComponent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            componentLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Data checkbox listener
        useData.setOnCheckedChangeListener((buttonView, isChecked) -> {
            dataLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        useActions.setOnCheckedChangeListener((buttonView, isChecked) -> {
            actionsLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Category checkbox listener
        useCategory.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            categoryInput.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            findViewById(R.id.categoriesLayout).setVisibility(isChecked ? View.VISIBLE : View.GONE);

        });

        // Extras checkbox listener
        useExtras.setOnCheckedChangeListener((buttonView, isChecked) -> {
            extrasLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Bundle checkbox listener
        useBundle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            bundlesLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
    }
    private void setDataDetails(Intent intent) {
        String dataUri = dataUriInput.getText().toString().trim();
        String dataType = dataTypeInput.getText().toString().trim();

        if (!dataUri.isEmpty()) {
            Uri uri = Uri.parse(dataUri);
            if (!dataType.isEmpty()) {
                intent.setDataAndType(uri, dataType);
            } else {
                intent.setData(uri);
            }
        } else if (!dataType.isEmpty()) {
            intent.setType(dataType);
        }
    }

    // Add a new method for handling categories similar to extras
    private void addNewCategory() {
        View categoryView = getLayoutInflater().inflate(R.layout.category_item, categoriesContainer, false);
        TextInputEditText categoryInput = categoryView.findViewById(R.id.categoryInput);
        ImageButton removeButton = categoryView.findViewById(R.id.removeCategoryButton);

        // Either add this method or use the existing one
        categoryInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.post(() -> {
                    v.requestFocus();
                    v.scrollTo(0, v.getBottom());
                });
            }
        });

        CategoryItem categoryItem = new CategoryItem(categoryView, categoryInput);
        categoriesList.add(categoryItem);
        categoriesContainer.addView(categoryView);

        removeButton.setOnClickListener(v -> {
            categoriesList.remove(categoryItem);
            categoriesContainer.removeView(categoryView);
        });
    }
    private void addNewExtra() {
        View extraView = getLayoutInflater().inflate(R.layout.extra_item, extrasContainer, false);
        Spinner typeSpinner = extraView.findViewById(R.id.extraTypeSpinner);
        EditText keyInput = extraView.findViewById(R.id.extraKeyInput);
        EditText valueInput = extraView.findViewById(R.id.extraValueInput);
        ImageButton removeButton = extraView.findViewById(R.id.removeExtraButton);

        setupEditTextFocusListener(keyInput);
        setupEditTextFocusListener(valueInput);

        ExtraItem extraItem = new ExtraItem(extraView, typeSpinner, keyInput, valueInput);
        extrasList.add(extraItem);
        extrasContainer.addView(extraView);

        removeButton.setOnClickListener(v -> {
            extrasList.remove(extraItem);
            extrasContainer.removeView(extraView);
        });
    }

    private void addExtrasToIntent(Intent intent) {
        for (ExtraItem extra : extrasList) {
            String key = extra.keyInput.getText().toString().trim();
            String value = extra.valueInput.getText().toString().trim();

            if (!key.isEmpty() && !value.isEmpty()) {
                addTypedExtra(intent, key, value, extra.typeSpinner.getSelectedItemPosition());
            }
        }
    }

    private void addExtrasToBundleItem(Bundle bundle, BundleItem bundleItem) {
        for (ExtraItem extra : bundleItem.extras) {
            String key = extra.keyInput.getText().toString().trim();
            String value = extra.valueInput.getText().toString().trim();

            if (!key.isEmpty() && !value.isEmpty()) {
                addTypedExtraToBundle(bundle, key, value, extra.typeSpinner.getSelectedItemPosition());
            }
        }
    }

    private void addTypedExtra(Intent intent, String key, String value, int type) {
        try {
            switch (type) {
                case 0: // String
                    intent.putExtra(key, value);
                    break;
                case 1: // Integer
                    intent.putExtra(key, Integer.parseInt(value));
                    break;
                case 2: // Boolean
                    intent.putExtra(key, Boolean.parseBoolean(value));
                    break;
                case 3: // Float
                    intent.putExtra(key, Float.parseFloat(value));
                    break;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid value for key: " + key, Toast.LENGTH_SHORT).show();
        }
    }

    private void addTypedExtraToBundle(Bundle bundle, String key, String value, int type) {
        try {
            switch (type) {
                case 0: // String
                    bundle.putString(key, value);
                    break;
                case 1: // Integer
                    bundle.putInt(key, Integer.parseInt(value));
                    break;
                case 2: // Boolean
                    bundle.putBoolean(key, Boolean.parseBoolean(value));
                    break;
                case 3: // Float
                    bundle.putFloat(key, Float.parseFloat(value));
                    break;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid value for key: " + key, Toast.LENGTH_SHORT).show();
        }
    }

    private void setComponentDetails(Intent intent) {
        String packageName = packageInput.getText().toString().trim();
        String componentName = componentInput.getText().toString().trim();
        if (!packageName.isEmpty() && !componentName.isEmpty()) {
//            ComponentName component = new ComponentName(packageName, componentName);
//            intent.setComponent(component);
            intent.setClassName(packageName, componentName);
        }
    }

    // This method should be removed or updated, as it's using the old single categoryInput
    private void setCategoryDetails(Intent intent) {
        // This should be updated to use the new categories list
        if (useCategory.isChecked()) {
            for (CategoryItem category : categoriesList) {
                String categoryString = category.categoryInput.getText().toString().trim();
                if (!categoryString.isEmpty()) {
                    intent.addCategory(categoryString);
                }
            }
        }
    }

    private void setupEditTextFocusListener(EditText editText) {
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.post(() -> {
                    v.requestFocus();
                    v.scrollTo(0, v.getBottom());
                });
            }
        });
    }

    private void launchActivity() {
        try {
            Intent intent = new Intent();

            if (useComponent.isChecked()) {
                setComponentDetails(intent);
            }

            // Add actions if checked
            if (useActions.isChecked()) {
                for (ActionItem action : actionsList) {
                    String actionString = action.actionInput.getText().toString().trim();
                    if (!actionString.isEmpty()) {
                        intent.setAction(actionString);
                    }
                }
            }

            // Add categories
            if (useCategory.isChecked()) {
                for (CategoryItem category : categoriesList) {
                    String categoryString = category.categoryInput.getText().toString().trim();
                    if (!categoryString.isEmpty()) {
                        intent.addCategory(categoryString);
                    }
                }
            }

            if (useData.isChecked()) {
                setDataDetails(intent);
            }

            // Add extras
            if (useExtras.isChecked()) {
                addExtrasToIntent(intent);
            }

            // Add bundles
            if (useBundle.isChecked()) {
                for (BundleItem bundleItem : bundlesList) {
                    String bundleKey = bundleItem.keyInput.getText().toString().trim();
                    if (!bundleKey.isEmpty()) {
                        Bundle bundle = new Bundle();
                        addExtrasToBundleItem(bundle, bundleItem);
                        intent.putExtra(bundleKey, bundle);
                    } else {
                        Bundle bundle = new Bundle();
                        addExtrasToBundleItem(bundle, bundleItem);
                        intent.putExtras(bundle);
                    }
                }
            }

            Log.d("INTENTER", "launchActivity: " + intent.toUri(Intent.URI_INTENT_SCHEME));
            // Get selected launch type
            int selectedLaunchType = launchTypeRadioGroup.getCheckedRadioButtonId();

            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (selectedLaunchType == R.id.launchActivity) {
                    // Start activity
                    startActivity(intent);
                    Toast.makeText(this, "Activity launched", Toast.LENGTH_SHORT).show();
                } else if (selectedLaunchType == R.id.launchService) {
                    // Start service
                    startService(intent);
                    Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
                } else if (selectedLaunchType == R.id.launchBroadcast) {
                    // Send broadcast
                    sendBroadcast(intent);
                    Toast.makeText(this, "Broadcast sent", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e("INTENTER", "Error launching: " + e.getMessage());
                Toast.makeText(this, "Launch failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Add a new class for category items
    private static class CategoryItem {
        View view;
        TextInputEditText categoryInput;

        CategoryItem(View view, TextInputEditText categoryInput) {
            this.view = view;
            this.categoryInput = categoryInput;
        }
    }

    private static class ActionItem {
        View view;
        EditText actionInput;

        ActionItem(View view, EditText actionInput) {
            this.view = view;
            this.actionInput = actionInput;
        }
    }

    private static class BundleItem {
        View view;
        EditText keyInput;
        List<ExtraItem> extras;

        BundleItem(View view, EditText keyInput, List<ExtraItem> extras) {
            this.view = view;
            this.keyInput = keyInput;
            this.extras = extras;
        }
    }

    private static class ExtraItem {
        View view;
        Spinner typeSpinner;
        EditText keyInput;
        EditText valueInput;

        ExtraItem(View view, Spinner typeSpinner, EditText keyInput, EditText valueInput) {
            this.view = view;
            this.typeSpinner = typeSpinner;
            this.keyInput = keyInput;
            this.valueInput = valueInput;
        }
    }
}