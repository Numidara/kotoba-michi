package fr.numidara.kotobamichi;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Leçons (intégrées et générées), progression et reprise. Tout reste sur le téléphone. */
public final class Store {
  private static Store instance;
  public static synchronized Store get(Context c) {
    if (instance == null) instance = new Store(c.getApplicationContext());
    return instance;
  }

  private final Context ctx;
  private final File lessonsDir, progressFile;
  public final List<Lesson> foundation = new ArrayList<>();
  public final List<Lesson> mine = new ArrayList<>();
  private JSONObject progress;

  private Store(Context c) {
    ctx = c;
    lessonsDir = new File(c.getFilesDir(), "lessons");
    lessonsDir.mkdirs();
    progressFile = new File(c.getFilesDir(), "progress.json");
    load();
  }

  static String read(InputStream in) throws Exception {
    try (InputStream is = in) {
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      byte[] buf = new byte[16384]; int n;
      while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
      return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private static void write(File f, String s) throws Exception {
    File tmp = new File(f.getPath() + ".tmp");
    try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(s.getBytes(StandardCharsets.UTF_8)); }
    if (!tmp.renameTo(f)) throw new Exception("écriture impossible : " + f);
  }

  private void load() {
    try {
      JSONArray a = new JSONArray(read(ctx.getAssets().open("lessons/foundation.json")));
      for (int i = 0; i < a.length(); i++) { Lesson l = Lesson.fromJson(a.getJSONObject(i)); l.builtin = true; foundation.add(l); }
    } catch (Exception e) { throw new RuntimeException("Leçons intégrées illisibles", e); }
    File[] fs = lessonsDir.listFiles();
    if (fs != null) for (File f : fs) {
      if (!f.getName().endsWith(".json")) continue;
      try { mine.add(Lesson.fromJson(new JSONObject(read(new FileInputStream(f))))); } catch (Exception ignored) {}
    }
    Collections.sort(mine, (x, y) -> Long.compare(x.created, y.created));
    try { progress = progressFile.exists() ? new JSONObject(read(new FileInputStream(progressFile))) : new JSONObject(); }
    catch (Exception e) { progress = new JSONObject(); }
  }

  public List<Lesson> all() { List<Lesson> l = new ArrayList<>(foundation); l.addAll(mine); return l; }

  public Lesson byId(String id) { for (Lesson l : all()) if (l.id.equals(id)) return l; return null; }

  public void saveLesson(Lesson l) throws Exception {
    write(new File(lessonsDir, l.id + ".json"), l.toJson().toString(1));
    mine.removeIf(x -> x.id.equals(l.id));
    mine.add(l);
  }

  public void deleteLesson(String id) {
    new File(lessonsDir, id + ".json").delete();
    mine.removeIf(x -> x.id.equals(id));
  }

  private JSONObject sub(String k) {
    JSONObject o = progress.optJSONObject(k);
    if (o == null) { o = new JSONObject(); try { progress.put(k, o); } catch (Exception ignored) {} }
    return o;
  }

  public int doneCount(String id) { JSONObject d = sub("done").optJSONObject(id); return d == null ? 0 : d.optInt("count"); }

  public synchronized void markDone(Session s) {
    long now = System.currentTimeMillis();
    try {
      sub("done").put(s.lesson.id, new JSONObject().put("count", doneCount(s.lesson.id) + 1).put("last", now));
      JSONObject items = sub("items");
      for (Lesson.Item it : s.lesson.items) items.put(it.key(), now);
      for (Lesson.Item it : s.review) items.put(it.key(), now);
      write(progressFile, progress.toString());
    } catch (Exception ignored) {}
  }

  /** Expressions des leçons déjà terminées, les moins récemment travaillées d'abord. */
  public List<Lesson.Item> reviewFor(Lesson lesson, int durMin) {
    int n = Math.round(durMin / 5f);
    Set<String> seen = new HashSet<>();
    for (Lesson.Item it : lesson.items) seen.add(it.key());
    JSONObject items = sub("items");
    List<Object[]> pool = new ArrayList<>();
    for (Lesson l : all()) {
      if (l.id.equals(lesson.id) || doneCount(l.id) == 0) continue;
      for (Lesson.Item it : l.items) if (seen.add(it.key())) pool.add(new Object[]{it, items.optLong(it.key(), 0)});
    }
    Collections.sort(pool, (a, b) -> Long.compare((Long) a[1], (Long) b[1]));
    List<Object[]> head = new ArrayList<>(pool.subList(0, Math.min(pool.size(), n * 3)));
    Collections.shuffle(head);
    List<Lesson.Item> out = new ArrayList<>();
    for (int i = 0; i < Math.min(n, head.size()); i++) out.add((Lesson.Item) head.get(i)[0]);
    return out;
  }

  public Lesson nextLesson(String pinned) {
    if (pinned != null) { Lesson p = byId(pinned); if (p != null && doneCount(p.id) == 0) return p; }
    for (Lesson l : all()) if (doneCount(l.id) == 0) return l;
    return null;
  }

  public int suggestedLevel() {
    int best = 1;
    for (Lesson l : all()) if (doneCount(l.id) > 0) best = Math.max(best, l.level);
    int at = 0;
    for (Lesson l : all()) if (l.level == best && doneCount(l.id) > 0) at++;
    return Math.min(6, at >= 3 ? best + 1 : best);
  }

  /** Vocabulaire déjà vu, transmis à Claude pour les recombinaisons. */
  public List<String> knownVocab(int level) {
    Set<String> out = new java.util.LinkedHashSet<>();
    for (Lesson l : all()) if (doneCount(l.id) > 0 || l.level <= level)
      for (Lesson.Item it : l.items) if (!it.isCombo()) out.add(it.kana.isEmpty() ? it.jp : it.kana);
    List<String> list = new ArrayList<>(out);
    return list.size() > 160 ? list.subList(list.size() - 160, list.size()) : list;
  }

  // ---------- reprise d'une séance interrompue ----------
  private SharedPreferences rp() { return ctx.getSharedPreferences("resume", Context.MODE_PRIVATE); }
  public void saveResume(String id, long seed, int index, int dur, List<Lesson.Item> review) {
    JSONArray r = new JSONArray();
    for (Lesson.Item it : review) r.put(it.key());
    rp().edit().putString("id", id).putLong("seed", seed).putInt("i", index).putInt("dur", dur)
        .putString("review", r.toString()).putLong("at", System.currentTimeMillis()).apply();
  }
  public void clearResume() { rp().edit().clear().apply(); }
  public String resumeId() {
    long at = rp().getLong("at", 0);
    return System.currentTimeMillis() - at < 7L * 86400000L ? rp().getString("id", null) : null;
  }
  public long resumeSeed() { return rp().getLong("seed", 1); }
  public int resumeIndex() { return rp().getInt("i", 0); }
  public int resumeDur() { return rp().getInt("dur", 30); }
  public List<Lesson.Item> resumeReview() {
    List<Lesson.Item> out = new ArrayList<>();
    try {
      JSONArray r = new JSONArray(rp().getString("review", "[]"));
      java.util.Map<String, Lesson.Item> byKey = new java.util.HashMap<>();
      for (Lesson l : all()) for (Lesson.Item it : l.items) if (!byKey.containsKey(it.key())) byKey.put(it.key(), it);
      for (int i = 0; i < r.length(); i++) { Lesson.Item it = byKey.get(r.getString(i)); if (it != null) out.add(it); }
    } catch (Exception ignored) {}
    return out;
  }
}
