package com.astratv.app;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;

public class ProviderRiskScannerTest {
    private static String fixture(String name)throws IOException{
        try(InputStream in=ProviderRiskScannerTest.class.getResourceAsStream("/risk/"+name)){
            assertNotNull("missing fixture "+name,in);
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static ProviderRiskScanner.Sample page(String url,String body){
        ProviderRiskScanner.Sample s=new ProviderRiskScanner.Sample();s.url=url;s.contentType="text/html; charset=utf-8";s.body=body;return s;
    }

    @Test public void mirrorStylePageScoresHighWithEveryTrait()throws Exception{
        ProviderRiskScanner.Report r=ProviderRiskScanner.scan(page("https://mirror.example.invalid/movie/sample",fixture("movierulz_like_page.html")));
        for(String rule:new String[]{"html.ad-network","html.popunder","html.obfuscated-js","html.hidden-iframe","html.iframe-ip-literal","html.meta-refresh","html.executable-link","html.third-party-scripts"})
            assertTrue("expected "+rule+" in "+r.findings,r.has(rule));
        assertEquals("High risk",r.level());
    }
    @Test public void legitimatePageHasNoFindings()throws Exception{
        ProviderRiskScanner.Report r=ProviderRiskScanner.scan(page("https://catalog.example.com/title/open-movie",fixture("legit_details_page.html")));
        assertTrue(r.findings.toString(),r.findings.isEmpty());assertEquals("No findings",r.level());
    }
    @Test public void playlistUrlChecks()throws Exception{
        ProviderRiskScanner.Report r=ProviderRiskScanner.playlist(fixture("movierulz_like_playlist.m3u"));
        for(String rule:new String[]{"url.insecure","url.ip-literal","url.userinfo","url.punycode","url.private-host","url.shortener","url.executable"})
            assertTrue("expected "+rule+" in "+r.findings,r.has(rule));
        assertEquals("High risk",r.level());
    }
    @Test public void legitimateManifestPassesAndHonestQualityIsAccepted()throws Exception{
        String master=fixture("legit_master.m3u8");
        assertTrue(ProviderRiskScanner.playlist(master).findings.isEmpty());
        ProviderRiskScanner.Sample s=new ProviderRiskScanner.Sample();s.url="https://media.example.com/open-movie/master.m3u8";s.contentType="application/vnd.apple.mpegurl";s.body=master;s.declaredQuality="1080p";s.expectsMedia=true;
        assertTrue(ProviderRiskScanner.scan(s).findings.isEmpty());
    }
    @Test public void hdClaimOnSdStreamIsFlagged()throws Exception{
        ProviderRiskScanner.Sample s=new ProviderRiskScanner.Sample();s.url="https://media.example.com/sd/master.m3u8";s.contentType="application/vnd.apple.mpegurl";s.body=fixture("sd_only_master.m3u8");s.declaredQuality="HD";s.expectsMedia=true;
        assertTrue(ProviderRiskScanner.scan(s).has("quality.overstated"));
    }
    @Test public void redirectChainsAndDowngrades(){
        ProviderRiskScanner.Sample s=new ProviderRiskScanner.Sample();s.url="https://start.example.com/x";s.expectsMedia=true;s.contentType="text/html";s.body="<html></html>";
        s.redirects=Arrays.asList("https://hop1.example.invalid/","https://hop2.example.invalid/","http://hop3.example.invalid/","https://10.0.0.5/final");
        ProviderRiskScanner.Report r=ProviderRiskScanner.scan(s);
        assertTrue(r.has("redirect.chain"));assertTrue(r.has("redirect.downgrade"));assertTrue(r.has("redirect.private-host"));assertTrue(r.has("redirect.cross-host"));assertTrue(r.has("content.mismatch"));
    }
    @Test public void relayedStreamUrlsAreFlagged(){
        for(String u:new String[]{"https://relay.example.invalid/proxy?url=https%3A%2F%2Fcdn.example.test%2Fa.m3u8&ext=.mp4","https://relay.example.invalid/p?a=1&src=http://cdn.example.test/v.mp4"})
            assertTrue(u,ProviderRiskScanner.url(u).stream().anyMatch(f->f.rule.equals("url.wrapped")));
        for(String u:new String[]{"https://cdn.example.com/v.m3u8?token=abc123&exp=1767225600","https://cdn.example.com/v.mp4?ref=https-landing"})
            assertFalse(u,ProviderRiskScanner.url(u).stream().anyMatch(f->f.rule.equals("url.wrapped")));
    }
    @Test public void privateHostDetectionDoesNotFlagPublicHosts(){
        for(String h:new String[]{"localhost","127.0.0.1","10.1.2.3","172.16.0.1","192.168.0.10","169.254.1.1","100.64.0.1","::1","[fd00::1]","fe80::1","::ffff:192.168.1.1","printer.local","0.0.0.0"})
            assertTrue(h,ProviderRiskScanner.privateHost(h));
        for(String h:new String[]{"api.themoviedb.org","8.8.8.8","172.32.0.1","203.0.113.5","2606:4700::1111"})
            assertFalse(h,ProviderRiskScanner.privateHost(h));
    }
}
