package com.astratv.app.coreui;

import android.view.*;
import androidx.dynamicanimation.animation.*;

/** Reversible springs retain velocity during rapid D-pad focus changes. */
public final class FocusMotion {
    private FocusMotion(){}
    public static void apply(View view,boolean focused,boolean card){
        boolean reduce=TvTheme.reduced(view.getContext());
        float scale=focused?(card?TvTheme.CARD_SCALE:TvTheme.ACTION_SCALE):1f;
        if(reduce){view.setScaleX(1);view.setScaleY(1);view.setRotationY(0);view.setTranslationZ(0);return;}
        spring(view,DynamicAnimation.SCALE_X,scale);
        spring(view,DynamicAnimation.SCALE_Y,scale);
        spring(view,DynamicAnimation.ROTATION_Y,focused&&card?-1.4f:0f);
        view.animate().translationZ(TvTheme.dp(view.getContext(),focused?(card?22:10):0)).setDuration(160).start();
        dimNeighbors(view,focused&&card);
    }
    private static void dimNeighbors(View view,boolean dim){
        ViewGroup parent=parentRow(view);if(parent==null)return;
        for(int i=0;i<parent.getChildCount();i++){
            View sibling=parent.getChildAt(i);
            boolean active=sibling==view||(sibling instanceof ViewGroup&&contains((ViewGroup)sibling,view));
            sibling.animate().alpha(dim&&!active?.72f:1f).setDuration(160).start();
        }
    }
    private static ViewGroup parentRow(View view){
        if(!(view.getParent() instanceof ViewGroup))return null;
        ViewGroup parent=(ViewGroup)view.getParent();
        if(parent.getChildCount()==1&&parent.getParent() instanceof ViewGroup)parent=(ViewGroup)parent.getParent();
        return parent;
    }
    private static boolean contains(ViewGroup group,View target){
        for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i)==target)return true;return false;
    }
    private static void spring(View v,DynamicAnimation.ViewProperty property,float end){
        int key=property==DynamicAnimation.SCALE_X?com.astratv.app.R.id.focus_scale_x:property==DynamicAnimation.SCALE_Y?com.astratv.app.R.id.focus_scale_y:com.astratv.app.R.id.focus_rotation;
        SpringAnimation animation=(SpringAnimation)v.getTag(key);
        if(animation==null){
            animation=new SpringAnimation(v,property);
            animation.setSpring(new SpringForce().setDampingRatio(.78f).setStiffness(260f));
            v.setTag(key,animation);
        }
        animation.animateToFinalPosition(end);
    }
}
