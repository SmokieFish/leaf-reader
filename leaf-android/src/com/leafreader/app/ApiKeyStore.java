package com.leafreader.app;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Credentials are independent of reader settings and are excluded from book exports. */
public final class ApiKeyStore {
 private static final String ALIAS="leafreader-deepseek-v1";
 private final SharedPreferences storage;
 public ApiKeyStore(Context context){storage=context.getSharedPreferences("encrypted_credentials",Context.MODE_PRIVATE);}
 private SecretKey key(boolean create)throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
  if(!create)throw new IOException("保存的 Key 无法解密，请重新填写");
  KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256).build());
  return generator.generateKey();
 }
 public synchronized String load()throws Exception{
  String blob=storage.getString("deepseek","");return blob.isEmpty()?"":SecretCodec.decrypt(key(false),blob);
 }
 public synchronized void save(String value)throws Exception{
  if(value.trim().isEmpty()){clear();return;}
  String blob=SecretCodec.encrypt(key(true),value.trim());
  if(!storage.edit().putString("deepseek",blob).commit())throw new IOException("无法保存 API Key，请检查存储空间");
 }
 public synchronized void clear()throws IOException{
  if(!storage.edit().remove("deepseek").commit())throw new IOException("无法清除 API Key，请重试");
 }
}
