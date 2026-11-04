package com.shikkhasathi.classroom.service;

import static com.shikkhasathi.classroom.service.ClassroomText.*;

import com.shikkhasathi.classroom.repository.ClassroomRepository;
import com.shikkhasathi.classroom.repository.ClassroomRepository.ReminderRow;
import com.shikkhasathi.notification.email.EmailTemplate;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Deadline reminders (every 5 min): 24 h and 2 h before due, plus one overdue nudge for work that became overdue in
 * the last 24 h. Turned-in / returned work is skipped; notifications dedupe per (user, type, coursework).
 */
@Service
public class ClassroomReminderService {
  private record Window(String type, double fromH, double toH) {}

  private static final List<Window> WINDOWS = List.of(new Window(NotificationTypes.CLASSROOM_DUE_24H, 23.5, 24.5), new Window(NotificationTypes.CLASSROOM_DUE_2H, 1.5, 2.5));

  private final ClassroomRepository repo;
  private final NotificationService notifications;

  public ClassroomReminderService(ClassroomRepository repo, NotificationService notifications) {
    this.repo = repo;
    this.notifications = notifications;
  }

  private static boolean submitted(ReminderRow r) {
    return "TURNED_IN".equals(r.subState()) || "RETURNED".equals(r.subState());
  }

  private static Instant plusHours(Instant now, double h) {
    return now.plusMillis((long) (h * 3_600_000));
  }

  /** @return notifications newly created */
  public int runDeadlineReminders(Instant now) {
    int sent = 0;
    for (Window w : WINDOWS) {
      for (ReminderRow r : repo.dueBetween(plusHours(now, w.fromH()), plusHours(now, w.toH()), false)) {
        if (submitted(r)) continue;
        String when = dhaka(r.dueAt());
        Map<String, Object> data = new HashMap<>();
        data.put("title", r.title());
        data.put("courseName", r.courseName());
        data.put("when", when);
        data.put("link", r.alternateLink());
        boolean ok = notifications.notify(NotifyInput.of(r.userId(), w.type(), r.gcCourseworkId(),
                (NotificationTypes.CLASSROOM_DUE_2H.equals(w.type()) ? "আর ২ ঘণ্টা" : "আগামীকাল জমা") + " · " + shortCourse(r.courseName()), r.title() + " — জমা " + when)
            .withLink("/classroom/assignments?id=" + uri(r.gcCourseworkId()))
            .withEmail(EmailTemplate.CLASSROOM_DEADLINE, data));
        if (ok) sent++;
      }
    }
    // one overdue nudge, only for items that became overdue in the last 24 h
    for (ReminderRow r : repo.dueBetween(now.minusMillis(86_400_000L), now, true)) {
      if (submitted(r)) continue;
      boolean ok = notifications.notify(NotifyInput.of(r.userId(), NotificationTypes.CLASSROOM_OVERDUE, r.gcCourseworkId(), "সময় পেরিয়ে গেছে · " + shortCourse(r.courseName()),
          r.title() + " — দেরিতে হলেও Classroom-এ জমা দিন").withLink("/classroom/assignments?id=" + uri(r.gcCourseworkId())));
      if (ok) sent++;
    }
    return sent;
  }
}
