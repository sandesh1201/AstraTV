package com.astratv.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads a small prefix, never a full film. A successful probe is not a decoding guarantee. */
final class StreamProbe {
    static String classify(String type,byte[] prefix){
        String text=new String(prefix,StandardCharsets.UTF_8).trim();String mime=type==null?"":type.toLowerCase(Locale.ROOT);
        if(text.startsWith("#EXTM3U")&&text.contains("#EXT-X-"))return "application/x-mpegURL";
        if(text.startsWith("<MPD")||text.startsWith("<?xml")&&text.contains("<MPD"))return "application/dash+xml";
        if(text.startsWith("<")||mime.contains("text/html")||text.startsWith("{\"error"))return "blocked";
        if(prefix.length>=8&&prefix[4]=='f'&&prefix[5]=='t'&&prefix[6]=='y'&&prefix[7]=='p')return "video/mp4";
        if(mime.startsWith("video/"))return mime;
        if(mime.contains("mpegurl"))return "application/x-mpegURL";
        if(mime.contains("dash+xml"))return "application/dash+xml";
        return "";
    }
    static void inspect(ProviderRepository.Stream stream)throws IOException{
        String address=stream.url;
        for(int i=0;i<5;i++){
            if(!Library.https(address))throw new IOException("Source requires an unsupported non-HTTPS connection");
            HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setRequestProperty("Range","bytes=0-2047");c.setRequestProperty("User-Agent","AstraTV/0.3");for(Map.Entry<String,String> h:stream.headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
            try{int status=c.getResponseCode();if(status>=300&&status<400){String next=c.getHeaderField("Location");if(next==null)throw new IOException("Source returned an invalid redirect");address=ProviderParser.absolute(address,next);continue;}if(status<200||status>=300)throw new IOException("Source returned HTTP "+status);
                byte[] prefix=new byte[2048];int used=0;try(InputStream in=c.getInputStream()){int n;while(used<prefix.length&&(n=in.read(prefix,used,prefix.length-used))>0)used+=n;}
                String mime=classify(c.getContentType(),Arrays.copyOf(prefix,used));if(mime.equals("blocked"))throw new IOException("Source returned a web page or access gate, not playable media");stream.mimeType=mime;return;
            }finally{c.disconnect();}
        }throw new IOException("Source redirected too many times");
    }
}
