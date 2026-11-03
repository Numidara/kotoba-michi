package fr.numidara.kotobamichi;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stocke la clé API chiffrée en AES-GCM par une clé non exportable de l'Android Keystore. */
public final class KeyVault {
  private static final String ALIAS = "kotoba-michi-api";
  private final SharedPreferences p;

  public KeyVault(Context c) { p = c.getSharedPreferences("vault", Context.MODE_PRIVATE); }

  private SecretKey key() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
    KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256).build());
    return kg.generateKey();
  }

  public void put(String secret) {
    try {
      if (secret == null || secret.trim().isEmpty()) { p.edit().remove("k").remove("iv").apply(); return; }
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key());
      byte[] ct = c.doFinal(secret.trim().getBytes(StandardCharsets.UTF_8));
      p.edit().putString("k", Base64.encodeToString(ct, Base64.NO_WRAP))
          .putString("iv", Base64.encodeToString(c.getIV(), Base64.NO_WRAP)).apply();
    } catch (Exception e) {
      throw new RuntimeException("Chiffrement impossible : " + e.getMessage(), e);
    }
  }

  public String get() {
    String k = p.getString("k", null), iv = p.getString("iv", null);
    if (k == null || iv == null) return null;
    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
      return new String(c.doFinal(Base64.decode(k, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return null;
    }
  }

  public boolean has() { return p.contains("k"); }
}
