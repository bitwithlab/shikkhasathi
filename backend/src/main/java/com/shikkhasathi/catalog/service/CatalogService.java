package com.shikkhasathi.catalog.service;

import com.shikkhasathi.catalog.dto.CatalogDtos.*;
import com.shikkhasathi.catalog.entity.InstitutionRequest;
import com.shikkhasathi.catalog.repository.AcademicProfileRepository;
import com.shikkhasathi.catalog.repository.CatalogRepository;
import com.shikkhasathi.catalog.repository.CatalogRepository.Counts;
import com.shikkhasathi.catalog.repository.CatalogRepository.InstitutionRow;
import com.shikkhasathi.catalog.repository.CatalogRepository.SubjectRow;
import com.shikkhasathi.catalog.repository.InstitutionRequestRepository;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.user.dto.MeDto;
import com.shikkhasathi.user.service.MeService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public master data (institutions, departments, programs, subjects), academic profile, onboarding, institution requests. */
@Service
public class CatalogService {
  private final CatalogRepository repo;
  private final AcademicProfileRepository profiles;
  private final InstitutionRequestRepository requests;
  private final MeService me;

  public CatalogService(CatalogRepository repo, AcademicProfileRepository profiles, InstitutionRequestRepository requests, MeService me) {
    this.repo = repo;
    this.profiles = profiles;
    this.requests = requests;
    this.me = me;
  }

  static InstitutionItem toInstitution(InstitutionRow r, Counts c) {
    return new InstitutionItem(r.id(), r.name(), r.nameBn(), r.shortName(), r.slug(), r.type(), r.city(), r.logoUrl(), c == null ? 0 : c.courses(), c == null ? 0 : c.teachers());
  }

  static SubjectItem toSubject(SubjectRow s, Integer n) {
    return new SubjectItem(s.id(), s.name(), s.nameBn(), s.slug(), s.aliases(), s.category(), n == null ? 0 : n);
  }

  static String blankToNull(String s) {
    return s == null || s.isEmpty() ? null : s;
  }

  // ---------------------------------------------------------------- public lists

  @Transactional(transactionManager = "contentTransactionManager", readOnly = true)
  public Page<InstitutionItem> listInstitutions(String q, String type, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    String term = blankToNull(q == null ? null : q.strip());
    var rows = repo.approvedInstitutions(term, type, cur == null ? null : cur.sortString(), cur == null ? null : cur.id(), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(r.name(), r.id().toString()));
    var counts = repo.institutionCounts(pg.items().stream().map(InstitutionRow::id).toList());
    return new Page<>(pg.items().stream().map(r -> toInstitution(r, counts.get(r.id()))).toList(), pg.nextCursor());
  }

  @Transactional(transactionManager = "contentTransactionManager", readOnly = true)
  public InstitutionDetail institutionBySlug(String slug) {
    var inst = repo.institutionBySlug(slug).orElse(null);
    if (inst != null && "merged".equals(inst.status()) && inst.mergedIntoId() != null) inst = repo.institution(inst.mergedIntoId()).orElse(null);
    if (inst == null || !"approved".equals(inst.status())) throw ApiException.notFound("এই প্রতিষ্ঠানটি পাওয়া যায়নি।");
    var id = inst.id();
    var depts = repo.departments(id);
    var subs = repo.institutionSubjects(id);
    var counts = repo.institutionCounts(List.of(id));
    var progs = repo.programsOf(depts.stream().map(CatalogRepository.DepartmentRow::id).toList());
    Map<UUID, Integer> sc = subs.isEmpty() ? Map.of() : repo.subjectCourseCountsIn(id);
    var c = counts.get(id);
    return new InstitutionDetail(
        inst.id(), inst.name(), inst.nameBn(), inst.shortName(), inst.slug(), inst.type(), inst.city(), inst.logoUrl(), c == null ? 0 : c.courses(), c == null ? 0 : c.teachers(),
        inst.website(), inst.aliases(),
        depts.stream().map(d -> new DepartmentDetail(d.id(), d.institutionId(), d.name(), d.nameBn(), d.shortName(), d.icon(),
            progs.stream().filter(p -> p.departmentId().equals(d.id())).map(p -> new ProgramItem(p.id(), p.departmentId(), p.name(), p.level(), p.semesters())).toList(),
            (int) subs.stream().filter(s -> d.id().equals(s.departmentId())).count())).toList(),
        subs.stream().map(s -> new InstitutionSubjectItem(s.id(), s.subject().id(), s.subject().name(), s.subject().slug(), s.departmentId(), s.semester(), s.courseCode(),
            sc.getOrDefault(s.subject().id(), 0))).toList());
  }

  @Transactional(transactionManager = "contentTransactionManager", readOnly = true)
  public Items<DepartmentItem> listDepartments(UUID institutionId) {
    return new Items<>(repo.departments(institutionId).stream().map(d -> new DepartmentItem(d.id(), d.institutionId(), d.name(), d.nameBn(), d.shortName(), d.icon())).toList());
  }

