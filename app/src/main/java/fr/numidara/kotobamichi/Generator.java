package fr.numidara.kotobamichi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Écrit une nouvelle leçon avec Claude (API Anthropic, clé de l'utilisatrice). */
public final class Generator {
  private Generator() {}

  public static String prompt(String theme, int level, Lesson prev, List<String> known) {
    StringBuilder b = new StringBuilder();
    b.append("Tu conçois une leçon audio de japonais selon la méthode Pimsleur, pour un adulte francophone qui l'écoute en conduisant, sans aucun support écrit.\n\n");
    b.append("Thème : ").append(theme).append('\n');
    b.append("Niveau ").append(level).append(" sur 6 (« ").append(Ui.LEVEL_NAMES[level]).append(" ») : ").append(Ui.LEVEL_DESC[level]).append('\n');
    if (prev != null) {
      b.append("Cette leçon fait suite à « ").append(prev.title).append(" » (niveau ").append(prev.level)
          .append(") sur le même thème : va plus loin, sans répéter ses expressions. Expressions déjà vues dans cette suite : ");
      for (Lesson.Item it : prev.items) b.append(it.jp).append(" / ");
      b.append('\n');
    }
    b.append("Vocabulaire déjà connu (à réutiliser librement dans les phrases à construire et le dialogue, sans le réenseigner) : ");
    b.append(known.isEmpty() ? "aucun" : String.join(" / ", known)).append("\n\n");
    b.append("Règles :\n"
        + "- 7 à 9 éléments \"new\" (mots ou courtes expressions utiles pour ce thème) et 4 à 6 éléments \"combo\" (phrases que l'apprenant construit lui-même à partir de ce qui a été enseigné juste avant ou déjà connu). Intercale-les : chaque combo vient après les éléments qu'il utilise.\n"
        + "- Un seul point de grammaire nouveau au maximum, adapté au niveau. Phrases courtes : 12 mores au plus pour un \"new\", 20 pour un \"combo\".\n"
        + "- \"jp\" : japonais naturel en kanji et kana usuels. Écris les nombres en kanji, jamais en chiffres arabes.\n"
        + "- \"kana\" : la lecture complète en hiragana et katakana, avec une espace entre les groupes de mots (bunsetsu), sans ponctuation.\n"
        + "- \"fr\" : l'indice que le narrateur dira, en français naturel et court, en minuscules, sans guillemets.\n"
        + "- \"note\" : facultative, une phrase courte en français sur la prononciation ou la grammaire, faite pour être dite à voix haute : aucun caractère japonais, uniquement du rōmaji entre guillemets « ».\n"
        + "- \"dialogue\" : 5 à 8 répliques alternant \"A\" (l'apprenant, un visiteur) et \"B\" (un Japonais), qui n'utilisent que ce qui est enseigné ou connu.\n"
        + "- \"title\" : 2 à 4 mots en français. \"summary\" : une phrase sur ce qu'on apprend.\n\n"
        + "Réponds uniquement avec un objet JSON de cette forme :\n"
        + "{\"title\":\"\",\"summary\":\"\",\"dialogue\":[{\"spk\":\"A\",\"jp\":\"\",\"kana\":\"\",\"fr\":\"\"}],\"items\":[{\"kind\":\"new\",\"jp\":\"\",\"kana\":\"\",\"fr\":\"\",\"note\":\"\"}]}");
    return b.toString();
  }

  /** Appel bloquant : à lancer hors du fil principal. */
  public static Lesson generate(String apiKey, String model, String theme, int level, Lesson prev, List<String> known) throws Exception {
    JSONObject body = new JSONObject()
        .put("model", model)
        .put("max_tokens", 8000)
        .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt(theme, level, prev, known))));
    HttpURLConnection c = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
    c.setRequestMethod("POST");
    c.setConnectTimeout(20000);
    c.setReadTimeout(240000);
    c.setDoOutput(true);
    c.setRequestProperty("content-type", "application/json");
    c.setRequestProperty("x-api-key", apiKey);
    c.setRequestProperty("anthropic-version", "2023-06-01");
    try (OutputStream o = c.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
    int code = c.getResponseCode();
    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
    String resp = in != null ? Store.read(in) : "";
    if (code == 401) throw new Exception("Clé API refusée. Vérifie-la dans les réglages.");
    if (code == 429) throw new Exception("Limite d'utilisation de l'API atteinte. Réessaie plus tard.");
    if (code == 404 || code == 400) {
      String msg = "";
      try { msg = new JSONObject(resp).getJSONObject("error").optString("message"); } catch (Exception ignored) {}
      throw new Exception("Requête refusée (" + code + ") : " + msg);
    }
    if (code >= 500) throw new Exception("Service Anthropic indisponible (" + code + "). Réessaie dans un instant.");
    JSONObject r = new JSONObject(resp);
    JSONArray content = r.getJSONArray("content");
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < content.length(); i++) {
      JSONObject b = content.getJSONObject(i);
      if ("text".equals(b.optString("type"))) text.append(b.optString("text"));
    }
    String s = text.toString();
    int a = s.indexOf('{'), z = s.lastIndexOf('}');
    if (a < 0 || z <= a) throw new Exception("La réponse ne contenait pas de leçon lisible. Relance la génération.");
    return validate(new JSONObject(s.substring(a, z + 1)), theme, level, prev);
  }

  static Lesson validate(JSONObject o, String theme, int level, Lesson prev) throws Exception {
    Lesson l = new Lesson();
    l.id = "g" + Long.toString(System.currentTimeMillis(), 36);
    l.title = o.optString("title", theme);
    if (l.title.length() > 60) l.title = l.title.substring(0, 60);
    l.theme = theme; l.level = level; l.summary = o.optString("summary", "");
    l.created = System.currentTimeMillis();
    if (prev != null) l.after = prev.id;
    JSONArray d = o.optJSONArray("dialogue");
    if (d != null) for (int i = 0; i < d.length(); i++) {
      JSONObject x = d.optJSONObject(i);
      if (x == null || x.optString("jp").isEmpty()) continue;
      Lesson.Line ln = new Lesson.Line();
      ln.spk = "B".equals(x.optString("spk")) ? "B" : "A";
      ln.jp = x.optString("jp");
      ln.kana = x.optString("kana", "").replaceAll("[、。？！?!]", " ").trim();
      ln.fr = x.optString("fr", "");
      l.dialogue.add(ln);
    }
    JSONArray it = o.optJSONArray("items");
    if (it != null) for (int i = 0; i < it.length(); i++) {
      JSONObject x = it.optJSONObject(i);
      if (x == null || x.optString("jp").isEmpty() || x.optString("fr").isEmpty()) continue;
      Lesson.Item m = new Lesson.Item();
      m.kind = "combo".equals(x.optString("kind")) ? "combo" : "new";
      m.jp = x.optString("jp").trim();
      String kana = x.optString("kana", "").replaceAll("[、。？！?!・,.]", " ").replaceAll("\\s+", " ").trim();
      m.kana = Kana.isKana(kana) ? kana : "";
      m.fr = x.optString("fr").trim();
      String note = x.optString("note", "").trim();
      m.note = note.matches(".*[\\u3040-\\u30ff\\u4e00-\\u9fff].*") ? "" : note;
      l.items.add(m);
    }
    if (l.items.size() < 5) throw new Exception("La leçon reçue était incomplète. Relance la génération.");
    return l;
  }
}
