package fr.numidara.kotobamichi;

import android.content.Context;
import android.content.SharedPreferences;

/** Réglages de l'utilisateur, persistés dans les SharedPreferences. */
public final class Settings {
  public int dur = 30;
  public float jaSpeed = 0.9f, frSpeed = 1.0f, think = 1.0f;
  public int sidNarr = 2, sidA = 5, sidB = 0;   // voix Supertonic 0..9
  public int quality = 5;                       // pas de débruitage (num_steps)
  public boolean kanji = true;                  // lire les phrases japonaises écrites en kanji si le modèle les connaît
  public boolean showText = true;
  public String model = "claude-sonnet-5-5";

  private final SharedPreferences p;

  /** Réglages par défaut, sans persistance (tests). */
  Settings() { p = null; }

  public Settings(Context c) {
    p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    dur = p.getInt("dur", dur);
    jaSpeed = p.getFloat("jaSpeed", jaSpeed);
    frSpeed = p.getFloat("frSpeed", frSpeed);
    think = p.getFloat("think", think);
    sidNarr = p.getInt("sidNarr", sidNarr);
    sidA = p.getInt("sidA", sidA);
    sidB = p.getInt("sidB", sidB);
    quality = p.getInt("quality", quality);
    kanji = p.getBoolean("kanji", kanji);
    showText = p.getBoolean("showText", showText);
    model = p.getString("model", model);
  }

  public void save() {
    if (p == null) return;
    p.edit().putInt("dur", dur).putFloat("jaSpeed", jaSpeed).putFloat("frSpeed", frSpeed).putFloat("think", think)
        .putInt("sidNarr", sidNarr).putInt("sidA", sidA).putInt("sidB", sidB).putInt("quality", quality)
        .putBoolean("kanji", kanji).putBoolean("showText", showText).putString("model", model).apply();
  }

  public int sidFor(char role) { return role == 'N' ? sidNarr : role == 'B' ? sidB : sidA; }
}
