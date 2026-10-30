package com.astratv.app;

import org.json.*;
import java.util.*;

/** Episodes retain their own URL/ID. A series is an index, never a disguised video URL. */
final class SeriesIndex {
    static String normalized(String name){return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
    static String nameOf(String name){return name.replaceAll("(?i)\\s*[-–]?\\s*season\\s*\\d+","").trim();}
    static List<Library.Title> group(List<Library.Title> posts)throws JSONException{
        Map<String,Library.Title> shows=new LinkedHashMap<>();
        for(Library.Title post:posts){String name=nameOf(post.name);String key=normalized(name);Library.Title show=shows.get(key);
            if(show==null){show=Library.Title.from(post.json());show.name=name;show.id="show-"+PlaylistParser.stable(key);show.type="series";show.url="";show.genre="Series";show.videos=new JSONArray();show.seasons=new JSONArray();show.language=post.language.isEmpty()?(post.provider.equals("telugu")?"Telugu":"Tamil"):post.language;show.seriesUrl=post.seriesUrl;shows.put(key,show);}
            post.type="episode";post.language=show.language;post.name=post.episode>0?"Episode "+post.episode+(post.released.isEmpty()?"":" · "+post.released):post.released.isEmpty()?"Latest episode":post.released;
            show.videos.put(post.json());
            boolean seen=false;for(int i=0;i<show.seasons.length();i++)if(show.seasons.getJSONObject(i).optInt("s")==post.season)seen=true;if(!seen)show.seasons.put(new JSONObject().put("s",post.season));
        }
        for(Library.Title show:shows.values())show.description=show.videos.length()+" available episode"+(show.videos.length()==1?"":"s")+". Browse episodes to choose a date.";
        return new ArrayList<>(shows.values());
    }
}
