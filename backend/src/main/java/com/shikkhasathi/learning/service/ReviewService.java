package com.shikkhasathi.learning.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.Text;
import com.shikkhasathi.learning.dto.LearningDtos.*;
import com.shikkhasathi.learning.repository.LearningContentRepository;
import com.shikkhasathi.learning.repository.LearningCoreRepository;
import com.shikkhasathi.learning.repository.LearningCoreRepository.ReviewRow;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Course reviews: verified purchase with ≥ 20% progress, one per student (edit), seller replies once. */
@Service
public class ReviewService {
  private final LearningAccess acc;
  private final LearningCoreRepository core;
  private final LearningContentRepository content;

  public ReviewService(LearningAccess acc, LearningCoreRepository core, LearningContentRepository content) {
    this.acc = acc;
    this.core = core;
    this.content = content;
  }

  private List<Review> toReviews(List<ReviewRow> rows) {
    if (rows.isEmpty()) return List.of();
    var people = core.userNames(rows.stream().map(ReviewRow::studentId).distinct().toList());
    return rows.stream().map(r -> new Review(r.id(), r.rating(), r.body(), Text.shortName(people.getOrDefault(r.studentId(), "")), r.createdAt(), r.sellerReply(),
        r.sellerRepliedAt(), r.verified())).toList();
  }

  public Page<Review> listReviews(UUID courseId, Integer rating, String cursor, int limit) {
    var rows = core.courseReviews(courseId, rating, QaService.before(cursor), limit);
    var pg = Cursor.page(rows, limit, r -> NotesService.cursorOf(r.createdAt(), r.id()));
    return new Page<>(toReviews(pg.items()), pg.nextCursor());
  }

  private void recomputeRating(UUID courseId) {
    var r = core.ratingOf(courseId);
    content.updateRating(courseId, r.avg() == null ? "0" : r.avg(), r.count());
  }

  @Transactional
  public Review upsertReview(UUID userId, UUID courseId, ReviewBody b) {
    var a = acc.access(userId, courseId);
    if (!a.enrolled()) throw ApiException.forbidden("শুধু কোর্স কেনা শিক্ষার্থীরা রিভিউ দিতে পারেন।");
    if ((a.enrollment() == null ? 0 : a.enrollment().progressPercent()) < PlayerService.REVIEW_MIN_PROGRESS)
      throw ApiException.forbidden("কোর্সের অন্তত " + PlayerService.REVIEW_MIN_PROGRESS + "% শেষ করে রিভিউ দিন — তাহলে রিভিউটা অন্যদের কাজে লাগবে।");
    String body = b.body() == null || b.body().isEmpty() ? null : b.body();
    var r = core.upsertReview(courseId, a.course().sellerId(), userId, b.rating(), body);
    recomputeRating(courseId);
    return toReviews(List.of(r)).get(0);
  }

  public Page<SellerReview> sellerReviews(UUID sellerId, String cursor, int limit) {
    var rows = core.sellerReviews(sellerId, QaService.before(cursor), limit);
    var pg = Cursor.page(rows, limit, r -> NotesService.cursorOf(r.createdAt(), r.id()));
    var base = toReviews(pg.items());
    var titles = content.courseTitles(pg.items().stream().map(ReviewRow::courseId).distinct().toList());
    var items = new ArrayList<SellerReview>();
    for (int i = 0; i < base.size(); i++) {
      UUID cid = pg.items().get(i).courseId();
      items.add(SellerReview.of(base.get(i), cid, titles.getOrDefault(cid, "")));
    }
    return new Page<>(items, pg.nextCursor());
  }

  @Transactional
  public Review replyReview(UUID sellerId, UUID id, String reply) {
    var r = core.review(id).orElseThrow(ApiException::notFound);
    if (!r.sellerId().equals(sellerId)) throw ApiException.forbidden();
    if (r.sellerReply() != null && !r.sellerReply().isEmpty()) throw ApiException.conflict("এই রিভিউর উত্তর আগেই দেওয়া হয়েছে।");
    return toReviews(List.of(core.setSellerReply(id, reply))).get(0);
  }
}
