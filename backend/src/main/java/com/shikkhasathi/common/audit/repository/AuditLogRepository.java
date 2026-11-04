package com.shikkhasathi.common.audit.repository;

import com.shikkhasathi.common.audit.entity.AuditLog;
import com.shikkhasathi.config.db.CoreRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@CoreRepository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {}
