package com.shikkhasathi.classroom.service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/** Formatting helpers shared by the Classroom, assignment and dashboard code (same output as before). */
public final class ClassroomText {
  private ClassroomText() {}

  public static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

  /** Class colors (stable per course order). */
  public static final List<String> COLORS = List.of("#4F46E5", "#0EA5E9", "#10B981", "#F59E0B", "#EC4899", "#8B5CF6", "#14B8A6", "#F97316", "#64748B");

  private static final String[] WEEKDAYS = {"সোম", "মঙ্গল", "বুধ", "বৃহস্পতি", "শুক্র", "শনি", "রবি"};
  private static final String[] MONTHS = {"জানু", "ফেব", "মার্চ", "এপ্রি", "মে", "জুন", "জুল", "আগ", "সেপ", "অক্টো", "নভে", "ডিসে"};

  /** JS {@code encodeURIComponent}. */
  public static String uri(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20").replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~");
  }

  /** First word of a course name ("CSE-2203 Data Structures" → "CSE-2203"). */
  public static String shortCourse(String name) {
    return name.split(" ", -1)[0];
  }

  public static String bnDigits(String s) {
    StringBuilder b = new StringBuilder(s.length());
    for (char c : s.toCharArray()) b.append(c >= '0' && c <= '9' ? (char) ('০' + (c - '0')) : c);
    return b.toString();
  }

  /**
   * Asia/Dhaka date-time in Bangla, like {@code Intl.DateTimeFormat('bn-BD', {weekday:'short', day:'numeric',
   * month:'short', hour:'numeric', minute:'2-digit'})}: "বৃহস্পতি ৫ নভে, ৫:৫৯ PM".
   */
  public static String dhaka(Instant i) {
    ZonedDateTime z = i.atZone(DHAKA);
    int h = z.getHour() % 12 == 0 ? 12 : z.getHour() % 12;
    String time = bnDigits(h + ":" + String.format("%02d", z.getMinute())) + " " + (z.getHour() < 12 ? "AM" : "PM");
    return WEEKDAYS[z.getDayOfWeek().getValue() - 1] + " " + bnDigits(String.valueOf(z.getDayOfMonth())) + " " + MONTHS[z.getMonthValue() - 1] + ", " + time;
  }

  /** Asia/Dhaka calendar day. */
  public static LocalDate dhakaDay(Instant i) {
    return i.atZone(DHAKA).toLocalDate();
  }

  /** numeric column → JSON number (integral values without a fraction, like JS). */
  public static Number num(BigDecimal v) {
    if (v == null) return null;
    BigDecimal s = v.stripTrailingZeros();
    if (s.scale() <= 0 && s.precision() - s.scale() <= 18) return s.longValueExact();
    return v.doubleValue();
  }

  public static BigDecimal dec(Double v) {
    return v == null ? null : BigDecimal.valueOf(v);
  }

  /** JS {@code String(number)} for whole and simple decimal numbers. */
  public static String js(Number n) {
    if (n == null) return "null";
    double d = n.doubleValue();
    if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
    return String.valueOf(d);
  }
}
