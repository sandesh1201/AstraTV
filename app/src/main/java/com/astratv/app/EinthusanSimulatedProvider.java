package com.astratv.app;

import org.json.*;
import java.net.URLEncoder;
import java.util.*;

/**
 * Client for the hypothetical licensed partner API described in docs/EINTHUSAN.md, wired to an
 * in-process {@link SimulatedPartnerServer}. Serves Creative Commons sample films only.
 */
public final class EinthusanSimulatedProvider implements LicensedProvider {
    public static final String ID="einthusan_sim";
    public static final String NAME="Einthusan (simulation)";
    public static final String FIXTURE="simulation/einthusan_partner_v1.json";
    /** Public value baked into the fixture so the auth path is exercised. It grants nothing outside the simulation. */
    public static final String SIM_TOKEN="simulation-public-token";

    public interface Transport{Response get(String pathAndQuery,String token);}
    public static final class Response{public final int status;public final String body;public Response(int status,String body){this.status=status;this.body=body==null?"":body;}}

    private final Transport transport;private final String token;
    public EinthusanSimulatedProvider(Transport transport,String token){this.transport=transport;this.token=token==null?"":token;}

    @Override public String id(){return ID;}
    @Override public String displayName(){return NAME;}
    @Override public Authorization authorization(){return new Authorization(true,true,"Local simulation with Creative Commons sample films. No Einthusan content or endpoint is used.");}

    @Override public Page catalog(Query query)throws ProviderException{
        StringBuilder path=new StringBuilder("/v1/catalog?page=").append(query.page).append("&pageSize=").append(query.pageSize);
        if(!query.language.isEmpty())path.append("&language=").append(encode(query.language));
        if(!query.search.isEmpty())path.append("&q=").append(encode(query.search));
        JSONObject body=call(path.toString());
        JSONArray items=body.optJSONArray("items");if(items==null)throw malformed("catalog items missing");
        List<Library.Title> out=new ArrayList<>();
        for(int i=0;i<items.length();i++){JSONObject o=items.optJSONObject(i);if(o==null)throw malformed("catalog item is not an object");out.add(title(o));}
        return new Page(out,body.optInt("page",query.page),Math.max(1,body.optInt("totalPages",1)));
    }
    @Override public Library.Title details(String titleId)throws ProviderException{
        JSONObject o=call("/v1/titles/"+encode(titleId));Library.Title t=title(o);
        JSONArray cast=o.optJSONArray("cast");if(cast!=null){List<String> names=new ArrayList<>();for(int i=0;i<cast.length();i++)names.add(cast.optString(i));t.cast=String.join(" · ",names);}
        return t;
    }
    @Override public List<Playback> playback(String titleId)throws ProviderException{
        JSONArray streams=call("/v1/titles/"+encode(titleId)+"/playback").optJSONArray("streams");
        if(streams==null)throw malformed("streams missing");
        List<Playback> out=new ArrayList<>();boolean drmOnly=false;
        for(int i=0;i<streams.length();i++){
            JSONObject s=streams.optJSONObject(i);if(s==null)continue;
            String url=s.optString("url");if(!Library.https(url))continue;
            Playback p=new Playback(url,s.optString("protocol"),Math.max(0,s.optInt("maxHeight",0)),s.optString("drm",""),strings(s.optJSONArray("audioLanguages")));
            if(!p.playableWithoutLicenseServer()){drmOnly=true;continue;}
            out.add(p);
        }
        if(out.isEmpty()&&drmOnly)throw new ProviderException(ProviderException.Code.UNSUPPORTED_DRM,"This title is only offered with DRM. Saandio TV has no licensed DRM configuration for this partner, so it cannot play it.");
        if(out.isEmpty())throw new ProviderException(ProviderException.Code.UNAVAILABLE,"No secure (HTTPS) stream is available for this title.");
        out.sort((a,b)->Integer.compare(b.maxHeight,a.maxHeight));
        return out;
    }

    /** Maps the partner model onto the app's title model, attaching playable sources. */
    public Library.Title withPlayback(Library.Title t)throws ProviderException{
        List<Playback> streams=playback(t.id);JSONArray videos=new JSONArray();
        try{for(Playback p:streams)videos.put(new JSONObject().put("name",p.quality.isEmpty()?"Source · "+p.protocol.toUpperCase(Locale.ROOT):p.quality+" · "+p.protocol.toUpperCase(Locale.ROOT)).put("url",p.url));}catch(JSONException e){throw malformed("stream mapping failed");}
        t.videos=videos;t.url=streams.get(0).url;return t;
    }

    private Library.Title title(JSONObject o)throws ProviderException{
        String id=o.optString("id"),name=o.optString("title");
        if(id.isEmpty()||name.isEmpty())throw malformed("title without id or name");
        List<String> genres=new ArrayList<>();for(String g:strings(o.optJSONArray("genres")))genres.add(GenreTaxonomy.canonical(g));
        List<String> langs=strings(o.optJSONArray("languages"));
        String year=o.has("year")?String.valueOf(o.optInt("year")):"";
        Library.Title t=new Library.Title(id,name,o.optString("synopsis"),"","",year,String.join(" · ",genres));
        t.provider=ID;t.providerName=NAME;t.type="movie";
        t.language=langs.isEmpty()?"":LanguageTaxonomy.canonical(langs.get(0));
        t.released=o.optString("releaseDate");
        String poster=o.optString("poster");if(Library.https(poster)){t.poster=poster;t.background=poster;}
        return t;
    }
    private JSONObject call(String path)throws ProviderException{
        Response r=transport.get(path,token);
        if(r.status==200){try{return new JSONObject(r.body);}catch(JSONException e){throw malformed("response is not JSON");}}
        String code="";try{code=new JSONObject(r.body).getJSONObject("error").optString("code");}catch(JSONException ignored){/* status code is enough */}
        if(r.status==401)throw new ProviderException(ProviderException.Code.UNAUTHENTICATED,"The partner rejected the app credentials.");
        if(r.status==403)throw new ProviderException(ProviderException.Code.NOT_AUTHORIZED,"The partner has not authorized this app.");
        if(r.status==404)throw new ProviderException(ProviderException.Code.NOT_FOUND,"This title is no longer listed by the partner.");
        if(r.status==429)throw new ProviderException(ProviderException.Code.RATE_LIMITED,"The partner asked the app to slow down. Try again shortly.");
        if(r.status==451||code.equals("region_unavailable"))throw new ProviderException(ProviderException.Code.REGION_UNAVAILABLE,"This title is not licensed for your region.");
        throw new ProviderException(ProviderException.Code.UNAVAILABLE,"The partner service is unavailable (HTTP "+r.status+").");
    }
    private static ProviderException malformed(String what){return new ProviderException(ProviderException.Code.MALFORMED,"The partner returned an unsupported response: "+what+".");}
    private static List<String> strings(JSONArray a){List<String> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){String s=a.optString(i).trim();if(!s.isEmpty())out.add(s);}return out;}
    private static String encode(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);}}
}
