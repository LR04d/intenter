package com.open.intenter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Inbox: broadcast listener controls, info about the exported receiver
 * activity, and the event log (launches, results, received intents…).
 */
public class InboxFragment extends Fragment implements EventStore.Listener {

    public static final String RECEIVER_COMPONENT = "com.open.intenter/.ReceiverActivity";

    private View listenerBody;
    private ImageView listenerChevron;
    private TextView listenerStatus, eventsEmpty;
    private TextInputEditText actionsInput, schemeInput, resultCodeInput, resultDataInput;
    private MaterialSwitch exportedSwitch, abortSwitch;
    private MaterialButton toggleButton;
    private RecyclerView recyclerView;
    private final EventAdapter adapter = new EventAdapter();
    private EventStore store;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inbox, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        store = EventStore.get(requireContext());
        listenerBody = v.findViewById(R.id.listenerBody);
        listenerChevron = v.findViewById(R.id.listenerChevron);
        listenerStatus = v.findViewById(R.id.listenerStatus);
        actionsInput = v.findViewById(R.id.listenerActionsInput);
        schemeInput = v.findViewById(R.id.listenerSchemeInput);
        resultCodeInput = v.findViewById(R.id.listenerResultCodeInput);
        resultDataInput = v.findViewById(R.id.listenerResultDataInput);
        exportedSwitch = v.findViewById(R.id.listenerExportedSwitch);
        abortSwitch = v.findViewById(R.id.listenerAbortSwitch);
        toggleButton = v.findViewById(R.id.listenerToggleButton);
        eventsEmpty = v.findViewById(R.id.eventsEmpty);
        recyclerView = v.findViewById(R.id.eventsRecyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        v.findViewById(R.id.headerListener).setOnClickListener(x -> toggleListenerBody());
        toggleButton.setOnClickListener(x -> toggleListener());
        v.findViewById(R.id.clearEventsButton).setOnClickListener(x -> confirmClear());
        v.findViewById(R.id.copyReceiverComponentButton).setOnClickListener(x ->
                UiUtil.copy(requireContext(), "Component", RECEIVER_COMPONENT));
        v.findViewById(R.id.copyReceiverAdbButton).setOnClickListener(x ->
                UiUtil.copy(requireContext(), "adb", "adb shell am start -n " + RECEIVER_COMPONENT
                        + " -a android.intent.action.VIEW -d 'intenter://echo?x=1' --es hello world --ei n 7"));

        restoreListenerConfig();
        updateListenerStatus();
        refresh();
    }

    @Override
    public void onStart() {
        super.onStart();
        store.addListener(this);
        refresh();
    }

