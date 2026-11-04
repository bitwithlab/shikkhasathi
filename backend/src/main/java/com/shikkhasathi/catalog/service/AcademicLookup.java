package com.shikkhasathi.catalog.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Batched cross-database lookups (CORE rows → CONTENT names). Never one query per row. */
@Service
public class AcademicLookup {
  public record InstitutionName(UUID id, String name, String shortName, String slug) {}

  public record DepartmentName(UUID id, String name, String shortName) {}

  public record ProgramName(UUID id, String name) {}

  public record Names(Map<UUID, InstitutionName> institution, Map<UUID, DepartmentName> department, Map<UUID, ProgramName> program) {}

  private final NamedParameterJdbcTemplate content;

  public AcademicLookup(@Qualifier("contentJdbc") NamedParameterJdbcTemplate content) {
    this.content = content;
  }

  public Names lookup(Collection<UUID> institutionIds, Collection<UUID> departmentIds, Collection<UUID> programIds) {
    var inst = new HashMap<UUID, InstitutionName>();
    var dept = new HashMap<UUID, DepartmentName>();
    var prog = new HashMap<UUID, ProgramName>();
    var i = uniq(institutionIds);
    if (!i.isEmpty())
      content.query("select id, name, short_name, slug from institutions where id in (:ids)", Map.of("ids", i), rs -> {
        var id = rs.getObject("id", UUID.class);
        inst.put(id, new InstitutionName(id, rs.getString("name"), rs.getString("short_name"), rs.getString("slug")));
      });
    var d = uniq(departmentIds);
    if (!d.isEmpty())
      content.query("select id, name, short_name from departments where id in (:ids)", Map.of("ids", d), rs -> {
        var id = rs.getObject("id", UUID.class);
        dept.put(id, new DepartmentName(id, rs.getString("name"), rs.getString("short_name")));
      });
    var p = uniq(programIds);
    if (!p.isEmpty())
      content.query("select id, name from programs where id in (:ids)", Map.of("ids", p), rs -> {
        var id = rs.getObject("id", UUID.class);
        prog.put(id, new ProgramName(id, rs.getString("name")));
      });
    return new Names(inst, dept, prog);
  }

  private static List<UUID> uniq(Collection<UUID> ids) {
    return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
  }
}
