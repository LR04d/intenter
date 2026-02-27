package com.open.intenter;

import android.content.Intent;
import android.view.View;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.action.ViewActions;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

/**
 * Instrumented tests for MainActivity covering all UI and intent building scenarios.
 */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    // ─── TC-01: App launches ─────────────────────────────────────────────
    @Test
    public void tc01_appLaunchesSuccessfully() {
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
        onView(withId(R.id.launchButton)).check(matches(isDisplayed()));
    }

    // ─── TC-02: Component on by default ──────────────────────────────────
    @Test
    public void tc02_componentSectionVisibleByDefault() {
        onView(withId(R.id.componentLayout)).check(matches(isDisplayed()));
        onView(withId(R.id.useComponent)).check(matches(isChecked()));
    }

    // ─── TC-03: Toggle component switch ──────────────────────────────────
    @Test
    public void tc03_componentSwitchToggle() {
        onView(withId(R.id.useComponent)).perform(click());
        onView(withId(R.id.componentLayout)).check(matches(not(isDisplayed())));
        onView(withId(R.id.useComponent)).perform(click());
        onView(withId(R.id.componentLayout)).check(matches(isDisplayed()));
    }

    // ─── TC-04: Other sections hidden by default ─────────────────────────
    @Test
    public void tc04_otherSectionsHiddenByDefault() {
        onView(withId(R.id.actionsLayout)).check(matches(not(isDisplayed())));
        onView(withId(R.id.dataLayout)).check(matches(not(isDisplayed())));
        onView(withId(R.id.categoriesLayout)).check(matches(not(isDisplayed())));
        onView(withId(R.id.extrasLayout)).check(matches(not(isDisplayed())));
        onView(withId(R.id.bundlesLayout)).check(matches(not(isDisplayed())));
        onView(withId(R.id.flagsLayout)).check(matches(not(isDisplayed())));
        onView(withId(R.id.chooserLayout)).check(matches(not(isDisplayed())));
    }

    // ─── TC-05: Action toggle ────────────────────────────────────────────
    @Test
    public void tc05_actionSwitchToggle() {
        onView(withId(R.id.useActions)).perform(click());
        onView(withId(R.id.actionsLayout)).check(matches(isDisplayed()));
        onView(withId(R.id.actionInput)).check(matches(isDisplayed()));
    }

    // ─── TC-06: Data toggle ──────────────────────────────────────────────
    @Test
    public void tc06_dataSwitchToggle() {
        onView(withId(R.id.useData)).perform(click());
        onView(withId(R.id.dataLayout)).check(matches(isDisplayed()));
        onView(withId(R.id.dataUriInput)).check(matches(isDisplayed()));
        onView(withId(R.id.dataTypeInput)).check(matches(isDisplayed()));
    }

    // ─── TC-07: Category toggle ──────────────────────────────────────────
    @Test
    public void tc07_categorySwitchToggle() {
        onView(withId(R.id.useCategory)).perform(click());
        onView(withId(R.id.categoriesLayout)).check(matches(isDisplayed()));
    }

    // ─── TC-08: Flags toggle ─────────────────────────────────────────────
    @Test
    public void tc08_flagsSwitchToggle() {
        onView(withId(R.id.useFlags)).perform(scrollTo(), click());
        onView(withId(R.id.flagsLayout)).check(matches(isDisplayed()));
        onView(withId(R.id.flagsChipGroup)).check(matches(isDisplayed()));
    }

    // ─── TC-09: Add category ─────────────────────────────────────────────
    @Test
    public void tc09_addCategoryItem() {
        onView(withId(R.id.useCategory)).perform(click());
        onView(withId(R.id.addCategoryButton)).perform(click());
        onView(withId(R.id.categoriesContainer)).check(matches(isDisplayed()));
    }

    // ─── TC-10: Add extra ────────────────────────────────────────────────
    @Test
    public void tc10_addExtraItem() {
        onView(withId(R.id.useExtras)).perform(click());
        onView(withId(R.id.addExtraButton)).perform(click());
        onView(withId(R.id.extrasContainer)).check(matches(isDisplayed()));
    }

    // ─── TC-11: Add bundle ───────────────────────────────────────────────
    @Test
    public void tc11_addBundleItem() {
        onView(withId(R.id.useBundle)).perform(scrollTo(), click());
        onView(withId(R.id.addBundleButton)).perform(scrollTo(), click());
        onView(withId(R.id.bundlesContainer)).check(matches(isDisplayed()));
    }

    // ─── TC-12: Launch type chips visible ────────────────────────────────
    @Test
    public void tc12_launchTypeChips() {
        onView(withId(R.id.chipActivity)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.chipService)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.chipFgService)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.chipBroadcast)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.chipActivityResult)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    // ─── TC-13: Preview visible ──────────────────────────────────────────
    @Test
    public void tc13_intentPreviewVisible() {
        onView(withId(R.id.intentPreviewText)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.copyIntentButton)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    // ─── TC-14: Package input updates preview ────────────────────────────
    @Test
    public void tc14_typingPackageUpdatesPreview() {
        onView(withId(R.id.packageInput)).perform(typeText("com.test.app"), ViewActions.closeSoftKeyboard());
        onView(withId(R.id.intentPreviewText)).perform(scrollTo())
                .check(matches(withText(containsString("com.test.app"))));
    }

    // ─── TC-15: Launch empty intent no crash ─────────────────────────────
    @Test
    public void tc15_launchWithEmptyComponentNoCrash() {
        onView(withId(R.id.useComponent)).perform(click());
        onView(withId(R.id.launchButton)).perform(scrollTo(), click());
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
    }

    // ─── TC-16: Extras toggle ────────────────────────────────────────────
    @Test
    public void tc16_extrasSwitchToggle() {
        onView(withId(R.id.useExtras)).perform(click());
        onView(withId(R.id.extrasLayout)).check(matches(isDisplayed()));
        onView(withId(R.id.addExtraButton)).check(matches(isDisplayed()));
    }

    // ─── TC-17: Bundle toggle ────────────────────────────────────────────
    @Test
    public void tc17_bundleSwitchToggle() {
        onView(withId(R.id.useBundle)).perform(scrollTo(), click());
        onView(withId(R.id.bundlesLayout)).check(matches(isDisplayed()));
        onView(withId(R.id.addBundleButton)).check(matches(isDisplayed()));
    }

    // ─── TC-18: Flags chip count ─────────────────────────────────────────
    @Test
    public void tc18_flagsChipGroupHasChildren() {
        onView(withId(R.id.useFlags)).perform(scrollTo(), click());
        activityRule.getScenario().onActivity(activity -> {
            com.google.android.material.chip.ChipGroup cg = activity.findViewById(R.id.flagsChipGroup);
            assertTrue("Flags chip group should have children", cg.getChildCount() > 0);
        });
    }

    // ─── TC-19: Preview updates with component ──────────────────────────
    @Test
    public void tc19_previewUpdatesWithComponentInput() {
        onView(withId(R.id.packageInput)).perform(typeText("com.foo"), ViewActions.closeSoftKeyboard());
        onView(withId(R.id.componentInput)).perform(typeText(".Bar"), ViewActions.closeSoftKeyboard());
        onView(withId(R.id.intentPreviewText)).perform(scrollTo())
                .check(matches(withText(containsString("com.foo"))));
    }

    // ─── TC-20: Browse button exists ─────────────────────────────────────
    @Test
    public void tc20_browsePackageButtonVisible() {
        onView(withId(R.id.browsePackageButton)).check(matches(isDisplayed()));
    }

    // ─── TC-21: Chooser toggle ───────────────────────────────────────────
    @Test
    public void tc21_chooserSwitchToggle() {
        onView(withId(R.id.useChooser)).perform(scrollTo(), click());
        onView(withId(R.id.chooserLayout)).check(matches(isDisplayed()));
        onView(withId(R.id.chooserTitleInput)).check(matches(isDisplayed()));
    }

    // ─── TC-22: History manager saves ────────────────────────────────────
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

    // ─── TC-23: History manager cap ──────────────────────────────────────
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

    // ─── TC-24: IntentModel round-trip in activity context ───────────────
    @Test
    public void tc24_intentModelJsonRoundTrip() {
        activityRule.getScenario().onActivity(activity -> {
            try {
                IntentModel m = new IntentModel();
                m.useComponent = true;
                m.packageName = "com.example";
                m.componentName = ".Act";
                m.useAction = true;
                m.action = "android.intent.action.VIEW";

                org.json.JSONObject json = m.toJson();
                IntentModel restored = IntentModel.fromJson(json);

                assertEquals("com.example", restored.packageName);
                assertEquals(".Act", restored.componentName);
                assertEquals("android.intent.action.VIEW", restored.action);
            } catch (Exception e) {
                fail("JSON round-trip failed: " + e.getMessage());
            }
        });
    }
}
