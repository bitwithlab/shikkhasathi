package com.shikkhasathi.integration.repository;

import com.shikkhasathi.config.db.CoreRepository;
import com.shikkhasathi.integration.entity.IntegrationTest;
import org.springframework.data.jpa.repository.JpaRepository;

@CoreRepository
public interface IntegrationTestRepository extends JpaRepository<IntegrationTest, String> {}
