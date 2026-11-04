package com.shikkhasathi.catalog.service;

import com.shikkhasathi.catalog.dto.CatalogDtos.*;
import com.shikkhasathi.catalog.entity.Department;
import com.shikkhasathi.catalog.entity.Institution;
import com.shikkhasathi.catalog.entity.InstitutionSubject;
import com.shikkhasathi.catalog.entity.Program;
import com.shikkhasathi.catalog.entity.SubjectMaster;
import com.shikkhasathi.catalog.repository.*;
import com.shikkhasathi.catalog.repository.CatalogRepository.Counts;
import com.shikkhasathi.catalog.repository.CatalogRepository.InstitutionRow;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.common.audit.service.AuditService.Entry;
import com.shikkhasathi.config.JsonConfig;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin master data: institution request queue (approve / merge / reject), institutions, departments, programs, subjects. */
@Service
public class AdminCatalogService {
  private final CatalogRepository repo;
  private final AcademicProfileRepository core;
  private final InstitutionRepository institutions;
  private final InstitutionRequestRepository requests;
  private final DepartmentRepository departments;
  private final ProgramRepository programs;
  private final SubjectMasterRepository subjects;
  private final InstitutionSubjectRepository institutionSubjects;
  private final AuditService audit;

  public AdminCatalogService(CatalogRepository repo, AcademicProfileRepository core, InstitutionRepository institutions, InstitutionRequestRepository requests,
                             DepartmentRepository departments, ProgramRepository programs, SubjectMasterRepository subjects,
                             InstitutionSubjectRepository institutionSubjects, AuditService audit) {
    this.repo = repo;
    this.core = core;
    this.institutions = institutions;
    this.requests = requests;
    this.departments = departments;
    this.programs = programs;
    this.subjects = subjects;
    this.institutionSubjects = institutionSubjects;
    this.audit = audit;
  }

