package com.shikkhasathi.catalog.repository;

import com.shikkhasathi.catalog.entity.Program;
import com.shikkhasathi.config.db.ContentRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@ContentRepository
public interface ProgramRepository extends JpaRepository<Program, UUID> {}
