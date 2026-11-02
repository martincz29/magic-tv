package com.magictv.launcher;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class MagicHomeView extends View {
    private static final int PAGE_HOME = 0;
    private static final int PAGE_APPS = 1;
    private static final int PAGE_SETTINGS = 2;
    private static final int PAGE_CONTEXT = 3;
    private static final int PAGE_EDIT_TILE = 4;
    private static final int PAGE_MOVE = 5;
    private static final int PAGE_SKINS = 6;
    private static final int PAGE_HOME_NAV = 7;
    private static final int PAGE_ABOUT = 8;
    private static final int PAGE_HOME_EDIT = 9;

    private static final int PICK_LAUNCH = 0;
    private static final int PICK_TILE = 1;

    private static final int MENU_EDIT = 0;
    private static final int MENU_MOVE = 1;
    private static final int MENU_HIDE = 2;
    private static final int MENU_INFO = 3;
    private static final int MENU_UNINSTALL = 4;

    private static final long LONG_PRESS_MS = 650L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences preferences;
    private final PackageManager packageManager;
    private final TileStore tileStore;
    private final ArrayList<AppItem> installed = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ClockTicker ticker = new ClockTicker();

    private ArrayList<TileConfig> tiles;
    private ArrayList<TileConfig> moveSnapshot;
    private ArrayList<Integer> contextActions = new ArrayList<>();
    private SkinDefinition skin;
    private Bitmap wallpaper;
    private Locale locale = Locale.ENGLISH;

    private int page = PAGE_HOME;
    private int returnPage = PAGE_HOME;
    private int focus = 0;
    private int menuFocus = 0;
    private int appFocus = 0;
    private int editingIndex = 0;
    private int pickerMode = PICK_LAUNCH;
    private int moveStartIndex = 0;
    private boolean okHeld;
    private boolean longPressTriggered;

    private final String[][] defaultLabels = {
            {"YouTube", "Stream Debrid", "GPT", "Apps", "Weather", "Radio", "Files", "Settings"},
            {"YouTube", "Stream Debrid", "GPT", "Aplikace", "Počasí", "Rádio", "Soubory", "Nastavení"},
            {"YouTube", "Stream Debrid", "GPT", "Aplicaciones", "Clima", "Radio", "Archivos", "Ajustes"}
    };

    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            if (!okHeld || (page != PAGE_HOME && page != PAGE_HOME_EDIT) || focus < 0) return;
            longPressTriggered = true;
            openContextMenu(focus, page);
        }
    };

    public MagicHomeView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        preferences = context.getSharedPreferences("magic_tv", Context.MODE_PRIVATE);
        packageManager = context.getPackageManager();
        tileStore = new TileStore(preferences);
        tiles = tileStore.load();
        setLanguage(preferences.getString("lang", "en"));
        loadActiveSkin();
        loadInstalledApps();
        reconcileRemovedPackages();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        ensureFocusVisible();
        ticker.start(this);
    }

    public void refreshSystemState() {
        loadInstalledApps();
        reconcileRemovedPackages();
        if (page == PAGE_CONTEXT) buildContextActions();
        ensureFocusVisible();
        requestFocus();
        invalidate();
    }

    public void showHome() {
        handler.removeCallbacks(longPressRunnable);
        okHeld = false; longPressTriggered = false;
        page = PAGE_HOME; focus = firstVisible(); requestFocus(); invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable);
        ticker.stop();
        if (wallpaper != null && !wallpaper.isRecycled()) wallpaper.recycle();
        wallpaper = null;
        super.onDetachedFromWindow();
    }

    private void loadActiveSkin() {
        String active = preferences.getString("skin", SkinManager.MAGIC_CASTLE);
        skin = SkinManager.load(active);
        if (wallpaper != null && !wallpaper.isRecycled()) wallpaper.recycle();
        wallpaper = BitmapFactory.decodeResource(getResources(), skin.wallpaperResId);
    }

    private int langIndex() {
        String language = preferences.getString("lang", "en");
        if ("cs".equals(language)) return 1;
        if ("es".equals(language)) return 2;
        return 0;
    }

    private void setLanguage(String language) {
        if ("cs".equals(language)) locale = new Locale("cs", "CZ");
        else if ("es".equals(language)) locale = new Locale("es", "ES");
        else locale = Locale.ENGLISH;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackground(canvas);
        switch (page) {
            case PAGE_HOME:
            case PAGE_HOME_EDIT:
            case PAGE_MOVE:
                drawHome(canvas);
                if (page == PAGE_HOME_EDIT) drawHomeModeHint(canvas,
                        tr("Choose a tile • OK or long OK to edit", "Vyber box • OK nebo dlouhé OK upraví",
                                "Elige un cuadro • OK o mantén OK para editar"));
                if (page == PAGE_MOVE) drawHomeModeHint(canvas,
                        tr("Move with D-pad • OK saves • Back cancels", "Přesuň D-padem • OK uloží • Zpět zruší",
                                "Mueve con D-pad • OK guarda • Atrás cancela"));
                break;
            case PAGE_APPS:
                drawApps(canvas);
                break;
            case PAGE_SETTINGS:
                drawSettings(canvas);
                break;
            case PAGE_CONTEXT:
                drawHome(canvas);
                drawContextMenu(canvas);
                break;
            case PAGE_EDIT_TILE:
                drawEditTile(canvas);
                break;
            case PAGE_SKINS:
                drawSkins(canvas);
                break;
            case PAGE_HOME_NAV:
                drawHomeNavigation(canvas);
                break;
            case PAGE_ABOUT:
                drawAbout(canvas);
                break;
            default:
                drawHome(canvas);
        }
    }

    private void drawBackground(Canvas canvas) {
        int width = getWidth(), height = getHeight();
        if (wallpaper != null) {
            canvas.drawBitmap(wallpaper, new Rect(0, 0, wallpaper.getWidth(), wallpaper.getHeight()),
                    new Rect(0, 0, width, height), paint);
        } else canvas.drawColor(Color.rgb(4, 14, 26));
        paint.setShader(new LinearGradient(0, height * .22f, 0, height,
                Color.TRANSPARENT, Color.argb(62, 0, 8, 18), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
    }

    private void drawHome(Canvas canvas) {
        int width = getWidth(), height = getHeight();
        drawCrest(canvas, width * .105f, height * .105f, height * .072f);
        drawClock(canvas);
        boolean any = false;
        for (int i = 0; i < tiles.size(); i++) {
            TileConfig tile = tiles.get(i);
            if (tile.hidden && page != PAGE_HOME_EDIT && page != PAGE_MOVE) continue;
            any = true;
            drawCard(canvas, i, tile, focus == i);
            if (tile.hidden) drawHiddenBadge(canvas, i);
        }
        if (!any) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            paint.setColor(skin.textColor);
            paint.setTextSize(height * .035f);
            canvas.drawText(tr("No visible tiles", "Žádné viditelné boxy", "No hay cuadros visibles"),
                    width / 2f, height * .53f, paint);
            paint.setTextSize(height * .021f);
            canvas.drawText(tr("Press MENU for Settings", "Stiskni MENU pro Nastavení",
                    "Pulsa MENU para Ajustes"), width / 2f, height * .58f, paint);
        }
    }

    private void drawClock(Canvas canvas) {
        int width = getWidth(), height = getHeight();
        Date now = new Date();
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(Color.WHITE);
        paint.setTextSize(height * .064f);
        canvas.drawText(new SimpleDateFormat("HH:mm", locale).format(now), width * .955f, height * .076f, paint);
        paint.setTextSize(height * .023f);
        paint.setColor(Color.LTGRAY);
        canvas.drawText(new SimpleDateFormat("EEE d MMM", locale).format(now), width * .955f, height * .108f, paint);
        stroke.setColor(Color.argb(155, 226, 187, 106));
        stroke.setStrokeWidth(Math.max(2f, height * .002f));
        canvas.drawLine(width * .962f, height * .025f, width * .962f, height * .112f, stroke);
    }

    private void drawCrest(Canvas canvas, float centerX, float centerY, float radius) {
        Path shield = new Path();
        shield.moveTo(centerX - radius * .72f, centerY - radius * .6f);
        shield.quadTo(centerX, centerY - radius, centerX + radius * .72f, centerY - radius * .6f);
        shield.lineTo(centerX + radius * .56f, centerY + radius * .44f);
        shield.quadTo(centerX, centerY + radius, centerX - radius * .56f, centerY + radius * .44f);
        shield.close();
        paint.setColor(Color.argb(170, 39, 18, 16));
        canvas.drawPath(shield, paint);
        stroke.setColor(skin.gold);
        stroke.setStrokeWidth(radius * .055f);
        canvas.drawPath(shield, stroke);
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(skin.brightGold);
        paint.setTextSize(radius * .72f);
        canvas.drawText("MV", centerX, centerY + radius * .22f, paint);
    }

    private void drawCard(Canvas canvas, int index, TileConfig tile, boolean selected) {
        float x = getWidth() * skin.x[index];
        float y = getHeight() * skin.y[index];
        float width = getWidth() * skin.width[index];
        float height = getHeight() * skin.height[index];
        boolean large = skin.large[index];
        Path card = bannerPath(x, y, width, height, large ? .10f : .12f);
        if (selected) {
            for (int layer = 3; layer >= 1; layer--) {
                stroke.setColor(Color.argb(34 + layer * 20, Color.red(skin.glowColor),
                        Color.green(skin.glowColor), Color.blue(skin.glowColor)));
                stroke.setStrokeWidth((large ? 4f : 3f) + layer * (large ? 5f : 3.5f));
                canvas.drawPath(card, stroke);
            }
        }
        int baseColor = skin.cardColors[index];
        paint.setShader(new LinearGradient(x, y, x, y + height, blend(baseColor, Color.WHITE, .12f),
                baseColor, Shader.TileMode.CLAMP));
        canvas.drawPath(card, paint);
        paint.setShader(null);
        stroke.setColor(selected ? skin.brightGold : Color.rgb(147, 99, 44));
        stroke.setStrokeWidth(selected ? (large ? 6f : 5f) : (large ? 3.5f : 2.5f));
        canvas.drawPath(card, stroke);
        drawOrnaments(canvas, x, y, width, height, selected, large);

        Drawable appIcon = tile.useAppIcon ? applicationIcon(tile.packageName) : null;
        if (appIcon != null) drawDrawableIcon(canvas, appIcon, x + width / 2f,
                y + height * (large ? .30f : .31f), height * (large ? .30f : .38f));
        else drawBuiltInIcon(canvas, tile.defaultKind, x + width / 2f,
                y + height * (large ? .30f : .32f), height * (large ? .25f : .31f));

        drawTileLabel(canvas, titleFor(tile), x, y, width, height, large);
        if (large && TileConfig.ACTION_GPT.equals(tile.action)) {
            paint.setTextSize(height * .085f);
            paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
            paint.setColor(Color.rgb(232, 210, 178));
            canvas.drawText(tr("Wise Guide", "Moudrý průvodce", "Guía sabia"),
                    x + width / 2f, y + height * .82f, paint);
        }
    }

    private void drawTileLabel(Canvas canvas, String label, float x, float y, float width,
                               float height, boolean large) {
        paint.setColor(skin.textColor);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        paint.setTextSize(height * (large ? .175f : .22f));
        if (large && label.length() > 13 && label.contains(" ")) {
            int split = bestSplit(label);
            canvas.drawText(label.substring(0, split), x + width / 2f, y + height * .62f, paint);
            canvas.drawText(label.substring(split + 1), x + width / 2f, y + height * .78f, paint);
        } else {
            while (paint.measureText(label) > width * .82f && paint.getTextSize() > height * .12f)
                paint.setTextSize(paint.getTextSize() - 1f);
            canvas.drawText(label, x + width / 2f, y + height * (large ? .69f : .72f), paint);
        }
    }

    private int bestSplit(String text) {
        int middle = text.length() / 2, best = text.indexOf(' '), distance = Integer.MAX_VALUE;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == ' ' && Math.abs(i - middle) < distance) {
            best = i;
            distance = Math.abs(i - middle);
        }
        return best;
    }

    private void drawDrawableIcon(Canvas canvas, Drawable icon, float centerX, float centerY, float size) {
        icon.setBounds((int) (centerX - size / 2f), (int) (centerY - size / 2f),
                (int) (centerX + size / 2f), (int) (centerY + size / 2f));
        icon.draw(canvas);
    }

    private Path bannerPath(float x, float y, float width, float height, float cut) {
        float corner = width * cut;
        Path path = new Path();
        path.moveTo(x + corner, y);
        path.lineTo(x + width - corner, y);
        path.lineTo(x + width, y + corner * .72f);
        path.lineTo(x + width * .965f, y + height * .82f);
        path.lineTo(x + width * .5f, y + height);
        path.lineTo(x + width * .035f, y + height * .82f);
        path.lineTo(x, y + corner * .72f);
        path.close();
        return path;
    }

    private void drawOrnaments(Canvas canvas, float x, float y, float width, float height,
                               boolean selected, boolean large) {
        float inset = width * (large ? .035f : .045f), centerX = x + width / 2f, flourishY = y + height * .86f;
        stroke.setColor(selected ? Color.rgb(255, 231, 145) : Color.rgb(203, 146, 67));
        stroke.setStrokeWidth(large ? 1.8f : 1.4f);
        stroke.setAlpha(selected ? 230 : 160);
        canvas.drawLine(x + inset * 1.8f, y + inset, x + width - inset * 1.8f, y + inset, stroke);
        Path flourish = new Path();
        flourish.moveTo(centerX - width * .16f, flourishY);
        flourish.quadTo(centerX - width * .08f, flourishY - height * .08f, centerX, flourishY);
        flourish.quadTo(centerX + width * .08f, flourishY - height * .08f, centerX + width * .16f, flourishY);
        flourish.moveTo(centerX, flourishY - height * .045f);
        flourish.lineTo(centerX - width * .035f, flourishY + height * .035f);
        flourish.lineTo(centerX, flourishY + height * .075f);
        flourish.lineTo(centerX + width * .035f, flourishY + height * .035f);
        flourish.close();
        canvas.drawPath(flourish, stroke);
        drawCorner(canvas, x + inset, y + height * .70f, width * .12f, false);
        drawCorner(canvas, x + width - inset, y + height * .70f, width * .12f, true);
        stroke.setAlpha(255);
    }

    private void drawCorner(Canvas canvas, float x, float y, float size, boolean mirror) {
        float direction = mirror ? -1f : 1f;
        Path path = new Path();
        path.moveTo(x, y); path.lineTo(x + direction * size, y);
        path.moveTo(x, y); path.lineTo(x, y + size * .58f);
        path.moveTo(x + direction * size * .24f, y);
        path.quadTo(x + direction * size * .46f, y - size * .28f,
                x + direction * size * .68f, y);
        canvas.drawPath(path, stroke);
    }

    private void drawBuiltInIcon(Canvas canvas, int icon, float centerX, float centerY, float size) {
        stroke.setColor(skin.brightGold);
        stroke.setStrokeWidth(Math.max(3f, size * .07f));
        if (icon == 0) {
            paint.setColor(Color.rgb(245, 10, 24));
            RectF rect = new RectF(centerX - size * .64f, centerY - size * .35f,
                    centerX + size * .64f, centerY + size * .35f);
            canvas.drawRoundRect(rect, size * .17f, size * .17f, paint);
            Path play = new Path();
            play.moveTo(centerX - size * .15f, centerY - size * .22f);
            play.lineTo(centerX + size * .28f, centerY);
            play.lineTo(centerX - size * .15f, centerY + size * .22f);
            play.close();
            paint.setColor(Color.WHITE); canvas.drawPath(play, paint);
        } else if (icon == 1) {
            Path outer = new Path();
            outer.moveTo(centerX - size * .35f, centerY - size * .48f);
            outer.lineTo(centerX + size * .5f, centerY);
            outer.lineTo(centerX - size * .35f, centerY + size * .48f);
            outer.close();
            paint.setShader(new LinearGradient(centerX - size * .4f, centerY, centerX + size * .5f,
                    centerY, Color.CYAN, Color.rgb(0, 126, 220), Shader.TileMode.CLAMP));
            canvas.drawPath(outer, paint); paint.setShader(null);
        } else if (icon == 2) {
            for (int i = 0; i < 4; i++) {
                float dx = (i % 2 == 0 ? -1 : 1) * size * .17f;
                float dy = (i < 2 ? -1 : 1) * size * .13f;
                canvas.drawOval(new RectF(centerX + dx - size * .27f, centerY + dy - size * .23f,
                        centerX + dx + size * .27f, centerY + dy + size * .23f), stroke);
            }
        } else if (icon == 3) {
            paint.setColor(skin.brightGold);
            float cell = size * .32f, gap = size * .12f;
            for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) {
                float left = centerX + (col - 1) * (cell + gap) + gap * .5f;
                float top = centerY + (row - 1) * (cell + gap) + gap * .5f;
                canvas.drawRoundRect(new RectF(left, top, left + cell, top + cell), cell * .2f, cell * .2f, paint);
            }
        } else if (icon == 4) {
            canvas.drawCircle(centerX - size * .2f, centerY - size * .12f, size * .22f, stroke);
            Path cloud = new Path();
            cloud.moveTo(centerX - size * .45f, centerY + size * .2f);
            cloud.cubicTo(centerX - size * .42f, centerY - size * .02f, centerX - size * .12f,
                    centerY - size * .05f, centerX - size * .04f, centerY + size * .1f);
            cloud.cubicTo(centerX + size * .13f, centerY - size * .08f, centerX + size * .46f,
                    centerY + size * .02f, centerX + size * .45f, centerY + size * .24f);
            cloud.close(); canvas.drawPath(cloud, stroke);
        } else if (icon == 5) {
            RectF radio = new RectF(centerX - size * .5f, centerY - size * .28f,
                    centerX + size * .5f, centerY + size * .34f);
            canvas.drawRoundRect(radio, size * .09f, size * .09f, stroke);
            canvas.drawLine(centerX - size * .32f, centerY - size * .3f,
                    centerX + size * .25f, centerY - size * .65f, stroke);
            canvas.drawCircle(centerX + size * .24f, centerY + size * .04f, size * .15f, stroke);
        } else if (icon == 6) {
            Path folder = new Path();
            folder.moveTo(centerX - size * .52f, centerY - size * .3f);
            folder.lineTo(centerX - size * .12f, centerY - size * .3f);
            folder.lineTo(centerX + size * .02f, centerY - size * .12f);
            folder.lineTo(centerX + size * .52f, centerY - size * .12f);
            folder.lineTo(centerX + size * .45f, centerY + size * .36f);
            folder.lineTo(centerX - size * .52f, centerY + size * .36f);
            folder.close(); canvas.drawPath(folder, stroke);
        } else {
            canvas.drawCircle(centerX, centerY, size * .22f, stroke);
            canvas.drawCircle(centerX, centerY, size * .48f, stroke);
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * i / 4d;
                canvas.drawLine(centerX + (float) Math.cos(angle) * size * .48f,
                        centerY + (float) Math.sin(angle) * size * .48f,
                        centerX + (float) Math.cos(angle) * size * .65f,
                        centerY + (float) Math.sin(angle) * size * .65f, stroke);
            }
        }
    }

    private void drawHomeModeHint(Canvas canvas, String text) {
        int width = getWidth(), height = getHeight();
        RectF panel = new RectF(width * .25f, height * .885f, width * .75f, height * .945f);
        paint.setColor(Color.argb(220, 12, 14, 19));
        canvas.drawRoundRect(panel, 18, 18, paint);
        stroke.setColor(skin.gold); stroke.setStrokeWidth(2f); canvas.drawRoundRect(panel, 18, 18, stroke);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(height * .019f); paint.setColor(Color.WHITE);
        canvas.drawText(text, width / 2f, height * .923f, paint);
    }

    private void drawHiddenBadge(Canvas canvas, int index) {
        float centerX = getWidth() * (skin.x[index] + skin.width[index] / 2f);
        float y = getHeight() * (skin.y[index] + skin.height[index] * .16f);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(getHeight() * .018f); paint.setColor(Color.argb(220, 220, 220, 220));
        canvas.drawText(tr("HIDDEN", "SKRYTÝ", "OCULTO"), centerX, y, paint);
    }

    private void drawApps(Canvas canvas) {
        drawPageVeil(canvas);
        String heading = pickerMode == PICK_TILE
                ? tr("Choose an app", "Vyber aplikaci", "Elige una aplicación")
                : tr("All apps", "Všechny aplikace", "Aplicaciones");
        drawPageTitle(canvas, heading);
        int count = installed.size();
        if (count == 0) {
            drawCenteredMessage(canvas, tr("No launchable TV apps found", "Nenalezeny spustitelné TV aplikace",
                    "No se encontraron aplicaciones de TV"));
            return;
        }
        int columns = 5, pageSize = 15;
        int start = (appFocus / pageSize) * pageSize;
        float gap = getWidth() * .018f, startX = getWidth() * .06f, startY = getHeight() * .18f;
        float cardWidth = (getWidth() * .88f - gap * (columns - 1)) / columns;
        float cardHeight = getHeight() * .16f;
        for (int i = start; i < Math.min(start + pageSize, count); i++) {
            int local = i - start, row = local / columns, column = local % columns;
            drawAppTile(canvas, startX + column * (cardWidth + gap), startY + row * (cardHeight + gap),
                    cardWidth, cardHeight, installed.get(i), i == appFocus);
        }
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(getHeight() * .019f); paint.setColor(Color.LTGRAY);
        canvas.drawText((start / pageSize + 1) + " / " + ((count + pageSize - 1) / pageSize),
                getWidth() / 2f, getHeight() * .91f, paint);
    }

    private void drawAppTile(Canvas canvas, float x, float y, float width, float height,
                             AppItem app, boolean selected) {
        RectF rect = new RectF(x, y, x + width, y + height);
        paint.setColor(Color.argb(235, 20, 28, 38)); canvas.drawRoundRect(rect, 16, 16, paint);
        stroke.setColor(selected ? skin.brightGold : Color.rgb(112, 82, 47));
        stroke.setStrokeWidth(selected ? 7 : 2); canvas.drawRoundRect(rect, 16, 16, stroke);
        Drawable icon = app.icon(packageManager);
        if (icon != null) drawDrawableIcon(canvas, icon, x + width / 2f, y + height * .34f, height * .42f);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(height * .16f); paint.setColor(Color.WHITE);
        canvas.drawText(ellipsize(app.label, 22), x + width / 2f, y + height * .78f, paint);
    }

    private void drawContextMenu(Canvas canvas) {
        paint.setColor(Color.argb(150, 0, 5, 12)); canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        String[] labels = contextLabels();
        drawMenuPanel(canvas, tr("Tile menu", "Menu boxu", "Menú del cuadro"), labels, menuFocus, .30f, .19f, .70f);
    }

    private void drawEditTile(Canvas canvas) {
        drawPageVeil(canvas);
        TileConfig tile = tiles.get(editingIndex);
        String[] rows = tile.packageName == null
                ? new String[]{tr("Choose app", "Vybrat aplikaci", "Elegir aplicación"),
                tr("Rename", "Přejmenovat", "Cambiar nombre"),
                tr("Reset tile", "Obnovit box", "Restablecer cuadro")}
                : new String[]{tr("Choose app", "Vybrat aplikaci", "Elegir aplicación"),
                tr("Rename", "Přejmenovat", "Cambiar nombre"),
                (tile.useAppIcon ? "✓ " : "") + tr("Use app icon", "Použít ikonu aplikace", "Usar icono de la aplicación"),
                tr("Reset tile", "Obnovit box", "Restablecer cuadro")};
        drawMenuPanel(canvas, tr("Edit Tile", "Upravit box", "Editar cuadro") + " — " + titleFor(tile),
                rows, menuFocus, .22f, .18f, .78f);
    }

    private void drawSettings(Canvas canvas) {
        String language = preferences.getString("lang", "en");
        String languageName = "cs".equals(language) ? "Čeština" : "es".equals(language) ? "Español" : "English";
        String[] rows = {
                tr("Language", "Jazyk", "Idioma") + ": " + languageName,
                tr("Edit Home Screen", "Upravit hlavní obrazovku", "Editar pantalla principal"),
                tr("Skins", "Vzhledy", "Apariencias"),
                tr("Home & Navigation", "Domov a navigace", "Inicio y navegación"),
                tr("Open Original Google TV", "Otevřít původní Google TV", "Abrir Google TV original"),
                tr("Reset Layout", "Obnovit rozložení", "Restablecer diseño"),
                tr("About Magic TV", "O Magic TV", "Acerca de Magic TV")
        };
        drawListPage(canvas, tr("Settings", "Nastavení", "Ajustes"), rows, menuFocus);
    }

    private void drawSkins(Canvas canvas) {
        String[] ids = SkinManager.availableSkinIds();
        String[] rows = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            SkinDefinition item = SkinManager.load(ids[i]);
            rows[i] = (item.id.equals(skin.id) ? "✓ " : "") + item.displayName;
        }
        drawListPage(canvas, tr("Skins", "Vzhledy", "Apariencias"), rows, menuFocus);
    }

    private void drawHomeNavigation(Canvas canvas) {
        String[] rows = {
                tr("Choose default Home app", "Vybrat výchozí aplikaci Domů", "Elegir aplicación de inicio"),
                tr("Open Original Google TV", "Otevřít původní Google TV", "Abrir Google TV original")
        };
        drawListPage(canvas, tr("Home & Navigation", "Domov a navigace", "Inicio y navegación"), rows, menuFocus);
    }

    private void drawAbout(Canvas canvas) {
        drawPageVeil(canvas); drawPageTitle(canvas, tr("About Magic TV", "O Magic TV", "Acerca de Magic TV"));
        RectF panel = new RectF(getWidth() * .20f, getHeight() * .21f, getWidth() * .80f, getHeight() * .74f);
        drawFantasyPanel(canvas, panel);
        paint.setTextAlign(Paint.Align.CENTER); paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD)); paint.setTextSize(getHeight() * .045f);
        canvas.drawText("Magic TV 0.2", getWidth() / 2f, getHeight() * .33f, paint);
        paint.setTypeface(Typeface.DEFAULT); paint.setTextSize(getHeight() * .026f);
        canvas.drawText(tr("Active skin: ", "Aktivní vzhled: ", "Apariencia activa: ") + skin.displayName,
                getWidth() / 2f, getHeight() * .42f, paint);
        canvas.drawText(tr("Long-press OK on any tile to customize it.",
                "Dlouhým stiskem OK na boxu otevřeš úpravy.",
                "Mantén OK sobre un cuadro para personalizarlo."), getWidth() / 2f, getHeight() * .51f, paint);
        canvas.drawText(tr("MENU always opens Magic TV Settings.",
                "MENU vždy otevře nastavení Magic TV.",
                "MENU siempre abre los ajustes de Magic TV."), getWidth() / 2f, getHeight() * .58f, paint);
    }

    private void drawListPage(Canvas canvas, String heading, String[] rows, int selected) {
        drawPageVeil(canvas); drawPageTitle(canvas, heading);
        float top = getHeight() * .17f, rowHeight = getHeight() * .085f, gap = getHeight() * .018f;
        for (int i = 0; i < rows.length; i++) {
            RectF rect = new RectF(getWidth() * .19f, top + i * (rowHeight + gap),
                    getWidth() * .81f, top + i * (rowHeight + gap) + rowHeight);
            drawFantasyRow(canvas, rect, rows[i], i == selected);
        }
    }

    private void drawMenuPanel(Canvas canvas, String heading, String[] rows, int selected,
                               float left, float top, float right) {
        float rowHeight = getHeight() * .073f;
        float panelBottom = top * getHeight() + getHeight() * .11f + rows.length * rowHeight;
        RectF panel = new RectF(getWidth() * left, getHeight() * top, getWidth() * right, panelBottom);
        drawFantasyPanel(canvas, panel);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        paint.setColor(skin.textColor); paint.setTextSize(getHeight() * .033f);
        canvas.drawText(heading, getWidth() * (left + right) / 2f, getHeight() * top + getHeight() * .066f, paint);
        for (int i = 0; i < rows.length; i++) {
            RectF row = new RectF(panel.left + getWidth() * .025f,
                    panel.top + getHeight() * .09f + i * rowHeight,
                    panel.right - getWidth() * .025f,
                    panel.top + getHeight() * .09f + i * rowHeight + rowHeight * .78f);
            drawFantasyRow(canvas, row, rows[i], i == selected);
        }
    }

    private void drawFantasyPanel(Canvas canvas, RectF rect) {
        paint.setColor(Color.argb(245, 14, 15, 22)); canvas.drawRoundRect(rect, 22, 22, paint);
        stroke.setColor(skin.gold); stroke.setStrokeWidth(3f); canvas.drawRoundRect(rect, 22, 22, stroke);
        stroke.setColor(Color.argb(145, 255, 225, 145)); stroke.setStrokeWidth(1.2f);
        canvas.drawRoundRect(new RectF(rect.left + 8, rect.top + 8, rect.right - 8, rect.bottom - 8), 18, 18, stroke);
    }

    private void drawFantasyRow(Canvas canvas, RectF rect, String text, boolean selected) {
        paint.setColor(selected ? Color.argb(235, 76, 45, 18) : Color.argb(225, 24, 25, 31));
        canvas.drawRoundRect(rect, 14, 14, paint);
        stroke.setColor(selected ? skin.brightGold : Color.rgb(120, 83, 44));
        stroke.setStrokeWidth(selected ? 5f : 2f); canvas.drawRoundRect(rect, 14, 14, stroke);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        paint.setColor(Color.WHITE); paint.setTextSize(getHeight() * .027f);
        canvas.drawText(text, rect.centerX(), rect.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    private void drawPageVeil(Canvas canvas) {
        paint.setColor(Color.argb(180, 1, 10, 22)); canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    private void drawPageTitle(Canvas canvas, String title) {
        paint.setTextAlign(Paint.Align.LEFT); paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        paint.setTextSize(getHeight() * .052f); paint.setColor(Color.WHITE);
        canvas.drawText(title, getWidth() * .06f, getHeight() * .10f, paint);
    }

    private void drawCenteredMessage(Canvas canvas, String message) {
        paint.setTextAlign(Paint.Align.CENTER); paint.setColor(Color.WHITE);
        paint.setTextSize(getHeight() * .032f); paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(message, getWidth() / 2f, getHeight() * .5f, paint);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isOk(keyCode)) {
            if (page == PAGE_HOME || page == PAGE_HOME_EDIT) {
                if (!okHeld && event.getRepeatCount() == 0) {
                    okHeld = true; longPressTriggered = false;
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                }
            } else if (event.getRepeatCount() == 0) handleShortOk();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) { handleBack(); return true; }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            openSettings(); return true;
        }
        if (isDirection(keyCode)) { handleDirection(keyCode); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isOk(keyCode) && okHeld) {
            handler.removeCallbacks(longPressRunnable);
            if (!longPressTriggered && (page == PAGE_HOME || page == PAGE_HOME_EDIT)) handleShortOk();
            okHeld = false; longPressTriggered = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private boolean isDirection(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private void handleDirection(int keyCode) {
        if (page == PAGE_HOME || page == PAGE_HOME_EDIT) moveHomeFocus(keyCode, page == PAGE_HOME_EDIT);
        else if (page == PAGE_MOVE) moveTile(keyCode);
        else if (page == PAGE_APPS) moveAppFocus(keyCode);
        else {
            int count = menuCount();
            if (count <= 0) return;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) menuFocus = (menuFocus + count - 1) % count;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) menuFocus = (menuFocus + 1) % count;
            invalidate();
        }
    }

    private int menuCount() {
        if (page == PAGE_SETTINGS) return 7;
        if (page == PAGE_CONTEXT) return contextActions.size();
        if (page == PAGE_EDIT_TILE) return tiles.get(editingIndex).packageName == null ? 3 : 4;
        if (page == PAGE_SKINS) return SkinManager.availableSkinIds().length;
        if (page == PAGE_HOME_NAV) return 2;
        return 0;
    }

    private void moveHomeFocus(int keyCode, boolean includeHidden) {
        if (focus < 0) { ensureFocusVisible(); invalidate(); return; }
        float currentX = skin.x[focus] + skin.width[focus] / 2f;
        float currentY = skin.y[focus] + skin.height[focus] / 2f;
        int best = -1; float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < tiles.size(); i++) {
            if (i == focus || (!includeHidden && tiles.get(i).hidden)) continue;
            float x = skin.x[i] + skin.width[i] / 2f, y = skin.y[i] + skin.height[i] / 2f;
            float dx = x - currentX, dy = y - currentY;
            boolean direction = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? dx < -.01f
                    : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? dx > .01f
                    : keyCode == KeyEvent.KEYCODE_DPAD_UP ? dy < -.01f : dy > .01f;
            if (!direction) continue;
            float primary = (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    ? Math.abs(dx) : Math.abs(dy);
            float secondary = (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    ? Math.abs(dy) : Math.abs(dx);
            float score = primary + secondary * 2.2f;
            if (score < bestScore) { bestScore = score; best = i; }
        }
        if (best >= 0) focus = best;
        invalidate();
    }

    private void moveAppFocus(int keyCode) {
        int count = installed.size();
        if (count == 0) return;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) appFocus = Math.max(0, appFocus - 1);
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) appFocus = Math.min(count - 1, appFocus + 1);
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) appFocus = Math.max(0, appFocus - 5);
        else appFocus = Math.min(count - 1, appFocus + 5);
        invalidate();
    }

    private void moveTile(int keyCode) {
        int old = focus;
        moveHomeFocus(keyCode, true);
        int target = focus;
        if (target != old) {
            Collections.swap(tiles, old, target);
            focus = target;
            editingIndex = target;
        }
        invalidate();
    }

    private void handleShortOk() {
        switch (page) {
            case PAGE_HOME:
                activateTile(focus);
                break;
            case PAGE_HOME_EDIT:
                if (focus >= 0) openContextMenu(focus, PAGE_HOME_EDIT);
                break;
            case PAGE_MOVE:
                tileStore.save(tiles); moveSnapshot = null; page = PAGE_CONTEXT; menuFocus = 0; invalidate();
                break;
            case PAGE_APPS:
                chooseApp();
                break;
            case PAGE_SETTINGS:
                activateSettings();
                break;
            case PAGE_CONTEXT:
                activateContextAction();
                break;
            case PAGE_EDIT_TILE:
                activateEditTile();
                break;
            case PAGE_SKINS:
                chooseSkin();
                break;
            case PAGE_HOME_NAV:
                if (menuFocus == 0) openHomeChooser(); else openOriginalGoogleTv();
                break;
            default:
                break;
        }
    }

    private void handleBack() {
        switch (page) {
            case PAGE_HOME:
                return;
            case PAGE_HOME_EDIT:
                page = PAGE_SETTINGS; menuFocus = 1; break;
            case PAGE_APPS:
                page = pickerMode == PICK_TILE ? PAGE_EDIT_TILE : PAGE_HOME; break;
            case PAGE_SETTINGS:
                page = PAGE_HOME; focus = firstVisible(); break;
            case PAGE_CONTEXT:
                page = returnPage; focus = editingIndex; break;
            case PAGE_EDIT_TILE:
                page = PAGE_CONTEXT; menuFocus = 0; break;
            case PAGE_MOVE:
                if (moveSnapshot != null) tiles = moveSnapshot;
                moveSnapshot = null; editingIndex = moveStartIndex; focus = moveStartIndex; page = PAGE_CONTEXT; break;
            case PAGE_SKINS:
            case PAGE_HOME_NAV:
            case PAGE_ABOUT:
                page = PAGE_SETTINGS; break;
            default:
                page = PAGE_HOME;
        }
        invalidate();
    }

    private void activateTile(int index) {
        if (index < 0 || index >= tiles.size() || tiles.get(index).hidden) return;
        TileConfig tile = tiles.get(index);
        if (TileConfig.ACTION_PACKAGE.equals(tile.action)) {
            if (!launchPackage(tile.packageName)) openAppPicker(PICK_TILE, index);
        } else if (TileConfig.ACTION_APPS.equals(tile.action)) openAppPicker(PICK_LAUNCH, index);
        else if (TileConfig.ACTION_SETTINGS.equals(tile.action)) openSettings();
        else if (TileConfig.ACTION_FILES.equals(tile.action)) openFiles();
        else if (TileConfig.ACTION_GPT.equals(tile.action)) toast(tr("GPT connection will be added later",
                "GPT připojíme později", "GPT se conectará después"));
        else if (TileConfig.ACTION_WEATHER.equals(tile.action)) toast(tr("Weather module comes next",
                "Počasí přidáme v další verzi", "El clima llegará después"));
        else openAppPicker(PICK_TILE, index);
    }

    private void openContextMenu(int index, int sourcePage) {
        if (index < 0 || (tiles.get(index).hidden && sourcePage != PAGE_HOME_EDIT && sourcePage != PAGE_MOVE)) return;
        editingIndex = index; returnPage = sourcePage; buildContextActions(); menuFocus = 0;
        page = PAGE_CONTEXT; invalidate();
    }

    private void buildContextActions() {
        contextActions.clear();
        contextActions.add(MENU_EDIT); contextActions.add(MENU_MOVE); contextActions.add(MENU_HIDE);
        TileConfig tile = tiles.get(editingIndex);
        if (tile.packageName != null && isPackageInstalled(tile.packageName)) {
            contextActions.add(MENU_INFO);
            if (canUninstall(tile.packageName)) contextActions.add(MENU_UNINSTALL);
        }
        if (menuFocus >= contextActions.size()) menuFocus = Math.max(0, contextActions.size() - 1);
    }

    private String[] contextLabels() {
        String[] result = new String[contextActions.size()];
        for (int i = 0; i < result.length; i++) {
            int action = contextActions.get(i);
            if (action == MENU_EDIT) result[i] = tr("Edit Tile", "Upravit box", "Editar cuadro");
            else if (action == MENU_MOVE) result[i] = tr("Move", "Přesunout", "Mover");
            else if (action == MENU_HIDE) result[i] = tiles.get(editingIndex).hidden
                    ? tr("Show", "Zobrazit", "Mostrar") : tr("Hide", "Skrýt", "Ocultar");
            else if (action == MENU_INFO) result[i] = tr("App Info", "Informace o aplikaci", "Información de la aplicación");
            else result[i] = tr("Uninstall", "Odinstalovat", "Desinstalar");
        }
        return result;
    }

    private void activateContextAction() {
        if (contextActions.isEmpty()) return;
        int action = contextActions.get(menuFocus);
        if (action == MENU_EDIT) { page = PAGE_EDIT_TILE; menuFocus = 0; }
        else if (action == MENU_MOVE) {
            moveSnapshot = TileStore.copy(tiles); moveStartIndex = editingIndex; focus = editingIndex; page = PAGE_MOVE;
        } else if (action == MENU_HIDE) {
            tiles.get(editingIndex).hidden = !tiles.get(editingIndex).hidden; tileStore.save(tiles); page = returnPage;
            focus = page == PAGE_HOME_EDIT ? editingIndex : firstVisible();
        } else if (action == MENU_INFO) openAppInfo(tiles.get(editingIndex).packageName);
        else uninstallPackage(tiles.get(editingIndex).packageName);
        invalidate();
    }

    private void activateEditTile() {
        TileConfig tile = tiles.get(editingIndex);
        boolean hasPackage = tile.packageName != null;
        if (menuFocus == 0) openAppPicker(PICK_TILE, editingIndex);
        else if (menuFocus == 1) showRenameDialog();
        else if (hasPackage && menuFocus == 2) {
            tile.useAppIcon = !tile.useAppIcon; tileStore.save(tiles); invalidate();
        } else {
            int kind = tile.defaultKind; tiles.set(editingIndex, TileConfig.defaultTile(kind));
            tileStore.save(tiles); buildContextActions(); page = PAGE_CONTEXT; menuFocus = 0; invalidate();
        }
    }

    private void showRenameDialog() {
        final TileConfig tile = tiles.get(editingIndex);
        final EditText input = new EditText(getContext());
        input.setSingleLine(true); input.setText(titleFor(tile)); input.selectAll();
        new AlertDialog.Builder(getContext())
                .setTitle(tr("Rename tile", "Přejmenovat box", "Cambiar nombre"))
                .setView(input)
                .setPositiveButton(tr("Save", "Uložit", "Guardar"), (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    tile.title = value.isEmpty() ? null : value;
                    tileStore.save(tiles); invalidate();
                })
                .setNegativeButton(tr("Cancel", "Zrušit", "Cancelar"), null)
                .show();
    }

    private void openAppPicker(int mode, int tileIndex) {
        pickerMode = mode; editingIndex = tileIndex; appFocus = 0; loadInstalledApps(); page = PAGE_APPS; invalidate();
    }

    private void chooseApp() {
        if (installed.isEmpty()) return;
        AppItem app = installed.get(appFocus);
        if (pickerMode == PICK_LAUNCH) { launchAppItem(app); return; }
        TileConfig tile = tiles.get(editingIndex);
        tile.action = TileConfig.ACTION_PACKAGE; tile.packageName = app.packageName;
        tile.title = app.label; tile.useAppIcon = true; tile.hidden = false;
        tileStore.save(tiles); buildContextActions(); page = PAGE_EDIT_TILE; menuFocus = 0; invalidate();
    }

    private void activateSettings() {
        if (menuFocus == 0) cycleLanguage();
        else if (menuFocus == 1) { page = PAGE_HOME_EDIT; focus = firstVisible(); if (focus < 0) focus = 0; }
        else if (menuFocus == 2) { page = PAGE_SKINS; menuFocus = 0; }
        else if (menuFocus == 3) { page = PAGE_HOME_NAV; menuFocus = 0; }
        else if (menuFocus == 4) openOriginalGoogleTv();
        else if (menuFocus == 5) confirmResetLayout();
        else page = PAGE_ABOUT;
        invalidate();
    }

    private void cycleLanguage() {
        String language = preferences.getString("lang", "en");
        language = "en".equals(language) ? "cs" : "cs".equals(language) ? "es" : "en";
        preferences.edit().putString("lang", language).apply(); setLanguage(language); invalidate();
    }

    private void chooseSkin() {
        String[] ids = SkinManager.availableSkinIds();
        if (menuFocus < 0 || menuFocus >= ids.length) return;
        preferences.edit().putString("skin", ids[menuFocus]).apply(); loadActiveSkin(); invalidate();
    }

    private void confirmResetLayout() {
        new AlertDialog.Builder(getContext())
                .setTitle(tr("Reset layout?", "Obnovit rozložení?", "¿Restablecer diseño?"))
                .setMessage(tr("All tile changes will be removed.", "Všechny úpravy boxů budou odstraněny.",
                        "Se eliminarán todos los cambios de los cuadros."))
                .setPositiveButton(tr("Reset", "Obnovit", "Restablecer"), (dialog, which) -> {
                    tileStore.reset(); tiles = TileStore.defaults(); focus = firstVisible(); invalidate();
                })
                .setNegativeButton(tr("Cancel", "Zrušit", "Cancelar"), null).show();
    }

    private void openSettings() { page = PAGE_SETTINGS; menuFocus = 0; invalidate(); }

    private void openHomeChooser() {
        try { getContext().startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (Exception exception) {
            try { getContext().startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) { toast(tr("Home settings are unavailable", "Nastavení Domů není dostupné", "Ajustes de inicio no disponibles")); }
        }
    }

    private void openOriginalGoogleTv() {
        Intent query = new Intent(Intent.ACTION_MAIN); query.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes = packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL);
        ResolveInfo best = null;
        int bestScore = -1;
        for (ResolveInfo info : homes) {
            if (info.activityInfo == null || info.activityInfo.packageName.equals(getContext().getPackageName())) continue;
            boolean system = (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            String packageName = info.activityInfo.packageName.toLowerCase(Locale.US);
            int score = packageName.contains("launcherx") ? 100
                    : packageName.contains("google") && system ? 70
                    : system ? 40 : 10;
            if (score > bestScore) { bestScore = score; best = info; }
        }
        if (best != null) {
            Intent original = new Intent(Intent.ACTION_MAIN); original.addCategory(Intent.CATEGORY_HOME);
            original.setComponent(new ComponentName(best.activityInfo.packageName, best.activityInfo.name));
            original.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getContext().startActivity(original);
        } else toast(tr("Original Google TV launcher was not found", "Původní Google TV launcher nebyl nalezen",
                "No se encontró el launcher original de Google TV"));
    }

    private void openAppInfo(String packageName) {
        if (packageName == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName));
            getContext().startActivity(intent);
        } catch (Exception ignored) { toast(tr("App info is unavailable", "Informace o aplikaci nejsou dostupné", "Información no disponible")); }
    }

    private void uninstallPackage(String packageName) {
        if (packageName == null || !canUninstall(packageName)) return;
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
            getContext().startActivity(intent);
        } catch (Exception ignored) { toast(tr("This app cannot be uninstalled", "Tuto aplikaci nelze odinstalovat", "No se puede desinstalar")); }
    }

    private boolean canUninstall(String packageName) {
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            return (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
        } catch (Exception ignored) { return false; }
    }

    private boolean launchPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        try {
            Intent intent = packageManager.getLeanbackLaunchIntentForPackage(packageName);
            if (intent == null) intent = packageManager.getLaunchIntentForPackage(packageName);
            if (intent != null) { getContext().startActivity(intent); return true; }
        } catch (Exception ignored) {}
        return false;
    }

    private void launchAppItem(AppItem app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(app.component); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception ignored) { launchPackage(app.packageName); }
    }

    private void openFiles() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE); getContext().startActivity(intent);
        } catch (Exception exception) {
            try { getContext().startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) {}
        }
    }

    private void loadInstalledApps() {
        installed.clear(); HashSet<String> seen = new HashSet<>();
        addLaunchableApps(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), seen);
        addLaunchableApps(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), seen);
        Collections.sort(installed, (first, second) -> first.label.compareToIgnoreCase(second.label));
        if (appFocus >= installed.size()) appFocus = Math.max(0, installed.size() - 1);
    }

    private void addLaunchableApps(Intent query, HashSet<String> seen) {
        List<ResolveInfo> results = packageManager.queryIntentActivities(query, 0);
        for (ResolveInfo result : results) {
            if (result.activityInfo == null) continue;
            String packageName = result.activityInfo.packageName;
            if (packageName.equals(getContext().getPackageName()) || seen.contains(packageName)) continue;
            seen.add(packageName);
            installed.add(new AppItem(result.loadLabel(packageManager).toString(), packageName,
                    new ComponentName(packageName, result.activityInfo.name)));
        }
    }

    private void reconcileRemovedPackages() {
        boolean changed = false;
        for (TileConfig tile : tiles) {
            if (!TileConfig.ACTION_PACKAGE.equals(tile.action) || tile.packageName == null ||
                    isPackageInstalled(tile.packageName)) continue;
            boolean untouchedYouTube = tile.defaultKind == 0 && tile.title == null &&
                    "com.google.android.youtube.tv".equals(tile.packageName);
            if (untouchedYouTube) continue;
            tile.action = TileConfig.ACTION_NONE; tile.packageName = null; tile.useAppIcon = false;
            tile.title = tr("Choose app", "Vybrat aplikaci", "Elegir aplicación"); changed = true;
        }
        if (changed) tileStore.save(tiles);
    }

    private boolean isPackageInstalled(String packageName) {
        if (packageName == null) return false;
        try { packageManager.getApplicationInfo(packageName, 0); return true; }
        catch (Exception ignored) { return false; }
    }

    private Drawable applicationIcon(String packageName) {
        if (packageName == null) return null;
        try { return packageManager.getApplicationIcon(packageName); }
        catch (Exception ignored) { return null; }
    }

    private void ensureFocusVisible() {
        if (focus >= 0 && focus < tiles.size() && !tiles.get(focus).hidden) return;
        focus = firstVisible();
    }

    private int firstVisible() {
        for (int i = 0; i < tiles.size(); i++) if (!tiles.get(i).hidden) return i;
        return -1;
    }

    private String titleFor(TileConfig tile) {
        return tile.title == null || tile.title.trim().isEmpty()
                ? defaultLabels[langIndex()][tile.defaultKind] : tile.title;
    }

    private String tr(String english, String czech, String spanish) {
        int index = langIndex(); return index == 1 ? czech : index == 2 ? spanish : english;
    }

    private String ellipsize(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private int blend(int first, int second, float ratio) {
        float inverse = 1f - ratio;
        return Color.rgb((int) (Color.red(first) * inverse + Color.red(second) * ratio),
                (int) (Color.green(first) * inverse + Color.green(second) * ratio),
                (int) (Color.blue(first) * inverse + Color.blue(second) * ratio));
    }

    private void toast(String message) { Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show(); }

    private static class AppItem {
        final String label;
        final String packageName;
        final ComponentName component;
        Drawable cachedIcon;

        AppItem(String label, String packageName, ComponentName component) {
            this.label = label; this.packageName = packageName; this.component = component;
        }

        Drawable icon(PackageManager packageManager) {
            if (cachedIcon == null) {
                try { cachedIcon = packageManager.getActivityIcon(component); }
                catch (Exception ignored) {}
            }
            return cachedIcon;
        }
    }

    private static class ClockTicker {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private View view;
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (view == null) return;
                view.invalidate(); handler.postDelayed(this, 30_000L);
            }
        };
        void start(View target) { view = target; handler.postDelayed(tick, 30_000L); }
        void stop() { view = null; handler.removeCallbacks(tick); }
    }
}
