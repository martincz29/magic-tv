package com.magictv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Looper;
import android.view.KeyEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MagicHomeViewTest {
    private MainActivity activity;
    private MagicHomeView view;

    @Before public void setUp() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("magic_tv", Context.MODE_PRIVATE).edit().clear().commit();
        activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Field field = MainActivity.class.getDeclaredField("homeView");
        field.setAccessible(true);
        view = (MagicHomeView) field.get(activity);
    }

    @Test public void dpadMovesBetweenMagicCastleTiles() throws Exception {
        assertEquals(0, intField("focus"));
        view.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(1, intField("focus"));
    }

    @Test public void shortOkOpensSettingsTile() throws Exception {
        setIntField("focus", 7);
        KeyEvent down = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
        KeyEvent up = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER);
        view.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, down);
        view.onKeyUp(KeyEvent.KEYCODE_DPAD_CENTER, up);
        assertEquals(2, intField("page"));
    }

    @Test public void longOkOpensTileContextMenu() throws Exception {
        KeyEvent down = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
        view.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, down);
        shadowOf(Looper.getMainLooper()).idleFor(700, TimeUnit.MILLISECONDS);
        assertEquals(3, intField("page"));
        view.onKeyUp(KeyEvent.KEYCODE_DPAD_CENTER,
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
    }

    @Test public void backDoesNothingOnHomeAndReturnsOneLevelFromSettings() throws Exception {
        view.onKeyDown(KeyEvent.KEYCODE_BACK, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
        assertEquals(0, intField("page"));
        assertFalse(activity.isFinishing());

        view.onKeyDown(KeyEvent.KEYCODE_MENU, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
        assertEquals(2, intField("page"));
        view.onKeyDown(KeyEvent.KEYCODE_BACK, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
        assertEquals(0, intField("page"));
    }

    @Test public void emptyAppListHandlesNavigationAndBack() throws Exception {
        Field apps = MagicHomeView.class.getDeclaredField("installed");
        apps.setAccessible(true);
        ((ArrayList<?>) apps.get(view)).clear();
        setIntField("page", 1);
        setIntField("pickerMode", 0);
        view.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
        view.onKeyDown(KeyEvent.KEYCODE_BACK, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
        assertEquals(0, intField("page"));
    }

    @Test public void tileSettingsPersistAcrossStoreReload() {
        SharedPreferences preferences = RuntimeEnvironment.getApplication()
                .getSharedPreferences("magic_tv", Context.MODE_PRIVATE);
        TileStore store = new TileStore(preferences);
        ArrayList<TileConfig> tiles = TileStore.defaults();
        tiles.get(4).title = "SmartTube";
        tiles.get(4).packageName = "com.example.smarttube";
        tiles.get(4).action = TileConfig.ACTION_PACKAGE;
        tiles.get(4).useAppIcon = true;
        tiles.get(6).hidden = true;
        store.save(tiles);

        ArrayList<TileConfig> restored = new TileStore(preferences).load();
        assertEquals("SmartTube", restored.get(4).title);
        assertEquals("com.example.smarttube", restored.get(4).packageName);
        assertTrue(restored.get(4).useAppIcon);
        assertTrue(restored.get(6).hidden);
    }

    @Test public void appIsRegisteredAsHomeLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> results = activity.getPackageManager().queryIntentActivities(home, 0);
        boolean found = false;
        for (ResolveInfo result : results) {
            if (result.activityInfo != null && activity.getPackageName().equals(result.activityInfo.packageName)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    private int intField(String name) throws Exception {
        Field field = MagicHomeView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(view);
    }

    private void setIntField(String name, int value) throws Exception {
        Field field = MagicHomeView.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(view, value);
    }
}
