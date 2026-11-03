package com.astratv.app;

/** The top navigation tabs, in order. "Movies" was "All Movies"; HD Movies was removed with its Einthusan source. */
final class Navigation {
    static final String[] TABS={"Home","Movies","TV Shows","Catch-Up TV","Live TV","Sports Live"};
    private Navigation(){}
    static boolean isTab(String name){for(String t:TABS)if(t.equals(name))return true;return false;}
}
