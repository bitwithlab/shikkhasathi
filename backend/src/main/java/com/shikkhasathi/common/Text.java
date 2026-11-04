package com.shikkhasathi.common;

/** Small text helpers shared by several modules. */
public final class Text {
  private Text() {}

  /** "Rahim Uddin" → "Rahim Ud." (public display of students: first name + 2 letters of the second). */
  public static String shortName(String name) {
    if (name == null) return "";
    String[] p = name.trim().split("\\s+");
    if (p.length < 2) return p.length == 1 ? p[0] : "";
    int[] cps = p[1].codePoints().limit(2).toArray();
    return p[0] + " " + new String(cps, 0, cps.length) + ".";
  }
}
