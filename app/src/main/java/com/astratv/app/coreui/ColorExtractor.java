package com.astratv.app.coreui;

import android.graphics.Bitmap;
import android.util.LruCache;
import androidx.palette.graphics.Palette;

/** Cached dominant colors for page-gradient tinting. Never blocks the UI thread for decode. */
public final class ColorExtractor {
    private ColorExtractor(){}
    private static final LruCache<String,Integer> COLORS=new LruCache<>(128);
    public static int cached(String url){if(url==null||url.isEmpty())return 0;Integer value=COLORS.get(url);return value==null?0:value;}
    public static int extract(String url,Bitmap bitmap){
        if(url==null||url.isEmpty()||bitmap==null||bitmap.isRecycled())return TvTheme.BASE;
        Integer hit=COLORS.get(url);if(hit!=null)return hit;
        Palette palette=Palette.from(bitmap).resizeBitmapArea(24*24).generate();
        int color=palette.getDarkVibrantColor(palette.getDarkMutedColor(palette.getMutedColor(TvTheme.CHARCOAL)));
        COLORS.put(url,color);return color;
    }
}
