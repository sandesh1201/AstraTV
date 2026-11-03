package com.astratv.app;

import java.util.*;
import java.util.regex.Pattern;

/**
 * The broadcaster behind a catch-up serial, its language and the broadcaster's own app, which is
 * the only place Saandio sends viewers to watch an episode. The language follows the broadcaster,
 * so a Telugu serial listed on a Tamil site is still filed as Telugu.
 */
final class CatchupBroadcasters {
    static final class Broadcaster{
        final String name,language,app;
        Broadcaster(String name,String language,String app){this.name=name;this.language=language;this.app=app;}
        boolean watchable(){return !app.isEmpty();}
    }
    private static final class Rule{final Pattern pattern;final Broadcaster broadcaster;Rule(String regex,Broadcaster b){pattern=Pattern.compile(regex,Pattern.CASE_INSENSITIVE);broadcaster=b;}}
    private static final List<Rule> RULES=Arrays.asList(
        new Rule("\\bzee\\s*tamil\\b",new Broadcaster("Zee Tamil","Tamil",OfficialAppHandoff.ZEE5)),
        new Rule("\\bzee\\s*telugu\\b",new Broadcaster("Zee Telugu","Telugu",OfficialAppHandoff.ZEE5)),
        new Rule("\\bzee\\s*kannada\\b",new Broadcaster("Zee Kannada","Kannada",OfficialAppHandoff.ZEE5)),
        new Rule("\\bzee\\s*keralam\\b",new Broadcaster("Zee Keralam","Malayalam",OfficialAppHandoff.ZEE5)),
        new Rule("\\bsony\\s*vizha\\b",new Broadcaster("Sony Vizha","Tamil",OfficialAppHandoff.SONY_LIV)),
        new Rule("\\bvijay\\b",new Broadcaster("Star Vijay","Tamil",OfficialAppHandoff.JIOHOTSTAR)),
        new Rule("\\bstar\\s*maa\\b|^maa\\b",new Broadcaster("Star Maa","Telugu",OfficialAppHandoff.JIOHOTSTAR)),
        new Rule("\\bcolors\\s*tamil\\b",new Broadcaster("Colors Tamil","Tamil",OfficialAppHandoff.JIOHOTSTAR)),
        new Rule("\\bcolors\\s*kannada\\b",new Broadcaster("Colors Kannada","Kannada",OfficialAppHandoff.JIOHOTSTAR)),
        new Rule("\\b(?:star\\s*)?suvarna\\b",new Broadcaster("Star Suvarna","Kannada",OfficialAppHandoff.JIOHOTSTAR)),
        new Rule("\\basianet\\b",new Broadcaster("Asianet","Malayalam",OfficialAppHandoff.JIOHOTSTAR)),
        new Rule("\\bgemini\\b",new Broadcaster("Gemini TV","Telugu",OfficialAppHandoff.SUN_NXT)),
        new Rule("\\budaya\\b",new Broadcaster("Udaya TV","Kannada",OfficialAppHandoff.SUN_NXT)),
        new Rule("\\bsurya\\b",new Broadcaster("Surya TV","Malayalam",OfficialAppHandoff.SUN_NXT)),
        new Rule("\\bktv\\b",new Broadcaster("KTV","Tamil",OfficialAppHandoff.SUN_NXT)),
        new Rule("\\bsun\\s*(?:tv)?\\b",new Broadcaster("Sun TV","Tamil",OfficialAppHandoff.SUN_NXT)),
        new Rule("\\betv\\b",new Broadcaster("ETV","Telugu",OfficialAppHandoff.ETV_WIN)),
        new Rule("\\bkalaignar\\b",new Broadcaster("Kalaignar TV","Tamil","")),
        new Rule("\\bpolimer\\b",new Broadcaster("Polimer TV","Tamil","")),
        new Rule("\\braj\\s*tv\\b",new Broadcaster("Raj TV","Tamil","")),
        new Rule("\\bjaya\\s*tv\\b",new Broadcaster("Jaya TV","Tamil",""))
    );
    private CatchupBroadcasters(){}

    /** The broadcaster named in a label such as "Sun Tv Serial" or "Zee Telugu Serials"; null when unknown. */
    static Broadcaster find(String label){
        if(label==null||label.trim().isEmpty())return null;
        for(Rule r:RULES)if(r.pattern.matcher(label).find())return r.broadcaster;
        return null;
    }
    static boolean named(String label){return find(label)!=null;}
}
