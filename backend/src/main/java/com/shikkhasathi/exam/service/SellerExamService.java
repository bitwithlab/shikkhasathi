package com.shikkhasathi.exam.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.exam.dto.ExamDtos.*;
import com.shikkhasathi.exam.repository.ExamContentRepository;
import com.shikkhasathi.exam.repository.ExamContentRepository.OptionRow;
import com.shikkhasathi.exam.repository.ExamContentRepository.QuestionRow;
import com.shikkhasathi.exam.repository.ExamCoreRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;

/** Teacher Studio authoring of MCQ mock tests and flashcards (contract group {@code sellerExam}). */
@Service
public class SellerExamService {
  private final ExamContentRepository content;
  private final ExamCoreRepository core;
  private final AuditService audit;
  private final TransactionTemplate contentTx;

  public SellerExamService(ExamContentRepository content, ExamCoreRepository core, AuditService audit, @Qualifier("contentTransactionManager") PlatformTransactionManager contentTm) {
    this.content = content;
    this.core = core;
    this.audit = audit;
    this.contentTx = new TransactionTemplate(contentTm);
  }

  private void ownCourse(UUID sellerId, UUID courseId) {
    var c = content.course(courseId).orElseThrow(ApiException::notFound);
    if (!sellerId.equals(c.sellerId())) throw ApiException.forbidden();
  }

  /** Mock tests with questions, options (in position order) and attempt counts — batched, no per-test queries. */
  private List<SellerMockTest> sellerMocks(List<ExamContentRepository.TestRow> tests) {
    if (tests.isEmpty()) return List.of();
    List<UUID> ids = tests.stream().map(ExamContentRepository.TestRow::id).toList();
    List<QuestionRow> qs = content.questionsOfTests(ids);
    Map<UUID, List<OptionRow>> opts = content.optionsOf(qs.stream().map(QuestionRow::id).toList()).stream().collect(Collectors.groupingBy(OptionRow::questionId));
    Map<UUID, Integer> attempts = core.attemptCounts(ids);
    List<SellerMockTest> out = new ArrayList<>();
    for (var t : tests) {
      var questions = qs.stream().filter(q -> q.testId().equals(t.id()))
          .map(q -> new SellerQuestion(q.id(), q.body(), q.explanation(), q.chapterTag(),
              opts.getOrDefault(q.id(), List.of()).stream().map(o -> new SellerOption(o.body(), o.isCorrect())).toList()))
          .toList();
      out.add(new SellerMockTest(t.id(), t.title(), t.description(), t.durationSec(), questions, attempts.getOrDefault(t.id(), 0)));
    }
    return out;
  }

  private SellerMockTest sellerMock(UUID id) {
    return sellerMocks(List.of(content.findTest(id).orElseThrow(ApiException::notFound))).get(0);
  }

  private static void checkMock(MockTestInput d) {
    for (int i = 0; i < d.questions().size(); i++) {
      if (d.questions().get(i).options().stream().filter(OptionInput::isCorrect).count() != 1) throw ApiException.badRequest("প্রশ্ন " + (i + 1) + ": ঠিক একটি সঠিক উত্তর বাছাই করুন।");
    }
  }

  private void writeQuestions(UUID testId, MockTestInput d) {
    for (int i = 0; i < d.questions().size(); i++) {
      var q = d.questions().get(i);
      UUID qid = content.insertQuestion(testId, q.body(), q.explanation(), q.chapterTag(), i);
      for (int j = 0; j < q.options().size(); j++) content.insertOption(qid, q.options().get(j).body(), q.options().get(j).isCorrect(), j);
    }
  }

  public SellerMockList listSellerMocks(UUID sellerId, UUID courseId) {
    ownCourse(sellerId, courseId);
    return new SellerMockList(sellerMocks(content.testsOfCourses(List.of(courseId))));
  }

  public SellerMockTest createMock(UUID sellerId, UUID courseId, MockTestInput d) {
    ownCourse(sellerId, courseId);
    checkMock(d);
    UUID id = contentTx.execute(s -> {
      UUID t = content.insertTest(courseId, d.title(), d.description(), d.durationSec());
      writeQuestions(t, d);
      return t;
    });
    audit.log(AuditService.Entry.of(sellerId, "mock.create", "mock_tests", id).after(Map.of("courseId", courseId, "questions", d.questions().size())));
    return sellerMock(id);
  }

  public SellerMockTest updateMock(UUID sellerId, UUID id, MockTestInput d) {
    var t = content.findTest(id).orElseThrow(ApiException::notFound);
    ownCourse(sellerId, t.courseId());
    checkMock(d);
    // open attempts keep their question ids; finished attempts keep scores (review shows what still exists)
    contentTx.executeWithoutResult(s -> {
      content.updateTest(id, d.title(), d.description(), d.durationSec());
      content.deleteQuestionsOf(id);
      writeQuestions(id, d);
    });
    return sellerMock(id);
  }

  public void deleteMock(UUID sellerId, UUID id) {
    var t = content.findTest(id).orElseThrow(ApiException::notFound);
    ownCourse(sellerId, t.courseId());
    content.deleteTest(id);
    audit.log(AuditService.Entry.of(sellerId, "mock.delete", "mock_tests", id));
  }

  public SellerFlashcardList sellerFlashcards(UUID sellerId, UUID courseId) {
    ownCourse(sellerId, courseId);
    return new SellerFlashcardList(content.flashcards(List.of(courseId)).stream().map(r -> new SellerFlashcard(r.id(), r.front(), r.back())).toList());
  }

  public SellerFlashcardList setFlashcards(UUID sellerId, UUID courseId, List<FlashcardInput> cards) {
    ownCourse(sellerId, courseId);
    contentTx.executeWithoutResult(s -> {
      content.deleteFlashcards(courseId);
      for (int i = 0; i < cards.size(); i++) content.insertFlashcard(courseId, cards.get(i).front(), cards.get(i).back(), i);
    });
    return sellerFlashcards(sellerId, courseId);
  }
}
