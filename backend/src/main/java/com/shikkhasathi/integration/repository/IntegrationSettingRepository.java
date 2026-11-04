package com.shikkhasathi.integration.repository;

import com.shikkhasathi.config.db.CoreRepository;
import com.shikkhasathi.integration.entity.IntegrationSetting;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@CoreRepository
public interface IntegrationSettingRepository extends JpaRepository<IntegrationSetting, UUID> {
  List<IntegrationSetting> findByProvider(String provider);

  Optional<IntegrationSetting> findByProviderAndKey(String provider, String key);

  void deleteByProviderAndKey(String provider, String key);
}
