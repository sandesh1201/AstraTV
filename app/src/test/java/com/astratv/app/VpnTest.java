package com.astratv.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class VpnTest {
    private static final String FAKE_KEY="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static String config(String allowed,String dns,String extra){
        return "[Interface]\nPrivateKey = "+FAKE_KEY+"\nAddress = 10.64.0.2/32\n"+(dns.isEmpty()?"":"DNS = "+dns+"\n")+extra
            +"\n[Peer]\nPublicKey = "+FAKE_KEY+"\nAllowedIPs = "+allowed+"\nEndpoint = vpn.example.net:51820\n";
    }

    @Test public void fullTunnelWithDnsHasNoWarnings(){
        VpnConfigAnalysis a=VpnConfigAnalysis.of(config("0.0.0.0/0, ::/0","10.64.0.1",""));
        assertTrue(a.errors.toString(),a.valid());assertTrue(a.fullTunnel());assertTrue(a.warnings.isEmpty());
        assertEquals("vpn.example.net",a.endpointHost);assertEquals("10.64.0.1",a.dns.get(0));
    }
    @Test public void splitTunnelIpv6AndDnsGapsAreWarned(){
        VpnConfigAnalysis a=VpnConfigAnalysis.of(config("10.0.0.0/8","",""));
        assertTrue(a.valid());assertEquals(3,a.warnings.size());
    }
    @Test public void ipv6BlockAddsDefaultRouteOnceAndKeepsComments(){
        String fixed=VpnConfigAnalysis.withIpv6Block(config("0.0.0.0/0 # all v4","1.1.1.1",""));
        VpnConfigAnalysis a=VpnConfigAnalysis.of(fixed);
        assertTrue(a.fullV6);assertTrue(fixed.contains("0.0.0.0/0, ::/0 # all v4"));
        assertEquals(fixed,VpnConfigAnalysis.withIpv6Block(fixed));
    }
    @Test public void shellHooksAndMissingFieldsAreRefused(){
        assertFalse(VpnConfigAnalysis.of(config("0.0.0.0/0","1.1.1.1","PostUp = iptables -F")).valid());
        assertFalse(VpnConfigAnalysis.of("[Interface]\nPrivateKey = "+FAKE_KEY+"\n").valid());
        assertFalse(VpnConfigAnalysis.of("").valid());
        assertFalse(VpnConfigAnalysis.of(new String(new char[VpnConfigAnalysis.MAX_BYTES+1]).replace('\0','x')).valid());
    }
    @Test public void redactionHidesKeys(){
        String r=VpnConfigAnalysis.redacted(config("0.0.0.0/0","1.1.1.1","")+"PresharedKey = "+FAKE_KEY+"\n");
        assertFalse(r.contains("PrivateKey = "+FAKE_KEY));assertFalse(r.contains("PresharedKey = "+FAKE_KEY));assertTrue(r.contains("(hidden)"));
    }
    @Test public void protectionNeedsTunnelOsConfirmationAndFreshHandshake(){
        long now=1_000_000_000L;
        assertEquals(VpnHealth.State.NOT_CONFIGURED,VpnHealth.evaluate(false,true,true,true,now,now,true));
        assertEquals(VpnHealth.State.OFF,VpnHealth.evaluate(true,false,false,false,0,now,true));
        assertEquals(VpnHealth.State.CONNECTING,VpnHealth.evaluate(true,true,false,true,now,now,true));
        assertEquals(VpnHealth.State.CONNECTING,VpnHealth.evaluate(true,true,true,false,now,now,true));
        assertEquals(VpnHealth.State.AWAITING_HANDSHAKE,VpnHealth.evaluate(true,true,true,true,0,now,true));
        assertEquals(VpnHealth.State.STALE,VpnHealth.evaluate(true,true,true,true,now-VpnHealth.HANDSHAKE_FRESH_MS-1,now,true));
        assertEquals(VpnHealth.State.PARTIAL,VpnHealth.evaluate(true,true,true,true,now-1000,now,false));
        assertEquals(VpnHealth.State.PROTECTED,VpnHealth.evaluate(true,true,true,true,now-1000,now,true));
        for(VpnHealth.State s:VpnHealth.State.values())
            assertEquals(s==VpnHealth.State.PROTECTED,VpnHealth.claimsProtection(s));
        for(VpnHealth.State s:VpnHealth.State.values())
            if(s!=VpnHealth.State.PROTECTED)assertFalse(VpnHealth.label(s).toLowerCase().startsWith("protected"));
    }
    @Test public void reconnectBackoffIsBoundedAndRatesIgnoreResets(){
        assertEquals(2000,VpnHealth.backoffMs(0));assertEquals(4000,VpnHealth.backoffMs(1));assertEquals(30000,VpnHealth.backoffMs(10));
        assertEquals(500,VpnHealth.rate(1000,1500,1000));assertEquals(0,VpnHealth.rate(5000,10,1000));
    }
}
