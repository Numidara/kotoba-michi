package fr.numidara.kotobamichi;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
  private final Handler ui = new Handler(Looper.getMainLooper());
  private Settings S;
  private LinearLayout root;
  private TextView testStatus;
  private AudioTrack testTrack;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(Ui.NIGHT);
    getWindow().setNavigationBarColor(Ui.NIGHT);
    S = new Settings(this);
    ScrollView sv = new ScrollView(this);
    sv.setBackgroundColor(Ui.NIGHT);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    int p = Ui.dp(this, 16);
    root.setPadding(p, Ui.dp(this, 20), p, Ui.dp(this, 40));
    sv.addView(root);
    sv.setOnApplyWindowInsetsListener((v, in) -> { v.setPadding(0, in.getSystemWindowInsetTop(), 0, in.getSystemWindowInsetBottom()); return in; });
    setContentView(sv);
    render();
  }

  @Override protected void onPause() { super.onPause(); S.save(); }

  private void render() {
    root.removeAllViews();
    Context c = this;
    root.addView(Ui.serif(c, "Voix et rythme", 26));

    LinearLayout voices = Ui.card(c);
    voices.addView(Ui.eyebrow(c, "Voix embarquées"));
    voices.addView(Ui.text(c, "Le moteur Supertonic propose 10 voix qui parlent toutes français et japonais. Choisis-en une pour le narrateur et deux pour les dialogues.", 14, Ui.SOFT, false), Ui.lpTop(c, 6));
    voiceRow(voices, "Narrateur (français)", 'N');
    voiceRow(voices, "Voix japonaise 1", 'A');
    voiceRow(voices, "Voix japonaise 2", 'B');
    testStatus = Ui.text(c, "", 13, Ui.DIM, false);
    voices.addView(testStatus, Ui.lpTop(c, 8));
    root.addView(voices, Ui.lpTop(c, 16));

    LinearLayout rhythm = Ui.card(c);
    rhythm.addView(Ui.eyebrow(c, "Rythme"));
    choice(rhythm, "Débit du japonais", new String[]{"Lent", "Posé", "Naturel"}, new float[]{0.75f, 0.9f, 1.05f}, S.jaSpeed, v -> S.jaSpeed = v);
    choice(rhythm, "Débit du narrateur", new String[]{"Posé", "Normal", "Rapide"}, new float[]{0.9f, 1.0f, 1.15f}, S.frSpeed, v -> S.frSpeed = v);
    choice(rhythm, "Temps pour répondre", new String[]{"Court", "Normal", "Long", "Très long"}, new float[]{0.8f, 1f, 1.4f, 1.8f}, S.think, v -> S.think = v);
    choice(rhythm, "Qualité de la voix", new String[]{"Rapide", "Standard", "Haute"}, new float[]{3, 5, 8}, S.quality, v -> S.quality = Math.round(v));
    rhythm.addView(Ui.text(c, "La qualité haute demande plus de calcul au téléphone pour préparer chaque phrase.", 13, Ui.DIM, false), Ui.lpTop(c, 6));
    CheckBox kanji = check(c, "Lire les phrases à partir des kanji (sinon en kana)", S.kanji);
    kanji.setOnCheckedChangeListener((v, on) -> { S.kanji = on; S.save(); });
    rhythm.addView(kanji, Ui.lpTop(c, 12));
    CheckBox show = check(c, "Afficher le texte pendant la lecture", S.showText);
    show.setOnCheckedChangeListener((v, on) -> { S.showText = on; S.save(); });
    rhythm.addView(show, Ui.lpTop(c, 4));
    root.addView(rhythm, Ui.lpTop(c, 16));

    LinearLayout api = Ui.card(c);
    api.addView(Ui.eyebrow(c, "Génération par Claude"));
    KeyVault kv = new KeyVault(c);
    api.addView(Ui.text(c, kv.has() ? "Une clé API est enregistrée, chiffrée par le Keystore du téléphone. Saisis-en une nouvelle pour la remplacer." :
        "Colle ta clé API Anthropic (console.anthropic.com). Elle est chiffrée par le Keystore du téléphone et n'est envoyée qu'à api.anthropic.com.", 14, Ui.SOFT, false), Ui.lpTop(c, 6));
    EditText key = field(c, "sk-ant-…", "");
    key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    api.addView(key, Ui.lpTop(c, 10));
    api.addView(Ui.text(c, "Modèle", 14, Ui.SOFT, false), Ui.lpTop(c, 12));
    EditText model = field(c, "claude-sonnet-5-5", S.model);
    api.addView(model, Ui.lpTop(c, 6));
    LinearLayout row = new LinearLayout(c);
    Button save = Ui.ghost(c, "Enregistrer");
    TextView saved = Ui.text(c, "", 13, Ui.OK, false);
    save.setOnClickListener(v -> {
      String k = key.getText().toString().trim();
      if (!k.isEmpty()) kv.put(k);
      String m = model.getText().toString().trim();
      if (!m.isEmpty()) S.model = m;
      S.save();
      key.setText("");
      saved.setText(k.isEmpty() ? "Modèle enregistré." : "Clé enregistrée.");
    });
    Button forget = Ui.ghost(c, "Effacer la clé");
    forget.setOnClickListener(v -> { kv.put(""); saved.setText("Clé effacée."); });
    row.addView(save);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.leftMargin = Ui.dp(c, 8);
    row.addView(forget, lp);
    api.addView(row, Ui.lpTop(c, 12));
    api.addView(saved, Ui.lpTop(c, 6));
    root.addView(api, Ui.lpTop(c, 16));

    LinearLayout cache = Ui.card(c);
    cache.addView(Ui.eyebrow(c, "Cache audio"));
    Voice voice = Voice.get(c);
    TextView size = Ui.text(c, "Phrases déjà synthétisées : " + (voice.cacheBytes() / (1024 * 1024)) + " Mo. Elles rendent la réécoute instantanée.", 14, Ui.SOFT, false);
    cache.addView(size, Ui.lpTop(c, 6));
    Button clear = Ui.ghost(c, "Vider le cache");
    clear.setOnClickListener(v -> { voice.clearCache(); size.setText("Cache vidé."); });
    cache.addView(clear, Ui.lpTop(c, 10));
    root.addView(cache, Ui.lpTop(c, 16));

    root.addView(Ui.text(c, "Synthèse vocale : Supertonic 3 (Supertone) exécuté hors ligne par sherpa-onnx. Leçons et progression restent sur ce téléphone.", 12, Ui.DIM, false), Ui.lpTop(c, 18));
  }

  interface FloatSetter { void set(float v); }

  private void choice(LinearLayout parent, String label, String[] names, float[] values, float current, FloatSetter set) {
    Context c = this;
    parent.addView(Ui.text(c, label, 14, Ui.SOFT, false), Ui.lpTop(c, 12));
    LinearLayout row = new LinearLayout(c);
    int best = 0;
    for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - current) < Math.abs(values[best] - current)) best = i;
    for (int i = 0; i < names.length; i++) {
      final int k = i;
      TextView ch = Ui.chip(c, names[i], i == best, Ui.PAPER, Ui.NIGHT);
      ch.setOnClickListener(v -> { set.set(values[k]); S.save(); render(); });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.rightMargin = Ui.dp(c, 8);
      row.addView(ch, lp);
    }
    parent.addView(row, Ui.lpTop(c, 6));
  }

  private void voiceRow(LinearLayout parent, String label, char role) {
    Context c = this;
    parent.addView(Ui.text(c, label, 14, Ui.PAPER, true), Ui.lpTop(c, 14));
    LinearLayout grid = new LinearLayout(c);
    grid.setOrientation(LinearLayout.VERTICAL);
    int cur = S.sidFor(role);
    for (int r = 0; r < 2; r++) {
      LinearLayout line = new LinearLayout(c);
      for (int k = 0; k < 5; k++) {
        final int sid = r * 5 + k;
        TextView ch = Ui.chip(c, String.valueOf(sid + 1), sid == cur, Ui.GOLD, Ui.NIGHT);
        ch.setGravity(Gravity.CENTER);
        ch.setContentDescription(label + ", voix " + (sid + 1));
        ch.setOnClickListener(v -> {
          if (role == 'N') S.sidNarr = sid; else if (role == 'A') S.sidA = sid; else S.sidB = sid;
          S.save();
          render();
          test(role == 'N' ? "fr" : "ja", sid);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1);
        lp.rightMargin = k < 4 ? Ui.dp(c, 6) : 0;
        line.addView(ch, lp);
      }
      grid.addView(line, Ui.lpTop(c, r == 0 ? 6 : 6));
    }
    parent.addView(grid);
  }

  private void test(String lang, int sid) {
    testStatus.setText("Préparation de l'extrait…");
    String text = lang.equals("fr") ? "Voici comment on dit : je comprends." : "日本語が少し分かります。よろしくお願いします。";
    float speed = lang.equals("fr") ? S.frSpeed : S.jaSpeed;
    new Thread(() -> {
      try {
        Voice v = Voice.get(this);
        short[] pcm = v.speak(text, lang, sid, speed, S.quality);
        int rate = v.sampleRate();
        ui.post(() -> {
          testStatus.setText("Voix " + (sid + 1) + " en " + (lang.equals("fr") ? "français" : "japonais"));
          playPcm(pcm, rate);
        });
      } catch (Exception e) {
        ui.post(() -> testStatus.setText("Extrait impossible : " + e.getMessage()));
      }
    }, "km-test").start();
  }

  private void playPcm(short[] pcm, int rate) {
    if (pcm.length == 0) return;
    if (testTrack != null) { try { testTrack.release(); } catch (Exception ignored) {} }
    testTrack = new AudioTrack.Builder()
        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(pcm.length * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
    testTrack.write(pcm, 0, pcm.length);
    testTrack.play();
  }

  @Override protected void onDestroy() { if (testTrack != null) testTrack.release(); super.onDestroy(); }

  private CheckBox check(Context c, String label, boolean on) {
    CheckBox cb = new CheckBox(c);
    cb.setText(label);
    cb.setTextColor(Ui.PAPER);
    cb.setChecked(on);
    cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.SHU));
    return cb;
  }

  private EditText field(Context c, String hint, String value) {
    EditText e = new EditText(c);
    e.setHint(hint);
    e.setText(value);
    e.setHintTextColor(Ui.DIM);
    e.setTextColor(Ui.PAPER);
    e.setSingleLine(true);
    int p = Ui.dp(c, 12);
    e.setPadding(p, p, p, p);
    e.setBackground(Ui.round(0xFF191C42, Ui.LINE, Ui.dp(c, 12), Ui.dp(c, 1)));
    return e;
  }
}
