package com.astratv.app.coreui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Shared cinematic tokens. Provider contracts and library JSON stay unrelated. */
public final class TvTheme {
    private TvTheme(){}
    public static final int BASE=0xff08090c, CHARCOAL=0xff12141a, SURFACE=0xe6212329, TEXT=0xfff5f5f7, MUTED=0xffa6a7af, ACCENT=0xffc3d8ff;
    public static final int GLASS=0x29ffffff, GLASS_FALLBACK=0xd91c1e24, FOCUS=0xccf5f5f7, DIM=0xff16181f;
    public static final float CARD_SCALE=1.10f, ACTION_SCALE=1.045f;
    public static final int RADIUS_CARD=18, RADIUS_PANEL=24, RADIUS_PILL=28;
    private static Typeface font;
    public static Typeface font(Context context,int weight){
        if(font==null)font=Typeface.createFromAsset(context.getAssets(),"fonts/Inter.ttf");
        return android.os.Build.VERSION.SDK_INT>=28?Typeface.create(font,weight,false):Typeface.create(font,weight>=600?Typeface.BOLD:Typeface.NORMAL);
    }
    public static boolean reduced(Context context){
        return context.getSharedPreferences("astra_ui_v1",0).getBoolean("reduce_motion",false)
            ||Settings.Global.getFloat(context.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f;
    }
    public static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    public static GradientDrawable panel(Context c,int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));d.setStroke(dp(c,1),0x24ffffff);return d;
    }
    public static GradientDrawable fill(int color,float radiusPx){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radiusPx);return d;}
    public static int blend(int a,int b,float t){
        t=Math.max(0f,Math.min(1f,t));
        return Color.argb(mix(Color.alpha(a),Color.alpha(b),t),mix(Color.red(a),Color.red(b),t),mix(Color.green(a),Color.green(b),t),mix(Color.blue(a),Color.blue(b),t));
    }
    private static int mix(int a,int b,float t){return Math.round(a+(b-a)*t);}
    public static int darken(int color,float amount){return blend(color,BASE,amount);}
    public static void round(View view,float radiusPx){
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View v,android.graphics.Outline outline){outline.setRoundRect(0,0,v.getWidth(),v.getHeight(),radiusPx);}
        });
    }
}
