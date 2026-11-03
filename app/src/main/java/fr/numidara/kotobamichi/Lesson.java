package fr.numidara.kotobamichi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Une leçon : un dialogue cible et une suite ordonnée de points à enseigner. */
public final class Lesson {
  public String id, title, theme, summary, after;
  public int level;
  public boolean builtin;
  public long created;
  public final List<Line> dialogue = new ArrayList<>();
  public final List<Item> items = new ArrayList<>();

  /** Un point de la leçon. kind "new" = présenté et reconstruit, "combo" = l'apprenant le construit. */
  public static final class Item {
    public String kind, jp, kana, fr, note;
    public boolean isCombo() { return "combo".equals(kind); }
    public String key() { return jp; }
  }

  /** Une réplique du dialogue ; A est le rôle de l'apprenant. */
  public static final class Line {
    public String spk, jp, kana, fr;
  }

  public static Lesson fromJson(JSONObject o) throws JSONException {
    Lesson l = new Lesson();
    l.id = o.getString("id");
    l.title = o.optString("title", "Leçon");
    l.theme = o.optString("theme", l.title);
    l.summary = o.optString("summary", "");
    l.after = o.optString("after", null);
    l.level = Math.max(1, Math.min(6, o.optInt("level", 1)));
    l.created = o.optLong("created", 0);
    JSONArray d = o.optJSONArray("dialogue");
    if (d != null) for (int i = 0; i < d.length(); i++) {
      JSONObject x = d.getJSONObject(i);
      Line ln = new Line();
      ln.spk = "B".equals(x.optString("spk")) ? "B" : "A";
      ln.jp = x.getString("jp");
      ln.kana = x.optString("kana", "");
      ln.fr = x.optString("fr", "");
      l.dialogue.add(ln);
    }
    JSONArray it = o.getJSONArray("items");
    for (int i = 0; i < it.length(); i++) {
      JSONObject x = it.getJSONObject(i);
      Item m = new Item();
      m.kind = "combo".equals(x.optString("kind")) ? "combo" : "new";
      m.jp = x.getString("jp").trim();
      m.kana = x.optString("kana", "").trim();
      m.fr = x.getString("fr").trim();
      m.note = x.optString("note", "").trim();
      l.items.add(m);
    }
    return l;
  }

  public JSONObject toJson() {
    try {
      JSONObject o = new JSONObject();
      o.put("id", id).put("title", title).put("theme", theme).put("summary", summary)
          .put("level", level).put("created", created);
      if (after != null) o.put("after", after);
      JSONArray d = new JSONArray();
      for (Line ln : dialogue) d.put(new JSONObject().put("spk", ln.spk).put("jp", ln.jp).put("kana", ln.kana).put("fr", ln.fr));
      o.put("dialogue", d);
      JSONArray it = new JSONArray();
      for (Item m : items) it.put(new JSONObject().put("kind", m.kind).put("jp", m.jp).put("kana", m.kana).put("fr", m.fr).put("note", m.note));
      o.put("items", it);
      return o;
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }
}
