package tr.olgun.baskonus;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts LAN session material using a non-exportable Android Keystore key. */
public final class SessionVault {
 private static final String ALIAS="bas_konus_lan_session_v1";
 private static final String PREFS="protected_lan_session";
 private SessionVault(){}
 private static SecretKey key() throws Exception {
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");
  store.load(null);
  if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
  KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
   .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
   .setKeySize(256).build());
  return generator.generateKey();
 }
 public static void save(Context context,String hosts,byte[] sessionKey) throws Exception {
  if(hosts==null||hosts.isEmpty()||sessionKey==null||sessionKey.length!=32)throw new IllegalArgumentException("Invalid session");
  byte[] hostsBytes=hosts.getBytes("UTF-8");
  if(hostsBytes.length>1024)throw new IllegalArgumentException("Too many hosts");
  byte[] plain=new byte[2+hostsBytes.length+sessionKey.length];
  plain[0]=(byte)(hostsBytes.length>>>8);plain[1]=(byte)hostsBytes.length;
  System.arraycopy(hostsBytes,0,plain,2,hostsBytes.length);
  System.arraycopy(sessionKey,0,plain,2+hostsBytes.length,sessionKey.length);
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.ENCRYPT_MODE,key());
  byte[] iv=cipher.getIV(),encrypted=cipher.doFinal(plain);
  byte[] packed=new byte[iv.length+encrypted.length];
  System.arraycopy(iv,0,packed,0,iv.length);
  System.arraycopy(encrypted,0,packed,iv.length,encrypted.length);
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("sealed",Base64.getEncoder().encodeToString(packed)).apply();
  java.util.Arrays.fill(plain,(byte)0);
 }
 public static Session load(Context context) throws Exception {
  SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
  String saved=prefs.getString("sealed",null);
  if(saved==null)return null;
  byte[] packed=Base64.getDecoder().decode(saved);
  if(packed.length<29)throw new IllegalArgumentException("Corrupt session");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,packed,0,12));
  byte[] plain=cipher.doFinal(packed,12,packed.length-12);
  int n=((plain[0]&255)<<8)|(plain[1]&255);
  if(n<1||n>1024||plain.length!=n+34)throw new IllegalArgumentException("Invalid session");
  String hosts=new String(plain,2,n,"UTF-8");
  byte[] secret=java.util.Arrays.copyOfRange(plain,n+2,plain.length);
  java.util.Arrays.fill(plain,(byte)0);
  return new Session(hosts,secret);
 }
 public static final class Session {
  public final String hosts;
  public final byte[] key;
  Session(String hosts,byte[] key){this.hosts=hosts;this.key=key;}
 }
}
