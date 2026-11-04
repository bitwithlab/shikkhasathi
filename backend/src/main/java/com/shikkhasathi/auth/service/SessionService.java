package com.shikkhasathi.auth.service;

import com.shikkhasathi.auth.dto.AuthDtos.SessionItem;
import com.shikkhasathi.auth.repository.AuthRepository;
import com.shikkhasathi.auth.repository.AuthRepository.NewSession;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.common.ErrorCode;
import com.shikkhasathi.common.Ids;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.JwtService;
import com.shikkhasathi.user.entity.User;
import com.shikkhasathi.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Refresh sessions: issue (random 48-byte token, stored as HMAC with the refresh secret), rotation with reuse
 * detection (a rotated token presented again after the grace window revokes the whole family), logout and the
 * signed-in devices list. Statements run one by one (no wrapping transaction), like the previous API, so the
 * compare-and-set steps ({@code markRotated}) are what guarantees a single winner.
 */
@Service
public class SessionService {
  /** A refresh token presented again within this window after rotation is treated as a race, not theft. */
  static final long ROTATION_GRACE_MS = 30_000;

  /** Where the session was created from; {@code userAgent} is truncated to 300 chars. */
  public record DeviceInfo(String deviceId, String deviceName, String platform, String ip, String userAgent) {}

  public record Tokens(String accessToken, String accessTokenExpiresAt, String refreshToken, UUID sessionId) {}

  public record Refreshed(User user, Tokens tokens) {}

  private final AuthRepository repo;
  private final UserRepository users;
  private final JwtService jwt;
  private final Crypto crypto;

  public SessionService(AuthRepository repo, UserRepository users, JwtService jwt, Crypto crypto) {
    this.repo = repo;
    this.users = users;
    this.jwt = jwt;
    this.crypto = crypto;
  }

  public Tokens issue(User user, DeviceInfo device) {
    return issue(user, device, Ids.uuidv7());
  }

  public Tokens issue(User user, DeviceInfo device, UUID familyId) {
    if ("suspended".equals(user.getStatus())) throw new ApiException(ErrorCode.FORBIDDEN, "আপনার অ্যাকাউন্ট সাময়িকভাবে বন্ধ আছে। সাপোর্টে যোগাযোগ করুন।");
    if ("deleted".equals(user.getStatus())) throw ApiException.unauthorized();
    String refreshToken = Crypto.randomToken(48);
    String ua = device.userAgent() == null ? null : device.userAgent().substring(0, Math.min(300, device.userAgent().length()));
    UUID sid = repo.createSession(new NewSession(user.getId(), familyId, crypto.hmac(refreshToken), device.deviceId(), device.deviceName(),
        device.platform(), device.ip(), ua, Instant.now().plusSeconds(JwtService.REFRESH_TTL_SEC)));
    var signed = jwt.signAccess(new AuthUser(user.getId(), user.getRoles(), sid));
    repo.touch(user.getId());
    return new Tokens(signed.token(), JsonConfig.iso(signed.expiresAt()), refreshToken, sid);
  }

  public Refreshed refresh(String refreshToken, DeviceInfo device) {
    var s = repo.sessionByHash(crypto.hmac(refreshToken)).orElse(null);
    if (s == null || s.revokedAt() != null || s.expiresAt().isBefore(Instant.now())) throw ApiException.unauthorized();
    if (s.rotatedAt() != null) {
      // Reuse of an already-rotated token. Outside the grace window → assume theft and kill the family.
      if (System.currentTimeMillis() - s.rotatedAt().toEpochMilli() > ROTATION_GRACE_MS) repo.revokeFamily(s.familyId());
      throw ApiException.unauthorized();
    }
    if (!repo.markRotated(s.id())) throw ApiException.unauthorized();
    User user = users.findById(s.userId()).orElseThrow(ApiException::unauthorized);
    var d = new DeviceInfo(s.deviceId() != null ? s.deviceId() : device.deviceId(), s.deviceName() != null ? s.deviceName() : device.deviceName(),
        s.platform(), device.ip(), device.userAgent());
    return new Refreshed(user, issue(user, d, s.familyId()));
  }

  public void logout(String refreshToken) {
    if (refreshToken == null || refreshToken.isEmpty()) return;
    repo.sessionByHash(crypto.hmac(refreshToken)).ifPresent(s -> repo.revokeFamily(s.familyId()));
  }

  public List<SessionItem> list(UUID userId, UUID currentSessionId) {
    return repo.activeSessions(userId).stream()
        .map(r -> new SessionItem(r.id(), r.deviceName(), r.platform(), JsonConfig.iso(r.lastSeenAt()), JsonConfig.iso(r.createdAt()), r.id().equals(currentSessionId)))
        .toList();
  }

  public void revoke(UUID userId, UUID sessionId) {
    repo.revokeSession(sessionId, userId).ifPresent(repo::revokeFamily);
  }

  /** Signs the user out everywhere (account deletion). */
  public void revokeAll(UUID userId) {
    repo.activeSessions(userId).forEach(s -> repo.revokeFamily(s.familyId()));
  }
}
