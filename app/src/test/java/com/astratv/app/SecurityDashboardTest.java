package com.astratv.app;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class SecurityDashboardTest {
    static class FakeEnv implements SecurityFindings.Env{
        boolean cleartext,backup,debuggable,keysEmpty=true,leak=true,fileAccess,contentAccess,cookies,gate=true,dashboardExported,routesV6=true;
        List<String> exported=new ArrayList<>(Collections.singletonList("com.astratv.app.MainActivity"));
        VpnConfigAnalysis vpn;VpnHealth.State state=VpnHealth.State.NOT_CONFIGURED;List<String> dns=new ArrayList<>();
        String log="{\"events\":[]}",sql="";
        @Override public boolean cleartextAllowed(){return cleartext;}
        @Override public boolean backupAllowed(){return backup;}
        @Override public boolean debuggable(){return debuggable;}
        @Override public List<String> exportedComponents(){return exported;}
        @Override public boolean buildConfigKeysEmpty(){return keysEmpty;}
        @Override public VpnConfigAnalysis vpnConfig(){return vpn;}
        @Override public boolean vpnLeakProtection(){return leak;}
        @Override public VpnHealth.State vpnState(){return state;}
        @Override public List<String> vpnDnsReported(){return dns;}
        @Override public boolean vpnRoutesIpv6(){return routesV6;}
        @Override public boolean webViewFileAccess(){return fileAccess;}
        @Override public boolean webViewContentAccess(){return contentAccess;}
        @Override public boolean webCookiesAccepted(){return cookies;}
        @Override public String researchExport(){return log;}
        @Override public String sqlInjectionProbe(){return sql;}
        @Override public boolean testerGateConfigured(){return gate;}
        @Override public boolean dashboardExported(){return dashboardExported;}
    }
    private static Map<String,SecurityFindings.Outcome> run(SecurityFindings.Env env){
        Map<String,SecurityFindings.Outcome> out=new HashMap<>();for(SecurityFindings.Finding f:SecurityFindings.all())out.put(f.id,f.retest.run(env).outcome);return out;
    }

    @Test public void everyFindingIsCompleteAndAllTwentyAreasAreCovered(){
        Set<String> ids=new HashSet<>();
        for(SecurityFindings.Finding f:SecurityFindings.all()){
            assertTrue(ids.add(f.id));
            for(String field:new String[]{f.title,f.component,f.reproduction,f.impact,f.remediation,f.status,f.detected})assertFalse(f.id,field==null||field.trim().isEmpty());
            assertTrue(f.areas.length>0);
        }
        assertEquals(20,SecurityFindings.AREAS.length);
        assertEquals(Collections.emptyList(),SecurityFindings.uncoveredAreas(SecurityFindings.all()));
    }
    @Test public void hardenedAppHasNoFailingRetests(){
        for(Map.Entry<String,SecurityFindings.Outcome> e:run(new FakeEnv()).entrySet())assertNotEquals(e.getKey(),SecurityFindings.Outcome.FAIL,e.getValue());
    }
    @Test public void regressionsAreCaught(){
        FakeEnv env=new FakeEnv();env.cleartext=true;env.backup=true;env.keysEmpty=false;env.cookies=true;env.exported.add("com.astratv.app.PlayerActivity");env.log="{\"url\":\"https://x\"}";env.sql="Injected selector matched a row";env.gate=false;
        Map<String,SecurityFindings.Outcome> r=run(env);
        for(String id:new String[]{"AST-03","AST-04","AST-05","AST-06","AST-07","AST-08","AST-09","AST-10","AST-17"})assertEquals(id,SecurityFindings.Outcome.FAIL,r.get(id));
    }
    @Test public void vpnLeakRetestReadsConfigAndLiveState(){
        String key="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
        FakeEnv env=new FakeEnv();
        assertEquals(SecurityFindings.Outcome.NOT_TESTABLE,run(env).get("AST-13"));
        env.vpn=VpnConfigAnalysis.of("[Interface]\nPrivateKey="+key+"\nAddress=10.0.0.2/32\nDNS=10.0.0.1\n[Peer]\nPublicKey="+key+"\nAllowedIPs=10.0.0.0/8\nEndpoint=vpn.example.net:51820\n");
        assertEquals(SecurityFindings.Outcome.FAIL,run(env).get("AST-13"));
        env.vpn=VpnConfigAnalysis.of("[Interface]\nPrivateKey="+key+"\nAddress=10.0.0.2/32\nDNS=10.0.0.1\n[Peer]\nPublicKey="+key+"\nAllowedIPs=0.0.0.0/0\nEndpoint=vpn.example.net:51820\n");
        assertEquals(SecurityFindings.Outcome.PASS,run(env).get("AST-13"));
        env.state=VpnHealth.State.PROTECTED;env.dns=Collections.singletonList("192.168.1.1");
        assertEquals("ISP resolver in use while protected",SecurityFindings.Outcome.FAIL,run(env).get("AST-13"));
        env.dns=Collections.singletonList("10.0.0.1");
        assertEquals(SecurityFindings.Outcome.PASS,run(env).get("AST-13"));
    }
    @Test public void testerGateHashesAndLocksOut()throws Exception{
        String hash=TesterGate.hash("482913","salt-1");
        assertTrue(TesterGate.configured(hash));assertFalse(TesterGate.configured(""));
        assertTrue(TesterGate.verify("482913","salt-1",hash));
        assertFalse(TesterGate.verify("482914","salt-1",hash));
        assertFalse(TesterGate.verify("482913","salt-2",hash));
        assertFalse(TesterGate.verify("","salt-1",hash));
        assertEquals(0,TesterGate.lockedFor(4,1000,1001));
        assertTrue(TesterGate.lockedFor(5,1000,1001)>0);
        assertEquals(0,TesterGate.lockedFor(5,1000,1000+TesterGate.LOCKOUT_MS));
    }
}
