package com.shikkhasathi.catalog.repository;

import com.shikkhasathi.catalog.entity.Department;
import com.shikkhasathi.config.db.ContentRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@ContentRepository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {}
