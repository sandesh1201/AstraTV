package com.astratv.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import com.astratv.app.coreui.TvTheme;

/**
 * Card artwork with four explicit states. LOADING shows a quiet skeleton, LOADED the image,
 * MISSING (no usable artwork URL) and FAILED (artwork broke after its retries) a branded Saandio
 * card with the title, so a card is never a blank rectangle.
 */
final class Poster implements Artwork.StateListener {
    enum State{LOADING,LOADED,MISSING,FAILED}
    final FrameLayout root;final ImageView image;
    private final View skeleton;private final LinearLayout placeholder;private final TextView name,year;
    private final TvUi ui;private final boolean wide;private final int decodeW,decodeH;
    private ObjectAnimator pulse;private State state;

    Poster(TvUi ui,boolean wide,int radius){
        this.ui=ui;this.wide=wide;decodeW=wide?Artwork.POSTER_W*2:Artwork.POSTER_W;decodeH=wide?Artwork.POSTER_W*9/8:Artwork.POSTER_H;
        root=new FrameLayout(ui.context);root.setClipToOutline(true);root.setBackground(ui.shape(0xff161d2a,0,radius));
        placeholder=ui.column();placeholder.setGravity(Gravity.CENTER);placeholder.setPadding(ui.dp(10),ui.dp(10),ui.dp(10),ui.dp(10));
        TextView mark=ui.text("S A A N D I O",wide?8:9,0x99c3d8ff);mark.setGravity(Gravity.CENTER);placeholder.addView(mark);ui.gap(placeholder,wide?6:10);
        name=ui.bold("",wide?15:16);name.setGravity(Gravity.CENTER);name.setMaxLines(wide?2:5);name.setEllipsize(TextUtils.TruncateAt.END);placeholder.addView(name);
        year=ui.text("",11,TvUi.MUTED);year.setGravity(Gravity.CENTER);ui.gap(placeholder,6);placeholder.addView(year);
        root.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        skeleton=new View(ui.context);skeleton.setBackground(ui.shape(0xff222c3d,0,radius));root.addView(skeleton,new FrameLayout.LayoutParams(-1,-1));
        /* Stays VISIBLE and transparent until a bitmap arrives: Artwork defers off-screen loads until the view is shown. */
        image=new ImageView(ui.context);image.setScaleType(wide?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);root.addView(image,new FrameLayout.LayoutParams(-1,-1));
    }
    State state(){return state;}

    void bind(String title,int releaseYear,String url){
        name.setText(title);year.setText(releaseYear>0?String.valueOf(releaseYear):"");
        int seed=title.hashCode();
        placeholder.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff1d2a44|(seed&0x000f0f0f),0xff111723}));
        set(State.LOADING);
        Artwork.loadCard(image,url,decodeW,decodeH,this);
    }
    void recycle(){Artwork.cancel(image);stopPulse();}

    @Override public void onArtwork(int result){set(result==Artwork.LOADED?State.LOADED:result==Artwork.FAILED?State.FAILED:State.MISSING);}

    private void set(State next){
        if(next==state)return;state=next;
        boolean loading=next==State.LOADING,fallback=next==State.MISSING||next==State.FAILED;
        skeleton.setVisibility(loading?View.VISIBLE:View.GONE);
        placeholder.setVisibility(fallback?View.VISIBLE:View.INVISIBLE);
        if(loading)startPulse();else stopPulse();
    }
    private void startPulse(){
        if(TvTheme.reduced(ui.context)){skeleton.setAlpha(.7f);return;}
        if(pulse==null){pulse=ObjectAnimator.ofFloat(skeleton,View.ALPHA,.45f,.9f);pulse.setDuration(900);pulse.setRepeatCount(ValueAnimator.INFINITE);pulse.setRepeatMode(ValueAnimator.REVERSE);}
        if(!pulse.isStarted())pulse.start();
    }
    private void stopPulse(){if(pulse!=null&&pulse.isStarted())pulse.cancel();skeleton.setAlpha(1f);}
}
