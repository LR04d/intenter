package com.open.intenter;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;

/**
 * Hosts the four tabs: Build (intent builder), Provider (content provider
 * client), Inbox (everything received / returned) and Saved (favorites,
 * history, presets).
 */
public class MainActivity extends AppCompatActivity implements EventStore.Listener {

    public static final String EXTRA_LOAD_MODEL_JSON = "com.open.intenter.LOAD_MODEL_JSON";
    public static final String EXTRA_TAB = "com.open.intenter.TAB";

    private static final String TAG_BUILD = "build";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_INBOX = "inbox";
    private static final String TAG_SAVED = "saved";
    private static final String KEY_TAB = "selectedTab";

    private BottomNavigationView bottomNav;
    private MaterialToolbar toolbar;
    private int selectedTab = R.id.nav_build;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        bottomNav = findViewById(R.id.bottomNav);

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            Fragment build = new BuildFragment();
            Fragment provider = new ProviderFragment();
            Fragment inbox = new InboxFragment();
            Fragment saved = new SavedFragment();
            fm.beginTransaction()
                    .add(R.id.fragmentContainer, build, TAG_BUILD)
                    .add(R.id.fragmentContainer, provider, TAG_PROVIDER)
                    .add(R.id.fragmentContainer, inbox, TAG_INBOX)
                    .add(R.id.fragmentContainer, saved, TAG_SAVED)
                    .hide(provider)
                    .hide(inbox)
                    .hide(saved)
                    .commitNow();
        } else {
            selectedTab = savedInstanceState.getInt(KEY_TAB, R.id.nav_build);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        bottomNav.setOnItemReselectedListener(item -> { /* no-op */ });
        bottomNav.setSelectedItemId(selectedTab);
        showTab(selectedTab);

        handleIncoming(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }

    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        String json = intent.getStringExtra(EXTRA_LOAD_MODEL_JSON);
        if (json != null) {
            intent.removeExtra(EXTRA_LOAD_MODEL_JSON);
            try {
                openBuildWithModel(IntentModel.fromJsonString(json));
            } catch (JSONException e) {
                UiUtil.showError(this, "Could not load intent: " + e.getMessage());
            }
        }
        String tab = intent.getStringExtra(EXTRA_TAB);
        if (tab != null) {
            intent.removeExtra(EXTRA_TAB);
            switch (tab) {
                case "inbox": selectTab(R.id.nav_inbox); break;
                case "saved": selectTab(R.id.nav_saved); break;
                case "provider": selectTab(R.id.nav_provider); break;
                default: selectTab(R.id.nav_build); break;
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, selectedTab);
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventStore.get(this).addListener(this);
        onEventsChanged();
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventStore.get(this).removeListener(this);
    }

    @Override
    public void onEventsChanged() {
        int unread = EventStore.get(this).unread();
        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_inbox);
        if (unread > 0 && selectedTab != R.id.nav_inbox) {
            badge.setVisible(true);
            badge.setNumber(unread);
        } else {
            badge.setVisible(false);
            badge.clearNumber();
        }
    }

    // ─── Tabs ────────────────────────────────────────────────────────────

    private Fragment fragment(String tag, FragmentManager fm) {
        return fm.findFragmentByTag(tag);
    }

    private String tagFor(@IdRes int id) {
        if (id == R.id.nav_provider) return TAG_PROVIDER;
        if (id == R.id.nav_inbox) return TAG_INBOX;
        if (id == R.id.nav_saved) return TAG_SAVED;
        return TAG_BUILD;
    }

    public void selectTab(@IdRes int id) {
        if (bottomNav.getSelectedItemId() != id) bottomNav.setSelectedItemId(id);
        else showTab(id);
    }

    private void showTab(@IdRes int id) {
        selectedTab = id;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        for (String tag : new String[]{TAG_BUILD, TAG_PROVIDER, TAG_INBOX, TAG_SAVED}) {
            Fragment f = fragment(tag, fm);
            if (f == null) continue;
            if (tag.equals(tagFor(id))) tx.show(f); else tx.hide(f);
        }
        tx.commitNowAllowingStateLoss();
        if (id == R.id.nav_inbox) EventStore.get(this).markRead();
        if (id == R.id.nav_build) toolbar.setSubtitle("Build and fire any intent");
        else if (id == R.id.nav_provider) toolbar.setSubtitle("Content provider client");
        else if (id == R.id.nav_inbox) toolbar.setSubtitle("Received intents, results and events");
        else toolbar.setSubtitle("Favorites, history and presets");
        invalidateOptionsMenu();
        onEventsChanged();
    }

    public BuildFragment buildFragment() {
        return (BuildFragment) fragment(TAG_BUILD, getSupportFragmentManager());
    }

    public ProviderFragment providerFragment() {
        return (ProviderFragment) fragment(TAG_PROVIDER, getSupportFragmentManager());
    }

    /** Loads a model into the builder and switches to the Build tab. */
    public void openBuildWithModel(IntentModel model) {
        BuildFragment b = buildFragment();
        if (b != null && b.isAdded()) {
            selectTab(R.id.nav_build);
            b.loadModel(model);
        } else {
            SavedStore.get(this).setPendingLoad(model);
            selectTab(R.id.nav_build);
        }
    }

    public void openProviderWith(String uri) {
        ProviderFragment p = providerFragment();
        selectTab(R.id.nav_provider);
        if (p != null) p.setUri(uri);
    }

    // ─── Menu ────────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean build = selectedTab == R.id.nav_build;
        menu.findItem(R.id.action_save_favorite).setVisible(build);
        menu.findItem(R.id.action_clear).setVisible(build);
        menu.findItem(R.id.action_import).setVisible(build);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        BuildFragment b = buildFragment();
        if (id == R.id.action_history) {
            selectTab(R.id.nav_saved);
            return true;
        } else if (id == R.id.action_clear) {
            if (b != null) b.clearAll();
            return true;
        } else if (id == R.id.action_import) {
            if (b != null) b.showImportDialog();
            return true;
        } else if (id == R.id.action_save_favorite) {
            if (b != null) b.showSaveFavoriteDialog();
            return true;
        } else if (id == R.id.action_about) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Intenter")
                .setMessage("Build, preview and fire any Android intent without writing a test app.\n\n" +
                        "• Explicit and implicit intents, every launch mode: activity, for result, " +
                        "service, foreground service, stop, bind, broadcast, ordered broadcast, dry-run resolve\n" +
                        "• Typed extras incl. nested bundles, intents, arrays and lists; ClipData; flags; " +
                        "chooser; receiver permission; identifier\n" +
                        "• Content provider client: query, insert, update, delete, call, type, read\n" +
                        "• Inbox: activity results, ordered-broadcast results, a broadcast listener and an " +
                        "exported receiver activity that dumps whatever other apps send\n" +
                        "• Export as intent URI, adb shell am, Java, Kotlin or JSON; import any of them\n" +
                        "• Favorites, history and presets with file export / import")
                .setPositiveButton("OK", null)
                .show();
    }
}
