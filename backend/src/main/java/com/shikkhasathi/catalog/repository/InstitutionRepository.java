package com.shikkhasathi.catalog.repository;

import com.shikkhasathi.catalog.entity.Institution;
import com.shikkhasathi.config.db.ContentRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@ContentRepository
public interface InstitutionRepository extends JpaRepository<Institution, UUID> {}
