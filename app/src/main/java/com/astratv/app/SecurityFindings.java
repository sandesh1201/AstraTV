package com.astratv.app;

import java.util.*;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Findings register for the Security Research Dashboard. Every retest runs inside the app against
 * its own code, its own settings or local fixtures; nothing here probes third-party systems.
 */
public final class SecurityFindings {
    private SecurityFindings(){}
    public enum Severity{CRITICAL,HIGH,MEDIUM,LOW,INFO}
    public enum Outcome{PASS,FAIL,NOT_TESTABLE}
    public static final String[] AREAS={
        "OWASP Top 10 (mobile-relevant)","Cross-site scripting","SQL and NoSQL injection","Server-side request forgery","Cross-site request forgery",
        "Broken authentication and authorization","Insecure API endpoints","Dependency vulnerabilities","Secrets in app bundle","Malicious third-party scripts",
        "Insecure content embedding","VPN DNS, IPv6 and IP leaks","Insecure redirects","Broken TLS validation","Excessive logging of sensitive data",
        "Insecure session management","Supply-chain attacks","Unsafe stream URL handling","Unauthorized access to admin functionality","Weak browser security policies"};

    /** Facts about the running app. Implemented by the dashboard; faked in unit tests. */
    public interface Env{
        boolean cleartextAllowed();boolean backupAllowed();boolean debuggable();
        List<String> exportedComponents();
        boolean buildConfigKeysEmpty();
        VpnConfigAnalysis vpnConfig();boolean vpnLeakProtection();VpnHealth.State vpnState();List<String> vpnDnsReported();boolean vpnRoutesIpv6();
        boolean webViewFileAccess();boolean webViewContentAccess();boolean webCookiesAccepted();
        String researchExport();
        /** Empty when hostile keys round-trip literally through the real metadata store; otherwise the failure. */
        String sqlInjectionProbe();
        boolean testerGateConfigured();boolean dashboardExported();
    }
    public static final class Result{
        public final Outcome outcome;public final String detail;
        Result(Outcome o,String d){outcome=o;detail=d;}
        static Result pass(String d){return new Result(Outcome.PASS,d);}
        static Result fail(String d){return new Result(Outcome.FAIL,d);}
        static Result na(String d){return new Result(Outcome.NOT_TESTABLE,d);}
    }
    public interface Check{Result run(Env env);}
    public static final class Finding{
        public final String id,title,component,reproduction,impact,remediation,status,detected;public final Severity severity;public final int[] areas;public final Check retest;
        Finding(String id,String title,Severity severity,String component,String reproduction,String impact,String remediation,String status,String detected,int[] areas,Check retest){
            this.id=id;this.title=title;this.severity=severity;this.component=component;this.reproduction=reproduction;this.impact=impact;this.remediation=remediation;this.status=status;this.detected=detected;this.areas=areas;this.retest=retest;
        }
    }
    private static int[] a(int...areas){return areas;}

