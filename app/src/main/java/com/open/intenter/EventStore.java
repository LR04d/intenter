package com.open.intenter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Inbox of everything that happens at runtime: launches, activity results,
 * ordered broadcast results, intents received by our exported activity,
 * broadcasts caught by the listener, service connection events, provider
 * results and errors. Persisted as JSON, newest first, capped.
 */
public final class EventStore {

    private static final String TAG = "EventStore";
    private static final String PREFS = "intenter_events";
    private static final String KEY = "events_json";
    private static final int MAX = 300;

    public enum Kind {
        LAUNCH, RESULT, BROADCAST_RESULT, RECEIVED, LISTENER, SERVICE, PROVIDER, RESOLVE, ERROR;

        public String label() {
            switch (this) {
                case LAUNCH: return "Launched";
                case RESULT: return "Result";
                case BROADCAST_RESULT: return "Ordered result";
                case RECEIVED: return "Received";
                case LISTENER: return "Broadcast";
                case SERVICE: return "Service";
                case PROVIDER: return "Provider";
                case RESOLVE: return "Resolve";
                default: return "Error";
            }
        }
    }

    public static final class Event {
        public long id;
        public long time;
        public Kind kind;
        public String title = "";
        public String summary = "";
        public String details = "";
        /** Optional: JSON of an IntentModel that reproduces this intent (for "load into builder"). */
        public String modelJson;

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("time", time);
            o.put("kind", kind.name());
            o.put("title", title);
            o.put("summary", summary);
            o.put("details", details);
            if (modelJson != null) o.put("modelJson", modelJson);
            return o;
        }

        public static Event fromJson(JSONObject o) {
            Event e = new Event();
            e.id = o.optLong("id");
            e.time = o.optLong("time");
            try { e.kind = Kind.valueOf(o.optString("kind", "LAUNCH")); } catch (IllegalArgumentException ex) { e.kind = Kind.LAUNCH; }
            e.title = o.optString("title", "");
            e.summary = o.optString("summary", "");
            e.details = o.optString("details", "");
            e.modelJson = o.has("modelJson") ? o.optString("modelJson") : null;
            return e;
        }
    }

    public interface Listener {
        void onEventsChanged();
    }

    private static EventStore instance;
    private final SharedPreferences prefs;
    private final List<Event> events = new ArrayList<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int unread = 0;

    private EventStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized EventStore get(Context context) {
        if (instance == null) instance = new EventStore(context);
        return instance;
    }

    private void load() {
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) events.add(Event.fromJson(arr.getJSONObject(i)));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load events", e);
        }
    }

    private void persist() {
        JSONArray arr = new JSONArray();
        synchronized (events) {
            for (Event e : events) {
                try { arr.put(e.toJson()); } catch (JSONException ex) { Log.e(TAG, "serialize", ex); }
            }
        }
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    public Event add(Kind kind, String title, String summary, String details) {
        return add(kind, title, summary, details, null);
    }

    /** Adds an event; safe to call from any thread. Returns the stored event. */
    public Event add(Kind kind, String title, String summary, String details, String modelJson) {
        Event e = new Event();
        e.id = System.nanoTime();
        e.time = System.currentTimeMillis();
        e.kind = kind;
        e.title = title == null ? "" : title;
        e.summary = summary == null ? "" : summary;
        e.details = details == null ? "" : details;
        e.modelJson = modelJson;
        synchronized (events) {
            events.add(0, e);
            while (events.size() > MAX) events.remove(events.size() - 1);
            unread++;
        }
        persist();
        notifyChanged();
        return e;
    }

    public List<Event> all() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    public Event find(long id) {
        synchronized (events) {
            for (Event e : events) if (e.id == id) return e;
        }
        return null;
    }

    public void remove(long id) {
        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).id == id) { events.remove(i); break; }
            }
        }
        persist();
        notifyChanged();
    }

    public void clear() {
        synchronized (events) {
            events.clear();
            unread = 0;
        }
        persist();
        notifyChanged();
    }

    public int size() {
        synchronized (events) { return events.size(); }
    }

    public int unread() {
        synchronized (events) { return unread; }
    }

    public void markRead() {
        synchronized (events) { unread = 0; }
        notifyChanged();
    }

    public void addListener(Listener l) { listeners.addIfAbsent(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void notifyChanged() {
        main.post(() -> {
            for (Listener l : listeners) l.onEventsChanged();
        });
    }
}
