package com.shikkhasathi.catalog.repository;

import com.shikkhasathi.catalog.entity.InstitutionRequest;
import com.shikkhasathi.config.db.ContentRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@ContentRepository
public interface InstitutionRequestRepository extends JpaRepository<InstitutionRequest, UUID> {}
