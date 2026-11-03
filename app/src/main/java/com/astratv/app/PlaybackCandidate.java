package com.astratv.app;

import org.json.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * One authorized way to play a title: a media URL (never a trailer, poster, detail page or
 * metadata API URL) plus everything Media3 needs to open it. Built from a provider stream and
 * checked before the player is started.
 */
final class PlaybackCandidate {
    enum Type{HLS,DASH,PROGRESSIVE,UNKNOWN}
    static final String WIDEVINE="widevine";
    /** Refresh signed links this long before they expire so a slow start does not run into the deadline. */
    static final long EXPIRY_GRACE_MS=30_000L;

    final String providerId,name,url,mimeType,drmScheme,drmLicenseUrl;
    final Type type;final Map<String,String> headers;final JSONArray subtitles;final List<String> audio;final Set<String> regions;
    final int declaredHeight;final boolean available;final long expiresAt;

    PlaybackCandidate(String providerId,String name,String url,String mimeType,Map<String,String> headers,JSONArray subtitles,List<String> audio,int declaredHeight,boolean available,long expiresAt,String drmScheme,String drmLicenseUrl,Set<String> regions){
        this.providerId=providerId==null?"":providerId;this.name=name==null?"":name;this.url=url==null?"":url;
        this.type=typeOf(this.url,mimeType);this.mimeType=mimeFor(this.type,mimeType);
        this.headers=headers==null?Collections.emptyMap():headers;this.subtitles=subtitles==null?new JSONArray():subtitles;this.audio=audio==null?Collections.emptyList():audio;
        this.declaredHeight=Math.max(0,declaredHeight);this.available=available;this.expiresAt=expiresAt>0?expiresAt:expiryFromUrl(this.url);
        this.drmScheme=drmScheme==null?"":drmScheme.toLowerCase(Locale.ROOT);this.drmLicenseUrl=drmLicenseUrl==null?"":drmLicenseUrl;
        this.regions=regions==null?Collections.emptySet():regions;
    }
    static PlaybackCandidate of(String providerId,ProviderRepository.Stream s){
        return new PlaybackCandidate(providerId,s.name,s.url,s.mimeType,s.headers,s.subtitles,s.audio,s.height,s.available,s.expiresAt,s.drmScheme,s.drmLicenseUrl,s.regions);
    }

    /**
     * Why this candidate must not be handed to the player, or null when it may play.
     * {@code country} is the device's ISO country (only checked when the provider declares regions).
     */
    String problem(long now,String country,boolean widevineSupported){
        if(url.isEmpty())return "The provider did not return a playback link.";
        if(!Library.https(url))return "This source uses an insecure (non-HTTPS) link, which Saandio blocks.";
        if(!available)return "The provider lists this title but says it is not available to stream right now.";
        if(looksLikeNonMedia(url))return "This link is a web page or image, not a video stream.";
        if(expired(now))return "The playback link has expired. Ask the provider for a fresh link.";
        if(!regions.isEmpty()&&country!=null&&!country.isEmpty()&&!regions.contains(country.toUpperCase(Locale.ROOT)))return "The provider offers this stream only in "+String.join(", ",new TreeSet<>(regions))+".";
        if(!drmScheme.isEmpty()){
            if(!WIDEVINE.equals(drmScheme))return "This stream uses "+drmScheme+" DRM, which Saandio does not support.";
            if(!widevineSupported)return "This TV does not support Widevine DRM, which the provider requires.";
            if(!Library.https(drmLicenseUrl))return "The provider did not supply a secure DRM license server.";
        }
        return null;
    }
    boolean expired(long now){return expiresAt>0&&now>=expiresAt-EXPIRY_GRACE_MS;}
    boolean signed(){return expiresAt>0;}
    String label(){String q=PlaybackPolicy.heightLabel(declaredHeight);return CatalogViews.join(type==Type.UNKNOWN?"":type.name(),q,drmScheme.isEmpty()?"":"DRM");}

