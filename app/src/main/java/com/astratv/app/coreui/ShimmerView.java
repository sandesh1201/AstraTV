package com.astratv.app.coreui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** One draw pass; no timer or animator survives detachment. */
public final class ShimmerView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public ShimmerView(Context c){
        super(c);
        setContentDescription("Loading titles");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        int gap=TvTheme.dp(getContext(),16),w=Math.max(1,(getWidth()-gap*3)/4);
        float phase=(SystemClock.uptimeMillis()%1600)/1600f;
        boolean reduce=TvTheme.reduced(getContext());
        paint.setShader(reduce?null:new LinearGradient(-getWidth()+getWidth()*3*phase,0,getWidth()*3*phase,0,new int[]{0xff191b22,0xff3a3f4c,0xff191b22},null,Shader.TileMode.CLAMP));
        paint.setColor(0xff191b22);
        float radius=TvTheme.dp(getContext(),18);
        for(int i=0;i<4;i++)canvas.drawRoundRect(i*(w+gap),0,i*(w+gap)+w,getHeight(),radius,radius,paint);
        if(isAttachedToWindow()&&isShown()&&!reduce)postInvalidateOnAnimation();
    }
}