  @Transactional(transactionManager = "contentTransactionManager", readOnly = true)
  public Items<ProgramItem> listPrograms(UUID departmentId) {
    return new Items<>(repo.programs(departmentId).stream().map(p -> new ProgramItem(p.id(), p.departmentId(), p.name(), p.level(), p.semesters())).toList());
  }

  @Transactional(transactionManager = "contentTransactionManager", readOnly = true)
  public Items<?> listSubjects(String q, UUID institutionId, UUID departmentId, Integer semester) {
    if (institutionId != null) {
      var rows = repo.scopedSubjects(institutionId, departmentId, semester != null && semester != 0 ? semester : null);
      var counts = repo.subjectCourseCounts(rows.stream().map(r -> r.subject().id()).toList());
      return new Items<>(rows.stream().map(r -> {
        var s = r.subject();
        return new ScopedSubjectItem(s.id(), s.name(), s.nameBn(), s.slug(), s.aliases(), s.category(), counts.getOrDefault(s.id(), 0), r.courseCode());
      }).toList());
    }
    var rows = repo.approvedSubjects(blankToNull(q == null ? null : q.strip()));
    var counts = repo.subjectCourseCounts(rows.stream().map(SubjectRow::id).toList());
    return new Items<>(rows.stream().map(r -> toSubject(r, counts.get(r.id()))).toList());
  }

  @Transactional(transactionManager = "contentTransactionManager", readOnly = true)
  public SubjectDetail subjectBySlug(String slug) {
    var s = repo.approvedSubjectBySlug(slug).orElseThrow(() -> ApiException.notFound("এই সাবজেক্টটি পাওয়া যায়নি।"));
    var offerings = repo.offerings(s.id());
    var counts = repo.subjectCourseCounts(List.of(s.id()));
    return new SubjectDetail(s.id(), s.name(), s.nameBn(), s.slug(), s.aliases(), s.category(), counts.getOrDefault(s.id(), 0),
        offerings.stream().map(o -> new Offering(o.institutionId(), o.institutionName(), o.institutionShort(), o.institutionSlug(), o.courseCode(), o.semester())).toList());
  }

  // ---------------------------------------------------------------- student academic profile

  private void validateAcademic(AcademicProfileBody a) {
    if (a.institutionId() == null) return;
    var inst = repo.institution(a.institutionId()).orElse(null);
    if (inst == null || !"approved".equals(inst.status())) throw ApiException.badRequest("ইউনিভার্সিটিটি তালিকায় নেই।");
    if (a.departmentId() != null) {
      var d = repo.department(a.departmentId()).orElse(null);
      if (d == null || !d.institutionId().equals(a.institutionId())) throw ApiException.badRequest("বিভাগটি এই ইউনিভার্সিটির নয়।");
    }
    if (a.programId() != null) {
      var p = repo.program(a.programId()).orElse(null);
      if (p == null || !p.departmentId().equals(a.departmentId())) throw ApiException.badRequest("প্রোগ্রামটি এই বিভাগের নয়।");
    }
  }

  public MeDto setAcademicProfile(UUID userId, AcademicProfileBody a) {
    saveAcademic(userId, a);
    return me.getMe(userId);
  }

  private void saveAcademic(UUID userId, AcademicProfileBody a) {
    validateAcademic(a);
    boolean hasInst = a.institutionId() != null;
    profiles.upsertAcademic(userId, new AcademicProfileRepository.Academic(
        a.institutionId(), hasInst ? a.departmentId() : null, hasInst ? a.programId() : null, a.semester(), a.batch(), a.section()));
  }

  /** Optional academic profile + optional first exam plan; marks onboarding complete either way. */
  public MeDto completeOnboarding(UUID userId, OnboardingBody body) {
    if (body.academic() != null) saveAcademic(userId, body.academic());
    var next = body.nextExam();
    if (next != null) {
      Instant date = OffsetDateTime.parse(next.examDate()).toInstant();
      if (date.toEpochMilli() < System.currentTimeMillis() - 86_400_000L) throw ApiException.badRequest("পরীক্ষার তারিখ আজ বা পরের কোনো দিন দিন।");
      profiles.insertExamPlan(userId, next.subjectId(), next.subjectName(), next.courseCode(), next.examType(), date);
    }
    profiles.markOnboardingCompleted(userId);
    return me.getMe(userId);
  }

  // ---------------------------------------------------------------- institution requests

  @Transactional(transactionManager = "contentTransactionManager")
  public IdStatus requestInstitution(UUID userId, InstitutionRequestBody b) {
    if (repo.pendingRequestExists(userId, b.name())) throw ApiException.conflict("এই প্রতিষ্ঠানের জন্য আপনার অনুরোধ আগেই জমা আছে।");
    var row = requests.saveAndFlush(new InstitutionRequest().setRequestedBy(userId).setName(b.name()).setType(b.type()).setCity(b.city()).setNote(b.note()));
    return new IdStatus(row.getId(), row.getStatus());
  }
}
