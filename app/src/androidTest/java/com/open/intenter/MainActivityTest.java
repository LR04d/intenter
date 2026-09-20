package com.open.intenter;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.espresso.action.ViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for the Build tab hosted by MainActivity.
 */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    private void setSwitchChecked(int switchId, boolean checked) {
        activityRule.getScenario().onActivity(activity -> {
            MaterialSwitch switchView = activity.findViewById(switchId);
            assertNotNull(switchView);
            switchView.setChecked(checked);
        });
    }

    private void clickOnUiThread(int viewId) {
        activityRule.getScenario().onActivity(activity -> {
            View view = activity.findViewById(viewId);
            assertNotNull(view);
            view.performClick();
        });
    }

    private void assertViewVisibility(int viewId, int expectedVisibility) {
        activityRule.getScenario().onActivity(activity -> {
            View view = activity.findViewById(viewId);
            assertNotNull(view);
            assertEquals(expectedVisibility, view.getVisibility());
        });
    }

    private void waitForIdle() {
        try { Thread.sleep(400); } catch (InterruptedException ignored) {}
    }

    @Test
    public void tc01_appLaunchesSuccessfully() {
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
        onView(withId(R.id.launchButton)).check(matches(isDisplayed()));
        onView(withId(R.id.bottomNav)).check(matches(isDisplayed()));
    }

    @Test
    public void tc02_componentSectionVisibleByDefault() {
        onView(withId(R.id.componentLayout)).check(matches(isDisplayed()));
        onView(withId(R.id.useComponent)).check(matches(isChecked()));
    }

    @Test
    public void tc03_componentSwitchToggle() {
        onView(withId(R.id.useComponent)).perform(click());
        onView(withId(R.id.componentLayout)).check(matches(not(isDisplayed())));
        onView(withId(R.id.useComponent)).perform(click());
        onView(withId(R.id.componentLayout)).check(matches(isDisplayed()));
    }

    @Test
    public void tc04_otherSectionsHiddenByDefault() {
        assertViewVisibility(R.id.actionsLayout, View.GONE);
        assertViewVisibility(R.id.dataLayout, View.GONE);
        assertViewVisibility(R.id.categoriesLayout, View.GONE);
        assertViewVisibility(R.id.extrasLayout, View.GONE);
        assertViewVisibility(R.id.clipDataLayout, View.GONE);
        assertViewVisibility(R.id.flagsLayout, View.GONE);
        assertViewVisibility(R.id.chooserLayout, View.GONE);
        assertViewVisibility(R.id.advancedLayout, View.GONE);
    }

    @Test
    public void tc05_actionSwitchAddsFirstRow() {
        setSwitchChecked(R.id.useActions, true);
        assertViewVisibility(R.id.actionsLayout, View.VISIBLE);
        activityRule.getScenario().onActivity(activity -> {
            LinearLayout c = activity.findViewById(R.id.actionsContainer);
            assertEquals(1, c.getChildCount());
        });
    }

    @Test
    public void tc06_dataSwitchToggle() {
        setSwitchChecked(R.id.useData, true);
        assertViewVisibility(R.id.dataLayout, View.VISIBLE);
        activityRule.getScenario().onActivity(activity -> {
            assertNotNull(activity.findViewById(R.id.dataUriInput));
            assertNotNull(activity.findViewById(R.id.dataTypeInput));
        });
    }

    @Test
    public void tc07_categorySwitchToggle() {
        setSwitchChecked(R.id.useCategory, true);
        assertViewVisibility(R.id.categoriesLayout, View.VISIBLE);
    }

    @Test
    public void tc08_flagsSwitchShowsGroupedChips() {
        setSwitchChecked(R.id.useFlags, true);
        assertViewVisibility(R.id.flagsLayout, View.VISIBLE);
        activityRule.getScenario().onActivity(activity -> {
            LinearLayout groups = activity.findViewById(R.id.flagsGroupsContainer);
            assertTrue("Flag groups should be populated", groups.getChildCount() >= 2 * FlagRegistry.groups().size());
        });
    }

    @Test
    public void tc09_addCategoryItem() {
        setSwitchChecked(R.id.useCategory, true);
        clickOnUiThread(R.id.addCategoryButton);
        activityRule.getScenario().onActivity(activity -> {
            LinearLayout c = activity.findViewById(R.id.categoriesContainer);
            assertEquals(2, c.getChildCount());
        });
    }

    @Test
    public void tc10_addExtraItemAndNestedBundle() {
        setSwitchChecked(R.id.useExtras, true);
        clickOnUiThread(R.id.addExtraButton);
        activityRule.getScenario().onActivity(activity -> {
            LinearLayout c = activity.findViewById(R.id.extrasContainer);
            assertEquals(2, c.getChildCount());
            assertNotNull(c.getChildAt(0).findViewById(R.id.extraChildrenContainer));
        });
    }

    @Test
    public void tc11_clipDataSwitchAddsRow() {
        setSwitchChecked(R.id.useClipData, true);
        assertViewVisibility(R.id.clipDataLayout, View.VISIBLE);
        activityRule.getScenario().onActivity(activity -> {
            LinearLayout c = activity.findViewById(R.id.clipDataItemsContainer);
            assertEquals(1, c.getChildCount());
        });
    }

    @Test
    public void tc12_modeButtonAndSheetPresent() {
        onView(withId(R.id.modeButton)).check(matches(isDisplayed()));
        onView(withId(R.id.previewLine)).check(matches(isDisplayed()));
        activityRule.getScenario().onActivity(activity ->
                assertNotNull(activity.findViewById(R.id.intentPreviewText)));
    }

    @Test
    public void tc13_previewControlsExist() {
        activityRule.getScenario().onActivity(activity -> {
            assertNotNull(activity.findViewById(R.id.copyIntentButton));
            assertNotNull(activity.findViewById(R.id.shareIntentButton));
            assertNotNull(activity.findViewById(R.id.formatChips));
        });
    }

    @Test
    public void tc14_typingPackageUpdatesPreview() {
        onView(withId(R.id.packageInput)).perform(typeText("com.test.app"), ViewActions.closeSoftKeyboard());
        waitForIdle();
        activityRule.getScenario().onActivity(activity -> {
            TextView preview = activity.findViewById(R.id.previewLine);
            assertTrue(preview.getText().toString(), preview.getText().toString().contains("com.test.app"));
        });
    }

    @Test
    public void tc15_launchWithEmptyIntentNoCrash() {
        setSwitchChecked(R.id.useComponent, false);
        clickOnUiThread(R.id.launchButton);
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
    }

    @Test
    public void tc16_extrasSwitchToggle() {
        setSwitchChecked(R.id.useExtras, true);
        assertViewVisibility(R.id.extrasLayout, View.VISIBLE);
        activityRule.getScenario().onActivity(activity ->
                assertNotNull(activity.findViewById(R.id.addExtraButton)));
    }

    @Test
    public void tc17_advancedSwitchToggle() {
        setSwitchChecked(R.id.useAdvanced, true);
        assertViewVisibility(R.id.advancedLayout, View.VISIBLE);
        activityRule.getScenario().onActivity(activity ->
                assertNotNull(activity.findViewById(R.id.receiverPermissionInput)));
    }

    @Test
    public void tc18_bottomNavSwitchesTabs() {
        onView(withId(R.id.nav_inbox)).perform(click());
        onView(withId(R.id.eventsRecyclerView)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_saved)).perform(click());
        onView(withId(R.id.savedTabs)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_provider)).perform(click());
        onView(withId(R.id.providerUriInput)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_build)).perform(click());
        onView(withId(R.id.launchButton)).check(matches(isDisplayed()));
    }

    @Test
    public void tc19_previewUpdatesWithComponentInput() {
        onView(withId(R.id.packageInput)).perform(typeText("com.foo"), ViewActions.closeSoftKeyboard());
        onView(withId(R.id.componentInput)).perform(typeText(".Bar"), ViewActions.closeSoftKeyboard());
        waitForIdle();
        activityRule.getScenario().onActivity(activity -> {
            TextView preview = activity.findViewById(R.id.previewLine);
            assertTrue(preview.getText().toString(), preview.getText().toString().contains("com.foo/.Bar")
                    || preview.getText().toString().contains("com.foo/com.foo.Bar"));
        });
    }

    @Test
    public void tc20_browsePackageButtonVisible() {
        onView(withId(R.id.browsePackageButton)).check(matches(isDisplayed()));
    }

    @Test
    public void tc21_chooserSwitchToggle() {
        setSwitchChecked(R.id.useChooser, true);
        assertViewVisibility(R.id.chooserLayout, View.VISIBLE);
        activityRule.getScenario().onActivity(activity ->
                assertNotNull(activity.findViewById(R.id.chooserTitleInput)));
    }

    @Test
    public void tc22_historyManagerSaveAndRetrieve() {
        activityRule.getScenario().onActivity(activity -> {
            HistoryManager hm = HistoryManager.getInstance(activity);
            hm.clearAll();

            IntentModel m = new IntentModel();
            m.packageName = "com.test";
            m.action = "VIEW";
            m.label = "Test";
            hm.save(m);

            assertEquals(1, hm.getAll().size());
            assertEquals("com.test", hm.getAll().get(0).packageName);

            hm.delete(0);
            assertEquals(0, hm.getAll().size());
            hm.clearAll();
        });
    }

    @Test
    public void tc23_historyManagerCapsAt100() {
        activityRule.getScenario().onActivity(activity -> {
            HistoryManager hm = HistoryManager.getInstance(activity);
            hm.clearAll();
            for (int i = 0; i < 110; i++) {
                IntentModel m = new IntentModel();
                m.label = "entry_" + i;
                hm.save(m);
            }
            assertTrue(hm.getAll().size() <= 100);
            hm.clearAll();
        });
    }

    @Test
    public void tc24_loadModelIntoBuilder() {
        activityRule.getScenario().onActivity(activity -> {
            IntentModel m = new IntentModel();
            m.useComponent = true;
            m.packageName = "com.example";
            m.componentName = ".Act";
            m.useAction = true;
            m.actions.add("android.intent.action.VIEW");
            m.useExtras = true;
            m.extras.add(new IntentModel.ExtraEntry("k", "v", ExtraTypes.STRING));
            m.launchType = IntentModel.MODE_BROADCAST;
            activity.openBuildWithModel(m);
            IntentModel back = activity.buildFragment().buildModel();
            assertEquals("com.example", back.packageName);
            assertEquals(".Act", back.componentName);
            assertEquals("android.intent.action.VIEW", back.primaryAction());
            assertEquals(1, back.extras.size());
            assertEquals(IntentModel.MODE_BROADCAST, back.launchType);
        });
    }

    @Test
    public void tc25_eventStoreRecordsAndClears() {
        activityRule.getScenario().onActivity(activity -> {
            EventStore store = EventStore.get(activity);
            store.clear();
            store.add(EventStore.Kind.LAUNCH, "t", "s", "d");
            assertEquals(1, store.size());
            store.clear();
            assertEquals(0, store.size());
        });
    }
}
