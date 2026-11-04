package com.shikkhasathi.teacher.repository;

import com.shikkhasathi.config.db.CoreRepository;
import com.shikkhasathi.teacher.entity.SellerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Keyed by user id. */
@CoreRepository
public interface SellerProfileRepository extends JpaRepository<SellerProfile, UUID> {
  Optional<SellerProfile> findBySlug(String slug);
}
