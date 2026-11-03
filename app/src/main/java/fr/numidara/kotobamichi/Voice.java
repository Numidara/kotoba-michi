package fr.numidara.kotobamichi;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.GenerationConfig;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Moteur vocal embarqué : Supertonic 3 exécuté par sherpa-onnx, entièrement hors ligne.
 * Un seul modèle parle français et japonais ; 10 voix (sid 0 à 9).
 * Chaque énoncé est mis en cache sur disque en PCM 16 bits.
 */
public final class Voice {
  private static final String TAG = "KotobaVoice";
  private static Voice instance;
  public static synchronized Voice get(Context c) {
    if (instance == null) instance = new Voice(c.getApplicationContext());
    return instance;
  }

  public static final String DIR = "supertonic";
  private final Context ctx;
  private final File cacheDir;
  private final CountDownLatch ready = new CountDownLatch(1);
  private volatile OfflineTts tts;
  private volatile String error;
  private volatile int sampleRate = 44100, speakers = 10;
  private int[] indexer = new int[0];

  private Voice(Context c) {
    ctx = c;
    cacheDir = new File(c.getCacheDir(), "tts");
    cacheDir.mkdirs();
    Thread th = new Thread(this::load, "voice-load");
    th.setPriority(Thread.NORM_PRIORITY);
    th.start();
  }

  private void load() {
    try {
      AssetManager am = ctx.getAssets();
      OfflineTtsConfig cfg = new OfflineTtsConfig();
      cfg.model.supertonic.durationPredictor = DIR + "/duration_predictor.int8.onnx";
      cfg.model.supertonic.textEncoder = DIR + "/text_encoder.int8.onnx";
      cfg.model.supertonic.vectorEstimator = DIR + "/vector_estimator.int8.onnx";
      cfg.model.supertonic.vocoder = DIR + "/vocoder.int8.onnx";
      cfg.model.supertonic.ttsJson = DIR + "/tts.json";
      cfg.model.supertonic.unicodeIndexer = DIR + "/unicode_indexer.bin";
      cfg.model.supertonic.voiceStyle = DIR + "/voice.bin";
      cfg.model.numThreads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
      cfg.model.provider = "cpu";
      cfg.maxNumSentences = 1;
      cfg.silenceScale = 0.2f;
      loadIndexer(am);
      long t0 = System.currentTimeMillis();
      tts = new OfflineTts(am, cfg);
      sampleRate = tts.sampleRate();
      speakers = Math.max(1, tts.numSpeakers());
      Log.i(TAG, "Supertonic prêt en " + (System.currentTimeMillis() - t0) + " ms, " + speakers + " voix, " + sampleRate + " Hz");
    } catch (Throwable e) {
      error = e.getClass().getSimpleName() + " : " + e.getMessage();
      Log.e(TAG, "Chargement du moteur impossible", e);
    } finally {
      ready.countDown();
    }
  }

  /** Table caractère → identifiant du modèle, pour savoir s'il sait lire les kanji d'une phrase. */
  private void loadIndexer(AssetManager am) {
    try {
      byte[] all = readAll(am.open(DIR + "/unicode_indexer.bin"));
      ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
      int[] idx = new int[all.length / 4];
      for (int i = 0; i < idx.length; i++) idx[i] = bb.getInt();
      indexer = idx;
    } catch (Exception e) {
      Log.w(TAG, "Indexeur illisible, lecture en kana par défaut", e);
    }
  }

  private static byte[] readAll(InputStream in) throws Exception {
    try (InputStream is = in) {
      java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
      byte[] buf = new byte[65536]; int n;
      while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
      return bo.toByteArray();
    }
  }

  /** Vrai si chaque caractère (après décomposition NFKD) est connu du modèle. */
  public boolean covers(String text) {
    if (indexer.length == 0) return false;
    String n = Normalizer.normalize(text, Normalizer.Form.NFKD);
    for (int i = 0; i < n.length(); i++) {
      char ch = n.charAt(i);
      if (Character.isWhitespace(ch)) continue;
      if (ch >= indexer.length || indexer[ch] < 0) return false;
    }
    return true;
  }

  public boolean isReady() { return ready.getCount() == 0 && tts != null; }
  public String error() { return error; }
  public int sampleRate() { return sampleRate; }
  public int speakers() { return speakers; }
  public boolean awaitReady(long ms) {
    try { ready.await(ms, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
    return tts != null;
  }

  private static String hash(String s) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < 10; i++) b.append(String.format("%02x", d[i]));
      return b.toString();
    } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
  }

  /** Synthétise (ou relit du cache) un énoncé ; renvoie du PCM 16 bits mono. Appelé depuis un seul fil. */
  public synchronized short[] speak(String text, String lang, int sid, float speed, int steps) {
    if (!awaitReady(60000)) throw new IllegalStateException(error != null ? error : "moteur vocal indisponible");
    int s = Math.max(0, Math.min(speakers - 1, sid));
    String key = hash(lang + "|" + s + "|" + speed + "|" + steps + "|" + text);
    File f = new File(cacheDir, key + ".pcm");
    if (f.exists()) {
      try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
        byte[] b = new byte[(int) f.length()];
        in.readFully(b);
        short[] out = new short[b.length / 2];
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out);
        f.setLastModified(System.currentTimeMillis());
        return out;
      } catch (Exception ignored) { f.delete(); }
    }
    GenerationConfig gc = new GenerationConfig();
    gc.sid = s;
    gc.speed = speed;
    gc.numSteps = steps;
    gc.silenceScale = 0.2f;
    Map<String, String> extra = new HashMap<>();
    extra.put("lang", lang);
    gc.extra = extra;
    GeneratedAudio a = tts.generateWithConfig(text, gc);
    float[] x = a.samples != null ? a.samples : new float[0];
    short[] pcm = trim(x);
    try (FileOutputStream o = new FileOutputStream(f)) {
      ByteBuffer bb = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
      bb.asShortBuffer().put(pcm);
      o.write(bb.array());
    } catch (Exception ignored) {}
    return pcm;
  }

  /** Conversion en 16 bits et retrait des silences de début et de fin. */
  private short[] trim(float[] x) {
    int n = x.length, a = 0, b = n - 1;
    float th = 0.008f;
    while (a < n && Math.abs(x[a]) < th) a++;
    while (b > a && Math.abs(x[b]) < th) b--;
    int pad = sampleRate / 50;
    a = Math.max(0, a - pad); b = Math.min(n - 1, b + pad);
    if (b <= a) return new short[0];
    short[] out = new short[b - a + 1];
    for (int i = a; i <= b; i++) {
      float v = Math.max(-1f, Math.min(1f, x[i]));
      out[i - a] = (short) (v * 32767f);
    }
    return out;
  }

  /** Garde le cache sous ~300 Mo en supprimant les plus anciens. */
  public void pruneCache() {
    File[] fs = cacheDir.listFiles();
    if (fs == null) return;
    long total = 0;
    for (File f : fs) total += f.length();
    if (total < 300L * 1024 * 1024) return;
    Arrays.sort(fs, Comparator.comparingLong(File::lastModified));
    for (File f : fs) { if (total < 200L * 1024 * 1024) break; total -= f.length(); f.delete(); }
  }

  public long cacheBytes() {
    File[] fs = cacheDir.listFiles(); long t = 0;
    if (fs != null) for (File f : fs) t += f.length();
    return t;
  }
  public void clearCache() { File[] fs = cacheDir.listFiles(); if (fs != null) for (File f : fs) f.delete(); }
}
