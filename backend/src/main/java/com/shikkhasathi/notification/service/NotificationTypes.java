package com.shikkhasathi.notification.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Notification types and channels (spec §10) — same strings as the API contract. */
public final class NotificationTypes {
  public static final String CLASSROOM_ASSIGNMENT_NEW = "classroom.assignment_new";
  public static final String CLASSROOM_MATERIAL_NEW = "classroom.material_new";
  public static final String CLASSROOM_ANNOUNCEMENT = "classroom.announcement";
  public static final String CLASSROOM_GRADED = "classroom.graded";
  public static final String CLASSROOM_DUE_24H = "classroom.due_24h";
  public static final String CLASSROOM_DUE_2H = "classroom.due_2h";
  public static final String CLASSROOM_OVERDUE = "classroom.overdue";
  public static final String EXAM_DAILY_PLAN = "exam.daily_plan";
  public static final String COURSE_QA_ANSWERED = "course.qa_answered";
  public static final String SELLER_QA_NEW = "seller.qa_new";
  public static final String ORDER_PAID = "order.paid";
  public static final String ORDER_REFUNDED = "order.refunded";
  public static final String REQUEST_PUBLISHED = "request.published";
  public static final String SELLER_COURSE_DECISION = "seller.course_decision";
  public static final String SELLER_PAYOUT_PAID = "seller.payout_paid";
  public static final String SELLER_NEW_ENROLLMENT = "seller.new_enrollment";
  public static final String SELLER_APPLICATION_RESULT = "seller.application_result";
  public static final String ADMIN_BROADCAST = "admin.broadcast";

  public static final String IN_APP = "in_app";
  public static final String PUSH = "push";
  public static final String EMAIL = "email";
  public static final String SMS = "sms";
  public static final List<String> CHANNELS = List.of(IN_APP, PUSH, EMAIL, SMS);

  /** Default channels per type (spec §10), in contract order. Users override per type × channel. */
  public static final Map<String, List<String>> DEFAULT_CHANNELS;

  static {
    Map<String, List<String>> m = new LinkedHashMap<>();
    m.put(CLASSROOM_ASSIGNMENT_NEW, List.of(IN_APP, PUSH));
    m.put(CLASSROOM_MATERIAL_NEW, List.of(IN_APP, PUSH));
    m.put(CLASSROOM_ANNOUNCEMENT, List.of(IN_APP, PUSH));
    m.put(CLASSROOM_GRADED, List.of(IN_APP, PUSH));
    m.put(CLASSROOM_DUE_24H, List.of(IN_APP, PUSH));
    m.put(CLASSROOM_DUE_2H, List.of(IN_APP, PUSH));
    m.put(CLASSROOM_OVERDUE, List.of(IN_APP));
    m.put(EXAM_DAILY_PLAN, List.of(PUSH));
    m.put(COURSE_QA_ANSWERED, List.of(IN_APP, PUSH));
    m.put(SELLER_QA_NEW, List.of(IN_APP, PUSH));
    m.put(ORDER_PAID, List.of(IN_APP, PUSH, EMAIL));
    m.put(ORDER_REFUNDED, List.of(IN_APP, PUSH, EMAIL));
    m.put(REQUEST_PUBLISHED, List.of(IN_APP, PUSH));
    m.put(SELLER_COURSE_DECISION, List.of(IN_APP, PUSH, EMAIL));
    m.put(SELLER_PAYOUT_PAID, List.of(IN_APP, PUSH, EMAIL));
    m.put(SELLER_NEW_ENROLLMENT, List.of(IN_APP, PUSH));
    m.put(SELLER_APPLICATION_RESULT, List.of(IN_APP, PUSH, EMAIL));
    m.put(ADMIN_BROADCAST, List.of(IN_APP, PUSH));
    DEFAULT_CHANNELS = java.util.Collections.unmodifiableMap(m);
  }

  /** Every type, in contract order. */
  public static final List<String> ALL = List.copyOf(DEFAULT_CHANNELS.keySet());

  /** Never batched or held back by quiet hours. */
  public static final Set<String> URGENT = Set.of(CLASSROOM_DUE_2H, ORDER_PAID, ORDER_REFUNDED);

  private NotificationTypes() {}

  public static boolean isType(String t) {
    return DEFAULT_CHANNELS.containsKey(t);
  }
}
