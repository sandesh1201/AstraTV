package com.astratv.app;

/** Pure state rules for the in-app tunnel. "Protected" requires live evidence, never intent. */
public final class VpnHealth {
    private VpnHealth(){}
    public enum State{NOT_CONFIGURED,OFF,CONSENT_REQUIRED,CONNECTING,AWAITING_HANDSHAKE,PROTECTED,PARTIAL,STALE,RECONNECTING,FAILED}
    /** WireGuard rekeys about every 2 minutes while traffic flows; older handshakes need verification. */
    public static final long HANDSHAKE_FRESH_MS=180_000;
    public static final int MAX_RECONNECTS=6;

    public static State evaluate(boolean configured,boolean wanted,boolean tunnelUp,boolean osReportsVpn,long lastHandshakeMs,long nowMs,boolean fullTunnel){
        if(!configured)return State.NOT_CONFIGURED;
        if(!tunnelUp)return wanted?State.CONNECTING:State.OFF;
        if(!osReportsVpn)return State.CONNECTING;
        if(lastHandshakeMs<=0)return State.AWAITING_HANDSHAKE;
        if(nowMs-lastHandshakeMs>HANDSHAKE_FRESH_MS)return State.STALE;
        return fullTunnel?State.PROTECTED:State.PARTIAL;
    }
    public static boolean claimsProtection(State s){return s==State.PROTECTED;}
    public static long backoffMs(int attempt){return Math.min(30_000L,2_000L<<Math.max(0,Math.min(attempt,4)));}
    public static String label(State s){
        switch(s){
            case NOT_CONFIGURED:return "No VPN configuration imported";
            case OFF:return "Disconnected · traffic uses your normal connection";
            case CONSENT_REQUIRED:return "Android needs your permission to start the VPN";
            case CONNECTING:return "Connecting · not protected yet";
            case AWAITING_HANDSHAKE:return "Tunnel created · waiting for the server handshake · not protected yet";
            case PROTECTED:return "Protected · full tunnel with a recent server handshake";
            case PARTIAL:return "Connected · split tunnel, only some traffic is protected";
            case STALE:return "Connection may be broken · no recent handshake";
            case RECONNECTING:return "Reconnecting · not protected";
            default:return "Connection failed · not protected";
        }
    }
    /** Bytes per second between two counter samples; counter resets read as zero. */
    public static long rate(long before,long after,long elapsedMs){return elapsedMs<=0||after<before?0:(after-before)*1000/elapsedMs;}
}
