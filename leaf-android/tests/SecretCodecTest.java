import com.leafreader.app.SecretCodec;
import javax.crypto.*;
import java.security.GeneralSecurityException;
import java.util.Base64;

public final class SecretCodecTest {
 static int count;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS: "+name);count++;}
 public static void main(String[] args)throws Exception{
  KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);SecretKey key=generator.generateKey();
  String example="example-not-a-real-api-key";
  String blob=SecretCodec.encrypt(key,example);
  check(SecretCodec.decrypt(key,blob).equals(example),"saved credential codec round-trips with the same key");
  check(!blob.contains(example),"stored credential format contains no plaintext secret");
  check(!SecretCodec.encrypt(key,example).equals(blob),"each save uses a fresh random encryption nonce");
  String[] parts=blob.split(":");byte[] changed=Base64.getDecoder().decode(parts[2]);changed[0]^=1;
  boolean rejected=false;try{SecretCodec.decrypt(key,parts[0]+":"+parts[1]+":"+Base64.getEncoder().encodeToString(changed));}catch(GeneralSecurityException expected){rejected=true;}
  check(rejected,"modified encrypted credentials fail authentication");
  rejected=false;try{SecretCodec.decrypt(generator.generateKey(),blob);}catch(GeneralSecurityException expected){rejected=true;}
  check(rejected,"a different encryption key cannot recover the credential");
  rejected=false;try{SecretCodec.decrypt(key,"1:broken:broken");}catch(GeneralSecurityException expected){rejected=true;}
  check(rejected,"corrupted saved data fails cleanly");
  System.out.println(count+" encryption checks passed. Android Keystore storage requires a device test.");
 }
}
