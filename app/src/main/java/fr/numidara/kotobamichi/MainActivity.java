package fr.numidara.kotobamichi;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public final class MainActivity extends Activity {
  private static final String[] THEMES = {"Prendre le shinkansen", "À l'hôtel", "Faire les courses", "Chez le médecin",
      "La météo", "Au travail", "Au dojo", "Inviter un ami", "Au konbini", "Les loisirs"};

  private final Handler ui = new Handler(Looper.getMainLooper());
  private Settings S;
  private Store store;
  private Voice voice;
  private LinearLayout root;
  private TextView engineLine, genStatus, levelDesc;
  private EditText themeIn;
  private LinearLayout levelRow;
  private Button genBtn;
  private int genLevel = 1;
  private boolean generating;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(Ui.NIGHT);
    getWindow().setNavigationBarColor(Ui.NIGHT);
    S = new Settings(this);
    store = Store.get(this);
    voice = Voice.get(this);
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    genLevel = store.suggestedLevel();
    ScrollView sv = new ScrollView(this);
    sv.setBackgroundColor(Ui.NIGHT);
    sv.setFillViewport(true);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    int p = Ui.dp(this, 16);
    root.setPadding(p, Ui.dp(this, 20), p, Ui.dp(this, 40));
    sv.addView(root);
    setContentView(sv);
    sv.setOnApplyWindowInsetsListener((v, insets) -> {
      v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
      return insets;
    });
  }

  @Override protected void onResume() {
    super.onResume();
    S = new Settings(this);
    render();
    pollEngine();
  }

  private void pollEngine() {
    if (engineLine == null) return;
    if (voice.isReady()) engineLine.setText("Voix embarquée prête · " + voice.speakers() + " voix, hors ligne");
    else if (voice.error() != null) { engineLine.setText("Moteur vocal indisponible : " + voice.error()); engineLine.setTextColor(Ui.ERR); }
    else { engineLine.setText("Chargement de la voix embarquée…"); ui.postDelayed(this::pollEngine, 500); }
  }

  private void render() {
    root.removeAllViews();
    Context c = this;

    // En-tête
    LinearLayout head = new LinearLayout(c);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    TextView kanji = Ui.serif(c, "言\n葉\n道", 26);
    kanji.setLineSpacing(0, 0.95f);
    kanji.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0);
    kanji.setBackground(leftBar(c));
    head.addView(kanji);
    LinearLayout ht = new LinearLayout(c);
    ht.setOrientation(LinearLayout.VERTICAL);
    ht.setPadding(Ui.dp(c, 12), 0, 0, 0);
    ht.addView(Ui.serif(c, "Kotoba Michi", 28));
    TextView sub = Ui.text(c, "Des leçons de japonais à écouter en conduisant, sur le principe Pimsleur : écouter, anticiper, répondre à voix haute.", 14, Ui.SOFT, false);
    ht.addView(sub);
    head.addView(ht, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    root.addView(head);
    engineLine = Ui.text(c, "", 13, Ui.DIM, false);
    root.addView(engineLine, Ui.lpTop(c, 10));

    // Reprise
    String rid = store.resumeId();
    Lesson rl = rid != null ? store.byId(rid) : null;
    if (rl != null && (PlayerService.current == null || PlayerService.current.session == null)) {
      LinearLayout r = new LinearLayout(c);
      r.setOrientation(LinearLayout.HORIZONTAL);
      r.setGravity(Gravity.CENTER_VERTICAL);
      int pp = Ui.dp(c, 14);
      r.setPadding(pp, Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
      r.setBackground(Ui.round(0xFF2A2052, Ui.SHU2, Ui.dp(c, 14), Ui.dp(c, 1)));
      r.addView(Ui.text(c, "Leçon interrompue : " + rl.title, 14, Ui.PAPER, false), new LinearLayout.LayoutParams(0, -2, 1));
      Button rb = Ui.ghost(c, "Reprendre");
      rb.setOnClickListener(v -> launch(rl, store.resumeDur(), store.resumeSeed(), store.resumeIndex(), store.resumeReview()));
      r.addView(rb);
      root.addView(r, Ui.lpTop(c, 16));
    } else if (PlayerService.current != null && PlayerService.current.session != null) {
      Button back = Ui.ghost(c, "Retour à la lecture en cours : " + PlayerService.current.session.lesson.title);
      back.setOnClickListener(v -> startActivity(new Intent(c, PlayerActivity.class)));
      root.addView(back, Ui.lpTop(c, 16));
    }

    // Prochaine leçon
    Lesson next = store.nextLesson(getSharedPreferences("ui", MODE_PRIVATE).getString("pinned", null));
    List<Lesson> all = store.all();
    Lesson nx = next != null ? next : all.get(all.size() - 1);
    LinearLayout nc = Ui.card(c);
    nc.addView(Ui.eyebrow(c, next != null ? "Prochaine leçon" : "Tout est fait · réécoute, ou génère la suite"));
    TextView nt = Ui.serif(c, nx.title, 24);
    nc.addView(nt, Ui.lpTop(c, 8));
    nc.addView(Ui.text(c, "Niveau " + nx.level + " · " + Ui.LEVEL_NAMES[nx.level] + " · " + (nx.summary.isEmpty() ? nx.theme : nx.summary), 14, Ui.SOFT, false), Ui.lpTop(c, 4));
    LinearLayout dur = new LinearLayout(c);
    dur.setOrientation(LinearLayout.HORIZONTAL);
    for (int d : new int[]{20, 30, 45}) {
      TextView ch = Ui.chip(c, d + " min", S.dur == d, Ui.PAPER, Ui.NIGHT);
      ch.setOnClickListener(v -> { S.dur = d; S.save(); render(); });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.rightMargin = Ui.dp(c, 8);
      dur.addView(ch, lp);
    }
    nc.addView(dur, Ui.lpTop(c, 14));
    Button go = Ui.primary(c, "▶  Démarrer et prendre la route");
    go.setOnClickListener(v -> launch(nx, S.dur, System.currentTimeMillis(), 0, null));
    nc.addView(go, Ui.lpTop(c, 14));
    nc.addView(Ui.text(c, "Lance la leçon, pose le téléphone et pars : la lecture continue écran éteint et se commande depuis le volant ou le casque. Réponds à voix haute pendant les silences.", 13, Ui.DIM, false), Ui.lpTop(c, 12));
    root.addView(nc, Ui.lpTop(c, 18));

    // Génération
    LinearLayout gc = Ui.card(c);
    gc.addView(Ui.eyebrow(c, "Nouvelle leçon"));
    gc.addView(Ui.serif(c, "Générer un thème", 22), Ui.lpTop(c, 6));
    themeIn = new EditText(c);
    themeIn.setHint("ex. prendre le shinkansen, réserver un ryokan…");
    themeIn.setHintTextColor(Ui.DIM);
    themeIn.setTextColor(Ui.PAPER);
    themeIn.setSingleLine(true);
    themeIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    int ip = Ui.dp(c, 12);
    themeIn.setPadding(ip, ip, ip, ip);
    themeIn.setBackground(Ui.round(0xFF191C42, Ui.LINE, Ui.dp(c, 12), Ui.dp(c, 1)));
    gc.addView(themeIn, Ui.lpTop(c, 12));
    HorizontalScrollView hs = new HorizontalScrollView(c);
    hs.setHorizontalScrollBarEnabled(false);
    LinearLayout ideas = new LinearLayout(c);
    for (String th : THEMES) {
      TextView ch = Ui.chip(c, th, false, 0, 0);
      ch.setOnClickListener(v -> themeIn.setText(th));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.rightMargin = Ui.dp(c, 8);
      ideas.addView(ch, lp);
    }
    hs.addView(ideas);
    gc.addView(hs, Ui.lpTop(c, 10));
    gc.addView(Ui.text(c, "Niveau", 14, Ui.SOFT, false), Ui.lpTop(c, 14));
    levelRow = new LinearLayout(c);
    gc.addView(levelRow, Ui.lpTop(c, 6));
    levelDesc = Ui.text(c, "", 14, Ui.SOFT, false);
    gc.addView(levelDesc, Ui.lpTop(c, 8));
    renderLevels();
    genBtn = Ui.primary(c, generating ? "Génération en cours…" : "Générer la leçon");
    genBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
    genBtn.setEnabled(!generating);
    genBtn.setOnClickListener(v -> generate(themeIn.getText().toString(), genLevel, null));
    gc.addView(genBtn, Ui.lpTop(c, 14));
    genStatus = Ui.text(c, new KeyVault(c).has() ? "" : "Ajoute ta clé API Anthropic dans les réglages pour générer des leçons.", 14, Ui.SOFT, false);
    gc.addView(genStatus, Ui.lpTop(c, 10));
    root.addView(gc, Ui.lpTop(c, 18));

    // Bibliothèque
    LinearLayout lib = Ui.card(c);
    lib.addView(Ui.eyebrow(c, "Bibliothèque"));
    if (!store.mine.isEmpty()) {
      lib.addView(sectionLabel(c, "Mes leçons"));
      for (int i = store.mine.size() - 1; i >= 0; i--) lib.addView(row(c, store.mine.get(i)));
    }
    lib.addView(sectionLabel(c, "Fondations"));
    for (Lesson l : store.foundation) lib.addView(row(c, l));
    if (!store.mine.isEmpty()) lib.addView(Ui.text(c, "Appui long sur une de tes leçons pour la supprimer.", 13, Ui.DIM, false), Ui.lpTop(c, 10));
    root.addView(lib, Ui.lpTop(c, 18));

    Button settings = Ui.ghost(c, "Voix, rythme et clé API");
    settings.setOnClickListener(v -> startActivity(new Intent(c, SettingsActivity.class)));
    root.addView(settings, Ui.lpTop(c, 18));
  }

  private android.graphics.drawable.Drawable leftBar(Context c) {
    android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
        Ui.round(Ui.SHU, Ui.SHU, 0, 0), Ui.round(Ui.NIGHT, Ui.NIGHT, 0, 0)});
    ld.setLayerInset(1, Ui.dp(c, 4), 0, 0, 0);
    return ld;
  }

  private TextView sectionLabel(Context c, String s) {
    TextView t = Ui.text(c, s.toUpperCase(), 12, Ui.DIM, true);
    t.setLetterSpacing(0.1f);
    t.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 4));
    return t;
  }

  private View row(Context c, Lesson l) {
    int n = store.doneCount(l.id);
    LinearLayout r = new LinearLayout(c);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
    TextView lv = Ui.text(c, n > 0 ? "✓" : String.valueOf(l.level), 15, n > 0 ? Ui.OK : Ui.SOFT, true);
    lv.setGravity(Gravity.CENTER);
    lv.setBackground(Ui.round(0x00000000, n > 0 ? Ui.OK : Ui.LINE, Ui.dp(c, 20), Ui.dp(c, 2)));
    r.addView(lv, new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
    LinearLayout t = new LinearLayout(c);
    t.setOrientation(LinearLayout.VERTICAL);
    t.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
    t.addView(Ui.text(c, l.title, 16, Ui.PAPER, true));
    t.addView(Ui.text(c, "Niv. " + l.level + " · " + (l.summary.isEmpty() ? l.theme : l.summary) + (n > 0 ? " · écoutée " + n + " fois" : ""), 13, Ui.SOFT, false));
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    TextView script = iconBtn(c, "≡", false);
    script.setContentDescription("Script de " + l.title);
    script.setOnClickListener(v -> showScript(l));
    r.addView(script, iconLp(c));
    if (!l.builtin) {
      TextView more = iconBtn(c, "+", false);
      more.setContentDescription("Générer la suite de " + l.title);
      more.setOnClickListener(v -> generate(l.theme, Math.min(6, l.level + 1), l));
      r.addView(more, iconLp(c));
      r.setOnLongClickListener(v -> {
        new AlertDialog.Builder(c).setTitle("Supprimer « " + l.title + " » ?")
            .setMessage("La leçon et sa progression disparaîtront de la bibliothèque.")
            .setPositiveButton("Supprimer", (d, w) -> { store.deleteLesson(l.id); render(); })
            .setNegativeButton("Annuler", null).show();
        return true;
      });
    }
    TextView play = iconBtn(c, "▶", true);
    play.setContentDescription("Écouter " + l.title);
    play.setOnClickListener(v -> launch(l, S.dur, System.currentTimeMillis(), 0, null));
    r.addView(play, iconLp(c));
    return r;
  }

  private LinearLayout.LayoutParams iconLp(Context c) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44));
    lp.leftMargin = Ui.dp(c, 6);
    return lp;
  }

  private TextView iconBtn(Context c, String glyph, boolean solid) {
    TextView t = Ui.text(c, glyph, 18, solid ? Ui.NIGHT : Ui.PAPER, true);
    t.setGravity(Gravity.CENTER);
    t.setBackground(solid ? Ui.round(Ui.PAPER, Ui.PAPER, Ui.dp(c, 12), 0) : Ui.round(0x00000000, Ui.LINE, Ui.dp(c, 12), Ui.dp(c, 1)));
    t.setClickable(true);
    t.setFocusable(true);
    return t;
  }

  private void renderLevels() {
    levelRow.removeAllViews();
    for (int lv = 1; lv <= 6; lv++) {
      final int L = lv;
      TextView b = Ui.chip(this, String.valueOf(lv), lv == genLevel, Ui.GOLD, Ui.NIGHT);
      b.setTypeface(Typeface.DEFAULT_BOLD);
      b.setContentDescription("Niveau " + lv + ", " + Ui.LEVEL_NAMES[lv]);
      b.setOnClickListener(v -> { genLevel = L; renderLevels(); });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1);
      lp.rightMargin = lv < 6 ? Ui.dp(this, 6) : 0;
      levelRow.addView(b, lp);
    }
    levelDesc.setText(Ui.LEVEL_NAMES[genLevel] + " : " + Ui.LEVEL_DESC[genLevel]);
  }

  private void showScript(Lesson l) {
    Session s = new Session(l, store.reviewFor(l, S.dur), 7, S.dur, S, jpPicker(this, S));
    ScrollView sv = new ScrollView(this);
    TextView t = Ui.text(this, s.script(), 14, Ui.PAPER, false);
    t.setTextIsSelectable(true);
    int p = Ui.dp(this, 18);
    t.setPadding(p, p, p, p);
    sv.addView(t);
    sv.setBackgroundColor(Ui.NIGHT2);
    new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_NoActionBar)
        .setTitle(l.title + " · ≈ " + Ui.fmt(s.est)).setView(sv).setPositiveButton("Fermer", null).show();
  }

  /** Lit les phrases en kanji quand le modèle connaît tous leurs caractères, sinon en kana. */
  static Session.JpText jpPicker(Context c, Settings s) {
    Voice v = Voice.get(c);
    return (jp, kana) -> {
      boolean hasKana = kana != null && !kana.isEmpty();
      if (s.kanji && v.covers(jp)) return jp;
      return hasKana ? kana.replaceAll("\\s+", " ") : jp;
    };
  }

  private void launch(Lesson l, int dur, long seed, int index, List<Lesson.Item> review) {
    if (voice.error() != null) {
      new AlertDialog.Builder(this).setTitle("Moteur vocal indisponible").setMessage(voice.error()).setPositiveButton("OK", null).show();
      return;
    }
    Context c = this;
    new Thread(() -> {
      voice.awaitReady(60000);   // le choix kanji/kana dépend de la table du modèle
      List<Lesson.Item> rev = review != null ? review : store.reviewFor(l, dur);
      Session s = new Session(l, rev, seed, dur, S, jpPicker(c, S));
      ui.post(() -> {
        PlayerService.start(c, s, seed, index, dur);
        startActivity(new Intent(c, PlayerActivity.class));
      });
    }, "km-build").start();
  }

  private void generate(String theme, int level, Lesson prev) {
    theme = theme == null ? "" : theme.trim();
    if (theme.isEmpty()) { genStatus.setTextColor(Ui.ERR); genStatus.setText("Indique un thème, ou touche une des idées."); return; }
    String key = new KeyVault(this).get();
    if (key == null) {
      genStatus.setTextColor(Ui.ERR);
      genStatus.setText("Ajoute d'abord ta clé API Anthropic dans « Voix, rythme et clé API ».");
      return;
    }
    generating = true;
    genBtn.setEnabled(false);
    genBtn.setText("Génération en cours…");
    genStatus.setTextColor(Ui.SOFT);
    genStatus.setText("Claude écrit « " + theme + " », niveau " + level + "… (environ une minute)");
    final String th = theme;
    List<String> known = store.knownVocab(level);
    new Thread(() -> {
      String msg; boolean ok = false;
      try {
        Lesson l = Generator.generate(key, S.model, th, level, prev, known);
        store.saveLesson(l);
        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("pinned", l.id).apply();
        msg = "« " + l.title + " » est prête : " + l.items.size() + " expressions. Elle est la prochaine leçon.";
        ok = true;
      } catch (java.net.UnknownHostException e) {
        msg = "Pas de connexion internet. La génération a besoin du réseau ; les leçons, elles, s'écoutent hors ligne.";
      } catch (Exception e) {
        msg = e.getMessage() != null ? e.getMessage() : "La génération n'a pas abouti.";
      }
      final String m = msg; final boolean success = ok;
      ui.post(() -> {
        generating = false;
        if (success) render();
        genBtn.setEnabled(true);
        genBtn.setText("Générer la leçon");
        genStatus.setTextColor(success ? Ui.OK : Ui.ERR);
        genStatus.setText(m);
      });
    }, "km-gen").start();
  }
}
