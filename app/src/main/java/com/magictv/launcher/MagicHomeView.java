package com.magictv.launcher;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.*;

public class MagicHomeView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences prefs;
    private final PackageManager pm;
    private final ArrayList<AppItem> installed = new ArrayList<>();
    private final HandlerCompat ticker = new HandlerCompat();

    private int focus = 0;
    private int page = 0; // 0 home, 1 apps, 2 settings
    private int appFocus = 0;
    private String bindTarget = null;
    private Locale locale = Locale.ENGLISH;

    private static final int COL_GOLD = Color.rgb(228,181,91);
    private static final int COL_BG = Color.rgb(4,14,26);
    private static final int COL_RED = Color.rgb(105,12,18);
    private static final int COL_BLUE = Color.rgb(5,49,77);
    private static final int COL_PURPLE = Color.rgb(52,25,73);

    private final String[][] labels = {
            {"YouTube", "Stream Debrid", "GPT", "Apps", "Weather", "Radio", "Files", "Settings"},
            {"YouTube", "Stream Debrid", "GPT", "Aplikace", "Počasí", "Rádio", "Soubory", "Nastavení"},
            {"YouTube", "Stream Debrid", "GPT", "Aplicaciones", "Clima", "Radio", "Archivos", "Ajustes"}
    };

    public MagicHomeView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        prefs = c.getSharedPreferences("magic_tv", Context.MODE_PRIVATE);
        pm = c.getPackageManager();
        setLanguage(prefs.getString("lang", "en"));
        loadInstalledApps();
        stroke.setStyle(Paint.Style.STROKE);
        ticker.start(this);
    }

    private int langIndex() {
        String l = prefs.getString("lang", "en");
        if ("cs".equals(l)) return 1;
        if ("es".equals(l)) return 2;
        return 0;
    }

    private void setLanguage(String l) {
        if ("cs".equals(l)) locale = new Locale("cs", "CZ");
        else if ("es".equals(l)) locale = new Locale("es", "ES");
        else locale = Locale.ENGLISH;
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        drawBackground(c);
        if (page == 0) drawHome(c);
        else if (page == 1) drawApps(c);
        else drawSettings(c);
    }

    private void drawBackground(Canvas c) {
        int w = getWidth(), h = getHeight();
        LinearGradient g = new LinearGradient(0,0,w,h, Color.rgb(2,12,24), Color.rgb(10,31,52), Shader.TileMode.CLAMP);
        p.setShader(g); c.drawRect(0,0,w,h,p); p.setShader(null);
        p.setColor(Color.rgb(219,230,238)); c.drawCircle(w*0.62f,h*0.10f,h*0.075f,p);
        p.setColor(Color.argb(85,255,255,255)); c.drawCircle(w*0.62f,h*0.10f,h*0.10f,p);
        p.setColor(Color.rgb(12,21,32));
        float baseY=h*0.34f; c.drawRect(w*0.29f,h*0.14f,w*0.62f,baseY,p);
        for(int i=0;i<7;i++){ float x=w*(0.30f+i*0.05f); c.drawRect(x,h*0.09f,x+w*0.025f,baseY,p); Path roof=new Path(); roof.moveTo(x-w*0.006f,h*0.09f); roof.lineTo(x+w*0.0125f,h*0.035f); roof.lineTo(x+w*0.031f,h*0.09f); roof.close(); c.drawPath(roof,p); }
        p.setColor(Color.rgb(233,163,67)); for(int i=0;i<14;i++){ float x=w*(0.31f+(i%7)*0.045f); float y=h*(0.17f+(i/7)*0.07f); c.drawRect(x,y,x+w*0.006f,y+h*0.015f,p); }
        p.setColor(Color.rgb(40,21,29)); Path d=new Path(); d.moveTo(w*.76f,h*.22f); d.cubicTo(w*.82f,h*.12f,w*.89f,h*.12f,w*.94f,h*.21f); d.lineTo(w*.88f,h*.19f); d.lineTo(w*.95f,h*.08f); d.lineTo(w*.98f,h*.30f); d.cubicTo(w*.90f,h*.26f,w*.88f,h*.36f,w*.80f,h*.38f); d.close(); c.drawPath(d,p);
        p.setColor(Color.rgb(21,16,17)); c.drawRect(0,h*.82f,w,h,p);
    }

    private void drawHome(Canvas c) {
        int w=getWidth(), h=getHeight();
        String[] l=labels[langIndex()];
        p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        p.setTextAlign(Paint.Align.LEFT); p.setColor(Color.WHITE); p.setTextSize(h*.06f);
        c.drawText("Magic TV", w*.045f,h*.09f,p);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));
        SimpleDateFormat tf=new SimpleDateFormat("HH:mm", locale), df=new SimpleDateFormat("EEE d MMM", locale);
        Date now=new Date();
        p.setTextAlign(Paint.Align.RIGHT); p.setTextSize(h*.052f); c.drawText(tf.format(now),w*.955f,h*.075f,p);
        p.setTextSize(h*.023f); p.setColor(Color.LTGRAY); c.drawText(df.format(now),w*.955f,h*.108f,p);

        float top=h*.35f, cardH=h*.25f, gap=w*.018f;
        float cardW=w*.205f; float total=cardW*4+gap*3; float x0=(w-total)/2;
        int[] cols={COL_RED,COL_BLUE,COL_PURPLE,Color.rgb(48,42,35)};
        for(int i=0;i<4;i++) drawCard(c,x0+i*(cardW+gap),top,cardW,cardH,l[i],cols[i],focus==i,true);
        float smallTop=h*.665f, smallW=w*.135f, smallH=h*.14f; float smallTotal=smallW*4+gap*3; float sx=(w-smallTotal)/2;
        for(int i=0;i<4;i++) drawCard(c,sx+i*(smallW+gap),smallTop,smallW,smallH,l[i+4],Color.rgb(32,32,34),focus==i+4,false);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(h*.019f); p.setColor(Color.rgb(194,201,209));
        c.drawText("D-pad  •  OK  •  Back",w*.5f,h*.95f,p);
    }

    private void drawCard(Canvas c,float x,float y,float ww,float hh,String label,int color,boolean selected,boolean large){
        RectF r=new RectF(x,y,x+ww,y+hh);
        p.setColor(color); p.setStyle(Paint.Style.FILL); c.drawRoundRect(r,18,18,p);
        stroke.setColor(selected?Color.rgb(255,207,92):Color.rgb(111,83,51)); stroke.setStrokeWidth(selected?8:3); c.drawRoundRect(r,18,18,stroke);
        if(selected){ p.setColor(Color.argb(65,255,204,69)); c.drawRoundRect(new RectF(x-8,y-8,x+ww+8,y+hh+8),24,24,p); }
        p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD)); p.setTextSize(hh*.22f);
        c.drawText(label,x+ww/2,y+hh*.62f,p);
        if(large && label.equals("GPT")){ p.setTextSize(hh*.10f); p.setColor(Color.rgb(236,215,176)); c.drawText("Wise guide",x+ww/2,y+hh*.78f,p); }
    }

    private void drawApps(Canvas c){
        int w=getWidth(), h=getHeight();
        title(c, bindTarget==null?tr("All apps","Všechny aplikace","Aplicaciones"):tr("Choose app for ","Vyber aplikaci pro ","Elige app para ")+bindTarget);
        int cols=5; float gap=w*.018f, x0=w*.06f, y0=h*.18f, cw=(w*.88f-gap*(cols-1))/cols, ch=h*.16f;
        int max=Math.min(installed.size(),15);
        for(int i=0;i<max;i++){
            int row=i/cols,col=i%cols; float x=x0+col*(cw+gap), y=y0+row*(ch+gap);
            drawAppTile(c,x,y,cw,ch,installed.get(i),i==appFocus);
        }
        if(installed.isEmpty()){ p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setTextSize(h*.035f); c.drawText(tr("No TV apps found","Nenalezeny TV aplikace","No se encontraron apps TV"),w/2f,h*.5f,p); }
    }

    private void drawAppTile(Canvas c,float x,float y,float ww,float hh,AppItem a,boolean sel){
        RectF r=new RectF(x,y,x+ww,y+hh); p.setColor(Color.rgb(20,28,38)); c.drawRoundRect(r,16,16,p);
        stroke.setColor(sel?COL_GOLD:Color.rgb(90,90,90)); stroke.setStrokeWidth(sel?7:2); c.drawRoundRect(r,16,16,stroke);
        if(a.icon!=null){ int s=(int)(hh*.42f); a.icon.setBounds((int)(x+ww/2-s/2),(int)(y+hh*.12f),(int)(x+ww/2+s/2),(int)(y+hh*.12f+s)); a.icon.draw(c); }
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD)); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(hh*.16f); p.setColor(Color.WHITE);
        c.drawText(ellipsize(a.label,22),x+ww/2,y+hh*.78f,p);
    }

    private void drawSettings(Canvas c){
        int w=getWidth(),h=getHeight(); title(c,tr("Settings","Nastavení","Ajustes"));
        String lang=prefs.getString("lang","en");
        String[] rows={
                tr("Language","Jazyk","Idioma")+": "+("cs".equals(lang)?"Čeština":"es".equals(lang)?"Español":"English"),
                "YouTube → "+friendlyBinding("youtube"),
                "Stream Debrid → "+friendlyBinding("stream"),
                tr("Radio","Rádio","Radio")+" → "+friendlyBinding("radio")
        };
        for(int i=0;i<rows.length;i++){ float y=h*(.22f+i*.13f); RectF r=new RectF(w*.18f,y,w*.82f,y+h*.09f); p.setColor(Color.rgb(20,28,38)); c.drawRoundRect(r,16,16,p); stroke.setColor(focus==i?COL_GOLD:Color.DKGRAY); stroke.setStrokeWidth(focus==i?7:2); c.drawRoundRect(r,16,16,stroke); p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setTextSize(h*.03f); p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD)); c.drawText(rows[i],w/2f,y+h*.058f,p); }
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(h*.02f); p.setColor(Color.LTGRAY); c.drawText(tr("OK to change • Back to return","OK změnit • Zpět návrat","OK cambiar • Atrás volver"),w/2f,h*.88f,p);
    }

    private void title(Canvas c,String t){ p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD)); p.setTextSize(getHeight()*.055f); p.setColor(Color.WHITE); c.drawText(t,getWidth()*.06f,getHeight()*.10f,p); }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e){
        if(page==0) return homeKeys(keyCode);
        if(page==1) return appsKeys(keyCode);
        return settingsKeys(keyCode);
    }

    private boolean homeKeys(int k){
        if(k==KeyEvent.KEYCODE_DPAD_LEFT){ focus=(focus+7)%8; invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_RIGHT){ focus=(focus+1)%8; invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_DOWN){ if(focus<4) focus+=4; invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_UP){ if(focus>=4) focus-=4; invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER){ activateHome(focus); return true; }
        return true;
    }

    private boolean appsKeys(int k){
        int n=Math.min(installed.size(),15); if(n==0 && k==KeyEvent.KEYCODE_BACK){page=0;invalidate();return true;}
        if(k==KeyEvent.KEYCODE_DPAD_LEFT){ appFocus=Math.max(0,appFocus-1); invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_RIGHT){ appFocus=Math.min(Math.max(0,n-1),appFocus+1); invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_UP){ appFocus=Math.max(0,appFocus-5); invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_DOWN){ appFocus=Math.min(Math.max(0,n-1),appFocus+5); invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER){ if(n>0){ AppItem a=installed.get(appFocus); if(bindTarget!=null){ prefs.edit().putString(bindTarget,a.pkg).apply(); Toast.makeText(getContext(),tr("Assigned","Přiřazeno","Asignado")+": "+a.label,Toast.LENGTH_SHORT).show(); bindTarget=null; page=2; focus=0; } else launchPackage(a.pkg); invalidate(); } return true; }
        if(k==KeyEvent.KEYCODE_BACK){ bindTarget=null; page=0; focus=3; invalidate(); return true; }
        return true;
    }

    private boolean settingsKeys(int k){
        if(k==KeyEvent.KEYCODE_DPAD_UP){ focus=Math.max(0,focus-1); invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_DOWN){ focus=Math.min(3,focus+1); invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER){
            if(focus==0){ String l=prefs.getString("lang","en"); l="en".equals(l)?"cs":"cs".equals(l)?"es":"en"; prefs.edit().putString("lang",l).apply(); setLanguage(l); invalidate(); }
            else { bindTarget=focus==1?"youtube":focus==2?"stream":"radio"; page=1; appFocus=0; invalidate(); }
            return true;
        }
        if(k==KeyEvent.KEYCODE_BACK){ page=0; focus=7; invalidate(); return true; }
        return true;
    }

    private void activateHome(int i){
        switch(i){
            case 0: String y=prefs.getString("youtube","com.google.android.youtube.tv"); if(!launchPackage(y)){bindTarget="youtube";page=1;appFocus=0;invalidate();} break;
            case 1: if(!launchPackage(prefs.getString("stream",""))){bindTarget="stream";page=1;appFocus=0;invalidate();} break;
            case 2: Toast.makeText(getContext(),tr("GPT connection will be added later","GPT připojíme později","GPT se conectará después"),Toast.LENGTH_SHORT).show(); break;
            case 3: page=1;appFocus=0;bindTarget=null;invalidate();break;
            case 4: Toast.makeText(getContext(),tr("Weather module comes next","Počasí přidáme v další verzi","El clima llegará en la próxima versión"),Toast.LENGTH_SHORT).show();break;
            case 5: if(!launchPackage(prefs.getString("radio",""))){bindTarget="radio";page=1;appFocus=0;invalidate();} break;
            case 6: openFiles();break;
            case 7: page=2;focus=0;invalidate();break;
        }
    }

    private boolean launchPackage(String pkg){
        if(pkg==null || pkg.isEmpty()) return false;
        try { Intent in=pm.getLeanbackLaunchIntentForPackage(pkg); if(in==null) in=pm.getLaunchIntentForPackage(pkg); if(in!=null){getContext().startActivity(in);return true;} } catch(Exception ignored){}
        return false;
    }

    private void openFiles(){
        try{ Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT); in.setType("*/*"); in.addCategory(Intent.CATEGORY_OPENABLE); getContext().startActivity(in); }
        catch(Exception ex){ try{ getContext().startActivity(new Intent(Settings.ACTION_SETTINGS)); }catch(Exception ignored){} }
    }

    private void loadInstalledApps(){
        installed.clear(); Intent q=new Intent(Intent.ACTION_MAIN); q.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> ris=pm.queryIntentActivities(q,0);
        for(ResolveInfo r:ris){ String pkg=r.activityInfo.packageName; if(pkg.equals(getContext().getPackageName())) continue; installed.add(new AppItem(r.loadLabel(pm).toString(),pkg,r.loadIcon(pm))); }
        Collections.sort(installed,(a,b)->a.label.compareToIgnoreCase(b.label));
    }

    private String friendlyBinding(String key){ String pkg=prefs.getString(key,""); if(pkg.isEmpty()) return tr("Not set","Nenastaveno","Sin asignar"); for(AppItem a:installed) if(a.pkg.equals(pkg)) return a.label; if(key.equals("youtube")) return "YouTube"; return pkg; }
    private String tr(String en,String cs,String es){ int i=langIndex(); return i==1?cs:i==2?es:en; }
    private String ellipsize(String s,int n){ return s.length()<=n?s:s.substring(0,n-1)+"…"; }

    private static class AppItem { final String label,pkg; final Drawable icon; AppItem(String l,String p,Drawable i){label=l;pkg=p;icon=i;} }

    private static class HandlerCompat {
        private final android.os.Handler h=new android.os.Handler(android.os.Looper.getMainLooper());
        void start(final View v){ h.postDelayed(new Runnable(){ public void run(){ v.invalidate(); h.postDelayed(this,30000); }},30000); }
    }
}
