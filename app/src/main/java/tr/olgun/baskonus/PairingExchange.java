package tr.olgun.baskonus;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Per-request ephemeral ECDH pairing material. Both users must approve
 * the displayed peer identity before the derived session key is accepted.
 * Discovery and approval transport are implemented by the caller.
 */
public final class PairingExchange {
 private static final byte[] DOMAIN="BasKonus-ECDH-pairing-v1".getBytes(StandardCharsets.UTF_8);
 private final KeyPair ephemeral;
 private final byte[] nonce=new byte[32];
 public PairingExchange() throws Exception {
  KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");
  generator.initialize(new ECGenParameterSpec("secp256r1"));
  ephemeral=generator.generateKeyPair();
  new SecureRandom().nextBytes(nonce);
 }
 public byte[] publicKey(){return ephemeral.getPublic().getEncoded().clone();}
 public byte[] nonce(){return nonce.clone();}
 public byte[] derive(byte[] remotePublicKey,byte[] remoteNonce,boolean localFirst) throws Exception {
  if(remoteNonce==null||remoteNonce.length!=32)throw new IllegalArgumentException("Invalid nonce");
  PublicKey peer=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(remotePublicKey));
  KeyAgreement agreement=KeyAgreement.getInstance("ECDH");
  agreement.init(ephemeral.getPrivate());
  agreement.doPhase(peer,true);
  byte[] shared=agreement.generateSecret();
  byte[] local=publicKey(),first=localFirst?local:remotePublicKey,second=localFirst?remotePublicKey:local;
  byte[] n1=localFirst?nonce:remoteNonce,n2=localFirst?remoteNonce:nonce;
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  digest.update(DOMAIN);digest.update(n1);digest.update(n2);digest.update(first);digest.update(second);
  byte[] salt=digest.digest();
  Mac mac=Mac.getInstance("HmacSHA256");
  mac.init(new SecretKeySpec(salt,"HmacSHA256"));
  byte[] prk=mac.doFinal(shared);
  mac.init(new SecretKeySpec(prk,"HmacSHA256"));
  mac.update(DOMAIN);mac.update((byte)1);
  byte[] key=mac.doFinal();
  java.util.Arrays.fill(shared,(byte)0);
  java.util.Arrays.fill(prk,(byte)0);
  return key;
 }
}
