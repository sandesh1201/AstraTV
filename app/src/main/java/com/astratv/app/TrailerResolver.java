package com.astratv.app;

import java.net.URI;

/** Provider URL, then TMDB YouTube key, then YouTube Data search. Never scrapes stream URLs. */
public final class TrailerResolver {
    private TrailerResolver(){}
    public enum Kind{PROVIDER,TMDB_YOUTUBE,YOUTUBE_SEARCH}
    public static final class Source {
        public final Kind kind;public final String value;
        public Source(Kind kind,String value){this.kind=kind;this.value=value;}
        public boolean youtube(){return kind!=Kind.PROVIDER;}
    }
    public static Source pick(String providerTrailerUrl,String tmdbYoutubeKey,String youtubeSearchKey){
        if(providerTrailerUrl!=null&&Library.https(providerTrailerUrl))return new Source(Kind.PROVIDER,providerTrailerUrl);
        if(tmdbYoutubeKey!=null&&!tmdbYoutubeKey.trim().isEmpty())return new Source(Kind.TMDB_YOUTUBE,tmdbYoutubeKey.trim());
        if(youtubeSearchKey!=null&&!youtubeSearchKey.trim().isEmpty())return new Source(Kind.YOUTUBE_SEARCH,youtubeSearchKey.trim());
        return null;
    }
    public static String providerUrl(Library.Title t){
        if(t==null)return "";
        if("samples".equals(t.provider)&&Library.https(t.url))return t.url;
        try{for(int i=0;i<t.videos.length();i++){org.json.JSONObject v=t.videos.optJSONObject(i);if(v==null)continue;String name=v.optString("name")+" "+v.optString("title");if(name.toLowerCase(java.util.Locale.ROOT).contains("trailer")&&Library.https(v.optString("url")))return v.getString("url");}}catch(Exception ignored){}
        return "";
    }
    public static boolean youtubeEmbed(String url){
        try{URI u=new URI(url);String host=u.getHost()==null?"":u.getHost().toLowerCase(java.util.Locale.ROOT);return "https".equalsIgnoreCase(u.getScheme())&&(host.equals("www.youtube-nocookie.com")||host.equals("youtube-nocookie.com"));}catch(Exception e){return false;}
    }
    /**
     * Hosts the official YouTube embedded player needs (player code, media, thumbnails, and the
     * Google ad and measurement services the YouTube API terms forbid blocking). Everything else is refused.
     */
    private static final String[] PLAYER_DOMAINS={"youtube.com","youtube-nocookie.com","ytimg.com","googlevideo.com","gstatic.com","google.com","googleapis.com",
        "doubleclick.net","googlesyndication.com","googleadservices.com","ggpht.com","googleusercontent.com"};
    public static boolean youtubePlayerResource(String url){
        try{URI u=new URI(url);String host=u.getHost()==null?"":u.getHost().toLowerCase(java.util.Locale.ROOT);
            if(!"https".equalsIgnoreCase(u.getScheme())||u.getUserInfo()!=null)return false;
            for(String d:PLAYER_DOMAINS)if(host.equals(d)||host.endsWith("."+d))return true;
            return false;
        }catch(Exception e){return false;}
    }
}
