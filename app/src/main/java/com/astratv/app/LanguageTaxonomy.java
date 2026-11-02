package com.astratv.app;

import java.util.*;

/** Fixed browse languages. Anything not listed is grouped as Other. */
public final class LanguageTaxonomy {
    private LanguageTaxonomy(){}
    public static final String OTHER="Other";
    public static final List<String> ALL=Collections.unmodifiableList(Arrays.asList(
        "Telugu","Tamil","Hindi","Malayalam","Kannada","Bengali","Marathi","Punjabi","English",OTHER));
    public static final List<String> INDIAN=Collections.unmodifiableList(Arrays.asList(
        "Telugu","Tamil","Hindi","Malayalam","Kannada","Bengali","Marathi","Punjabi"));
    private static final Map<String,String> CODES=new HashMap<>();
    static{
        String[][] pairs={{"te","Telugu"},{"ta","Tamil"},{"hi","Hindi"},{"ml","Malayalam"},{"kn","Kannada"},{"bn","Bengali"},{"mr","Marathi"},{"pa","Punjabi"},{"en","English"}};
        for(String[] p:pairs){CODES.put(p[0],p[1]);CODES.put(p[1].toLowerCase(Locale.ROOT),p[1]);}
        CODES.put("hollywood","English");CODES.put("eng","English");CODES.put("tel","Telugu");CODES.put("tam","Tamil");CODES.put("hin","Hindi");CODES.put("mal","Malayalam");CODES.put("kan","Kannada");CODES.put("ben","Bengali");CODES.put("bangla","Bengali");CODES.put("mar","Marathi");CODES.put("pan","Punjabi");
    }
    /** Maps an ISO code or a display name to one of {@link #ALL}. Empty input stays empty. */
    public static String canonical(String value){
        if(value==null)return "";
        String v=value.trim().toLowerCase(Locale.ROOT);if(v.isEmpty())return "";
        String hit=CODES.get(v);if(hit!=null)return hit;
        for(String token:v.split("[^a-z]+")){hit=CODES.get(token);if(hit!=null)return hit;}
        return OTHER;
    }
    public static String of(Library.Title t){return canonical(t.language);}
    public static boolean indian(String language){return INDIAN.contains(canonical(language));}
}
