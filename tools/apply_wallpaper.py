from pathlib import Path

path = Path("app/src/main/java/com/magictv/launcher/MagicHomeView.java")
s = path.read_text(encoding="utf-8")

field = "    private final HandlerCompat ticker = new HandlerCompat();\n"
if "private final Bitmap wallpaper;" not in s:
    s = s.replace(field, field + "    private final Bitmap wallpaper;\n")

old_ctor = "        stroke.setStyle(Paint.Style.STROKE);\n        ticker.start(this);"
new_ctor = """        stroke.setStyle(Paint.Style.STROKE);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        wallpaper = BitmapFactory.decodeResource(getResources(), R.drawable.fantasy_wallpaper, options);
        ticker.start(this);"""
if old_ctor in s:
    s = s.replace(old_ctor, new_ctor)

start = s.index("    private void drawBackground(Canvas c) {")
end = s.index("    private void drawHome(Canvas c) {", start)
new_bg = """    private void drawBackground(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (wallpaper != null) {
            Rect src = new Rect(0, 0, wallpaper.getWidth(), wallpaper.getHeight());
            Rect dst = new Rect(0, 0, w, h);
            p.setAlpha(255);
            c.drawBitmap(wallpaper, src, dst, p);
        } else {
            c.drawColor(Color.rgb(5, 14, 25));
        }
        LinearGradient shade = new LinearGradient(0, 0, 0, h,
                new int[]{Color.argb(95,0,0,0), Color.argb(8,0,0,0), Color.argb(80,0,0,0)},
                new float[]{0f, .50f, 1f}, Shader.TileMode.CLAMP);
        p.setShader(shade);
        p.setAlpha(255);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

"""
s = s[:start] + new_bg + s[end:]
path.write_text(s, encoding="utf-8")
