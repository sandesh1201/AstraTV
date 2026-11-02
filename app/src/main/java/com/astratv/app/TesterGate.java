package com.astratv.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Access gate for the Security Research Dashboard. The build embeds only a PBKDF2 hash of the
 * tester PIN (from local.properties), never the PIN. Five failures lock the gate for five minutes.
 */
public final class TesterGate {
    private TesterGate(){}
    public static final int ITERATIONS=120_000,MAX_FAILURES=5;
    public static final long LOCKOUT_MS=5*60_000L;

    public static boolean configured(String expectedHex){return expectedHex!=null&&expectedHex.matches("[0-9a-f]{64}");}
    public static String hash(String pin,String salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt.getBytes(StandardCharsets.UTF_8),ITERATIONS,256);
        try{byte[] out=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();StringBuilder hex=new StringBuilder();for(byte b:out)hex.append(String.format("%02x",b&0xff));return hex.toString();}
        finally{spec.clearPassword();}
    }
    public static boolean verify(String pin,String salt,String expectedHex){
        if(!configured(expectedHex)||pin==null||pin.isEmpty())return false;
        try{return MessageDigest.isEqual(hash(pin,salt).getBytes(StandardCharsets.US_ASCII),expectedHex.getBytes(StandardCharsets.US_ASCII));}catch(Exception e){return false;}
    }
    /** Remaining lockout in ms, given the stored failure count and the time of the last failure. */
    public static long lockedFor(int failures,long lastFailureAt,long now){
        if(failures<MAX_FAILURES)return 0;long left=lastFailureAt+LOCKOUT_MS-now;return Math.max(0,left);
    }
}
