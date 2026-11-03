package fr.numidara.kotobamichi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Construit le déroulé d'une séance : écoute du dialogue, présentation des
 * nouveautés avec reconstruction depuis la fin, anticipation, rappels à
 * intervalles croissants, révisions des leçons passées, jeu de rôle, écoute finale.
 */
public final class Session {

  public static final int SAY = 0, GAP = 1;
  public static final int GAP_BEAT = 0, GAP_THINK = 1, GAP_REPEAT = 2;

  /** Une étape jouée par le lecteur. role : N narrateur (fr), A ou B voix japonaises. */
  public static final class Step {
    public int type, group;
    public char role;
    public String speak;          // texte envoyé au moteur
    public String lang;           // "fr" ou "ja"
    public String showJp, showKana, showFr;
    public boolean ask;
    public float est;             // durée estimée en secondes
    public int gapMs, gapKind;
  }

  public static final class Group {
    public final String label; public final int start;
    Group(String label, int start) { this.label = label; this.start = start; }
  }

  public final Lesson lesson;
  public final List<Step> steps = new ArrayList<>();
  public final List<Group> groups = new ArrayList<>();
  public final List<Lesson.Item> review;
  public float est;

  // Rappels espacés (s) : les 5 s, 25 s, 2 min, 10 min… de Pimsleur, adaptés à une séance
  private static final int[] INTERVALS = {10, 30, 120, 330, 720, 1320};

  private final Settings S;
  private final Random R;
  private final JpText jpText;
  private int g = -1, alt = 0;
  private float t = 0;

  /** Choisit pour le moteur la forme écrite d'une phrase japonaise (kanji ou kana). */
  public interface JpText { String pick(String jp, String kana); }

  public Session(Lesson lesson, List<Lesson.Item> review, long seed, int durMin, Settings s, JpText jpText) {
    this.lesson = lesson; this.review = review; this.S = s; this.R = new Random(seed); this.jpText = jpText;
    build(durMin * 60f);
  }

  // ---------- primitives ----------
  private String pick(String... a) { return a[R.nextInt(a.length)]; }
  private void group(String label) { g = groups.size(); groups.add(new Group(label, steps.size())); }
  private float estFr(String t) { return 0.5f + t.length() / 14f / S.frSpeed; }
  private float estJa(String k) { return 0.45f + Math.max(1, Kana.count(k)) / 6.2f / S.jaSpeed; }
  private int thinkMs(int m) { return Math.round((2400 + 300 * m) * S.think); }
  private int repeatMs(int m) { return 1300 + 230 * m; }
  private char voice() { return (alt++ % 2 == 0) ? 'A' : 'B'; }

  private void fr(String text, String showFr, boolean ask) {
    Step s = new Step(); s.type = SAY; s.role = 'N'; s.lang = "fr"; s.speak = text; s.group = g;
    s.showFr = showFr != null ? showFr : text; s.ask = ask; s.est = estFr(text);
    steps.add(s); t += s.est;
  }
  private void fr(String text) { fr(text, null, false); }

  private void ja(String jp, String kana, String gloss, char role) {
    Step s = new Step(); s.type = SAY; s.role = role; s.lang = "ja"; s.group = g;
    s.speak = jpText.pick(jp, kana); s.showJp = jp; s.showKana = kana; s.showFr = gloss;
    s.est = estJa(kana != null && !kana.isEmpty() ? kana : jp);
    steps.add(s); t += s.est;
  }
  private void ja(Lesson.Item it, char role) { ja(it.jp, it.kana, it.fr, role); }

  private void frag(String kana) {
    Step s = new Step(); s.type = SAY; s.role = voice(); s.lang = "ja"; s.group = g;
    s.speak = kana.replaceAll("\\s+", ""); s.showJp = "…" + kana; s.showKana = ""; s.showFr = "";
    s.est = estJa(kana); steps.add(s); t += s.est;
  }

  private void gap(int ms, int kind) {
    Step s = new Step(); s.type = GAP; s.gapMs = ms; s.gapKind = kind; s.group = g; s.est = ms / 1000f;
    steps.add(s); t += s.est;
  }

  private static int mor(Lesson.Item it) { return Kana.count(it.kana != null && !it.kana.isEmpty() ? it.kana : it.jp); }

