package tr.olgun.baskonus;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Persistent non-exportable Android Keystore signing identity.
 * A fingerprint is only a comparison aid; actual peer authentication
 * requires challenge signatures and trusted public-key pinning.
 */
public final class DeviceIdentity {
 private static final String ALIAS="bas_konus_identity_v1";
 private DeviceIdentity(){}
 public static PublicKey getOrCreatePublicKey() throws Exception {
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");
  store.load(null);
  if(!store.containsAlias(ALIAS)){
   KeyPairGenerator generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");
   generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
    .setDigests(KeyProperties.DIGEST_SHA256)
    .setAlgorithmParameterSpec(new java.security.spec.ECGenParameterSpec("secp256r1"))
    .build());
   generator.generateKeyPair();
  }
  return store.getCertificate(ALIAS).getPublicKey();
 }
 public static String fingerprint() throws Exception {
  byte[] digest=MessageDigest.getInstance("SHA-256").digest(getOrCreatePublicKey().getEncoded());
  StringBuilder result=new StringBuilder();
  for(int i=0;i<16;i++){if(i>0&&i%2==0)result.append('-');result.append(String.format(java.util.Locale.ROOT,"%02X",digest[i]&255));}
  return result.toString();
 }
 public static byte[] sign(byte[] message) throws Exception {
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  java.security.PrivateKey key=(java.security.PrivateKey)store.getKey(ALIAS,null);
  java.security.Signature signature=java.security.Signature.getInstance("SHA256withECDSA");
  signature.initSign(key);signature.update(message);return signature.sign();
 }
 public static boolean verify(PublicKey key,byte[] message,byte[] signatureBytes) throws Exception {
  java.security.Signature signature=java.security.Signature.getInstance("SHA256withECDSA");
  signature.initVerify(key);signature.update(message);return signature.verify(signatureBytes);
 }
}
