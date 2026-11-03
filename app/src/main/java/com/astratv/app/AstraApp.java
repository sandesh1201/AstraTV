package com.astratv.app;

import android.app.Application;

/** Registers the always-on VPN callback before Android can start the tunnel service on boot. */
public class AstraApp extends Application {
    @Override public void onCreate(){super.onCreate();VpnController.get(this);}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);Artwork.trimMemory(level);if(level!=TRIM_MEMORY_UI_HIDDEN)Diagnostics.get().memoryWarning(level);}
    @Override public void onLowMemory(){super.onLowMemory();Artwork.trimMemory(TRIM_MEMORY_COMPLETE);Diagnostics.get().memoryWarning(-1);}
}
