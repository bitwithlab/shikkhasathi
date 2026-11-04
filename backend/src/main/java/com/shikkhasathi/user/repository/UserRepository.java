package com.shikkhasathi.user.repository;

import com.shikkhasathi.config.db.CoreRepository;
import com.shikkhasathi.user.entity.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@CoreRepository
public interface UserRepository extends JpaRepository<User, UUID> {}
