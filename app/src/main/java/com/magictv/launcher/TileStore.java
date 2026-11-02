package com.magictv.launcher;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

final class TileStore {
    private static final String KEY = "tiles_v2";
    private final SharedPreferences preferences;

    TileStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    ArrayList<TileConfig> load() {
        String stored = preferences.getString(KEY, null);
        if (stored == null) return defaults();
        try {
            JSONArray array = new JSONArray(stored);
            if (array.length() != 8) return defaults();
            ArrayList<TileConfig> result = new ArrayList<>();
            boolean[] seenKinds = new boolean[8];
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                TileConfig tile = new TileConfig();
                tile.defaultKind = object.getInt("defaultKind");
                if (tile.defaultKind < 0 || tile.defaultKind > 7 || seenKinds[tile.defaultKind]) return defaults();
                seenKinds[tile.defaultKind] = true;
                tile.title = object.isNull("title") ? null : object.getString("title");
                tile.packageName = object.isNull("packageName") ? null : object.getString("packageName");
                tile.action = object.getString("action");
                tile.useAppIcon = object.optBoolean("useAppIcon", false);
                tile.hidden = object.optBoolean("hidden", false);
                result.add(tile);
            }
            return result;
        } catch (Exception ignored) {
            return defaults();
        }
    }

    void save(ArrayList<TileConfig> tiles) {
        JSONArray array = new JSONArray();
        try {
            for (TileConfig tile : tiles) {
                JSONObject object = new JSONObject();
                object.put("defaultKind", tile.defaultKind);
                object.put("title", tile.title == null ? JSONObject.NULL : tile.title);
                object.put("packageName", tile.packageName == null ? JSONObject.NULL : tile.packageName);
                object.put("action", tile.action);
                object.put("useAppIcon", tile.useAppIcon);
                object.put("hidden", tile.hidden);
                array.put(object);
            }
            preferences.edit().putString(KEY, array.toString()).commit();
        } catch (Exception ignored) {
            // Existing valid settings stay untouched if serialization ever fails.
        }
    }

    void reset() {
        preferences.edit().remove(KEY).commit();
    }

    static ArrayList<TileConfig> defaults() {
        ArrayList<TileConfig> tiles = new ArrayList<>();
        for (int i = 0; i < 8; i++) tiles.add(TileConfig.defaultTile(i));
        return tiles;
    }

    static ArrayList<TileConfig> copy(ArrayList<TileConfig> source) {
        ArrayList<TileConfig> result = new ArrayList<>();
        for (TileConfig tile : source) result.add(tile.copy());
        return result;
    }
}
