package com.astratv.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.*;
import android.widget.*;
import com.astratv.app.coreui.*;

/** Shared 10-foot typography, focus motion, and controls. */
final class TvUi {
    static final int BG=TvTheme.BASE,WHITE=TvTheme.TEXT,MUTED=TvTheme.MUTED,ACCENT=TvTheme.ACCENT;
    final Context context;
    TvUi(Context context){this.context=context;}
    int dp(float n){return TvTheme.dp(context,n);}
    LinearLayout column(){LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);l.setClipChildren(false);l.setClipToPadding(false);return l;}
    TextView text(String value,int size,int color){
        TextView v=new TextView(context);v.setText(value);v.setTextColor(color);v.setTextSize(size);
        v.setTypeface(TvTheme.font(context,size>=30?300:400));v.setIncludeFontPadding(false);return v;
    }
    TextView bold(String value,int size){TextView v=text(value,size,WHITE);v.setTypeface(TvTheme.font(context,size>=30?400:600));return v;}
    GradientDrawable shape(int color,int border,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border!=0)d.setStroke(dp(2),border);return d;
    }
    void gap(LinearLayout l,int n){l.addView(new Space(context),new LinearLayout.LayoutParams(1,dp(n)));}
    TextView action(String label,boolean primary,Runnable click){
        TextView v=bold(label,14);v.setGravity(Gravity.CENTER);v.setPadding(dp(20),dp(12),dp(20),dp(12));v.setMinHeight(dp(44));
        v.setTextColor(primary?BG:WHITE);v.setBackground(shape(primary?WHITE:0xcc24252b,0,24));v.setFocusable(true);v.setClickable(true);v.setContentDescription(label);
        v.setOnClickListener(w->click.run());v.setOnFocusChangeListener((w,focus)->{
            FocusMotion.apply(w,focus,false);
            v.setBackground(shape(focus?WHITE:primary?WHITE:0xcc24252b,focus?ACCENT:0,24));v.setTextColor(focus||primary?BG:WHITE);
        });return v;
    }
    TextView navItem(String label,boolean selected,Runnable click){
        TextView v=bold(label,13);v.setGravity(Gravity.CENTER);v.setPadding(dp(16),dp(8),dp(16),dp(8));v.setMinHeight(dp(36));
        v.setFocusable(true);v.setClickable(true);v.setContentDescription(label);
        paintNav(v,selected,false);
        v.setOnClickListener(w->click.run());
        v.setOnFocusChangeListener((w,focus)->{FocusMotion.apply(w,focus,false);paintNav(v,selected,focus);});
        return v;
    }
    private void paintNav(TextView v,boolean selected,boolean focus){
        if(focus||selected){v.setBackground(shape(WHITE,0,22));v.setTextColor(BG);}
        else{v.setBackground(shape(0x00000000,0,22));v.setTextColor(WHITE);}
    }
    TextView chip(String label){
        TextView v=text(label,11,WHITE);v.setPadding(dp(12),dp(6),dp(12),dp(6));v.setBackground(shape(0x55ffffff,0,16));return v;
    }
    TextView badge(String label,int color){
        TextView v=bold(label,11);v.setPadding(dp(10),dp(5),dp(10),dp(5));v.setBackground(shape(color,0,8));return v;
    }
    void focusCard(View v,Runnable onFocus){
        v.setFocusable(true);v.setClickable(true);v.setBackground(cardChrome(false));
        v.setOnFocusChangeListener((w,focus)->{FocusMotion.apply(w,focus,true);w.setBackground(cardChrome(focus));if(focus&&onFocus!=null)onFocus.run();});
    }
    private Drawable cardChrome(boolean focused){
        GradientDrawable body=shape(0x00202734,focused?TvTheme.FOCUS:0,TvTheme.RADIUS_CARD);
        if(!focused)return body;
        GradientDrawable sheen=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0x55ffffff,0x00ffffff,0x22c3d8ff});
        sheen.setCornerRadius(dp(TvTheme.RADIUS_CARD));sheen.setStroke(dp(1),0x66ffffff);
        return new LayerDrawable(new Drawable[]{body,sheen});
    }
    EditText input(String hint,String value){
        EditText e=new EditText(context);e.setTextColor(WHITE);e.setHintTextColor(MUTED);e.setHint(hint);e.setText(value);e.setTextSize(16);
        e.setTypeface(TvTheme.font(context,400));e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(shape(0xff202631,0,12));e.setSingleLine(true);return e;
    }
}
