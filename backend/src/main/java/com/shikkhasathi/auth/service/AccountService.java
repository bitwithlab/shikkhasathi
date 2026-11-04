package com.shikkhasathi.auth.service;

import com.shikkhasathi.auth.dto.AuthDtos.UpdateMeBody;
import com.shikkhasathi.auth.repository.AuthRepository;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.notification.email.EmailService;
import com.shikkhasathi.notification.email.EmailTemplate;
import com.shikkhasathi.user.dto.MeDto;
import com.shikkhasathi.user.entity.User;
import com.shikkhasathi.user.repository.UserRepository;
import com.shikkhasathi.user.service.MeService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** PATCH /me (profile basics and preferences) and account deletion requests. */
@Service
public class AccountService {
  private final AuthRepository repo;
  private final UserRepository users;
  private final MeService me;
  private final AuthService auth;
  private final SessionService sessions;
  private final EmailService email;

  public AccountService(AuthRepository repo, UserRepository users, MeService me, AuthService auth, SessionService sessions, EmailService email) {
    this.repo = repo;
    this.users = users;
    this.me = me;
    this.auth = auth;
    this.sessions = sessions;
    this.email = email;
  }

  public MeDto updateMe(UUID userId, UpdateMeBody patch) {
    User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
    Map<String, Object> set = new HashMap<>();
    if (patch.getName() != null) set.put("name", patch.getName());
    if (patch.getLocale() != null) set.put("locale", patch.getLocale());
    if (patch.getTheme() != null) set.put("theme", patch.getTheme());
    if (patch.avatarUrlPresent()) set.put("avatar_url", patch.getAvatarUrl());
    String newEmail = patch.getEmail();
    boolean emailChanged = newEmail != null && !newEmail.isEmpty() && !newEmail.equals(user.getEmail());
    if (emailChanged) {
      var other = users.findByEmail(newEmail).orElse(null);
      if (other != null && !other.getId().equals(userId)) throw ApiException.conflict("এই ইমেইল অন্য অ্যাকাউন্টে ব্যবহার হচ্ছে।");
      set.put("email", newEmail);
      set.put("email_verified_at", null);
    }
    if (!set.isEmpty()) {
      repo.updateUser(userId, set);
      user = users.findById(userId).orElseThrow(ApiException::unauthorized);
    }
    if (patch.getLowDataMode() != null) repo.upsertLowDataMode(userId, patch.getLowDataMode());
    if (emailChanged) {
      String code = auth.createEmailCode(newEmail, "email_verify");
      email.send(newEmail, null, EmailTemplate.EMAIL_OTP, Map.of("code", code), null, "user:" + userId, null);
    }
    return me.buildMe(user);
  }

  public void requestDeletion(UUID userId) {
    repo.updateUser(userId, Map.of("status", "pending_deletion", "deletion_requested_at", Instant.now()));
    sessions.revokeAll(userId);
  }
}
