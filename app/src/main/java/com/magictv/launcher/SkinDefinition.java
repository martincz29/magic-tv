package com.magictv.launcher;

final class SkinDefinition {
    final String id;
    final String displayName;
    final int wallpaperResId;
    final int gold;
    final int brightGold;
    final int textColor;
    final int glowColor;
    final int[] cardColors;
    final float[] x;
    final float[] y;
    final float[] width;
    final float[] height;
    final boolean[] large;
    final String fontFamily;
    final String ornamentStyle;
    final String decorationStyle;

    SkinDefinition(String id, String displayName, int wallpaperResId, int gold, int brightGold,
                   int textColor, int glowColor, int[] cardColors, float[] x, float[] y,
                   float[] width, float[] height, boolean[] large, String fontFamily,
                   String ornamentStyle, String decorationStyle) {
        this.id = id;
        this.displayName = displayName;
        this.wallpaperResId = wallpaperResId;
        this.gold = gold;
        this.brightGold = brightGold;
        this.textColor = textColor;
        this.glowColor = glowColor;
        this.cardColors = cardColors;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.large = large;
        this.fontFamily = fontFamily;
        this.ornamentStyle = ornamentStyle;
        this.decorationStyle = decorationStyle;
    }
}
