package fr.numidara.kotobamichi;

import java.util.ArrayList;
import java.util.List;

/** Découpage en mores et reconstruction depuis la fin (backward build-up de Pimsleur). */
public final class Kana {
  private Kana() {}

  private static final String SMALL = "ゃゅょぁぃぅぇぉゎャュョァィゥェォヮー";

  public static List<String> morae(String kana) {
    List<String> out = new ArrayList<>();
    if (kana == null) return out;
    String s = kana.replaceAll("[\\s、。？！?!・]", "");
    for (int i = 0; i < s.length(); ) {
      int cp = s.codePointAt(i);
      String ch = new String(Character.toChars(cp));
      i += Character.charCount(cp);
      if (!out.isEmpty() && SMALL.contains(ch)) out.set(out.size() - 1, out.get(out.size() - 1) + ch);
      else out.add(ch);
    }
    return out;
  }

  public static int count(String kana) { return morae(kana).size(); }

  private static boolean badStart(String m) { return m.matches("^[っッんンー].*"); }

  /** Étapes de reconstruction : fin du dernier mot, puis on ajoute les groupes précédents. */
  public static List<String> buildUp(String kana) {
    List<String> steps = new ArrayList<>();
    if (kana == null) return steps;
    String[] chunks = kana.trim().split("\\s+");
    if (chunks.length == 0 || chunks[0].isEmpty() || count(kana) < 4) return steps;
    List<String> last = morae(chunks[chunks.length - 1]);
    for (int n = 2; n < last.size(); n += 2) {
      int start = last.size() - n;
      while (start > 0 && badStart(last.get(start))) start--;
      StringBuilder f = new StringBuilder();
      for (int k = start; k < last.size(); k++) f.append(last.get(k));
      if (!steps.contains(f.toString())) steps.add(f.toString());
    }
    String acc = chunks[chunks.length - 1];
    if (!steps.contains(acc)) steps.add(acc);
    for (int i = chunks.length - 2; i >= 0; i--) { acc = chunks[i] + " " + acc; steps.add(acc); }
    return steps.size() > 5 ? new ArrayList<>(steps.subList(steps.size() - 5, steps.size())) : steps;
  }

  /** Vrai si la chaîne ne contient que des kana et des espaces. */
  public static boolean isKana(String s) {
    return s != null && !s.isEmpty() && s.matches("^[\\u3040-\\u30ff\\u30fc\\s]+$");
  }
}