    @Override
    public void onStop() {
        super.onStop();
        store.removeListener(this);
        saveListenerConfig();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            store.markRead();
            refresh();
        }
    }

    @Override
    public void onEventsChanged() {
        refresh();
        updateListenerStatus();
    }

    private void refresh() {
        if (!isAdded()) return;
        List<EventStore.Event> list = store.all();
        adapter.submit(list);
        eventsEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear inbox")
                .setMessage("Delete all logged events?")
                .setPositiveButton("Clear", (d, w) -> store.clear())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Listener ────────────────────────────────────────────────────────

    private void toggleListenerBody() {
        boolean show = listenerBody.getVisibility() != View.VISIBLE;
        listenerBody.setVisibility(show ? View.VISIBLE : View.GONE);
        listenerChevron.setRotation(show ? 180 : 0);
    }

    private BroadcastListener.Config readConfig() {
        BroadcastListener.Config c = new BroadcastListener.Config();
        for (String part : UiUtil.raw(actionsInput).split("[,\\n]")) {
            if (!part.trim().isEmpty()) c.actions.add(part.trim());
        }
        c.scheme = UiUtil.text(schemeInput);
        c.exported = exportedSwitch.isChecked();
        c.abortOrdered = abortSwitch.isChecked();
        String code = UiUtil.text(resultCodeInput);
        c.resultCode = -1;
        if (!code.isEmpty()) {
            try { c.resultCode = Integer.parseInt(code); } catch (NumberFormatException ignored) {}
        }
        c.resultData = UiUtil.text(resultDataInput);
        return c;
    }

    private void toggleListener() {
        BroadcastListener listener = BroadcastListener.get(requireContext());
        if (listener.isRunning()) {
            listener.stop();
        } else {
            try {
                listener.start(readConfig());
                saveListenerConfig();
            } catch (RuntimeException e) {
                UiUtil.showError(requireContext(), UiUtil.describeThrowable(e));
            }
        }
        updateListenerStatus();
    }

    private void updateListenerStatus() {
        if (!isAdded()) return;
        BroadcastListener listener = BroadcastListener.get(requireContext());
        if (listener.isRunning()) {
            BroadcastListener.Config c = listener.activeConfig();
            listenerStatus.setText("Listening · " + (c == null ? "" : c.actions.size() + " action(s)"
                    + (c.exported ? ", from any app" : ", this app only")));
            toggleButton.setText("Stop listening");
            toggleButton.setIconResource(R.drawable.ic_stop);
        } else {
            listenerStatus.setText("Stopped · catch broadcasts other apps send");
            toggleButton.setText("Start listening");
            toggleButton.setIconResource(R.drawable.ic_play);
        }
    }

    private void saveListenerConfig() {
        try {
            JSONObject o = new JSONObject();
            o.put("actions", UiUtil.raw(actionsInput));
            o.put("scheme", UiUtil.raw(schemeInput));
            o.put("exported", exportedSwitch.isChecked());
            o.put("abort", abortSwitch.isChecked());
            o.put("resultCode", UiUtil.raw(resultCodeInput));
            o.put("resultData", UiUtil.raw(resultDataInput));
            SavedStore.get(requireContext()).saveListenerConfig(o);
        } catch (JSONException ignored) {}
    }

    private void restoreListenerConfig() {
        JSONObject o = SavedStore.get(requireContext()).listenerConfig();
        if (o == null) {
            actionsInput.setText("com.example.ACTION_TEST");
            return;
        }
        actionsInput.setText(o.optString("actions", ""));
        schemeInput.setText(o.optString("scheme", ""));
        exportedSwitch.setChecked(o.optBoolean("exported", true));
        abortSwitch.setChecked(o.optBoolean("abort", false));
        resultCodeInput.setText(o.optString("resultCode", ""));
        resultDataInput.setText(o.optString("resultData", ""));
    }

    // ─── Event detail ────────────────────────────────────────────────────

    private void showEvent(EventStore.Event e) {
        String text = UiUtil.clockTime(e.time) + " · " + e.kind.label() + "\n" + e.title
                + (e.summary.isEmpty() ? "" : "\n" + e.summary) + "\n\n" + (e.details.isEmpty() ? "(no details)" : e.details);
        if (e.modelJson != null) {
            UiUtil.showText(requireContext(), e.kind.label(), text, "Load into builder", () -> {
                try {
                    ((MainActivity) requireActivity()).openBuildWithModel(IntentModel.fromJsonString(e.modelJson));
                } catch (JSONException ex) {
                    UiUtil.showError(requireContext(), ex.getMessage());
                }
            });
        } else {
            UiUtil.showText(requireContext(), e.kind.label(), text);
        }
    }

    // ─── Adapter ─────────────────────────────────────────────────────────

    private class EventAdapter extends RecyclerView.Adapter<EventVH> {
        private final List<EventStore.Event> items = new ArrayList<>();

        void submit(List<EventStore.Event> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public EventVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new EventVH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull EventVH h, int position) {
            EventStore.Event e = items.get(position);
            h.badge.setText(badgeText(e.kind));
            h.badge.setBackgroundResource(badgeBg(e.kind));
            h.badge.setTextColor(requireContext().getColor(badgeColor(e.kind)));
            h.title.setText(e.title.isEmpty() ? e.kind.label() : e.title);
            h.summary.setText(e.summary);
            h.summary.setVisibility(e.summary.isEmpty() ? View.GONE : View.VISIBLE);
            h.time.setText(UiUtil.relativeTime(e.time));
            h.itemView.setOnClickListener(v -> showEvent(e));
            h.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Delete event?")
                        .setPositiveButton("Delete", (d, w) -> store.remove(e.id))
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static String badgeText(EventStore.Kind k) {
        switch (k) {
            case LAUNCH: return "SENT";
            case RESULT: return "RESULT";
            case BROADCAST_RESULT: return "ORDERED";
            case RECEIVED: return "IN";
            case LISTENER: return "BCAST";
            case SERVICE: return "SVC";
            case PROVIDER: return "PRV";
            case RESOLVE: return "RESOLVE";
            default: return "ERROR";
        }
    }

    private static int badgeBg(EventStore.Kind k) {
        switch (k) {
            case LAUNCH: return R.drawable.bg_badge_primary;
            case RESULT:
            case BROADCAST_RESULT: return R.drawable.bg_badge_success;
            case RECEIVED:
            case LISTENER: return R.drawable.bg_badge_tertiary;
            case SERVICE:
            case PROVIDER: return R.drawable.bg_badge_secondary;
            case RESOLVE: return R.drawable.bg_badge_neutral;
            default: return R.drawable.bg_badge_error;
        }
    }

    private static int badgeColor(EventStore.Kind k) {
        switch (k) {
            case LAUNCH: return R.color.app_on_primary_container;
            case RESULT:
            case BROADCAST_RESULT: return R.color.app_success;
            case RECEIVED:
            case LISTENER: return R.color.app_on_tertiary_container;
            case SERVICE:
            case PROVIDER: return R.color.app_on_secondary_container;
            case RESOLVE: return R.color.app_on_surface_variant;
            default: return R.color.app_on_error_container;
        }
    }

    static class EventVH extends RecyclerView.ViewHolder {
        TextView badge, title, summary, time;
        EventVH(View v) {
            super(v);
            badge = v.findViewById(R.id.eventBadge);
            title = v.findViewById(R.id.eventTitle);
            summary = v.findViewById(R.id.eventSummary);
            time = v.findViewById(R.id.eventTime);
        }
    }
}
