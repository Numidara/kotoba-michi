package fr.numidara.kotobamichi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Écran du tableau de bord : gros boutons, texte facultatif, progression en forme de route. */
public final class PlayerActivity extends Activity implements PlayerService.Listener {
  private final Handler ui = new Handler(Looper.getMainLooper());
  private Settings S;
  private TextView title, phase, big, kana, gloss, el, tot, status;
  private Road road;
  private Ring ring;
  private Button playBtn, textBtn;
  private LinearLayout stage, ctrls, donePanel;
  private boolean finishedShown;
  private final Runnable tick = new Runnable() { @Override public void run() { refresh(); ui.postDelayed(this, 120); } };

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    S = new Settings(this);
    getWindow().setStatusBarColor(0xFF12142F);
    getWindow().setNavigationBarColor(0xFF12142F);
    Context c = this;
    LinearLayout root = new LinearLayout(c);
    root.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF262B66, Ui.NIGHT, 0xFF12142F});
    root.setBackground(bg);
    int p = Ui.dp(c, 18);
    root.setPadding(p, p, p, p);
    root.setOnApplyWindowInsetsListener((v, in) -> {
      v.setPadding(p, p + in.getSystemWindowInsetTop(), p, p + in.getSystemWindowInsetBottom());
      return in;
    });

    title = Ui.text(c, "", 14, Ui.SOFT, false);
    title.setSingleLine(true);
    root.addView(title);
    road = new Road(c);
    root.addView(road, Ui.lpTop(c, 12));
    road.getLayoutParams().height = Ui.dp(c, 18);
    LinearLayout times = new LinearLayout(c);
    el = Ui.text(c, "0:00", 14, Ui.SOFT, false);
    tot = Ui.text(c, "", 14, Ui.SOFT, false);
    tot.setGravity(Gravity.END);
    times.addView(el, new LinearLayout.LayoutParams(0, -2, 1));
    times.addView(tot, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(times, Ui.lpTop(c, 6));

    FrameLayout mid = new FrameLayout(c);
    stage = new LinearLayout(c);
    stage.setOrientation(LinearLayout.VERTICAL);
    stage.setGravity(Gravity.CENTER);
    phase = Ui.text(c, "", 13, Ui.GOLD, true);
    phase.setLetterSpacing(0.14f);
    phase.setGravity(Gravity.CENTER);
    big = Ui.text(c, "", 40, Ui.PAPER, true);
    big.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
    big.setGravity(Gravity.CENTER);
    kana = Ui.text(c, "", 18, Ui.SOFT, false);
    kana.setGravity(Gravity.CENTER);
    gloss = Ui.text(c, "", 17, Ui.SOFT, false);
    gloss.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
    gloss.setGravity(Gravity.CENTER);
    ring = new Ring(c);
    status = Ui.text(c, "", 14, Ui.DIM, false);
    status.setGravity(Gravity.CENTER);
    stage.addView(phase);
    stage.addView(big, Ui.lpTop(c, 14));
    stage.addView(kana, Ui.lpTop(c, 10));
    stage.addView(gloss, Ui.lpTop(c, 8));
    LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(Ui.dp(c, 86), Ui.dp(c, 86));
    rl.topMargin = Ui.dp(c, 18);
    rl.gravity = Gravity.CENTER_HORIZONTAL;
    stage.addView(ring, rl);
    stage.addView(status, Ui.lpTop(c, 10));
    mid.addView(stage, new FrameLayout.LayoutParams(-1, -1));
    donePanel = new LinearLayout(c);
    donePanel.setOrientation(LinearLayout.VERTICAL);
    donePanel.setGravity(Gravity.CENTER);
    donePanel.setVisibility(View.GONE);
    mid.addView(donePanel, new FrameLayout.LayoutParams(-1, -1));
    mid.setOnClickListener(v -> { if (PlayerService.current != null && !finishedShown) PlayerService.current.toggle(); });
    root.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

    ctrls = new LinearLayout(c);
    ctrls.setGravity(Gravity.CENTER);
    Button back = roundBtn(c, "↺\nRejouer", Ui.NIGHT3, 72, 13);
    back.setContentDescription("Rejouer l'exercice");
    back.setOnClickListener(v -> { if (PlayerService.current != null) PlayerService.current.jump(-1); });
    playBtn = roundBtn(c, "❚❚", Ui.SHU, 128, 36);
    playBtn.setOnClickListener(v -> { if (PlayerService.current != null) PlayerService.current.toggle(); });
    Button next = roundBtn(c, "⏭\nPasser", Ui.NIGHT3, 72, 13);
    next.setContentDescription("Exercice suivant");
    next.setOnClickListener(v -> { if (PlayerService.current != null) PlayerService.current.jump(1); });
    ctrls.addView(back, sq(c, 72, 0));
    ctrls.addView(playBtn, sq(c, 128, 26));
    ctrls.addView(next, sq(c, 72, 26));
    root.addView(ctrls, Ui.lpTop(c, 8));

    LinearLayout foot = new LinearLayout(c);
    foot.setGravity(Gravity.CENTER_VERTICAL);
    textBtn = Ui.ghost(c, "");
    textBtn.setOnClickListener(v -> { S.showText = !S.showText; S.save(); refresh(); });
    TextView hint = Ui.text(c, "Touche l'écran pour la pause", 13, Ui.DIM, false);
    hint.setGravity(Gravity.CENTER);
    Button quit = Ui.ghost(c, "Quitter");
    quit.setOnClickListener(v -> { PlayerService.send(this, PlayerService.ACTION_STOP); finish(); });
    foot.addView(textBtn);
    foot.addView(hint, new LinearLayout.LayoutParams(0, -2, 1));
    foot.addView(quit);
    root.addView(foot, Ui.lpTop(c, 16));
    setContentView(root);
  }

  private LinearLayout.LayoutParams sq(Context c, int d, int leftDp) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, d), Ui.dp(c, d));
    lp.leftMargin = Ui.dp(c, leftDp);
    return lp;
  }

  private Button roundBtn(Context c, String label, int color, int sizeDp, float sp) {
    Button b = new Button(c);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextColor(Ui.PAPER);
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    b.setStateListAnimator(null);
    b.setPadding(0, 0, 0, 0);
    GradientDrawable g = new GradientDrawable();
    g.setShape(GradientDrawable.OVAL);
    g.setColor(color);
    b.setBackground(g);
    return b;
  }

  @Override protected void onStart() {
    super.onStart();
    PlayerService.listeners.add(this);
    ui.post(tick);
  }

  @Override protected void onStop() {
    PlayerService.listeners.remove(this);
    ui.removeCallbacks(tick);
    super.onStop();
  }

  @Override public void onPlayerChanged() { ui.post(this::refresh); }

  private void refresh() {
    PlayerService ps = PlayerService.current;
    Session s = ps != null ? ps.session : null;
    if (s == null) { title.setText("Préparation de la séance…"); return; }
    title.setText(s.lesson.title + " · niveau " + s.lesson.level);
    int i = Math.min(ps.index, s.steps.size() - 1);
    float e = s.elapsedAt(ps.index);
    el.setText(Ui.fmt(e));
    tot.setText("≈ " + Ui.fmt(s.est));
    road.setProgress(s.est > 0 ? e / s.est : 0);
    playBtn.setText(ps.playing ? "❚❚" : "▶");
    playBtn.setContentDescription(ps.playing ? "Pause" : "Lecture");
    textBtn.setText(S.showText ? "Masquer texte" : "Voir texte");
    if (S.showText != (stage.getAlpha() > 0.5f)) stage.setAlpha(S.showText ? 1f : 0f);
    // Garder l'écran allumé seulement si le texte est affiché pendant la lecture
    if (S.showText && ps.playing) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    if (ps.finished) { showDone(s); return; }
    if (finishedShown) { finishedShown = false; donePanel.setVisibility(View.GONE); stage.setVisibility(View.VISIBLE); ctrls.setVisibility(View.VISIBLE); }

    Session.Step st = s.steps.get(i);
    phase.setText(s.groups.get(st.group).label.toUpperCase());
    // Le texte affiché suit la dernière parole, pas le silence
    Session.Step shown = st;
    for (int k = i; k >= 0 && shown.type == Session.GAP; k--) shown = s.steps.get(k);
    if (shown.type == Session.SAY) {
      if (shown.role == 'N') {
        big.setTypeface(Typeface.DEFAULT);
        big.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        big.setText(shown.ask ? "« " + shown.showFr + " »" : shown.showFr);
        kana.setText(""); gloss.setText("");
      } else {
        big.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        big.setTextSize(TypedValue.COMPLEX_UNIT_SP, shown.showJp.length() > 12 ? 30 : 42);
        big.setText(shown.showJp);
        String k = shown.showKana == null ? "" : shown.showKana;
        kana.setText(k.replace(" ", "").equals(shown.showJp.replaceAll("[\\s、。？！]", "")) ? "" : k);
        gloss.setText(shown.showFr == null ? "" : shown.showFr);
      }
    }
    if (st.type == Session.GAP && st.gapKind != Session.GAP_BEAT && ps.gapMs > 0 && ps.playing) {
      ring.setVisibility(View.VISIBLE);
      ring.label = st.gapKind == Session.GAP_THINK ? "À vous" : "Répétez";
      ring.p = Math.min(1f, (SystemClock.elapsedRealtime() - ps.gapStart) / (float) ps.gapMs);
      ring.invalidate();
    } else ring.setVisibility(View.INVISIBLE);
    if (ps.error != null && !ps.playing) status.setText("Problème de voix : " + ps.error);
    else if (ps.waitingVoice && ps.playing) status.setText("Préparation de la voix…");
    else if (!ps.playing) status.setText("En pause");
    else status.setText("");
  }

  private void showDone(Session s) {
    if (finishedShown) return;
    finishedShown = true;
    stage.setVisibility(View.GONE);
    ctrls.setVisibility(View.GONE);
    donePanel.removeAllViews();
    donePanel.setVisibility(View.VISIBLE);
    donePanel.setAlpha(1f);
    Context c = this;
    TextView ph = Ui.eyebrow(c, "Leçon terminée");
    ph.setGravity(Gravity.CENTER);
    TextView h = Ui.serif(c, s.lesson.title, 30);
    h.setGravity(Gravity.CENTER);
    TextView p = Ui.text(c, s.lesson.items.size() + " expressions travaillées" + (s.review.isEmpty() ? "" : ", " + s.review.size() + " révisées")
        + ". Refais la leçon tant que les réponses ne viennent pas sans effort : c'est le conseil de Pimsleur.", 15, Ui.SOFT, false);
    p.setGravity(Gravity.CENTER);
    donePanel.addView(ph);
    donePanel.addView(h, Ui.lpTop(c, 8));
    donePanel.addView(p, Ui.lpTop(c, 10));
    Button again = Ui.primary(c, "Recommencer");
    again.setOnClickListener(v -> { if (PlayerService.current != null) PlayerService.current.resume(); });
    donePanel.addView(again, Ui.lpTop(c, 20));
    Button home = Ui.ghost(c, "Retour à la bibliothèque");
    home.setOnClickListener(v -> { PlayerService.send(this, PlayerService.ACTION_STOP); startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); });
    donePanel.addView(home, Ui.lpTop(c, 10));
  }

  /** Barre de progression dessinée comme une chaussée avec sa ligne médiane. */
  static final class Road extends View {
    private float progress;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    Road(Context c) { super(c); }
    void setProgress(float v) { if (Math.abs(v - progress) > 0.001f) { progress = v; invalidate(); } }
    @Override protected void onDraw(Canvas cv) {
      float w = getWidth(), h = getHeight(), r = h / 2f, pad = h * 0.1f;
      p.setColor(Ui.ROAD);
      cv.drawRoundRect(new RectF(0, pad, w, h - pad), r, r, p);
      p.setColor(Ui.SHU);
      cv.drawRoundRect(new RectF(0, pad, Math.max(h, w * progress), h - pad), r, r, p);
      p.setColor(0x59F6F0E4);
      p.setStrokeWidth(Math.max(2, h / 8));
      float dash = h, gapLen = h * 0.8f;
      for (float x = r; x < w - r; x += dash + gapLen) cv.drawLine(x, h / 2, Math.min(x + dash, w - r), h / 2, p);
      p.setColor(Ui.GOLD);
      float cx = r + (w - 2 * r) * Math.min(1, progress);
      cv.drawCircle(cx, h / 2, r, p);
    }
  }

  /** Anneau qui se vide pendant le silence laissé pour répondre ou répéter. */
  static final class Ring extends View {
    float p; String label = "";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Ring(Context c) { super(c); }
    @Override protected void onDraw(Canvas cv) {
      float s = Math.min(getWidth(), getHeight()), sw = s * 0.07f;
      RectF r = new RectF(sw, sw, s - sw, s - sw);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(sw);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setColor(Ui.NIGHT3);
      cv.drawArc(r, 0, 360, false, paint);
      paint.setColor(Ui.GOLD);
      cv.drawArc(r, -90, 360 * (1 - p), false, paint);
      paint.setStyle(Paint.Style.FILL);
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTextSize(s * 0.18f);
      paint.setFakeBoldText(true);
      cv.drawText(label, s / 2, s / 2 + s * 0.06f, paint);
    }
  }
}
