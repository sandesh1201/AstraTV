package com.astratv.app;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

/**
 * Static, offline risk scoring for provider responses. It inspects text that has already been
 * captured (fixtures or cached catalogs) and never performs network requests itself.
 */
public final class ProviderRiskScanner {
    private ProviderRiskScanner(){}
    public enum Severity{CRITICAL(40),HIGH(20),MEDIUM(8),LOW(3),INFO(0);public final int weight;Severity(int w){weight=w;}}
    public static final class Finding{
        public final String rule,detail;public final Severity severity;
        Finding(String rule,Severity severity,String detail){this.rule=rule;this.severity=severity;this.detail=detail;}
        @Override public String toString(){return severity+" "+rule+": "+detail;}
    }
    public static final class Sample{
        public String url="",contentType="",body="";public List<String> redirects=new ArrayList<>();
        /** Quality the provider claims, e.g. "HD" or "1080p". Empty if none. */
        public String declaredQuality="";public boolean expectsMedia;
    }
    public static final class Report{
        public final List<Finding> findings;public final int score;
        Report(List<Finding> findings){this.findings=Collections.unmodifiableList(findings);int s=0;for(Finding f:findings)s+=f.severity.weight;score=Math.min(100,s);}
        public String level(){return score>=60?"High risk":score>=25?"Elevated risk":score>0?"Low risk":"No findings";}
        public boolean has(String rule){for(Finding f:findings)if(f.rule.equals(rule))return true;return false;}
    }

