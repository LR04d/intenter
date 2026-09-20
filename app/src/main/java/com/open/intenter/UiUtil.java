package com.open.intenter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

/** Small UI helpers shared by the screens. */
public final class UiUtil {

    private UiUtil() {}

    public static String text(EditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }

    public static String raw(EditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString();
    }

    public static TextWatcher watcher(Runnable r) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { r.run(); }
        };
    }

    /** Configures a dropdown that only allows picking from the list. */
    public static void setupPicker(MaterialAutoCompleteTextView view, String[] items, String initial) {
        view.setSimpleItems(items);
        view.setInputType(0);
        view.setKeyListener(null);
        view.setText(initial == null ? items[0] : initial, false);
        view.setOnClickListener(v -> view.showDropDown());
        view.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) view.showDropDown(); });
    }

    /** Configures a free-text field with suggestions. */
    public static void setupSuggestions(MaterialAutoCompleteTextView view, String[] items) {
        view.setSimpleItems(items);
        view.setThreshold(1);
        view.setOnClickListener(v -> { if (view.getText().length() == 0) view.showDropDown(); });
    }

    public static void copy(Context ctx, String label, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show();
        }
    }

    public static void share(Context ctx, String subject, String text) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, text);
        ctx.startActivity(Intent.createChooser(send, subject));
    }

    public static void showError(Context ctx, String message) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    public static void showText(Context ctx, String title, String text) {
        showText(ctx, title, text, null, null);
    }

    /** Shows monospace text with Copy and Close, plus an optional extra action. */
    public static void showText(Context ctx, String title, String text, String actionLabel, Runnable action) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_text, null, false);
        TextView tv = v.findViewById(R.id.dialogText);
        tv.setText(text);
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(v)
                .setPositiveButton("Close", null)
                .setNegativeButton("Copy", (d, w) -> copy(ctx, title, text));
        if (actionLabel != null && action != null) b.setNeutralButton(actionLabel, (d, w) -> action.run());
        b.show();
    }

    public static CharSequence relativeTime(long time) {
        long now = System.currentTimeMillis();
        if (now - time < DateUtils.MINUTE_IN_MILLIS) return "just now";
        return DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE);
    }

    public static String clockTime(long time) {
        return new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(time));
    }

    public static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    public static String describeThrowable(Throwable t) {
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null || msg.isEmpty() ? "" : ": " + msg);
    }
}
