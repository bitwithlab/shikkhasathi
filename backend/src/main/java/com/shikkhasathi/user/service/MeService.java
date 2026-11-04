package com.shikkhasathi.user.service;

import com.shikkhasathi.catalog.service.AcademicLookup;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.student.repository.StudentProfileRepository;
import com.shikkhasathi.teacher.repository.SellerProfileRepository;
import com.shikkhasathi.user.dto.MeDto;
import com.shikkhasathi.user.entity.User;
import com.shikkhasathi.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the {@code Me} view of a user (profile + academic names from CONTENT + seller status). */
@Service
public class MeService {
  private final UserRepository users;
  private final StudentProfileRepository students;
  private final SellerProfileRepository sellers;
  private final AcademicLookup lookup;

  public MeService(UserRepository users, StudentProfileRepository students, SellerProfileRepository sellers, AcademicLookup lookup) {
    this.users = users;
    this.students = students;
    this.sellers = sellers;
    this.lookup = lookup;
  }

  /** 401 when the user is missing or deleted (same as before). */
  @Transactional(readOnly = true)
  public MeDto getMe(UUID userId) {
    var u = users.findById(userId).orElse(null);
    if (u == null || "deleted".equals(u.getStatus())) throw ApiException.unauthorized();
    return buildMe(u);
  }

  @Transactional(readOnly = true)
  public MeDto buildMe(User u) {
    var profile = students.findById(u.getId()).orElse(null);
    var seller = sellers.findById(u.getId()).orElse(null);
    UUID instId = profile == null ? null : profile.getInstitutionId();
    UUID deptId = profile == null ? null : profile.getDepartmentId();
    UUID progId = profile == null ? null : profile.getProgramId();
    var names = lookup.lookup(instId == null ? List.of() : List.of(instId), deptId == null ? List.of() : List.of(deptId), progId == null ? List.of() : List.of(progId));
    var inst = instId == null ? null : names.institution().get(instId);
    var dept = deptId == null ? null : names.department().get(deptId);
    var prog = progId == null ? null : names.program().get(progId);
    var academic =
        new MeDto.Academic(
            instId,
            inst == null ? null : inst.name(),
            inst == null ? null : inst.shortName(),
            deptId,
            dept == null ? null : dept.name(),
            dept == null ? null : dept.shortName(),
            progId,
            prog == null ? null : prog.name(),
            profile == null ? null : profile.getSemester(),
            profile == null ? null : profile.getBatch(),
            profile == null ? null : profile.getSection());
    return new MeDto(
        u.getId(),
        u.getName(),
        u.getPhone(),
        u.getEmail(),
        u.getEmailVerifiedAt() != null,
        u.getAvatarUrl(),
        u.getRoles(),
        u.getLocale(),
        u.getTheme(),
        profile != null && Boolean.TRUE.equals(profile.getLowDataMode()),
        u.getOnboardingCompletedAt() != null,
        u.getPasswordHash() != null,
        academic,
        seller == null ? null : new MeDto.Seller(seller.getSlug(), seller.getVerificationStatus()),
        JsonConfig.iso(u.getCreatedAt()));
  }
}
