package com.astratv.app;

import android.content.SharedPreferences;
import android.security.keystore.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Source sessions are encrypted with a non-exportable Android Keystore key and bound to an origin. */
final class SessionVault {
    private static final String ALIAS="astra-provider-session-v1",PREF="netmirror.session.encrypted";
    private final SharedPreferences prefs;
    SessionVault(SharedPreferences prefs){this.prefs=prefs;}
    static String origin(String address)throws Exception{URI uri=new URI(address);if(!Library.https(address))throw new IOException("Invalid session origin");return "https://"+uri.getHost().toLowerCase(Locale.ROOT)+":"+(uri.getPort()<0?443:uri.getPort());}
    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    static String seal(SecretKey key,String origin,String token)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(origin.getBytes(StandardCharsets.UTF_8));byte[] data=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(cipher.getIV())+"."+Base64.getEncoder().encodeToString(data);
    }
    static String open(SecretKey key,String origin,String envelope)throws Exception{
        String[] parts=envelope.split("\\.",2);if(parts.length!=2)throw new IOException("Invalid session envelope");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));cipher.updateAAD(origin.getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);
    }
    synchronized String read(String api){try{
        String encrypted=prefs.getString(PREF,"");if(encrypted.isEmpty()){
            String old=prefs.getString("netmirror.session","");if(old.isEmpty())return "";write(api,old);return old;
        }return open(key(),origin(api),encrypted);
    }catch(Exception e){prefs.edit().remove("netmirror.session").apply();return "";}}
    synchronized void write(String api,String token)throws IOException{
        int revision=prefs.getInt("netmirror.session.revision",0)+1;
        if(token.isEmpty()){prefs.edit().remove(PREF).remove("netmirror.session").putInt("netmirror.session.revision",revision).apply();return;}
        try{String encrypted=seal(key(),origin(api),token);prefs.edit().putString(PREF,encrypted).remove("netmirror.session").putInt("netmirror.session.revision",revision).apply();}catch(Exception e){throw new IOException("The device could not securely store this source session",e);}
    }
}