    private static final Pattern IPV4=Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern EXECUTABLE=Pattern.compile("\\.(apk|xapk|exe|msi|bat|cmd|scr|dmg|pkg|jar|vbs|ps1)(\\?|#|$)",Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_SRC=Pattern.compile("<script[^>]+src=[\"']([^\"']+)[\"']",Pattern.CASE_INSENSITIVE);
    private static final Pattern IFRAME=Pattern.compile("<iframe[^>]*>",Pattern.CASE_INSENSITIVE);
    private static final Pattern META_REFRESH=Pattern.compile("<meta[^>]+http-equiv=[\"']?refresh[\"']?[^>]+url=([^\"'>\\s]+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF=Pattern.compile("href=[\"']([^\"']+)[\"']",Pattern.CASE_INSENSITIVE);
    private static final Pattern RESOLUTION=Pattern.compile("RESOLUTION=\\d+x(\\d+)");
    private static final String[] SHORTENERS={"bit.ly","tinyurl.com","cutt.ly","shorte.st","adf.ly","ouo.io","t.co","is.gd","rb.gy"};
    private static final String[] AD_NETWORKS={"popads","popcash","propellerads","adsterra","exoclick","juicyads","hilltopads","onclickads","adcash","clickadu"};
    private static final String[] OBFUSCATION={"eval(","atob(","unescape(","string.fromcharcode","document.write(unescape","\\x65\\x76\\x61\\x6c"};
    private static final String[] MINERS={"coinhive","cryptonight","coin-hive","webminer","miner.start("};
    private static final String[] POPUNDER={"popunder","window.open(","onclick=\"window.location","target=\"_blank\" rel=\"nofollow sponsored\""};

    public static Report scan(Sample s){
        List<Finding> out=new ArrayList<>(url(s.url));
        redirects(s,out);content(s,out);quality(s,out);
        return new Report(out);
    }
    /** Scans every URL in an M3U/M3U8 playlist. */
    public static Report playlist(String text){
        List<Finding> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(String line:text.split("\\r?\\n")){
            String l=line.trim();if(l.isEmpty()||l.startsWith("#"))continue;
            for(Finding f:url(l))if(seen.add(f.rule+"|"+f.detail))out.add(f);
        }
        return new Report(out);
    }
    public static List<Finding> url(String address){
        List<Finding> out=new ArrayList<>();if(address==null||address.isEmpty())return out;
        URI u;try{u=new URI(address.trim());}catch(Exception e){out.add(new Finding("url.malformed",Severity.MEDIUM,"URL cannot be parsed"));return out;}
        String scheme=u.getScheme()==null?"":u.getScheme().toLowerCase(Locale.ROOT),host=u.getHost()==null?"":u.getHost().toLowerCase(Locale.ROOT);
        if(scheme.equals("http"))out.add(new Finding("url.insecure",Severity.HIGH,"Plain HTTP: "+host));
        else if(!scheme.equals("https"))out.add(new Finding("url.scheme",Severity.HIGH,"Unsupported scheme: "+scheme));
        if(u.getRawUserInfo()!=null)out.add(new Finding("url.userinfo",Severity.HIGH,"Credentials embedded in URL"));
        if(host.isEmpty()){out.add(new Finding("url.no-host",Severity.MEDIUM,"URL has no host"));return out;}
        if(privateHost(host))out.add(new Finding("url.private-host",Severity.HIGH,"Points at a local or private network address ("+host+")"));
        else if(ipLiteral(host))out.add(new Finding("url.ip-literal",Severity.MEDIUM,"Raw IP address instead of a domain"));
        if(host.contains("xn--"))out.add(new Finding("url.punycode",Severity.MEDIUM,"Internationalised (punycode) host; check for look-alike domains: "+host));
        for(char c:host.toCharArray())if(c>127){out.add(new Finding("url.non-ascii-host",Severity.MEDIUM,"Non-ASCII host characters"));break;}
        for(String s:SHORTENERS)if(host.equals(s)||host.endsWith("."+s))out.add(new Finding("url.shortener",Severity.MEDIUM,"Link shortener hides the destination: "+host));
        if(EXECUTABLE.matcher(u.getRawPath()==null?"":u.getRawPath()).find())out.add(new Finding("url.executable",Severity.CRITICAL,"Executable or installer download"));
        String query=u.getRawQuery()==null?"":u.getRawQuery().toLowerCase(Locale.ROOT);
        if(query.matches("(?s).*(^|[=&])[^=&]*=(https?(:|%3a)(//|%2f%2f)).*"))out.add(new Finding("url.wrapped",Severity.MEDIUM,"Stream is relayed through "+host+", which fetches another URL on the user's behalf; the real origin and its terms are hidden"));
        return out;
    }
    /** True for loopback, private, link-local, CGNAT and unspecified literals and local names. No DNS lookup is made. */
    public static boolean privateHost(String host){
        if(host==null)return false;String h=host.toLowerCase(Locale.ROOT);
        if(h.startsWith("[")&&h.endsWith("]"))h=h.substring(1,h.length()-1);
        if(h.equals("localhost")||h.endsWith(".localhost")||h.endsWith(".local")||h.endsWith(".internal"))return true;
        Matcher m=IPV4.matcher(h);
        if(m.matches()){
            int a=Integer.parseInt(m.group(1)),b=Integer.parseInt(m.group(2));
            return a==0||a==10||a==127||(a==169&&b==254)||(a==172&&b>=16&&b<=31)||(a==192&&b==168)||(a==100&&b>=64&&b<=127);
        }
        if(h.contains(":")){
            if(h.equals("::1")||h.equals("::"))return true;
            if(h.startsWith("fc")||h.startsWith("fd")||h.startsWith("fe8")||h.startsWith("fe9")||h.startsWith("fea")||h.startsWith("feb"))return true;
            if(h.startsWith("::ffff:"))return privateHost(h.substring(7));
        }
        return false;
    }
    static boolean ipLiteral(String host){String h=host.replace("[","").replace("]","");return IPV4.matcher(h).matches()||h.contains(":");}

    private static void redirects(Sample s,List<Finding> out){
        if(s.redirects.size()>3)out.add(new Finding("redirect.chain",Severity.MEDIUM,s.redirects.size()+" redirects before content"));
        String previous=s.url;Set<String> hosts=new LinkedHashSet<>();
        for(String r:s.redirects){
            if(previous.startsWith("https:")&&r.startsWith("http:"))out.add(new Finding("redirect.downgrade",Severity.HIGH,"HTTPS redirect downgraded to HTTP"));
            hosts.add(host(r));
            for(Finding f:url(r))if(f.severity.weight>=Severity.HIGH.weight&&!f.rule.equals("url.insecure"))out.add(new Finding("redirect."+f.rule.substring(4),f.severity,"Redirect target: "+f.detail));
            previous=r;
        }
        hosts.remove(host(s.url));if(hosts.size()>=2)out.add(new Finding("redirect.cross-host",Severity.LOW,"Redirects through "+hosts.size()+" other hosts"));
    }
    private static void content(Sample s,List<Finding> out){
        String type=s.contentType.toLowerCase(Locale.ROOT),body=s.body==null?"":s.body,lower=body.toLowerCase(Locale.ROOT);
        boolean html=type.contains("html")||lower.contains("<html");
        if(s.expectsMedia&&html)out.add(new Finding("content.mismatch",Severity.MEDIUM,"Expected a media stream but received an HTML page"));
        if(!html)return;
        String own=host(s.url);Set<String> scriptHosts=new LinkedHashSet<>();
        Matcher m=SCRIPT_SRC.matcher(body);while(m.find()){String h=host(ProviderParser.absolute(s.url,m.group(1)));if(!h.isEmpty()&&!h.equals(own))scriptHosts.add(h);}
        if(scriptHosts.size()>3)out.add(new Finding("html.third-party-scripts",Severity.MEDIUM,scriptHosts.size()+" third-party script hosts"));
        for(String ad:AD_NETWORKS)if(lower.contains(ad)){out.add(new Finding("html.ad-network",Severity.HIGH,"Known aggressive ad network: "+ad));break;}
        for(String p:POPUNDER)if(lower.contains(p)){out.add(new Finding("html.popunder",Severity.HIGH,"Pop-under or forced navigation pattern"));break;}
        for(String o:OBFUSCATION)if(lower.contains(o)){out.add(new Finding("html.obfuscated-js",Severity.HIGH,"Obfuscated JavaScript ("+o.replace("(","")+")"));break;}
        for(String c:MINERS)if(lower.contains(c)){out.add(new Finding("html.cryptominer",Severity.CRITICAL,"Browser crypto-miner signature"));break;}
        Matcher frames=IFRAME.matcher(body);int hidden=0,framesTotal=0;
        while(frames.find()){framesTotal++;String tag=frames.group().toLowerCase(Locale.ROOT);if(tag.contains("width=\"1\"")||tag.contains("width=\"0\"")||tag.contains("display:none")||tag.contains("visibility:hidden"))hidden++;
            Matcher src=Pattern.compile("src=[\"']([^\"']+)").matcher(tag);if(src.find()){for(Finding f:url(ProviderParser.absolute(s.url,src.group(1))))if(f.rule.equals("url.ip-literal")||f.rule.equals("url.private-host")||f.rule.equals("url.insecure"))out.add(new Finding("html.iframe-"+f.rule.substring(4),f.severity,"Embedded frame: "+f.detail));}}
        if(hidden>0)out.add(new Finding("html.hidden-iframe",Severity.HIGH,hidden+" hidden iframe(s)"));
        if(framesTotal>4)out.add(new Finding("html.many-iframes",Severity.LOW,framesTotal+" iframes on one page"));
        Matcher refresh=META_REFRESH.matcher(body);if(refresh.find()){String target=host(ProviderParser.absolute(s.url,refresh.group(1)));if(!target.equals(own))out.add(new Finding("html.meta-refresh",Severity.MEDIUM,"Automatic redirect to "+target));}
        Matcher links=HREF.matcher(body);int exe=0;while(links.find())if(EXECUTABLE.matcher(links.group(1)).find())exe++;
        if(exe>0)out.add(new Finding("html.executable-link",Severity.CRITICAL,exe+" link(s) to installers or executables"));
    }
    private static void quality(Sample s,List<Finding> out){
        int claimed=claimedHeight(s.declaredQuality);if(claimed==0||s.body==null)return;
        Matcher m=RESOLUTION.matcher(s.body);int max=0;while(m.find())max=Math.max(max,Integer.parseInt(m.group(1)));
        if(max>0&&max<claimed)out.add(new Finding("quality.overstated",Severity.HIGH,"Claims "+s.declaredQuality+" but the best rendition is "+max+"p"));
    }
    static int claimedHeight(String q){
        String v=q==null?"":q.toLowerCase(Locale.ROOT);
        if(v.contains("4k")||v.contains("2160"))return 2160;if(v.contains("1080")||v.contains("fhd")||v.contains("full hd"))return 1080;
        if(v.contains("720")||v.matches(".*\\bhd\\b.*"))return 720;return 0;
    }
    private static String host(String url){try{String h=new URI(url).getHost();return h==null?"":h.toLowerCase(Locale.ROOT);}catch(Exception e){return "";}}
}
