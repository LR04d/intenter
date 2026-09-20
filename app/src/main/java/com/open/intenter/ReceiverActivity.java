package com.open.intenter;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Exported activity that other apps (or adb) can target. It dumps the
 * incoming intent, records who sent it, checks URI grants and can return an
 * arbitrary result to a caller that used startActivityForResult.
 */
public class ReceiverActivity extends AppCompatActivity {

    private static final String[] RESULT_CODES = {"RESULT_OK (-1)", "RESULT_CANCELED (0)", "RESULT_FIRST_USER (1)", "Custom"};

    private TextView callerText, dumpText, uriAccessText, resultHint;
    private View cardUriAccess;
    private MaterialAutoCompleteTextView resultCodeInput;
    private TextInputEditText resultCustomCodeInput, resultDataInput;
    private ExtrasEditor resultExtras;
    private String currentDump = "";
    private IntentModel currentModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver);

        MaterialToolbar toolbar = findViewById(R.id.receiverToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        callerText = findViewById(R.id.callerText);
        dumpText = findViewById(R.id.dumpText);
        uriAccessText = findViewById(R.id.uriAccessText);
        cardUriAccess = findViewById(R.id.cardUriAccess);
        resultHint = findViewById(R.id.resultHint);
        resultCodeInput = findViewById(R.id.resultCodeInput);
        resultCustomCodeInput = findViewById(R.id.resultCustomCodeInput);
        resultDataInput = findViewById(R.id.resultDataInput);
        resultExtras = new ExtrasEditor(this, (LinearLayout) findViewById(R.id.resultExtrasContainer), ExtraTypes.ALL, null);

        UiUtil.setupPicker(resultCodeInput, RESULT_CODES, RESULT_CODES[0]);
        findViewById(R.id.addResultExtraButton).setOnClickListener(v -> resultExtras.add());
        findViewById(R.id.copyDumpButton).setOnClickListener(v -> UiUtil.copy(this, "Received intent", currentDump));
        findViewById(R.id.loadInBuilderButton).setOnClickListener(v -> {
            if (currentModel == null) return;
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra(MainActivity.EXTRA_LOAD_MODEL_JSON, currentModel.toJsonString());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        });
        findViewById(R.id.returnResultButton).setOnClickListener(v -> returnResult());
        findViewById(R.id.closeButton).setOnClickListener(v -> finish());

        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        // Caller identity
        StringBuilder caller = new StringBuilder();
        String callingPkg = getCallingPackage();
        ComponentName callingAct = getCallingActivity();
        boolean forResult = callingAct != null || callingPkg != null;
        caller.append("Calling package: ").append(callingPkg == null ? "(none — not started for result)" : callingPkg).append('\n');
        if (callingAct != null) caller.append("Calling activity: ").append(callingAct.flattenToShortString()).append('\n');
        Uri referrer = getReferrer();
        caller.append("Referrer: ").append(referrer == null ? "(none)" : referrer.toString()).append('\n');
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                String from = getLaunchedFromPackage();
                int uid = getLaunchedFromUid();
                caller.append("Launched from: ").append(from == null ? "(unknown)" : from).append(" uid=").append(uid).append('\n');
            } catch (RuntimeException ignored) {}
        }
        caller.append("Task id: ").append(getTaskId()).append('\n');
        caller.append("Received at: ").append(UiUtil.clockTime(System.currentTimeMillis()));
        callerText.setText(caller.toString());

        // Dump
        currentDump = IntentDumper.dump(intent);
        dumpText.setText(currentDump);
        try {
            currentModel = IntentCodec.fromIntent(intent);
            currentModel.useComponent = true;
            currentModel.packageName = getPackageName();
            currentModel.componentName = ReceiverActivity.class.getName();
        } catch (RuntimeException e) {
            currentModel = null;
        }

        // URI access check
        List<Uri> uris = new ArrayList<>();
        if (intent.getData() != null) uris.add(intent.getData());
        ClipData clip = intent.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) {
            if (clip.getItemAt(i).getUri() != null) uris.add(clip.getItemAt(i).getUri());
        }
        try {
            Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream instanceof Uri) uris.add((Uri) stream);
        } catch (RuntimeException ignored) {}
        if (uris.isEmpty()) {
            cardUriAccess.setVisibility(View.GONE);
        } else {
            cardUriAccess.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (Uri u : uris) {
                sb.append(u).append('\n');
                if (!"content".equals(u.getScheme())) {
                    sb.append("  (not a content URI)\n");
                    continue;
                }
                int read = checkCallingOrSelfUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                int write = checkCallingOrSelfUriPermission(u, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                sb.append("  read grant: ").append(read == PackageManager.PERMISSION_GRANTED ? "granted" : "denied");
                sb.append(", write grant: ").append(write == PackageManager.PERMISSION_GRANTED ? "granted" : "denied").append('\n');
                try (InputStream in = getContentResolver().openInputStream(u)) {
                    int first = in == null ? -1 : in.read();
                    sb.append("  openInputStream: OK").append(first >= 0 ? " (first byte 0x" + Integer.toHexString(first) + ")" : " (empty)").append('\n');
                } catch (Exception e) {
                    sb.append("  openInputStream: ").append(UiUtil.describeThrowable(e)).append('\n');
                }
                try {
                    sb.append("  type: ").append(getContentResolver().getType(u)).append('\n');
                } catch (RuntimeException e) {
                    sb.append("  type: ").append(UiUtil.describeThrowable(e)).append('\n');
                }
            }
            uriAccessText.setText(sb.toString().trim());
        }

        resultHint.setText(forResult
                ? "The caller used startActivityForResult; choose what it gets back."
                : "The caller did not request a result; returning one is harmless but has no effect.");

        // Log to inbox
        String details = caller + "\n\n" + currentDump
                + (uris.isEmpty() ? "" : "\n\nURI access:\n" + uriAccessText.getText());
        EventStore.get(this).add(EventStore.Kind.RECEIVED,
                "Received " + (intent.getAction() == null ? "explicit intent" : IntentDumper.shortAction(intent.getAction()))
                        + (callingPkg != null ? " from " + callingPkg : ""),
                IntentDumper.summary(intent), details,
                currentModel == null ? null : currentModel.toJsonString());
    }

    private void returnResult() {
        int code;
        String sel = resultCodeInput.getText() == null ? RESULT_CODES[0] : resultCodeInput.getText().toString();
        if (sel.startsWith("RESULT_OK")) code = RESULT_OK;
        else if (sel.startsWith("RESULT_CANCELED")) code = RESULT_CANCELED;
        else if (sel.startsWith("RESULT_FIRST_USER")) code = RESULT_FIRST_USER;
        else {
            try { code = Integer.parseInt(UiUtil.text(resultCustomCodeInput)); }
            catch (NumberFormatException e) { UiUtil.showError(this, "Enter a numeric custom result code"); return; }
        }
        Intent data = null;
        String dataUri = UiUtil.text(resultDataInput);
        List<IntentModel.ExtraEntry> extras = resultExtras.entries();
        if (!dataUri.isEmpty() || !extras.isEmpty()) {
            data = new Intent();
            if (!dataUri.isEmpty()) data.setData(Uri.parse(dataUri));
            try {
                Bundle b = IntentBuilder.buildBundle(extras);
                if (!b.isEmpty()) data.putExtras(b);
            } catch (RuntimeException e) {
                UiUtil.showError(this, e.getMessage());
                return;
            }
        }
        setResult(code, data);
        EventStore.get(this).add(EventStore.Kind.RESULT, "Returned result " + code,
                data == null ? "no data" : IntentDumper.summary(data),
                "Returned to caller: code=" + code + "\n" + (data == null ? "(no data)" : IntentDumper.dump(data)));
        finish();
    }
}
