package com.magictv.launcher;

import android.app.Activity;
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
    private final Bitmap wallpaper;

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
        wallpaper = BitmapFactory.decodeResource(getResources(), R.drawable.fantasy_wallpaper);
        setLanguage(prefs.getString("lang", "en"));
        loadInstalledApps();
        stroke.setStyle(Paint.Style.STROKE);
        ticker.start(this);
    }

    @Override protected void onDetachedFromWindow() {
        ticker.stop();
        super.onDetachedFromWindow();
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
        if (wallpaper != null) {
            c.drawBitmap(wallpaper, new Rect(0, 0, wallpaper.getWidth(), wallpaper.getHeight()),
                    new Rect(0, 0, w, h), p);
        } else {
            c.drawColor(COL_BG);
        }
        p.setShader(new LinearGradient(0, h * .22f, 0, h,
                Color.TRANSPARENT, Color.argb(62, 0, 8, 18), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    private void drawHome(Canvas c) {
        int w=getWidth(), h=getHeight();
        String[] l=labels[langIndex()];
        drawCrest(c,w*.105f,h*.105f,h*.072f);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));
        SimpleDateFormat tf=new SimpleDateFormat("HH:mm", locale), df=new SimpleDateFormat("EEE d MMM", locale);
        Date now=new Date();
        p.setTextAlign(Paint.Align.RIGHT); p.setColor(Color.WHITE); p.setTextSize(h*.064f); c.drawText(tf.format(now),w*.955f,h*.076f,p);
        p.setTextSize(h*.023f); p.setColor(Color.LTGRAY); c.drawText(df.format(now),w*.955f,h*.108f,p);
        stroke.setColor(Color.argb(155,226,187,106)); stroke.setStrokeWidth(Math.max(2f,h*.002f));
        c.drawLine(w*.962f,h*.025f,w*.962f,h*.112f,stroke);

        float top=h*.315f, cardH=h*.285f, gap=w*.022f;
        float cardW=w*.205f; float total=cardW*3+gap*2; float x0=(w-total)/2;
        int[] cols={COL_RED,COL_BLUE,COL_PURPLE};
        for(int i=0;i<3;i++) drawCard(c,x0+i*(cardW+gap),top,cardW,cardH,l[i],cols[i],focus==i,true,i);
        float smallTop=h*.655f, smallW=w*.132f, smallH=h*.145f, smallGap=w*.014f;
        float smallTotal=smallW*5+smallGap*4; float sx=(w-smallTotal)/2;
        for(int i=0;i<5;i++) drawCard(c,sx+i*(smallW+smallGap),smallTop,smallW,smallH,l[i+3],Color.rgb(28,26,26),focus==i+3,false,i+3);
    }

    private void drawCrest(Canvas c,float cx,float cy,float radius){
        Path shield=new Path(); shield.moveTo(cx-radius*.72f,cy-radius*.6f); shield.quadTo(cx,cy-radius,cx+radius*.72f,cy-radius*.6f);
        shield.lineTo(cx+radius*.56f,cy+radius*.44f); shield.quadTo(cx,cy+radius,cx-radius*.56f,cy+radius*.44f); shield.close();
        p.setColor(Color.argb(170,39,18,16)); c.drawPath(shield,p); stroke.setColor(COL_GOLD); stroke.setStrokeWidth(radius*.055f); c.drawPath(shield,stroke);
        p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD)); p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.rgb(255,220,116)); p.setTextSize(radius*.72f);
        c.drawText("MV",cx,cy+radius*.22f,p);
    }

    private void drawCard(Canvas c,float x,float y,float ww,float hh,String label,int color,boolean selected,boolean large,int icon){
        Path card=bannerPath(x,y,ww,hh,large?.10f:.12f);
        if(selected){
            for(int layer=3;layer>=1;layer--){ stroke.setColor(Color.argb(34+layer*20,255,190,43)); stroke.setStrokeWidth((large?4f:3f)+layer*(large?5f:3.5f)); c.drawPath(card,stroke); }
        }
        int lighter=blend(color,Color.WHITE,.12f); p.setShader(new LinearGradient(x,y,x,y+hh,lighter,color,Shader.TileMode.CLAMP)); c.drawPath(card,p); p.setShader(null);
        stroke.setColor(selected?Color.rgb(255,220,116):Color.rgb(147,99,44)); stroke.setStrokeWidth(selected?(large?6f:5f):(large?3.5f:2.5f)); c.drawPath(card,stroke);
        drawOrnaments(c,x,y,ww,hh,selected,large);
        drawIcon(c,icon,x+ww/2,y+hh*(large?.30f:.32f),hh*(large?.25f:.31f));
        p.setColor(Color.rgb(255,244,211)); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD)); p.setTextSize(hh*(large?.175f:.22f));
        if(large && icon==1){ c.drawText("Stream",x+ww/2,y+hh*.62f,p); c.drawText("Debrid",x+ww/2,y+hh*.78f,p); }
        else c.drawText(label,x+ww/2,y+hh*(large?.69f:.72f),p);
        if(large && icon==2){ p.setTextSize(hh*.085f); p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.NORMAL)); p.setColor(Color.rgb(232,210,178)); c.drawText(tr("Wise Guide","Moudrý průvodce","Guía sabia"),x+ww/2,y+hh*.82f,p); }
    }

    private Path bannerPath(float x,float y,float w,float h,float cut){
        float k=w*cut; Path path=new Path(); path.moveTo(x+k,y); path.lineTo(x+w-k,y); path.lineTo(x+w,y+k*.72f); path.lineTo(x+w*.965f,y+h*.82f);
        path.lineTo(x+w*.5f,y+h); path.lineTo(x+w*.035f,y+h*.82f); path.lineTo(x,y+k*.72f); path.close(); return path;
    }

    private void drawOrnaments(Canvas c,float x,float y,float w,float h,boolean selected,boolean large){
        float inset=w*(large?.035f:.045f), cx=x+w/2, fy=y+h*.86f;
        stroke.setColor(selected?Color.rgb(255,231,145):Color.rgb(203,146,67)); stroke.setStrokeWidth(large?1.8f:1.4f); stroke.setAlpha(selected?230:160);
        c.drawLine(x+inset*1.8f,y+inset,x+w-inset*1.8f,y+inset,stroke);
        Path f=new Path(); f.moveTo(cx-w*.16f,fy); f.quadTo(cx-w*.08f,fy-h*.08f,cx,fy); f.quadTo(cx+w*.08f,fy-h*.08f,cx+w*.16f,fy);
        f.moveTo(cx,fy-h*.045f); f.lineTo(cx-w*.035f,fy+h*.035f); f.lineTo(cx,fy+h*.075f); f.lineTo(cx+w*.035f,fy+h*.035f); f.close(); c.drawPath(f,stroke);
        drawCorner(c,x+inset,y+h*.70f,w*.12f,false); drawCorner(c,x+w-inset,y+h*.70f,w*.12f,true); stroke.setAlpha(255);
    }

    private void drawCorner(Canvas c,float x,float y,float size,boolean mirror){
        float d=mirror?-1f:1f; Path q=new Path(); q.moveTo(x,y); q.lineTo(x+d*size,y); q.moveTo(x,y); q.lineTo(x,y+size*.58f);
        q.moveTo(x+d*size*.24f,y); q.quadTo(x+d*size*.46f,y-size*.28f,x+d*size*.68f,y); c.drawPath(q,stroke);
    }

    private int blend(int a,int b,float r){ float i=1-r; return Color.rgb((int)(Color.red(a)*i+Color.red(b)*r),(int)(Color.green(a)*i+Color.green(b)*r),(int)(Color.blue(a)*i+Color.blue(b)*r)); }

    private void drawIcon(Canvas c,int icon,float cx,float cy,float size){
        stroke.setColor(Color.rgb(255,220,116)); stroke.setStrokeWidth(Math.max(3f,size*.07f));
        if(icon==0){
            p.setColor(Color.rgb(245,10,24)); RectF r=new RectF(cx-size*.64f,cy-size*.35f,cx+size*.64f,cy+size*.35f); c.drawRoundRect(r,size*.17f,size*.17f,p);
            Path play=new Path(); play.moveTo(cx-size*.15f,cy-size*.22f); play.lineTo(cx+size*.28f,cy); play.lineTo(cx-size*.15f,cy+size*.22f); play.close(); p.setColor(Color.WHITE); c.drawPath(play,p);
        } else if(icon==1){
            Path outer=new Path(); outer.moveTo(cx-size*.35f,cy-size*.48f); outer.lineTo(cx+size*.5f,cy); outer.lineTo(cx-size*.35f,cy+size*.48f); outer.close();
            p.setShader(new LinearGradient(cx-size*.4f,cy,cx+size*.5f,cy,Color.CYAN,Color.rgb(0,126,220),Shader.TileMode.CLAMP)); c.drawPath(outer,p); p.setShader(null);
            Path inner=new Path(); inner.moveTo(cx-size*.16f,cy-size*.23f); inner.lineTo(cx+size*.23f,cy); inner.lineTo(cx-size*.16f,cy+size*.23f); inner.close(); p.setColor(Color.rgb(4,35,57)); c.drawPath(inner,p);
        } else if(icon==2){
            for(int i=0;i<4;i++){ float dx=(i%2==0?-1:1)*size*.17f,dy=(i<2?-1:1)*size*.13f; c.drawOval(new RectF(cx+dx-size*.27f,cy+dy-size*.23f,cx+dx+size*.27f,cy+dy+size*.23f),stroke); }
        } else if(icon==3){
            p.setColor(Color.rgb(255,220,116)); float cell=size*.32f,gap=size*.12f;
            for(int row=0;row<2;row++) for(int col=0;col<2;col++){ float left=cx+(col-1)*(cell+gap)+gap*.5f,top=cy+(row-1)*(cell+gap)+gap*.5f; c.drawRoundRect(new RectF(left,top,left+cell,top+cell),cell*.2f,cell*.2f,p); }
        } else if(icon==4){
            c.drawCircle(cx-size*.2f,cy-size*.12f,size*.22f,stroke); for(int i=0;i<8;i++){ double a=Math.PI*i/4d; c.drawLine(cx-size*.2f+(float)Math.cos(a)*size*.3f,cy-size*.12f+(float)Math.sin(a)*size*.3f,cx-size*.2f+(float)Math.cos(a)*size*.42f,cy-size*.12f+(float)Math.sin(a)*size*.42f,stroke); }
            Path cloud=new Path(); cloud.moveTo(cx-size*.45f,cy+size*.2f); cloud.cubicTo(cx-size*.42f,cy-size*.02f,cx-size*.12f,cy-size*.05f,cx-size*.04f,cy+size*.1f); cloud.cubicTo(cx+size*.13f,cy-size*.08f,cx+size*.46f,cy+size*.02f,cx+size*.45f,cy+size*.24f); cloud.close(); c.drawPath(cloud,stroke);
        } else if(icon==5){
            RectF radio=new RectF(cx-size*.5f,cy-size*.28f,cx+size*.5f,cy+size*.34f); c.drawRoundRect(radio,size*.09f,size*.09f,stroke); c.drawLine(cx-size*.32f,cy-size*.3f,cx+size*.25f,cy-size*.65f,stroke); c.drawCircle(cx+size*.24f,cy+size*.04f,size*.15f,stroke); c.drawLine(cx-size*.34f,cy-size*.05f,cx-size*.06f,cy-size*.05f,stroke); c.drawLine(cx-size*.34f,cy+size*.12f,cx-size*.1f,cy+size*.12f,stroke);
        } else if(icon==6){
            Path folder=new Path(); folder.moveTo(cx-size*.52f,cy-size*.3f); folder.lineTo(cx-size*.12f,cy-size*.3f); folder.lineTo(cx+size*.02f,cy-size*.12f); folder.lineTo(cx+size*.52f,cy-size*.12f); folder.lineTo(cx+size*.45f,cy+size*.36f); folder.lineTo(cx-size*.52f,cy+size*.36f); folder.close(); c.drawPath(folder,stroke);
        } else if(icon==7){
            c.drawCircle(cx,cy,size*.22f,stroke); c.drawCircle(cx,cy,size*.48f,stroke); for(int i=0;i<8;i++){ double a=Math.PI*i/4d; c.drawLine(cx+(float)Math.cos(a)*size*.48f,cy+(float)Math.sin(a)*size*.48f,cx+(float)Math.cos(a)*size*.65f,cy+(float)Math.sin(a)*size*.65f,stroke); }
        }
    }

    private void drawApps(Canvas c){
        int w=getWidth(), h=getHeight();
        p.setColor(Color.argb(175,1,10,22)); c.drawRect(0,0,w,h,p);
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
        int w=getWidth(),h=getHeight(); p.setColor(Color.argb(175,1,10,22)); c.drawRect(0,0,w,h,p); title(c,tr("Settings","Nastavení","Ajustes"));
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
        if(k==KeyEvent.KEYCODE_DPAD_LEFT){ focus=focus<3?(focus+2)%3:3+(focus-3+4)%5; invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_RIGHT){ focus=focus<3?(focus+1)%3:3+(focus-3+1)%5; invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_DOWN){ if(focus<3) focus=new int[]{3,5,7}[focus]; invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_UP){ if(focus>=3) focus=new int[]{0,0,1,2,2}[focus-3]; invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER){ activateHome(focus); return true; }
        if(k==KeyEvent.KEYCODE_BACK){ Context context=getContext(); if(context instanceof Activity) ((Activity)context).finish(); return true; }
        return false;
    }

    private boolean appsKeys(int k){
        int n=Math.min(installed.size(),15); if(n==0 && k==KeyEvent.KEYCODE_BACK){page=0;invalidate();return true;}
        if(k==KeyEvent.KEYCODE_DPAD_LEFT){ appFocus=Math.max(0,appFocus-1); invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_RIGHT){ appFocus=Math.min(Math.max(0,n-1),appFocus+1); invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_UP){ appFocus=Math.max(0,appFocus-5); invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_DOWN){ appFocus=Math.min(Math.max(0,n-1),appFocus+5); invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER){ if(n>0){ AppItem a=installed.get(appFocus); if(bindTarget!=null){ prefs.edit().putString(bindTarget,a.pkg).apply(); Toast.makeText(getContext(),tr("Assigned","Přiřazeno","Asignado")+": "+a.label,Toast.LENGTH_SHORT).show(); bindTarget=null; page=2; focus=0; } else launchPackage(a.pkg); invalidate(); } return true; }
        if(k==KeyEvent.KEYCODE_BACK){ boolean binding=bindTarget!=null; bindTarget=null; page=binding?2:0; focus=binding?0:3; invalidate(); return true; }
        return false;
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
        return false;
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
        private View view;
        private final Runnable tick=new Runnable(){ public void run(){ if(view==null)return; view.invalidate(); h.postDelayed(this,30000); }};
        void start(final View v){ view=v; h.postDelayed(tick,30000); }
        void stop(){ view=null; h.removeCallbacks(tick); }
    }
}
