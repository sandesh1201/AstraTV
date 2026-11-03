package com.astratv.app;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks an official trailer from YouTube search candidates instead of playing the first result.
 * A candidate must be public and embeddable, trailer-length, name the movie (and not another year),
 * say "trailer" or "teaser", avoid reaction/review/fan-made/full-movie wording, and come from an
 * official, studio, label or distributor channel. Anything doubtful is rejected: the backdrop is shown instead.
 */
final class TrailerMatcher {
    static final int MIN_SECONDS=30,MAX_SECONDS=360,MIN_SCORE=6;

    static final class Candidate{
        final String videoId,title,channel,description;final int seconds;final boolean embeddable,isPublic,live;
        Candidate(String videoId,String title,String channel,String description,int seconds,boolean embeddable,boolean isPublic,boolean live){
            this.videoId=videoId==null?"":videoId;this.title=title==null?"":title;this.channel=channel==null?"":channel;this.description=description==null?"":description;
            this.seconds=seconds;this.embeddable=embeddable;this.isPublic=isPublic;this.live=live;
        }
    }

    private static final Pattern VIDEO_ID=Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern YEAR=Pattern.compile("\\b(19[3-9]\\d|20[0-4]\\d)\\b");
    private static final String[] REJECT={
        "reaction","react","review","reviews","fan made","fanmade","fan-made","fan edit","concept","fake","parody","spoof","full movie","full film",
        "explained","breakdown","ending","recap","scene","clip","song","lyric","lyrical","jukebox","video song","making of","behind the scenes","bts",
        "interview","press meet","pre release","pre-release","event","promo edit","whatsapp","status","shorts","#shorts","ai generated","unofficial","mashup",
        "reupload","re-upload","hindi dubbed full","public talk","roast","teaser reaction","trailer reaction","countdown"
    };
    /** Studios, labels and distributors that publish official trailers. Matched against the channel name. */
    private static final String[] OFFICIAL_CHANNELS={
        "t-series","sony music","sony pictures","zee music","zee studios","aditya music","lahari","saregama","tips official","tips films","think music","sun tv","sun pictures",
        "mythri movie makers","hombale films","geetha arts","sri venkateswara creations","dharma productions","yash raj films","yrf","excel entertainment",
        "red chillies","lyca productions","t-series telugu","t-series tamil","sony music south","mango music","junglee music","speed records","goldmines telefilms",
        "marvel entertainment","warner bros","universal pictures","paramount pictures","20th century studios","walt disney studios","disney","pixar","lionsgate",
        "netflix","prime video","amazon mgm studios","apple tv","a24","focus features","columbia pictures",
        "aha","sun nxt","zee5","jiocinema","jiohotstar","hotstar","blender foundation","blender studio","rajshri","eros now"
    };

    private TrailerMatcher(){}

    /** The search phrase: "{title} {year} {language} official trailer". */
    static String query(String title,String year,String language){
        StringBuilder q=new StringBuilder(title==null?"":title.trim());
        if(year!=null&&!year.trim().isEmpty())q.append(' ').append(year.trim());
        if(language!=null&&!language.trim().isEmpty()&&!language.equalsIgnoreCase("English"))q.append(' ').append(language.trim());
        return q.append(" official trailer").toString();
    }

    /** The best acceptable candidate, or null when none passes. */
    static Candidate best(String movieTitle,String year,String language,List<Candidate> candidates){
        Candidate best=null;int top=Integer.MIN_VALUE;
        for(Candidate c:candidates){int s=score(movieTitle,year,language,c);if(s>=MIN_SCORE&&s>top){top=s;best=c;}}
        return best;
    }

    /** Integer.MIN_VALUE for a hard reject; otherwise higher is more likely the official trailer. */
    static int score(String movieTitle,String year,String language,Candidate c){
        if(!VIDEO_ID.matcher(c.videoId).matches()||!c.embeddable||!c.isPublic||c.live)return Integer.MIN_VALUE;
        if(c.seconds>0&&(c.seconds<MIN_SECONDS||c.seconds>MAX_SECONDS))return Integer.MIN_VALUE;
        String title=norm(c.title),channel=norm(c.channel),movie=norm(movieTitle);
        if(movie.isEmpty()||!containsWords(title,movie))return Integer.MIN_VALUE;
        boolean trailer=title.contains("trailer"),teaser=title.contains("teaser");
        if(!trailer&&!teaser)return Integer.MIN_VALUE;
        for(String bad:REJECT){String b=norm(bad);if(!containsPhrase(movie,b)&&containsPhrase(title,b))return Integer.MIN_VALUE;}
        if(year!=null&&!year.isEmpty()){Matcher m=YEAR.matcher(c.title);while(m.find())if(!m.group(1).equals(year.trim())&&!containsPhrase(movie,m.group(1)))return Integer.MIN_VALUE;}

        boolean known=false;for(String o:OFFICIAL_CHANNELS)if(containsPhrase(channel,norm(o))){known=true;break;}
        boolean officialName=false;for(String w:new String[]{"official","studios","studio","pictures","films","productions","entertainment","movies official"})if(containsPhrase(channel,w)){officialName=true;break;}
        boolean movieChannel=containsWords(channel,movie)&&containsPhrase(channel,"official");
        if(!known&&!officialName&&!movieChannel)return Integer.MIN_VALUE;

        int s=0;
        if(known)s+=5;else if(movieChannel)s+=5;else s+=3;
        if(title.contains("official trailer"))s+=3;else if(trailer)s+=2;else s+=1;
        if(year!=null&&!year.isEmpty()&&c.title.contains(year.trim()))s+=1;
        if(language!=null&&!language.isEmpty()&&title.contains(norm(language)))s+=1;
        if(title.startsWith(movie))s+=1;
        return s;
    }

    static String norm(String s){
        if(s==null)return "";
        String n=Normalizer.normalize(s,Normalizer.Form.NFKD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
        n=n.replace("&#39;","'").replace("&amp;","&").replace("&quot;","\"");
        return n.replaceAll("[^a-z0-9#]+"," ").trim().replaceAll(" +"," ");
    }
    private static boolean containsPhrase(String text,String phrase){return !phrase.isEmpty()&&(" "+text+" ").contains(" "+phrase+" ");}
    /** Every word of the movie name appears, in order, as whole words. */
    private static boolean containsWords(String text,String words){return containsPhrase(text,words);}
}