  // ---------- séquences ----------
  private void teach(Lesson.Item it) {
    int m = mor(it);
    if (it.isCombo()) {
      group("À vous de construire");
      fr(pick("Comment diriez-vous", "Essayez de dire", "Et maintenant, essayez de dire", "Avec ce que vous savez, dites")
          + " « " + it.fr + " » ?", it.fr, true);
      gap(thinkMs(m) + 800, GAP_THINK);
      ja(it, voice()); gap(repeatMs(m), GAP_REPEAT);
      if (!it.note.isEmpty()) fr(it.note);
      ja(it, voice()); gap(repeatMs(m), GAP_REPEAT);
      return;
    }
    group("Nouvelle expression");
    fr(pick("Voici comment on dit", "Écoutez comment on dit", "Maintenant,", "Voici") + " « " + it.fr + " » :", it.fr, false);
    ja(it, 'A'); gap(700, GAP_BEAT); ja(it, 'B'); gap(500, GAP_BEAT);
    if (!it.note.isEmpty()) fr(it.note);
    List<String> bu = Kana.isKana(it.kana) ? Kana.buildUp(it.kana) : new ArrayList<>();
    if (bu.size() > 1) {
      fr(pick("Répétez, en partant de la fin :", "On le reconstruit depuis la fin. Répétez :", "Morceau par morceau, depuis la fin :"));
      for (int i = 0; i < bu.size() - 1; i++) { frag(bu.get(i)); gap(repeatMs(Kana.count(bu.get(i))), GAP_REPEAT); }
      fr(pick("Et maintenant, tout :", "Le tout :", "En entier :"));
    } else {
      fr(pick("Répétez :", "À vous, répétez :", "Dites-le :"));
    }
    ja(it, voice()); gap(repeatMs(m), GAP_REPEAT);
    ja(it, voice()); gap(repeatMs(m), GAP_REPEAT);
  }

  private void drill(Lesson.Item it, int stage, boolean isReview) {
    int m = mor(it);
    group(isReview ? "Révision" : "Rappel");
    String q = isReview ? pick("Vous vous souvenez ? Comment dit-on", "D'une leçon précédente : comment dit-on", "Et ça, vous le savez encore ?")
        : stage == 0 ? pick("Comment dit-on", "Dites", "Comment diriez-vous")
        : pick("Comment dit-on", "Dites", "Encore une fois,", "Et", "Rappelez-vous :", "Vite :");
    fr(q + " « " + it.fr + " » ?", it.fr, true);
    gap(thinkMs(m) + (isReview ? 600 : 0), GAP_THINK);
    ja(it, voice()); gap(repeatMs(m), GAP_REPEAT);
    if (stage <= 1 || isReview) { ja(it, voice()); gap(repeatMs(m), GAP_REPEAT); }
  }

  private static final class Q {
    Lesson.Item it; int stage; float due; boolean review; int slot = -1;
    Q(Lesson.Item it, int stage, float due, boolean review) { this.it = it; this.stage = stage; this.due = due; this.review = review; }
  }

