package com.astratv.app;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** A data parser, not a script/plugin loader. Header and DRM directives are never executed. */
public final class PlaylistParser {
    private PlaylistParser(){}
    public static final class Entry {
        public String id="",name="",logo="",group="",url="",language="";
        public boolean protectedContent=false;
        public final Map<String,String> headers=new LinkedHashMap<>();
    }
    public static List<Entry> entries(String text){
        List<Entry> out=new ArrayList<>();Entry pending=null;
        for(String line:text.replace("\uFEFF","").split("\\r?\\n")){
            line=line.trim();if(line.startsWith("#EXTINF:")){
                pending=new Entry();int comma=outsideComma(line);if(comma<0){pending=null;continue;}
                pending.name=line.substring(comma+1).trim();pending.id=attribute(line,"tvg-id");pending.logo=attribute(line,"tvg-logo");pending.group=attribute(line,"group-title");pending.language=attribute(line,"tvg-language");
                header(pending,"User-Agent",attribute(line,"http-user-agent"));header(pending,"Referer",attribute(line,"http-referrer"));continue;
            }
            if(pending==null)continue;
            if(line.toLowerCase(Locale.ROOT).contains("license_key")||line.toLowerCase(Locale.ROOT).contains("license_type")){pending.protectedContent=true;continue;}
            if(line.startsWith("#EXTVLCOPT:")){String option=line.substring(11);int eq=option.indexOf('=');if(eq>0){String key=option.substring(0,eq).toLowerCase(Locale.ROOT),value=option.substring(eq+1);if(key.equals("http-referrer"))header(pending,"Referer",value);if(key.equals("http-user-agent"))header(pending,"User-Agent",value);}continue;}
            if(line.startsWith("#")||line.isEmpty())continue;
            if(Library.https(line)&&!pending.protectedContent&&!pending.name.isEmpty()&&pending.name.length()<400){pending.url=line;out.add(pending);if(out.size()>=12000)break;}pending=null;
        }return out;
    }
    private static int outsideComma(String line){boolean quoted=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"')quoted=!quoted;if(c==','&&!quoted)return i;}return -1;}
    static String attribute(String line,String key){Matcher m=Pattern.compile("(?:^|\\s)"+Pattern.quote(key)+"=\"([^\"]*)\"").matcher(line);return m.find()?m.group(1):"";}
    private static void header(Entry e,String key,String value){if(!value.isEmpty()&&value.length()<1024&&!value.contains("\r")&&!value.contains("\n")&&(key.equals("User-Agent")||Library.https(value)))e.headers.put(key,value);}
    public static String stable(String value){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(int i=0;i<12;i++)out.append(String.format(Locale.ROOT,"%02x",digest[i]));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    public static List<Library.Title> movies(String text)throws JSONException{
        List<Library.Title> out=new ArrayList<>();Map<String,Library.Title> seen=new LinkedHashMap<>();Map<String,Set<String>> variants=new HashMap<>();
        for(Entry e:entries(text)){
            String[] groups=e.group.split("\\s*/\\s*");String language=groups.length>1?groups[1]:e.language;
            String year=groups.length>2&&groups[2].matches("(?:19|20)\\d{2}")?groups[2]:"";
            String key=e.name.toLowerCase(Locale.ROOT)+"|"+language+"|"+year;
            Library.Title t=seen.get(key);if(t==null){t=new Library.Title(stable(key),e.name,"Movie playlist entry. Availability is controlled by the source host.",e.url,"",year,"Movies");t.provider="movierulz";t.providerName="MovieRulz";t.poster=e.logo;t.background=e.logo;t.language=language;t.group=groups.length>0?groups[0]:"Movies";t.headers=new JSONObject(e.headers);seen.put(key,t);out.add(t);}
            Set<String> urls=variants.computeIfAbsent(key,k->new LinkedHashSet<>());if(urls.size()<12&&urls.add(e.url))t.videos.put(new JSONObject().put("url",e.url).put("name","Source "+(t.videos.length()+1)).put("headers",new JSONObject(e.headers)));
        }return out;
    }
    public static List<Library.Title> channels(List<Entry> entries,String language,Set<String> allowedIndianIds)throws JSONException{
        Map<String,Library.Title> channels=new LinkedHashMap<>();
        for(Entry e:entries){String raw=e.id.split("@")[0];if(!allowedIndianIds.contains(raw))continue;String key=raw.isEmpty()?stable(e.name):raw;
            Library.Title t=channels.get(key);if(t==null){t=new Library.Title(key+"~"+language,e.name.replaceAll("\\s*\\([^)]*\\)",""),"Publicly listed Indian live TV. Channel availability, regional access and source terms may change.",e.url,"","LIVE",e.group.isEmpty()?"General":e.group);t.provider="news";t.providerName="TV channels";t.type="tv";t.language=language;t.group=e.group.isEmpty()?"General":e.group;t.poster=e.logo;t.background=e.logo;t.headers=new JSONObject(e.headers);channels.put(key,t);}
            t.videos.put(new JSONObject().put("url",e.url).put("name",e.name).put("headers",new JSONObject(e.headers)));
        }return new ArrayList<>(channels.values());
    }
    static Map<String,String> safeHeaders(JSONObject object){
        Map<String,String> safe=new LinkedHashMap<>();if(object==null)return safe;Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next(),value=object.optString(key);String normalized=key.equalsIgnoreCase("referer")?"Referer":key.equalsIgnoreCase("origin")?"Origin":key.equalsIgnoreCase("user-agent")?"User-Agent":"";
            if(!normalized.isEmpty()&&value.length()<1024&&!value.contains("\r")&&!value.contains("\n")&&(normalized.equals("User-Agent")||Library.https(value)))safe.put(normalized,value);
        }return safe;
    }
}
