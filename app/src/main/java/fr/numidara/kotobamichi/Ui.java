package fr.numidara.kotobamichi;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Palette et petits constructeurs de vues, dans l'identité de Kotoba Dōjō (nuit, vermillon shu, or). */
public final class Ui {
  private Ui() {}

  public static final int NIGHT = 0xFF1C1F45, NIGHT2 = 0xFF242962, NIGHT3 = 0xFF2F3576, LINE = 0xFF414891,
      PAPER = 0xFFF6F0E4, SOFT = 0xFFB9B4D8, DIM = 0xFF8C88B4, SHU = 0xFFD9472B, SHU2 = 0xFFB9381F,
      GOLD = 0xFFF2B33D, OK = 0xFF4CC27E, ROAD = 0xFF14173A, ERR = 0xFFFF8A75;

  public static int dp(Context c, float v) {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
  }

  public static GradientDrawable round(int fill, int stroke, float radiusPx, int strokePx) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(fill);
    g.setCornerRadius(radiusPx);
    if (strokePx > 0) g.setStroke(strokePx, stroke);
    return g;
  }

  public static TextView text(Context c, String s, float sp, int color, boolean bold) {
    TextView t = new TextView(c);
    t.setText(s);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    t.setTextColor(color);
    if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
    t.setLineSpacing(0, 1.15f);
    return t;
  }

  public static TextView serif(Context c, String s, float sp) {
    TextView t = text(c, s, sp, PAPER, false);
    t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
    return t;
  }

  public static TextView eyebrow(Context c, String s) {
    TextView t = text(c, s.toUpperCase(), 12, GOLD, true);
    t.setLetterSpacing(0.12f);
    return t;
  }

  public static LinearLayout card(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    int p = dp(c, 18);
    l.setPadding(p, p, p, p);
    l.setBackground(round(NIGHT2, LINE, dp(c, 18), dp(c, 1)));
    return l;
  }

  public static Button primary(Context c, String label) {
    Button b = new Button(c);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextColor(Color.WHITE);
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
    b.setTypeface(Typeface.DEFAULT_BOLD);
    b.setMinHeight(dp(c, 62));
    b.setStateListAnimator(null);
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(SHU2), round(SHU, 0, dp(c, 16), 0), null));
    return b;
  }

  public static Button ghost(Context c, String label) {
    Button b = new Button(c);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextColor(PAPER);
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    b.setStateListAnimator(null);
    b.setMinHeight(dp(c, 44));
    b.setMinimumHeight(dp(c, 44));
    int p = dp(c, 14);
    b.setPadding(p, 0, p, 0);
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(NIGHT3), round(0x00000000, LINE, dp(c, 12), dp(c, 1)), null));
    return b;
  }

  /** Pastille sélectionnable (durée, idée de thème, niveau). */
  public static TextView chip(Context c, String label, boolean on, int onFill, int onText) {
    TextView t = text(c, label, 14, on ? onText : SOFT, on);
    t.setGravity(Gravity.CENTER);
    int ph = dp(c, 14), pv = dp(c, 8);
    t.setPadding(ph, pv, ph, pv);
    t.setBackground(on ? round(onFill, onFill, dp(c, 999), dp(c, 1)) : round(0x00000000, LINE, dp(c, 999), dp(c, 1)));
    t.setClickable(true);
    t.setFocusable(true);
    return t;
  }

  public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
  public static LinearLayout.LayoutParams lpTop(Context c, float topDp) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    p.topMargin = dp(c, topDp);
    return p;
  }

  public static View space(Context c, float hDp) {
    View v = new View(c);
    v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, hDp)));
    return v;
  }

  public static String fmt(float sec) {
    int s = Math.max(0, Math.round(sec));
    return (s / 60) + ":" + String.format("%02d", s % 60);
  }

  public static final String[] LEVEL_NAMES = {"", "Survie", "Bases", "Envies et demandes", "Petits récits", "Conversation naturelle", "Aisance"};
  public static final String[] LEVEL_DESC = {"",
      "mots et phrases figées, « desu », questions en « ka ».",
      "verbes polis en -masu, -masen, -mashita ; particules wa, ga, o, ni, de, e.",
      "-tai, -mashô, -te kudasai, adjectifs en -i et en -na.",
      "forme en -te (-te imasu, -te mo ii desu ka), raisons avec « kara », enchaîner des actions.",
      "formes neutres entre amis, -to omoimasu, -tara, propositions relatives.",
      "keigo courant, conditionnels en -ba, nuances (-noni, -yô ni, -sô desu)."};
}