    static Type typeOf(String url,String mime){
        String m=mime==null?"":mime.toLowerCase(Locale.ROOT);
        if(m.contains("mpegurl"))return Type.HLS;
        if(m.contains("dash"))return Type.DASH;
        if(m.startsWith("video/")||m.startsWith("audio/"))return Type.PROGRESSIVE;
        String path=path(url);
        if(path.endsWith(".m3u8"))return Type.HLS;
        if(path.endsWith(".mpd"))return Type.DASH;
        if(path.endsWith(".mp4")||path.endsWith(".m4v")||path.endsWith(".mkv")||path.endsWith(".webm")||path.endsWith(".mov")||path.endsWith(".ts"))return Type.PROGRESSIVE;
        return Type.UNKNOWN;
    }
    /** MIME hint for Media3; empty lets Media3 sniff progressive media. */
    static String mimeFor(Type type,String declared){
        switch(type){
            case HLS:return "application/x-mpegURL";
            case DASH:return "application/dash+xml";
            default:return declared!=null&&(declared.startsWith("video/")||declared.startsWith("audio/"))?declared:"";
        }
    }
    static boolean looksLikeNonMedia(String url){
        String p=path(url);
        for(String ext:new String[]{".html",".htm",".php",".aspx",".jpg",".jpeg",".png",".webp",".gif",".json"})if(p.endsWith(ext))return true;
        String u=url.toLowerCase(Locale.ROOT);
        return u.contains("youtube.com/watch")||u.contains("youtu.be/")||u.contains("api.themoviedb.org")||u.contains("image.tmdb.org");
    }
    private static String path(String url){
        if(url==null)return "";String u=url.toLowerCase(Locale.ROOT);int q=u.indexOf('?');if(q>=0)u=u.substring(0,q);int h=u.indexOf('#');if(h>=0)u=u.substring(0,h);return u;
    }

    /**
     * Expiry declared inside common signed-URL formats: {@code Expires}/{@code expires}/{@code exp}
     * (epoch seconds, CloudFront and most CDNs), Akamai {@code hdnts=exp=…} and AWS
     * {@code X-Amz-Date}+{@code X-Amz-Expires}. 0 when the link carries no expiry.
     */
    static long expiryFromUrl(String url){
        if(url==null)return 0;int q=url.indexOf('?');if(q<0)return 0;
        Map<String,String> params=new HashMap<>();
        for(String pair:url.substring(q+1).split("&")){
            int e=pair.indexOf('=');if(e<=0)continue;
            try{params.put(pair.substring(0,e).toLowerCase(Locale.ROOT),URLDecoder.decode(pair.substring(e+1),StandardCharsets.UTF_8.name()));}catch(Exception ignored){/* skip malformed pair */}
        }
        for(String key:new String[]{"expires","exp"}){long v=seconds(params.get(key));if(v>0)return v;}
        String hdnts=params.get("hdnts");
        if(hdnts!=null)for(String part:hdnts.split("~"))if(part.startsWith("exp=")){long v=seconds(part.substring(4));if(v>0)return v;}
        String amzDate=params.get("x-amz-date"),amzExpires=params.get("x-amz-expires");
        if(amzDate!=null&&amzExpires!=null)try{
            java.time.Instant start=java.time.LocalDateTime.parse(amzDate,java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")).toInstant(java.time.ZoneOffset.UTC);
            return start.toEpochMilli()+Long.parseLong(amzExpires)*1000L;
        }catch(Exception ignored){/* not an AWS signature */}
        return 0;
    }
    /** Epoch seconds in a plausible range (2001–2286) to milliseconds; anything else is not an expiry. */
    private static long seconds(String value){
        if(value==null||value.isEmpty()||value.length()>11)return 0;
        try{long s=Long.parseLong(value.trim());return s>1_000_000_000L&&s<9_999_999_999L?s*1000L:0;}catch(NumberFormatException e){return 0;}
    }
}
