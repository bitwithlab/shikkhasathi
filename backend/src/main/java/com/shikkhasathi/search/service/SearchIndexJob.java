package com.shikkhasathi.search.service;

import com.shikkhasathi.common.jobs.JobHandler;
import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.search.adapter.MeiliClient;
import com.shikkhasathi.search.adapter.SearchEngine;
import com.shikkhasathi.search.repository.CourseRows.CourseRow;
import com.shikkhasathi.search.repository.CourseRows.TargetRow;
import com.shikkhasathi.search.repository.SearchRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code search.indexCourse} — upsert published courses, remove everything else. Payload {@code {courseId}} or
 * {@code {all: true}} (reconfigures the index and upserts every published course). No-op when Meilisearch is
 * not configured (the Postgres fallback reads the tables directly).
 */
@Component
public class SearchIndexJob implements JobHandler<SearchIndexJob.Payload> {
  private static final Logger log = LoggerFactory.getLogger(SearchIndexJob.class);

  public record Payload(String courseId, Boolean all) {}

  private final SearchEngine engine;
  private final SearchRepository repo;

  public SearchIndexJob(SearchEngine engine, SearchRepository repo) {
    this.engine = engine;
    this.repo = repo;
  }

  @Override
  public String name() {
    return JobNames.SEARCH_INDEX_COURSE;
  }

  @Override
  public Class<Payload> payloadType() {
    return Payload.class;
  }

  @Override
  public void handle(Payload p) {
    var mo = engine.meili();
    if (mo.isEmpty()) return;
    MeiliClient m = mo.get();
    try {
      if (Boolean.TRUE.equals(p.all())) {
        m.configure();
        List<Map<String, Object>> docs = new ArrayList<>();
        for (UUID id : repo.publishedCourseIds()) {
          var d = courseDocument(id);
          if (d != null) docs.add(d);
        }
        m.upsert(docs);
        return;
      }
      if (p.courseId() == null || p.courseId().isEmpty()) return;
      var doc = courseDocument(UUID.fromString(p.courseId()));
      if (doc != null && "published".equals(doc.get("status"))) m.upsert(List.of(doc));
      else m.remove(p.courseId());
    } catch (RuntimeException e) {
      log.warn("meili index failed: {}", e.getMessage());
      throw e;
    }
  }

  /** One Meilisearch document per course, with the target fields as filterable arrays. */
  public Map<String, Object> courseDocument(UUID courseId) {
    var rows = repo.coursesByIds(List.of(courseId), false);
    if (rows.isEmpty()) return null;
    CourseRow c = rows.get(0);
    List<TargetRow> ts = repo.targets(List.of(courseId));
    List<UUID> instIds = ts.stream().map(TargetRow::institutionId).filter(Objects::nonNull).toList();
    List<UUID> subjIds = ts.stream().map(TargetRow::subjectMasterId).filter(Objects::nonNull).toList();
    var insts = repo.institutionNames(instIds);
    var subs = repo.subjectNames(subjIds);
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("id", c.id().toString());
    d.put("title", c.title());
    d.put("shortDescription", c.shortDescription());
    d.put("sellerName", c.sellerName());
    d.put("sellerId", c.sellerId().toString());
    d.put("examType", c.examType());
    d.put("courseType", c.courseType());
    d.put("language", c.language());
    d.put("price", c.price());
    d.put("status", c.status());
    d.put("institutionIds", instIds.stream().map(UUID::toString).toList());
    d.put("departmentIds", ts.stream().map(TargetRow::departmentId).filter(Objects::nonNull).map(UUID::toString).toList());
    d.put("semesters", ts.stream().map(TargetRow::semester).filter(Objects::nonNull).toList());
    d.put("subjectIds", subjIds.stream().map(UUID::toString).toList());
    d.put("courseCodes", ts.stream().map(TargetRow::courseCode).filter(s -> s != null && !s.isEmpty()).toList());
    d.put("courseCodesNorm", ts.stream().map(t -> QueryExpansion.normalizeCode(t.courseCode() == null ? "" : t.courseCode())).filter(s -> !s.isEmpty()).toList());
    d.put("subjectNames", subs.stream().flatMap(s -> Stream.concat(Stream.of(s.name(), s.nameBn()), s.aliases().stream())).filter(s -> s != null && !s.isEmpty()).toList());
    d.put("institutionNames", insts.stream().flatMap(i -> Stream.concat(Stream.of(i.name(), i.nameBn(), i.shortName()), i.aliases().stream())).filter(s -> s != null && !s.isEmpty()).toList());
    return d;
  }
}
