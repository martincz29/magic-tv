package com.magictv.launcher;

import android.graphics.Color;

final class SkinManager {
    static final String MAGIC_CASTLE = "magic_castle";

    private SkinManager() {}

    static String[] availableSkinIds() {
        return new String[]{MAGIC_CASTLE};
    }

    static SkinDefinition load(String id) {
        // Instantiate only the selected skin. Future skins can use their own wallpaper,
        // geometry, colors, typography and decorations without retaining inactive assets.
        return magicCastle();
    }

    private static SkinDefinition magicCastle() {
        float[] x = {.170f, .3975f, .625f, .142f, .288f, .434f, .580f, .726f};
        float[] y = {.315f, .315f, .315f, .655f, .655f, .655f, .655f, .655f};
        float[] w = {.205f, .205f, .205f, .132f, .132f, .132f, .132f, .132f};
        float[] h = {.285f, .285f, .285f, .145f, .145f, .145f, .145f, .145f};
        boolean[] large = {true, true, true, false, false, false, false, false};
        int[] colors = {
                Color.rgb(105, 12, 18), Color.rgb(5, 49, 77), Color.rgb(52, 25, 73),
                Color.rgb(28, 26, 26), Color.rgb(28, 26, 26), Color.rgb(28, 26, 26),
                Color.rgb(28, 26, 26), Color.rgb(28, 26, 26)
        };
        return new SkinDefinition(MAGIC_CASTLE, "Magic Castle", R.drawable.fantasy_wallpaper,
                Color.rgb(228, 181, 91), Color.rgb(255, 220, 116),
                Color.rgb(255, 244, 211), Color.rgb(255, 190, 43), colors, x, y, w, h,
                large, "serif", "royal_banner", "crest_clock");
    }
}
