package com.shikkhasathi.student.repository;

import com.shikkhasathi.config.db.CoreRepository;
import com.shikkhasathi.student.entity.StudentProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Keyed by user id. */
@CoreRepository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {}
