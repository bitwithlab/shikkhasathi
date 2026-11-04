package com.shikkhasathi.search.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bangla / English / transliteration / abbreviation expansion for search ("ডাটা স্ট্রাকচার" = "data structure" = "DS"). */
public final class QueryExpansion {
  private QueryExpansion() {}

  static final List<List<String>> GROUPS = List.of(
      List.of("data structures", "data structure", "ds", "dsa", "ডাটা স্ট্রাকচার", "ডেটা স্ট্রাকচার", "ডাটা স্ট্রাকচার্স"),
      List.of("algorithms", "algorithm", "daa", "অ্যালগরিদম", "এলগরিদম"),
      List.of("database management systems", "database", "dbms", "ডাটাবেস", "ডেটাবেস"),
      List.of("object oriented programming", "oop", "java", "অবজেক্ট ওরিয়েন্টেড"),
      List.of("digital logic design", "dld", "digital logic", "ডিজিটাল লজিক"),
      List.of("structured programming", "spl", "c programming", "programming language", "সি প্রোগ্রামিং"),
      List.of("discrete mathematics", "discrete math", "ডিসক্রিট ম্যাথ"),
      List.of("computer networks", "networking", "cn", "নেটওয়ার্ক"),
      List.of("operating systems", "os", "অপারেটিং সিস্টেম"),
      List.of("linear algebra", "matrix", "লিনিয়ার অ্যালজেব্রা"),
      List.of("electrical circuits", "circuit", "সার্কিট"),
      List.of("microeconomics", "micro", "ইকোনমিক্স", "অর্থনীতি"),
      List.of("midterm", "mid", "মিডটার্ম", "মিড"),
      List.of("final", "ফাইনাল"),
      List.of("quiz", "কুইজ"),
      List.of("metropolitan university", "mu", "মেট্রোপলিটন"),
      List.of("north south university", "nsu", "নর্থ সাউথ"),
      List.of("brac university", "bracu", "brac", "ব্র্যাক"));

  /** Exam words in the previous API's key order. */
  public static final Map<String, String> EXAM_WORDS;

  static {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("midterm", "midterm");
    m.put("mid", "midterm");
    m.put("মিডটার্ম", "midterm");
    m.put("মিড", "midterm");
    m.put("final", "final");
    m.put("ফাইনাল", "final");
    m.put("quiz", "quiz");
    m.put("কুইজ", "quiz");
    EXAM_WORDS = java.util.Collections.unmodifiableMap(m);
  }

  // JS \b with ASCII word chars
  private static final Pattern CODE = Pattern.compile("(?<![A-Za-z0-9_])([a-z]{2,4})[\\s-]?(\\d{3,4})(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
  private static final Pattern LOOKS_LIKE_CODE = Pattern.compile("^[a-z]{2,4}[\\s-]?\\d{3,4}$", Pattern.CASE_INSENSITIVE);
  private static final Pattern WORD_START = Pattern.compile("(?<![A-Za-z0-9_])[A-Za-z0-9_]");

  public record Interpretation(String term, String meaning) {}

  /**
   * @param terms       all text variants to match (lowercased)
   * @param code        normalized course code if the query is one
   * @param examType    midterm | final | quiz | null
   */
  public record Expanded(List<String> terms, String code, String examType, List<Interpretation> interpreted) {}

  static String norm(String s) {
    return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFC).replaceAll("\\s+", " ").trim();
  }

  /** Course codes: "cse 2203", "CSE2203", "cse-2203" → "CSE2203". */
  public static String normalizeCode(String s) {
    return s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
  }

  public static boolean looksLikeCode(String s) {
    return LOOKS_LIKE_CODE.matcher(s.trim()).matches();
  }

  private static String titleCase(String s) {
    Matcher m = WORD_START.matcher(s);
    StringBuilder b = new StringBuilder();
    while (m.find()) m.appendReplacement(b, Matcher.quoteReplacement(m.group().toUpperCase(Locale.ROOT)));
    m.appendTail(b);
    return b.toString();
  }

  public static Expanded expand(String q) {
    String raw = norm(q == null ? "" : q);
    List<String> terms = new ArrayList<>();
    List<Interpretation> interpreted = new ArrayList<>();
    if (raw.isEmpty()) return new Expanded(terms, null, null, interpreted);
    String code = null;
    Matcher cm = CODE.matcher(raw);
    String codeText = null;
    if (cm.find()) {
      codeText = cm.group();
      code = normalizeCode(codeText);
    }
    String examType = null;
    String rest = raw;
    for (var e : EXAM_WORDS.entrySet()) {
      Pattern re = Pattern.compile("(^|\\s)" + Pattern.quote(e.getKey()) + "(?=\\s|$)", Pattern.CASE_INSENSITIVE);
      Matcher m = re.matcher(rest);
      if (m.find()) {
        examType = e.getValue();
        rest = (rest.substring(0, m.start()) + " " + rest.substring(m.end())).trim();
      }
    }
    if (code != null) {
      String lit = codeText.toLowerCase(Locale.ROOT);
      int i = rest.indexOf(lit);
      if (i >= 0) rest = rest.substring(0, i) + " " + rest.substring(i + lit.length());
      rest = rest.trim();
    }
    if (!rest.isEmpty()) add(terms, rest);
    List<String> words = Arrays.asList(rest.split(" ", -1));
    for (List<String> g : GROUPS) {
      String hit = null;
      for (String v : g) {
        if (rest.equals(v) || (v.length() > 3 && rest.contains(v)) || words.contains(v)) {
          hit = v;
          break;
        }
      }
      if (hit != null) {
        g.forEach(v -> add(terms, v));
        if (!hit.equals(g.get(0)) && !EXAM_WORDS.containsKey(hit)) interpreted.add(new Interpretation(hit, titleCase(g.get(0))));
      }
    }
    return new Expanded(terms, code, examType, interpreted);
  }

  private static void add(List<String> terms, String t) {
    if (t != null && !t.isEmpty() && !terms.contains(t)) terms.add(t);
  }
}
