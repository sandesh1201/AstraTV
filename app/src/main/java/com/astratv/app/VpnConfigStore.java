package com.astratv.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.*;
import java.io.IOException;
import java.security.KeyStore;
import javax.crypto.*;

/** The user's WireGuard configuration, sealed with a non-exportable Android Keystore key. */
final class VpnConfigStore {
    private static final String ALIAS="astra-vpn-config-v1",PREF="vpn.config.v1",AAD="astra-vpn-config-v1";
    private final SharedPreferences prefs;
    VpnConfigStore(Context c){prefs=c.getApplicationContext().getSharedPreferences("astra_ui_v1",0);}
    boolean configured(){return !prefs.getString(PREF,"").isEmpty();}
    String label(){return prefs.getString("vpn.label","");}
    boolean leakProtection(){return prefs.getBoolean("vpn.leak_protection",true);}
    boolean autoReconnect(){return prefs.getBoolean("vpn.auto_reconnect",true);}
    void setLeakProtection(boolean on){prefs.edit().putBoolean("vpn.leak_protection",on).apply();}
    void setAutoReconnect(boolean on){prefs.edit().putBoolean("vpn.auto_reconnect",on).apply();}
    synchronized String read(){
        String sealed=prefs.getString(PREF,"");if(sealed.isEmpty())return "";
        try{return SessionVault.open(key(),AAD,sealed);}catch(Exception e){return "";}
    }
    synchronized void write(String config,String label)throws IOException{
        VpnConfigAnalysis a=VpnConfigAnalysis.of(config);if(!a.valid())throw new IOException(a.errors.get(0));
        try{prefs.edit().putString(PREF,SessionVault.seal(key(),AAD,config)).putString("vpn.label",label==null?"":label.trim()).apply();}
        catch(Exception e){throw new IOException("The device could not securely store this VPN configuration",e);}
    }
    synchronized void clear(){
        prefs.edit().remove(PREF).remove("vpn.label").apply();
        try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))store.deleteEntry(ALIAS);}catch(Exception ignored){/* nothing left to decrypt */}
    }
    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
