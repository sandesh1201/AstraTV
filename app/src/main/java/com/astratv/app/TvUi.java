package com.astratv.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared 10-foot typography, focus motion, and controls. */
final class TvUi {
    static final int BG=0xff080b12,WHITE=0xfff4f5f7,MUTED=0xffa4acba,ACCENT=0xffb4cdf7;
    final Context context;
    TvUi(Context context){this.context=context;}
    int dp(float n){return (int)(n*context.getResources().getDisplayMetrics().density+.5f);}
    LinearLayout column(){LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);l.setClipChildren(false);return l;}
    TextView text(String value,int size,int color){TextView v=new TextView(context);v.setText(value);v.setTextColor(color);v.setTextSize(size);v.setIncludeFontPadding(false);return v;}
    TextView bold(String value,int size){TextView v=text(value,size,WHITE);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    GradientDrawable shape(int color,int border,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border!=0)d.setStroke(dp(2),border);return d;}
    void gap(LinearLayout l,int n){l.addView(new Space(context),new LinearLayout.LayoutParams(1,dp(n)));}
    TextView action(String label,boolean primary,Runnable click){
        TextView v=bold(label,14);v.setGravity(Gravity.CENTER);v.setPadding(dp(20),dp(12),dp(20),dp(12));v.setMinHeight(dp(44));
        v.setTextColor(primary?BG:WHITE);v.setBackground(shape(primary?WHITE:0xcc242934,0,10));v.setFocusable(true);v.setClickable(true);v.setContentDescription(label);
        v.setOnClickListener(w->click.run());v.setOnFocusChangeListener((w,focus)->{
            w.animate().scaleX(focus?1.045f:1f).scaleY(focus?1.045f:1f).setDuration(140).start();
            v.setBackground(shape(focus?WHITE:primary?WHITE:0xcc242934,focus?ACCENT:0,10));v.setTextColor(focus||primary?BG:WHITE);
        });return v;
    }
    void focusCard(View v,Runnable onFocus){
        v.setFocusable(true);v.setClickable(true);v.setBackground(shape(0x00202734,0,10));
        v.setOnFocusChangeListener((w,focus)->{w.animate().scaleX(focus?1.045f:1).scaleY(focus?1.045f:1).setDuration(150).start();w.setBackground(shape(0x00202734,focus?WHITE:0,10));w.setElevation(dp(focus?8:0));if(focus&&onFocus!=null)onFocus.run();});
    }
    EditText input(String hint,String value){EditText e=new EditText(context);e.setTextColor(WHITE);e.setHintTextColor(MUTED);e.setHint(hint);e.setText(value);e.setTextSize(16);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(shape(0xff202631,0,8));e.setSingleLine(true);return e;}
}
