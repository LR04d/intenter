package com.open.intenter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages intent launch history using SharedPreferences (JSON array).
 * Thread-safe singleton.
 */
public class HistoryManager {

    private static final String TAG = "HistoryManager";
    private static final String PREFS_NAME = "intenter_history";
    private static final String KEY_HISTORY = "history_json";
    private static final int MAX_ENTRIES = 100;

    private static HistoryManager instance;
    private final SharedPreferences prefs;

    private HistoryManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized HistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new HistoryManager(context);
        }
        return instance;
    }

    /**
     * Save a new entry at the front of the history list.
     * Caps at MAX_ENTRIES (oldest dropped).
     */
    public void save(IntentModel model) {
        List<IntentModel> list = getAll();
        model.timestamp = System.currentTimeMillis();
        if (model.label.isEmpty()) {
            model.label = model.generateLabel();
        }
        list.add(0, model); // newest first
        if (list.size() > MAX_ENTRIES) {
            list = list.subList(0, MAX_ENTRIES);
        }
        persist(list);
    }

    /**
     * @return all history entries, newest first.
     */
    public List<IntentModel> getAll() {
        List<IntentModel> list = new ArrayList<>();
        String json = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(IntentModel.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error reading history", e);
        }
        return list;
    }

    /**
     * Delete a single entry by index.
     */
    public void delete(int index) {
        List<IntentModel> list = getAll();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            persist(list);
        }
    }

    /**
     * Clear all history.
     */
    public void clearAll() {
        prefs.edit().putString(KEY_HISTORY, "[]").apply();
    }

    /**
     * @return number of entries
     */
    public int size() {
        return getAll().size();
    }

    private void persist(List<IntentModel> list) {
        JSONArray arr = new JSONArray();
        for (IntentModel m : list) {
            try {
                arr.put(m.toJson());
            } catch (JSONException e) {
                Log.e(TAG, "Error serializing history entry", e);
            }
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }
}

