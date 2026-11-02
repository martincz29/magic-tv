package com.magictv.launcher;

final class TileConfig {
    static final String ACTION_PACKAGE = "package";
    static final String ACTION_NONE = "none";
    static final String ACTION_GPT = "gpt";
    static final String ACTION_APPS = "apps";
    static final String ACTION_WEATHER = "weather";
    static final String ACTION_FILES = "files";
    static final String ACTION_SETTINGS = "settings";

    int defaultKind;
    String title;
    String packageName;
    String action;
    boolean useAppIcon;
    boolean hidden;

    TileConfig copy() {
        TileConfig copy = new TileConfig();
        copy.defaultKind = defaultKind;
        copy.title = title;
        copy.packageName = packageName;
        copy.action = action;
        copy.useAppIcon = useAppIcon;
        copy.hidden = hidden;
        return copy;
    }

    static TileConfig defaultTile(int kind) {
        TileConfig tile = new TileConfig();
        tile.defaultKind = kind;
        tile.title = null;
        tile.hidden = false;
        tile.useAppIcon = false;
        switch (kind) {
            case 0:
                tile.action = ACTION_PACKAGE;
                tile.packageName = "com.google.android.youtube.tv";
                break;
            case 1:
                tile.action = ACTION_NONE;
                tile.packageName = null;
                break;
            case 2:
                tile.action = ACTION_GPT;
                break;
            case 3:
                tile.action = ACTION_APPS;
                break;
            case 4:
                tile.action = ACTION_WEATHER;
                break;
            case 5:
                tile.action = ACTION_NONE;
                break;
            case 6:
                tile.action = ACTION_FILES;
                break;
            default:
                tile.action = ACTION_SETTINGS;
                break;
        }
        return tile;
    }
}