  private void build(float D) {
    List<Lesson.Item> items = lesson.items;

    group("Écoute");
    fr(lesson.title + ". " + pick("Écoutez cette conversation. Vous n'avez pas besoin de tout comprendre.",
        "Voici la conversation de cette leçon. Laissez-la venir, sans chercher à tout saisir.",
        "Commençons par écouter une courte conversation."));
    gap(600, GAP_BEAT);
    for (Lesson.Line ln : lesson.dialogue) { ja(ln.jp, ln.kana, ln.fr, ln.spk.charAt(0)); gap(450, GAP_BEAT); }
    gap(900, GAP_BEAT);
    float closingEst = 30 + lesson.dialogue.size() * 9;

    List<Q> queue = new ArrayList<>();
    List<Lesson.Item> reviewQ = new ArrayList<>(review);
    float endTeach = D - closingEst;
    int i = 0, guard = 0;
    Comparator<Q> byDue = (a, b) -> Float.compare(a.due, b.due);
    while (guard++ < 600) {
      Q due = null;
      for (Q q : queue) if (q.due <= t && (due == null || q.due < due.due)) due = q;
      if (due != null) { drill(due.it, due.stage, due.review); next(queue, due); continue; }
      if (i < items.size()) {
        final int slot = i;
        boolean slotUsed = false;
        for (Q q : queue) if (q.slot == slot) { slotUsed = true; break; }
        if (!reviewQ.isEmpty() && i > 0 && i % 3 == 0 && !slotUsed) {
          Lesson.Item r = reviewQ.remove(0); drill(r, 0, true);
          Q q = new Q(r, 3, t + INTERVALS[3], true); q.slot = slot; queue.add(q); continue;
        }
        Lesson.Item it = items.get(i++); teach(it); queue.add(new Q(it, 0, t + INTERVALS[0], false)); continue;
      }
      if (t >= endTeach) break;
      if (!reviewQ.isEmpty()) { Lesson.Item r = reviewQ.remove(0); drill(r, 0, true); queue.add(new Q(r, 3, t + INTERVALS[3], true)); continue; }
      if (!queue.isEmpty()) { Q q = Collections.min(queue, byDue); drill(q.it, q.stage, q.review); next(queue, q); continue; }
      // Tout est consolidé et il reste du temps : un nouveau tour dans le désordre
      List<Lesson.Item> sh = new ArrayList<>(items);
      Collections.shuffle(sh, R);
      for (int k = 0; k < sh.size(); k++) queue.add(new Q(sh.get(k), 4, t + k * 20, false));
    }

    if (!lesson.dialogue.isEmpty()) {
      group("Jeu de rôle");
      fr(pick("Jouons la conversation. Vous prenez le premier rôle : je vous souffle chaque réplique en français, vous la dites en japonais.",
          "À vous de jouer la conversation. Je vous souffle vos répliques en français ; répondez en japonais."));
      gap(600, GAP_BEAT);
      for (Lesson.Line ln : lesson.dialogue) {
        if ("A".equals(ln.spk)) {
          fr(ln.fr, ln.fr, true);
          gap(thinkMs(Kana.count(ln.kana.isEmpty() ? ln.jp : ln.kana)), GAP_THINK);
          ja(ln.jp, ln.kana, ln.fr, 'A'); gap(700, GAP_BEAT);
        } else { ja(ln.jp, ln.kana, ln.fr, 'B'); gap(500, GAP_BEAT); }
      }
      group("Écoute finale");
      fr(pick("Écoutez une dernière fois la conversation. Cette fois, vous la comprenez.",
          "Réécoutons la conversation du début. Remarquez tout ce que vous comprenez maintenant."));
      gap(500, GAP_BEAT);
      for (Lesson.Line ln : lesson.dialogue) { ja(ln.jp, ln.kana, ln.fr, ln.spk.charAt(0)); gap(450, GAP_BEAT); }
    }
    group("Fin");
    fr(pick("C'est la fin de cette leçon. Bonne route !", "Leçon terminée. Bravo, et bonne route !", "Fin de la leçon. À la prochaine !"));
    est = t;
  }

  private void next(List<Q> queue, Q q) {
    q.stage++;
    if (q.stage >= INTERVALS.length) queue.remove(q); else q.due = t + INTERVALS[q.stage];
  }

  /** Temps estimé écoulé avant l'étape i. */
  public float elapsedAt(int i) {
    float e = 0;
    for (int k = 0; k < Math.min(i, steps.size()); k++) e += steps.get(k).est;
    return e;
  }

  /** Script lisible de la séance, pour le relire à l'arrêt. */
  public String script() {
    StringBuilder b = new StringBuilder();
    b.append("EXPRESSIONS\n");
    for (Lesson.Item it : lesson.items) b.append("• ").append(it.jp).append("  (").append(it.kana).append(")  ").append(it.fr).append(it.isCombo() ? "  [à construire]" : "").append('\n');
    int last = -1;
    for (Step s : steps) {
      if (s.group != last) { last = s.group; b.append("\n— ").append(groups.get(s.group).label.toUpperCase()).append(" —\n"); }
      if (s.type == GAP) { if (s.gapKind != GAP_BEAT) b.append(s.gapKind == GAP_THINK ? "   (silence pour répondre)\n" : "   (silence pour répéter)\n"); }
      else if (s.role == 'N') b.append(s.speak).append('\n');
      else b.append("   ").append(s.showJp).append('\n');
    }
    return b.toString();
  }
}
