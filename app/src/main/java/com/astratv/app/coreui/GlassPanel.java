package com.astratv.app.coreui;

import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Blurs a sampled artwork layer only. Text and controls stay sharp. */
public final class GlassPanel extends FrameLayout {
    private final ImageView art;
    private final android.view.View overlay;
    public GlassPanel(Context c){this(c,TvTheme.RADIUS_PILL);}
    public GlassPanel(Context c,int radiusDp){
        super(c);
        setBackground(TvTheme.panel(c,TvTheme.SURFACE,radiusDp));
        TvTheme.round(this,TvTheme.dp(c,radiusDp));
        art=new ImageView(c);art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setAlpha(.34f);
        addView(art,new LayoutParams(-1,-1));
        overlay=new android.view.View(c);
        boolean blur=Build.VERSION.SDK_INT>=31&&!TvTheme.reduced(c);
        overlay.setBackgroundColor(blur?TvTheme.GLASS:TvTheme.GLASS_FALLBACK);
        if(blur)art.setRenderEffect(RenderEffect.createBlurEffect(28,28,Shader.TileMode.CLAMP));
        addView(overlay,new LayoutParams(-1,-1));
    }
    public void artwork(android.graphics.drawable.Drawable drawable){art.setImageDrawable(drawable);}
}
