package com.open.intenter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamically registered broadcast receiver that logs every broadcast it
 * catches into the {@link EventStore}. Lives on the application context so
 * it survives configuration changes and tab switches.
 */
public final class BroadcastListener {

    public static final class Config {
        public List<String> actions = new ArrayList<>();
        public String scheme = "";
        public boolean exported = true;
        public boolean abortOrdered = false;
        public int resultCode = -1;          // -1 = leave untouched
        public String resultData = "";
    }

    private static BroadcastListener instance;
    private final Context app;
    private BroadcastReceiver receiver;
    private Config active;

    private BroadcastListener(Context context) {
        app = context.getApplicationContext();
    }

    public static synchronized BroadcastListener get(Context context) {
        if (instance == null) instance = new BroadcastListener(context);
        return instance;
    }

    public boolean isRunning() { return receiver != null; }

    public Config activeConfig() { return active; }

    public synchronized void start(Config config) {
        stop();
        IntentFilter filter = new IntentFilter();
        for (String a : config.actions) {
            if (a != null && !a.trim().isEmpty()) filter.addAction(a.trim());
        }
        if (filter.countActions() == 0) throw new IllegalArgumentException("Add at least one action to listen for");
        if (config.scheme != null && !config.scheme.trim().isEmpty()) filter.addDataScheme(config.scheme.trim());
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handle(this, intent, config);
            }
        };
        int flags = config.exported ? ContextCompat.RECEIVER_EXPORTED : ContextCompat.RECEIVER_NOT_EXPORTED;
        ContextCompat.registerReceiver(app, receiver, filter, flags);
        active = config;
        EventStore.get(app).add(EventStore.Kind.LISTENER, "Listener started",
                filter.countActions() + " action(s)" + (config.exported ? ", exported" : ", app-only"),
                describe(config));
    }

    public synchronized void stop() {
        if (receiver != null) {
            try { app.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) {}
            receiver = null;
            active = null;
            EventStore.get(app).add(EventStore.Kind.LISTENER, "Listener stopped", "", "");
        }
    }

    private void handle(BroadcastReceiver r, Intent intent, Config config) {
        StringBuilder sb = new StringBuilder();
        boolean ordered = r.isOrderedBroadcast();
        sb.append("Ordered: ").append(ordered).append('\n');
        sb.append("Initial sticky: ").append(r.isInitialStickyBroadcast()).append('\n');
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                String pkg = r.getSentFromPackage();
                int uid = r.getSentFromUid();
                sb.append("Sender: ").append(pkg == null ? "(identity not shared)" : pkg)
                        .append(uid >= 0 ? " uid=" + uid : "").append('\n');
            } catch (RuntimeException ignored) {}
        }
        sb.append('\n').append(IntentDumper.dump(intent));
        if (ordered) {
            sb.append("\n\nIncoming result: code=").append(r.getResultCode());
            if (r.getResultData() != null) sb.append(" data=").append(r.getResultData());
            Bundle extras = r.getResultExtras(false);
            if (extras != null && !extras.isEmpty()) sb.append("\n").append(IntentDumper.dumpBundle(extras));
            if (config.resultCode != -1) {
                r.setResultCode(config.resultCode);
                sb.append("\nSet result code → ").append(config.resultCode);
            }
            if (config.resultData != null && !config.resultData.isEmpty()) {
                r.setResultData(config.resultData);
                sb.append("\nSet result data → ").append(config.resultData);
            }
            if (config.abortOrdered) {
                r.abortBroadcast();
                sb.append("\nAborted broadcast");
            }
        }
        String modelJson = null;
        try { modelJson = IntentCodec.fromIntent(intent).toJsonString(); } catch (RuntimeException ignored) {}
        EventStore.get(app).add(EventStore.Kind.LISTENER,
                IntentDumper.shortAction(intent.getAction()),
                IntentDumper.summary(intent), sb.toString(), modelJson);
    }

    public static String describe(Config c) {
        StringBuilder sb = new StringBuilder("Actions:\n");
        for (String a : c.actions) sb.append("  ").append(a).append('\n');
        if (c.scheme != null && !c.scheme.isEmpty()) sb.append("Data scheme: ").append(c.scheme).append('\n');
        sb.append("Exported: ").append(c.exported).append('\n');
        sb.append("Abort ordered: ").append(c.abortOrdered).append('\n');
        if (c.resultCode != -1) sb.append("Result code: ").append(c.resultCode).append('\n');
        if (c.resultData != null && !c.resultData.isEmpty()) sb.append("Result data: ").append(c.resultData).append('\n');
        return sb.toString().trim();
    }
}
