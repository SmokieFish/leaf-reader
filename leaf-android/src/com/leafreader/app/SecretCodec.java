package com.leafreader.app;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Authenticated encryption only; the Android wrapper owns the non-exportable key. */
public final class SecretCodec {
 private static final byte[] AAD="leafreader/deepseek-key/v1".getBytes(StandardCharsets.UTF_8);
 public static String encrypt(SecretKey key,String value)throws GeneralSecurityException{
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);c.updateAAD(AAD);
  byte[] cipher=c.doFinal(value.getBytes(StandardCharsets.UTF_8));
  return "1:"+Base64.getEncoder().encodeToString(c.getIV())+":"+Base64.getEncoder().encodeToString(cipher);
 }
 public static String decrypt(SecretKey key,String blob)throws GeneralSecurityException{
  try{
   String[] parts=blob.split(":",-1);if(parts.length!=3||!parts[0].equals("1"))throw new GeneralSecurityException("Invalid credential format");
   byte[] iv=Base64.getDecoder().decode(parts[1]),cipher=Base64.getDecoder().decode(parts[2]);
   if(iv.length!=12||cipher.length<16)throw new GeneralSecurityException("Invalid credential data");
   Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));c.updateAAD(AAD);
   return new String(c.doFinal(cipher),StandardCharsets.UTF_8);
  }catch(IllegalArgumentException e){throw new GeneralSecurityException("Invalid credential data",e);}
 }
}
