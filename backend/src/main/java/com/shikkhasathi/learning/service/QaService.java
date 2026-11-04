package com.shikkhasathi.learning.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.Text;
import com.shikkhasathi.learning.dto.LearningDtos.*;
import com.shikkhasathi.learning.repository.LearningContentRepository;
import com.shikkhasathi.learning.repository.LearningContentRepository.LessonRow;
import com.shikkhasathi.learning.repository.LearningCoreRepository;
import com.shikkhasathi.learning.repository.LearningCoreRepository.AnswerRow;
import com.shikkhasathi.learning.repository.LearningCoreRepository.QuestionRow;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** Lesson Q&A: enrolled students ask, the teacher (or classmates) answer; both sides are notified. */
@Service
public class QaService {
  private final LearningAccess acc;
  private final LearningCoreRepository core;
  private final LearningContentRepository content;
  private final NotificationService notifications;

  public QaService(LearningAccess acc, LearningCoreRepository core, LearningContentRepository content, NotificationService notifications) {
    this.acc = acc;
    this.core = core;
    this.content = content;
    this.notifications = notifications;
  }

  private List<Question> toQuestions(List<QuestionRow> rows, UUID viewerId) {
    if (rows.isEmpty()) return List.of();
    var ans = core.visibleAnswers(rows.stream().map(QuestionRow::id).toList());
    var userIds = Stream.concat(rows.stream().map(QuestionRow::authorId), ans.stream().map(AnswerRow::authorId)).distinct().toList();
    var people = core.userNames(userIds);
    var sellers = core.sellerNames(userIds);
    var lessonTitles = new HashMap<UUID, String>();
    content.lessonTitles(rows.stream().map(QuestionRow::lessonId).distinct().toList()).forEach(t -> lessonTitles.putIfAbsent(t.id(), t.title()));
    java.util.function.BiFunction<UUID, Boolean, String> nameOf = (id, isSeller) -> {
      if (isSeller && sellers.containsKey(id) && sellers.get(id).name() != null) return sellers.get(id).name();
      return Text.shortName(people.getOrDefault(id, ""));
    };
    return rows.stream().map(q -> new Question(q.id(), q.lessonId(), lessonTitles.get(q.lessonId()), q.body(), q.timestampSec(), nameOf.apply(q.authorId(), false),
        q.authorId().equals(viewerId), q.status(), q.createdAt(),
        ans.stream().filter(a -> a.questionId().equals(q.id())).map(a -> new Answer(a.id(), a.body(), nameOf.apply(a.authorId(), a.isSeller()), a.isSeller(), a.createdAt())).toList())).toList();
  }

  private record QaAccess(LessonRow lesson, LearningAccess.Access access) {}

  private QaAccess qaAccess(UUID userId, UUID lessonId, List<String> roles) {
    var l = acc.lessonOr404(lessonId);
    var a = acc.access(userId, l.courseId(), roles);
    if (!a.canSeeAll()) throw ApiException.forbidden("প্রশ্নোত্তর দেখতে কোর্সটিতে এনরোল করুন।");
    return new QaAccess(l, a);
  }

  static Instant before(String cursor) {
    var c = Cursor.decode(cursor);
    if (c == null) return null;
    try {
      return Instant.parse(c.sortString());
    } catch (RuntimeException e) {
      throw ApiException.badRequest("অবৈধ cursor");
    }
  }

  public Page<Question> listQuestions(UUID userId, UUID lessonId, String cursor, int limit, List<String> roles) {
    qaAccess(userId, lessonId, roles);
    var rows = core.lessonQuestions(lessonId, userId, before(cursor), limit);
    var pg = Cursor.page(rows, limit, r -> NotesService.cursorOf(r.createdAt(), r.id()));
    return new Page<>(toQuestions(pg.items(), userId), pg.nextCursor());
  }

  /** Not one transaction (like before): the notification is queued only after the question row is committed. */
  public Question ask(UUID userId, UUID lessonId, AskBody b) {
    var qa = qaAccess(userId, lessonId, List.of());
    var l = qa.lesson();
    if (qa.access().isOwner()) throw ApiException.badRequest("নিজের কোর্সে প্রশ্ন না করে উত্তর দিন।");
    UUID sellerId = qa.access().course().sellerId();
    var q = core.insertQuestion(lessonId, l.courseId(), sellerId, userId, b.body(), b.timestampSec());
    notifications.notify(NotifyInput.of(sellerId, NotificationTypes.SELLER_QA_NEW, q.id().toString(), "নতুন প্রশ্ন এসেছে", l.title() + ": " + head(b.body(), 120))
        .withLink("/learn/" + l.courseId() + "/" + l.id() + "?tab=qa"));
    return toQuestions(List.of(q), userId).get(0);
  }

  public Question answer(UUID userId, UUID questionId, String body) {
    var q = core.question(questionId).orElse(null);
    if (q == null || "hidden".equals(q.status())) throw ApiException.notFound();
    var qa = qaAccess(userId, q.lessonId(), List.of());
    var l = qa.lesson();
    boolean owner = qa.access().isOwner();
    UUID ansId = core.insertAnswer(questionId, userId, body, owner);
    if (owner) core.markAnswered(questionId);
    if (!q.authorId().equals(userId))
      notifications.notify(NotifyInput.of(q.authorId(), NotificationTypes.COURSE_QA_ANSWERED, ansId.toString(), owner ? "শিক্ষক আপনার প্রশ্নের উত্তর দিয়েছেন" : "আপনার প্রশ্নে নতুন উত্তর",
          head(body, 140)).withLink("/learn/" + l.courseId() + "/" + l.id() + "?tab=qa"));
    return toQuestions(List.of(core.question(questionId).orElseThrow()), userId).get(0);
  }

  public Page<SellerQuestion> sellerQuestions(UUID sellerId, String status, String cursor, int limit) {
    var rows = core.sellerQuestions(sellerId, "all".equals(status) ? null : status, before(cursor), limit);
    var pg = Cursor.page(rows, limit, r -> NotesService.cursorOf(r.createdAt(), r.id()));
    var base = toQuestions(pg.items(), sellerId);
    var titles = content.courseTitles(pg.items().stream().map(QuestionRow::courseId).distinct().toList());
    var items = new ArrayList<SellerQuestion>();
    for (int i = 0; i < base.size(); i++) {
      UUID cid = pg.items().get(i).courseId();
      items.add(SellerQuestion.of(base.get(i), cid, titles.getOrDefault(cid, "")));
    }
    return new Page<>(items, pg.nextCursor());
  }

  /** JS {@code s.slice(0, n)} (UTF-16 units). */
  static String head(String s, int n) {
    return s.length() <= n ? s : s.substring(0, n);
  }
}