  /** Map that keeps null values (audit JSON mirrors the previous API's objects). */
  private static Map<String, Object> obj(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  // ---------------------------------------------------------------- institution requests

  private static final java.util.regex.Pattern NORM = java.util.regex.Pattern.compile("university|বিশ্ববিদ্যালয়|ইউনিভার্সিটি|[^a-z0-9ঀ-৿]");

  static String norm(String s) {
    return NORM.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("");
  }

  @Transactional(transactionManager = "contentTransactionManager", readOnly = true)
  public Page<InstitutionRequestItem> listRequests(String status, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    Instant after = null;
    if (cur != null) {
      try {
        after = Instant.parse(cur.sortString());
      } catch (RuntimeException e) {
        throw ApiException.badRequest("অবৈধ cursor");
      }
    }
    var rows = repo.requests(status, after, cur == null ? null : cur.id(), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.createdAt()), r.id().toString()));
    // batched cross-DB lookup of requesters (CORE)
    var byId = core.people(pg.items().stream().map(CatalogRepository.RequestRow::requestedBy).distinct().toList()).stream()
        .collect(Collectors.toMap(AcademicProfileRepository.Person::id, Function.identity()));
    var all = repo.nonMergedInstitutions();
    var items = pg.items().stream().map(r -> {
      String a = norm(r.name());
      var similar = all.stream()
          .filter(i -> Stream.concat(Stream.of(i.name(), i.shortName()), i.aliases().stream()).anyMatch(n -> {
            String b = norm(n);
            return b.length() > 1 && (a.contains(b) || b.contains(a));
          }))
          .limit(5)
          .map(i -> new Similar(i.id(), i.name(), i.shortName()))
          .toList();
      var p = byId.get(r.requestedBy());
      return new InstitutionRequestItem(r.id(), r.name(), r.type(), r.city(), r.note(), r.status(), p == null ? null : new Requester(p.id(), p.name()), similar,
          JsonConfig.iso(r.createdAt()));
    }).toList();
    return new Page<>(items, pg.nextCursor());
  }

  static String slugify(String s) {
    String v = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    return v.length() > 60 ? v.substring(0, 60) : v;
  }

  /** {@code d} is one of ApproveDecision / MergeDecision / RejectDecision. */
  @Transactional(transactionManager = "contentTransactionManager")
  public Ok decideRequest(UUID id, Object d, UUID actorId) {
    var req = requests.findById(id).orElseThrow(ApiException::notFound);
    if (!"pending".equals(req.getStatus())) throw ApiException.conflict("এই অনুরোধের সিদ্ধান্ত আগেই হয়েছে।");
    String beforeStatus = req.getStatus();
    UUID resolved = null;
    String status;
    String action;
    if (d instanceof ApproveDecision a) {
      action = "approve";
      String name = a.name() != null ? a.name() : req.getName();
      String slug = a.slug() != null ? a.slug() : (slugify(a.shortName()).isEmpty() ? slugify(name) : slugify(a.shortName()));
      if (repo.institutionIdBySlug(slug).isPresent()) throw ApiException.conflict("এই slug আগেই আছে — merge করুন বা অন্য slug দিন।");
      var inst = institutions.saveAndFlush(new Institution().setName(name).setShortName(a.shortName()).setSlug(slug).setType(req.getType())
          .setCity(a.city() != null ? a.city() : req.getCity()).setStatus("approved"));
      resolved = inst.getId();
      status = "approved";
    } else if (d instanceof MergeDecision m) {
      action = "merge";
      var target = institutions.findById(m.institutionId()).orElseThrow(ApiException::notFound);
      if (!target.getAliases().contains(req.getName())) {
        var aliases = new ArrayList<>(target.getAliases());
        aliases.add(req.getName());
        target.setAliases(aliases);
        institutions.saveAndFlush(target);
      }
      resolved = target.getId();
      status = "merged";
    } else {
      action = "reject";
      status = "rejected";
    }
    req.setStatus(status).setResolvedInstitutionId(resolved).setReviewedBy(actorId).setReviewedAt(Instant.now());
    requests.saveAndFlush(req);
    audit.log(Entry.of(actorId, "institution_request." + action, "institution_requests", id).before(obj("status", beforeStatus)).after(obj("status", status, "resolved", resolved)));
    return Ok.OK;
  }

  // ---------------------------------------------------------------- institutions

  static AdminInstitution toAdmin(Institution r, Counts c) {
    return new AdminInstitution(r.getId(), r.getName(), r.getNameBn(), r.getShortName(), r.getSlug(), r.getType(), r.getCity(), r.getLogoUrl(),
        c == null ? 0 : c.courses(), c == null ? 0 : c.teachers(), r.getStatus(), r.getAliases(), r.getWebsite());
  }

  static AdminInstitution toAdmin(InstitutionRow r, Counts c) {
    return new AdminInstitution(r.id(), r.name(), r.nameBn(), r.shortName(), r.slug(), r.type(), r.city(), r.logoUrl(),
        c == null ? 0 : c.courses(), c == null ? 0 : c.teachers(), r.status(), r.aliases(), r.website());
  }

  /** Row snapshot for the audit log (camelCase, like the previous API's rows). */
  static Map<String, Object> snapshot(Institution r) {
    return obj("id", r.getId(), "name", r.getName(), "nameBn", r.getNameBn(), "shortName", r.getShortName(), "slug", r.getSlug(), "type", r.getType(), "city", r.getCity(),
        "logoUrl", r.getLogoUrl(), "website", r.getWebsite(), "aliases", List.copyOf(r.getAliases()), "status", r.getStatus(), "mergedIntoId", r.getMergedIntoId(),
        "createdAt", JsonConfig.iso(r.getCreatedAt()), "updatedAt", JsonConfig.iso(r.getUpdatedAt()));
  }

  @Transactional(transactionManager = "contentTransactionManager", readOnly = true)
  public Items<AdminInstitution> listInstitutions(String q, String status) {
    var rows = repo.adminInstitutions(q, status);
    var counts = repo.institutionCounts(rows.stream().map(InstitutionRow::id).toList());
    return new Items<>(rows.stream().map(r -> toAdmin(r, counts.get(r.id()))).toList());
  }

  /** Create ({@code id == null}) or patch: only keys in {@code present} are changed. */
  @Transactional(transactionManager = "contentTransactionManager")
  public AdminInstitution upsertInstitution(UUID actorId, InstitutionUpsert b, Set<String> present, UUID id) {
    if (b.slug() != null && !b.slug().isEmpty()) {
      var clash = repo.institutionIdBySlug(b.slug());
      if (clash.isPresent() && !clash.get().equals(id)) throw ApiException.conflict("এই slug আগেই ব্যবহার হয়েছে।");
    }
    if (id != null) {
      var row = institutions.findById(id).orElseThrow(ApiException::notFound);
      var before = snapshot(row);
      if (present.contains("name")) row.setName(b.name());
      if (present.contains("nameBn")) row.setNameBn(b.nameBn());
      if (present.contains("shortName")) row.setShortName(b.shortName());
      if (present.contains("slug")) row.setSlug(b.slug());
      if (present.contains("type")) row.setType(b.type());
      if (present.contains("city")) row.setCity(b.city());
      if (present.contains("website")) row.setWebsite(b.website());
      if (present.contains("logoUrl")) row.setLogoUrl(b.logoUrl());
      if (present.contains("aliases")) row.setAliases(new ArrayList<>(b.aliases()));
      if (present.contains("status")) row.setStatus(b.status());
      row.setUpdatedAt(Instant.now());
      row = institutions.saveAndFlush(row);
      audit.log(Entry.of(actorId, "institution.update", "institutions", id).before(before).after(snapshot(row)));
      return toAdmin(row, null);
    }
    var row = institutions.saveAndFlush(new Institution().setName(b.name()).setNameBn(b.nameBn()).setShortName(b.shortName()).setSlug(b.slug()).setType(b.type())
        .setCity(b.city()).setWebsite(b.website()).setLogoUrl(b.logoUrl()).setAliases(new ArrayList<>(b.aliases() == null ? List.of() : b.aliases()))
        .setStatus(b.status() != null ? b.status() : "approved"));
    audit.log(Entry.of(actorId, "institution.create", "institutions", row.getId()).after(snapshot(row)));
    return toAdmin(row, null);
  }

  /** Merge duplicate: move departments/subjects/targets/profile refs to {@code intoId}, keep the names as aliases. */
  @Transactional(transactionManager = "contentTransactionManager")
  public Ok mergeInstitution(UUID actorId, UUID id, UUID intoId) {
    if (id.equals(intoId)) throw ApiException.badRequest("একই প্রতিষ্ঠানে merge করা যায় না।");
    var from = institutions.findById(id).orElse(null);
    var into = institutions.findById(intoId).orElse(null);
    if (from == null || into == null) throw ApiException.notFound();
    try {
      repo.moveDepartments(id, intoId);
    } catch (DataAccessException e) {
      throw ApiException.conflict("একই নামের বিভাগ দুই জায়গাতেই আছে — আগে বিভাগ মুছুন।");
    }
    repo.moveInstitutionSubjects(id, intoId);
    repo.moveCourseTargets(id, intoId);
    var aliases = new LinkedHashSet<String>(into.getAliases());
    aliases.add(from.getName());
    aliases.add(from.getShortName());
    aliases.addAll(from.getAliases());
    into.setAliases(new ArrayList<>(aliases));
    from.setStatus("merged").setMergedIntoId(intoId);
    institutions.saveAllAndFlush(List.of(into, from));
    // CORE refs (no cross-DB FK) — updated in the service, idempotent.
    core.moveInstitution(id, intoId);
    audit.log(Entry.of(actorId, "institution.merge", "institutions", id).after(obj("intoId", intoId)));
    return Ok.OK;
  }

  // ---------------------------------------------------------------- departments / programs

  static DepartmentItem toDept(Department d) {
    return new DepartmentItem(d.getId(), d.getInstitutionId(), d.getName(), d.getNameBn(), d.getShortName(), d.getIcon());
  }

  static Map<String, Object> snapshot(Department d) {
    return obj("id", d.getId(), "institutionId", d.getInstitutionId(), "name", d.getName(), "nameBn", d.getNameBn(), "shortName", d.getShortName(), "icon", d.getIcon(),
        "position", d.getPosition(), "createdAt", JsonConfig.iso(d.getCreatedAt()), "updatedAt", JsonConfig.iso(d.getUpdatedAt()));
  }

  @Transactional(transactionManager = "contentTransactionManager")
  public DepartmentItem upsertDepartment(UUID actorId, DepartmentUpsert b, Set<String> present, UUID id) {
    Department row;
    try {
      if (id != null) {
        row = departments.findById(id).orElseThrow(ApiException::notFound);
        if (present.contains("institutionId")) row.setInstitutionId(b.institutionId());
        if (present.contains("name")) row.setName(b.name());
        if (present.contains("nameBn")) row.setNameBn(b.nameBn());
        if (present.contains("shortName")) row.setShortName(b.shortName());
        if (present.contains("icon")) row.setIcon(b.icon());
        row.setUpdatedAt(Instant.now());
      } else {
        row = new Department().setInstitutionId(b.institutionId()).setName(b.name()).setNameBn(b.nameBn()).setShortName(b.shortName()).setIcon(b.icon());
      }
      row = departments.saveAndFlush(row);
    } catch (DataIntegrityViolationException e) {
      if (isUniqueViolation(e)) throw ApiException.conflict("এই সংক্ষিপ্ত নামে বিভাগ আগেই আছে।");
      throw e;
    }
    audit.log(Entry.of(actorId, id != null ? "department.update" : "department.create", "departments", row.getId()).after(snapshot(row)));
    return toDept(row);
  }

  private static boolean isUniqueViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause())
      if (t instanceof java.sql.SQLException s && "23505".equals(s.getSQLState())) return true;
    return false;
  }

  @Transactional(transactionManager = "contentTransactionManager")
  public Ok deleteDepartment(UUID actorId, UUID id) {
    if (repo.departmentHasTargets(id)) throw ApiException.conflict("এই বিভাগে কোর্স টার্গেট আছে — মুছে ফেলা যাবে না।");
    departments.deleteAllByIdInBatch(List.of(id));
    audit.log(Entry.of(actorId, "department.delete", "departments", id));
    return Ok.OK;
  }

  @Transactional(transactionManager = "contentTransactionManager")
  public ProgramItem createProgram(UUID actorId, ProgramUpsert b) {
    var row = programs.saveAndFlush(new Program().setDepartmentId(b.departmentId()).setName(b.name())
        .setLevel(b.level() != null ? b.level() : "undergraduate").setSemesters(b.semesters() != null ? b.semesters() : 8));
    audit.log(Entry.of(actorId, "program.create", "programs", row.getId()).after(obj("id", row.getId(), "departmentId", row.getDepartmentId(), "name", row.getName(),
        "level", row.getLevel(), "semesters", row.getSemesters(), "createdAt", JsonConfig.iso(row.getCreatedAt()), "updatedAt", JsonConfig.iso(row.getUpdatedAt()))));
    return new ProgramItem(row.getId(), row.getDepartmentId(), row.getName(), row.getLevel(), row.getSemesters());
  }

  @Transactional(transactionManager = "contentTransactionManager")
  public Ok deleteProgram(UUID actorId, UUID id) {
    programs.deleteAllByIdInBatch(List.of(id));
    audit.log(Entry.of(actorId, "program.delete", "programs", id));
    return Ok.OK;
  }

  // ---------------------------------------------------------------- subjects

  @Transactional(transactionManager = "contentTransactionManager", readOnly = true)
  public Items<AdminSubject> listSubjects(String q, String status) {
    var rows = repo.adminSubjects(q, status);
    var counts = repo.subjectCourseCounts(rows.stream().map(CatalogRepository.SubjectRow::id).toList());
    return new Items<>(rows.stream().map(r -> new AdminSubject(r.id(), r.name(), r.nameBn(), r.slug(), r.aliases(), r.category(), counts.getOrDefault(r.id(), 0), r.status())).toList());
  }

  static Map<String, Object> snapshot(SubjectMaster s) {
    return obj("id", s.getId(), "name", s.getName(), "nameBn", s.getNameBn(), "slug", s.getSlug(), "aliases", List.copyOf(s.getAliases()), "category", s.getCategory(),
        "status", s.getStatus(), "proposedBy", s.getProposedBy(), "createdAt", JsonConfig.iso(s.getCreatedAt()), "updatedAt", JsonConfig.iso(s.getUpdatedAt()));
  }

  /** Create ({@code id == null}; status is not settable on create) or patch the keys in {@code present}. */
  @Transactional(transactionManager = "contentTransactionManager")
  public SubjectItem upsertSubject(UUID actorId, SubjectUpsert b, Set<String> present, UUID id) {
    if (b.slug() != null && !b.slug().isEmpty()) {
      var clash = repo.subjectIdBySlug(b.slug());
      if (clash.isPresent() && !clash.get().equals(id)) throw ApiException.conflict("এই slug আগেই ব্যবহার হয়েছে।");
    }
    SubjectMaster row;
    if (id != null) {
      row = subjects.findById(id).orElseThrow(ApiException::notFound);
      if (present.contains("name")) row.setName(b.name());
      if (present.contains("nameBn")) row.setNameBn(b.nameBn());
      if (present.contains("slug")) row.setSlug(b.slug());
      if (present.contains("aliases")) row.setAliases(new ArrayList<>(b.aliases()));
      if (present.contains("category")) row.setCategory(b.category());
      if (present.contains("status")) row.setStatus(b.status());
      row.setUpdatedAt(Instant.now());
    } else {
      row = new SubjectMaster().setName(b.name()).setNameBn(b.nameBn()).setSlug(b.slug()).setAliases(new ArrayList<>(b.aliases() == null ? List.of() : b.aliases()))
          .setCategory(b.category() != null ? b.category() : "general");
    }
    row = subjects.saveAndFlush(row);
    audit.log(Entry.of(actorId, id != null ? "subject.update" : "subject.create", "subject_masters", row.getId()).after(snapshot(row)));
    return new SubjectItem(row.getId(), row.getName(), row.getNameBn(), row.getSlug(), row.getAliases(), row.getCategory(), 0);
  }

  @Transactional(transactionManager = "contentTransactionManager")
  public IdOnly createInstitutionSubject(UUID actorId, InstitutionSubjectUpsert b) {
    var row = institutionSubjects.saveAndFlush(new InstitutionSubject().setInstitutionId(b.institutionId()).setDepartmentId(b.departmentId()).setProgramId(b.programId())
        .setSemester(b.semester()).setSubjectMasterId(b.subjectMasterId()).setCourseCode(b.courseCode()));
    audit.log(Entry.of(actorId, "institution_subject.create", "institution_subjects", row.getId()).after(b));
    return new IdOnly(row.getId());
  }

  @Transactional(transactionManager = "contentTransactionManager")
  public Ok deleteInstitutionSubject(UUID actorId, UUID id) {
    institutionSubjects.deleteAllByIdInBatch(List.of(id));
    audit.log(Entry.of(actorId, "institution_subject.delete", "institution_subjects", id));
    return Ok.OK;
  }
}
