package com.k2fsa.sherpa.onnx;
import android.content.res.AssetManager;
/** Liaison Java vers libsherpa-onnx-jni.so (noms JNI identiques à l'API Kotlin officielle). */
public class OfflineTts {
  static { System.loadLibrary("sherpa-onnx-jni"); }
  private long ptr;
  public OfflineTts(AssetManager assetManager, OfflineTtsConfig config) {
    ptr = assetManager != null ? newFromAsset(assetManager, config) : newFromFile(config);
    if (ptr == 0L) throw new IllegalStateException("Configuration TTS invalide : moteur natif non créé");
  }
  public int sampleRate() { return getSampleRate(ptr); }
  public int numSpeakers() { return getNumSpeakers(ptr); }
  public GeneratedAudio generateWithConfig(String text, GenerationConfig config) { return generateWithConfigImpl(ptr, text, config, null); }
  public synchronized void release() { if (ptr != 0L) { delete(ptr); ptr = 0L; } }
  private native long newFromAsset(AssetManager assetManager, OfflineTtsConfig config);
  private native long newFromFile(OfflineTtsConfig config);
  private native void delete(long ptr);
  private native int getSampleRate(long ptr);
  private native int getNumSpeakers(long ptr);
  private native GeneratedAudio generateWithConfigImpl(long ptr, String text, GenerationConfig config, Object callback);
}