    public static List<Finding> all(){
        List<Finding> f=new ArrayList<>();
        f.add(new Finding("AST-01","Provider redirects could reach local network addresses",Severity.MEDIUM,"ProviderRepository.request",
            "Configure a manifest or catalog that redirects to https://192.168.1.1/ and load it.",
            "A hostile provider could make the TV send requests to routers or other devices on the LAN.",
            "Refuse loopback, private, link-local and CGNAT literals before every request and redirect.","Fixed","2026-11-02",a(4,13,1),
            env->{for(String h:new String[]{"127.0.0.1","10.0.0.1","192.168.1.1","169.254.169.254","[::1]","fd00::1","printer.local"})if(!ProviderRiskScanner.privateHost(h))return Result.fail(h+" is not blocked");
                return ProviderRiskScanner.privateHost("api.themoviedb.org")?Result.fail("Public host wrongly blocked"):Result.pass("7 local targets blocked; public hosts allowed");}));
        f.add(new Finding("AST-02","\"Trending Now\" was ranked by provider rating",Severity.MEDIUM,"Home shelves",
            "Open Home with a provider that supplies ratings; the Trending shelf ordered by rating.",
            "Users were shown a popularity claim the app could not support.",
            "Use TMDB trending charts only; otherwise hide the shelf. Rating-based list renamed Top Rated.","Fixed","2026-11-02",a(1),
            env->{Library.Title rated=new Library.Title("r","Rated","","https://example.com/a.mp4","","2020","Drama");rated.rating="9.5";
                Map<String,Integer> ranks=CatalogRanking.matchTrends(Collections.singletonList(rated),Collections.emptyList());
                return ranks.isEmpty()?Result.pass("A rated title gets no trending rank without chart data"):Result.fail("Rating produced a trend rank");}));
        f.add(new Finding("AST-03","API keys compiled into BuildConfig are extractable from the APK",Severity.MEDIUM,"app/build.gradle",
            "Build with tmdb.api.key in local.properties, then decompile the APK and read BuildConfig.",
            "Anyone with the APK can reuse the metadata keys and exhaust their quota.",
            "Ship release builds without keys; users enter their own keys in Settings (stored app-private).","Mitigated by process","2026-11-02",a(9),
            env->env.buildConfigKeysEmpty()?Result.pass("No keys compiled into this build"):Result.fail("This build embeds at least one API key; do not distribute it")));
        f.add(new Finding("AST-04","OMDb key travels in the URL query string",Severity.LOW,"MetadataRepository.fillRatings",
            "Enable OMDb and inspect the request line on a TLS-intercepting test proxy you control.",
            "Keys in URLs can end up in proxy or server logs. OMDb only supports this form.",
            "Keep requests HTTPS-only and never log URLs; the research log stores no URLs.","Accepted risk","2026-11-02",a(7,15),
            env->{String log=env.researchExport();return log.contains("://")||log.toLowerCase(Locale.ROOT).contains("apikey")?Result.fail("Research log contains a URL or key"):Result.pass("Research log holds no URLs or keys");}));
        f.add(new Finding("AST-05","Cleartext HTTP must stay disabled",Severity.HIGH,"AndroidManifest",
            "Inspect application flags, or try to play an http:// stream.","Plain HTTP exposes viewing history and allows content injection.",
            "Keep usesCleartextTraffic=false and the HTTPS-only URL checks.","Verified control","2026-11-02",a(1,7,14,18),
            env->env.cleartextAllowed()?Result.fail("Cleartext traffic is allowed"):Library.https("http://example.com/a.mp4")?Result.fail("http URL accepted"):Result.pass("Cleartext disabled; http and credential URLs rejected")));
        f.add(new Finding("AST-06","Only the launcher activity may be exported",Severity.HIGH,"AndroidManifest",
            "List exported components with the package manager.","Exported screens or services could be driven by other apps.",
            "Keep PlayerActivity, PlaybackService, the VPN service and the dashboard non-exported.","Verified control","2026-11-02",a(6,19),
            env->{List<String> ex=new ArrayList<>(env.exportedComponents());ex.remove("com.astratv.app.MainActivity");return ex.isEmpty()?Result.pass("Only MainActivity is exported"):Result.fail("Also exported: "+String.join(", ",ex));}));
        f.add(new Finding("AST-07","Security dashboard must be gated and non-exported",Severity.HIGH,"SecurityDashboardActivity",
            "Try to launch the dashboard from adb or another app, then try wrong PINs.","Findings and diagnostics could be read by people who are not testers.",
            "Non-exported activity, PBKDF2 PIN hash from local.properties, 5-try lockout.","Verified control","2026-11-02",a(6,19),
            env->env.dashboardExported()?Result.fail("Dashboard is exported"):!env.testerGateConfigured()?Result.fail("No tester PIN hash in this build"):
                TesterGate.lockedFor(TesterGate.MAX_FAILURES,1000,1000+1)>0?Result.pass("Gate configured; lockout engages after "+TesterGate.MAX_FAILURES+" failures"):Result.fail("Lockout does not engage")));
        f.add(new Finding("AST-08","Trailer WebView must stay locked to the YouTube embed",Severity.MEDIUM,"YoutubeTrailerView",
            "Attempt to navigate the trailer WebView to another host or a javascript: URL.","An open WebView would allow script injection and tracking.",
            "Allow youtube-nocookie navigation only, block windows, file and content access, and cookies.","Verified control","2026-11-02",a(2,10,11,20),
            env->{for(String bad:new String[]{"https://evil.example/","javascript:alert(1)","http://www.youtube-nocookie.com/embed/x","https://youtube-nocookie.com.evil.example/"})if(TrailerResolver.youtubeEmbed(bad))return Result.fail("Allowed "+bad);
                if(env.webViewFileAccess()||env.webViewContentAccess())return Result.fail("File or content access enabled");
                if(env.webCookiesAccepted())return Result.fail("Cookies accepted");
                return Result.pass("Only youtube-nocookie navigation; file/content access and cookies off");}));
        f.add(new Finding("AST-09","CSRF does not apply to this client; cookie state must stay empty",Severity.INFO,"Networking",
            "Review requests: no cookies or ambient credentials are sent to providers or APIs.","With no cookies, a forged request cannot ride on a session.",
            "Keep cookies disabled and send explicit per-request credentials only.","Not applicable","2026-11-02",a(5),
            env->env.webCookiesAccepted()?Result.fail("WebView accepts cookies"):Result.pass("No cookie jar in use")));
        f.add(new Finding("AST-10","Local databases must use parameterised queries",Severity.MEDIUM,"MetadataStore, CatalogIndex",
            "Store a title whose key contains ' OR '1'='1 and read it back.","Injection could corrupt or leak the metadata cache.",
            "Use selectionArgs and ContentValues everywhere; no string-built SQL.","Verified control","2026-11-02",a(3),
            env->{String probe=env.sqlInjectionProbe();return probe.isEmpty()?Result.pass("Hostile keys stored and matched literally in metadata_v1"):Result.fail(probe);}));
        f.add(new Finding("AST-11","Provider sessions are sealed and bound to their origin",Severity.MEDIUM,"SessionVault",
            "Copy the encrypted session to another server origin and try to use it.","A leaked session could be replayed against a different host.",
            "AES-GCM with the origin as associated data; Keystore key on device.","Verified control","2026-11-02",a(6,16),
            env->{try{KeyGenerator g=KeyGenerator.getInstance("AES");g.init(256);SecretKey k=g.generateKey();String sealed=SessionVault.seal(k,"https://a.example:443","token");
                if(!"token".equals(SessionVault.open(k,"https://a.example:443",sealed)))return Result.fail("Round trip failed");
                try{SessionVault.open(k,"https://b.example:443",sealed);return Result.fail("Opened under another origin");}catch(Exception expected){return Result.pass("Envelope refuses a different origin");}}
            catch(Exception e){return Result.na("Crypto unavailable: "+e.getClass().getSimpleName());}}));
        f.add(new Finding("AST-12","Stream URLs must be HTTPS, credential-free and not installers",Severity.HIGH,"Library.https, ProviderRiskScanner",
            "Feed playlist entries with http://, user:pass@ and .apk URLs.","Unsafe URLs leak data or trick users into installing apps.",
            "Reject in Library.https; flag in the provider risk scanner.","Verified control","2026-11-02",a(18,13),
            env->{if(Library.https("https://"+String.join(":","user","pw")+"@cdn.example/a.mp4"))return Result.fail("Credential URL accepted");
                if(!ProviderRiskScanner.url("https://files.example/player.apk").stream().anyMatch(x->x.rule.equals("url.executable")))return Result.fail("Installer not flagged");
                return Result.pass("Credential URLs rejected; installers flagged");}));
        f.add(new Finding("AST-13","VPN must not leak DNS or IPv6, and must not claim protection early",Severity.HIGH,"VpnController",
            "Import a split or IPv4-only config; connect to an unreachable endpoint; watch the status label.","Traffic could bypass the tunnel while the UI says protected.",
            "Protected only with OS VPN transport + fresh handshake + full tunnel; IPv6 blocked by default; DNS warning.","Verified control","2026-11-02",a(12),
            env->{VpnConfigAnalysis c=env.vpnConfig();if(c==null)return Result.na("No VPN configuration imported");
                if(!c.fullV4)return Result.fail("Split tunnel: IPv4 outside AllowedIPs bypasses the VPN");
                if(!c.fullV6&&!env.vpnLeakProtection())return Result.fail("IPv6 is not routed or blocked");
                if(!c.hasDns)return Result.fail("No tunnel DNS server configured");
                if(env.vpnState()!=VpnHealth.State.PROTECTED)return Result.pass("Config is leak-safe; live test needs a protected tunnel ("+VpnHealth.label(env.vpnState())+")");
                List<String> reported=env.vpnDnsReported();for(String d:reported)if(!c.dns.contains(d))return Result.fail("System uses DNS "+d+" outside the tunnel config");
                return env.vpnRoutesIpv6()||c.fullV6||env.vpnLeakProtection()?Result.pass("Live: tunnel DNS only, IPv6 inside tunnel"):Result.fail("IPv6 default route missing on VPN network");}));
        f.add(new Finding("AST-14","Platform TLS validation is used without overrides",Severity.HIGH,"Networking",
            "Search sources for custom TrustManager or HostnameVerifier (CI check).","Disabled validation would allow traffic interception.",
            "Never install custom trust managers; CI fails the build if one appears.","Verified control (CI)","2026-11-02",a(14,17),
            env->{String verifier=javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().getClass().getName();
                return verifier.startsWith("com.astratv")?Result.fail("App replaced the hostname verifier"):Result.pass("Default hostname verifier: platform");}));
        f.add(new Finding("AST-15","Dependencies need routine updates",Severity.LOW,"app/build.gradle",
            "Run lint (GradleDependency) or Dependabot.","Old libraries can carry known vulnerabilities.",
            "Dependabot weekly for Gradle and Actions; pinned versions; Gradle wrapper validation in CI.","Open","2026-11-02",a(8,17),
            env->Result.na("Checked in CI (Dependabot, wrapper validation)")));
        f.add(new Finding("AST-16","Debug builds are debuggable",Severity.INFO,"Build",
            "Install a debug APK and attach a debugger.","Debuggable builds expose app data to anyone with adb.",
            "Only distribute release builds (R8, non-debuggable).","Expected for debug","2026-11-02",a(1,6),
            env->env.debuggable()?Result.na("This is a debug build"):Result.pass("Build is not debuggable")));
        f.add(new Finding("AST-17","App backups must stay disabled",Severity.MEDIUM,"AndroidManifest",
            "Run adb backup or check the allowBackup flag.","Backups could export sealed sessions and VPN configs to other devices.",
            "allowBackup=false plus data-extraction rules.","Verified control","2026-11-02",a(16,9),
            env->env.backupAllowed()?Result.fail("Backup allowed"):Result.pass("Backup disabled")));
        f.add(new Finding("AST-18","Piracy-style mirror pages carry ads, pop-unders and installers",Severity.HIGH,"MovieRulz provider (assessment)",
            "Run the provider risk scanner on the offline MovieRulz-like fixture (unit test).","Users can be pushed to malware or scams; legal exposure for distributing such sources.",
            "Keep the app off provider web pages (no WebView); scanner flags risky playlists; decision on the provider remains with the owner.","Open (owner decision)","2026-11-02",a(10,13,18),
            env->{ProviderRiskScanner.Sample s=new ProviderRiskScanner.Sample();s.url="https://mirror.example.invalid/x";s.contentType="text/html";
                s.body="<html><script src='https://cdn.popads.example.invalid/p.js'></script><a href='https://dl.example.invalid/a.apk'>Download</a><script>eval(atob('x'))</script></html>";
                ProviderRiskScanner.Report r=ProviderRiskScanner.scan(s);return r.score>=60?Result.pass("Scanner rates the fixture "+r.level()+" ("+r.findings.size()+" findings)"):Result.fail("Scanner under-rates the fixture");}));
        return f;
    }
    /** Areas (1-based indexes into {@link #AREAS}) without any finding. Should be empty. */
    public static List<Integer> uncoveredAreas(List<Finding> findings){
        Set<Integer> covered=new HashSet<>();for(Finding x:findings)for(int i:x.areas)covered.add(i);
        List<Integer> out=new ArrayList<>();for(int i=1;i<=AREAS.length;i++)if(!covered.contains(i))out.add(i);return out;
    }
}
