package com.shikkhasathi.auth.controller;

import com.shikkhasathi.auth.dto.AuthDtos.*;
import com.shikkhasathi.auth.service.AccountService;
import com.shikkhasathi.auth.service.AuthService;
import com.shikkhasathi.auth.service.SessionService;
import com.shikkhasathi.security.Auth;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.RateLimit;
import com.shikkhasathi.user.dto.MeDto;
import com.shikkhasathi.user.service.MeService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** The signed-in user's account: profile, e-mail verification, password, devices, deletion (group {@code auth}, paths {@code /me*}). */
@RestController
@RequestMapping("/api/v1/me")
@Auth(Auth.Level.USER)
public class AccountController {
  private final MeService me;
  private final AuthService auth;
  private final AccountService account;
  private final SessionService sessions;
  private final AuthCookies cookies;

  public AccountController(MeService me, AuthService auth, AccountService account, SessionService sessions, AuthCookies cookies) {
    this.me = me;
    this.auth = auth;
    this.account = account;
    this.sessions = sessions;
    this.cookies = cookies;
  }

  @GetMapping
  public MeDto get(AuthUser user) {
    return me.getMe(user.id());
  }

  @PatchMapping
  public MeDto update(AuthUser user, @Valid @RequestBody UpdateMeBody body) {
    return account.updateMe(user.id(), body);
  }

  @PostMapping("/verify-email")
  public MeDto verifyEmail(AuthUser user, @Valid @RequestBody EmailVerifyBody body) {
    return me.buildMe(auth.verifyEmail(user.id(), body.code()));
  }

  @PostMapping("/password")
  @RateLimit(max = AuthController.STRICT_MAX, window = AuthController.STRICT_WINDOW)
  public Ok changePassword(AuthUser user, @Valid @RequestBody ChangePasswordBody body) {
    auth.changePassword(user.id(), body.currentPassword(), body.newPassword());
    return Ok.OK;
  }

  @GetMapping("/sessions")
  public SessionList sessions(AuthUser user) {
    return new SessionList(sessions.list(user.id(), user.sessionId()));
  }

  @DeleteMapping("/sessions/{id}")
  public Ok revokeSession(AuthUser user, @PathVariable UUID id) {
    sessions.revoke(user.id(), id);
    return Ok.OK;
  }

  @DeleteMapping
  public Ok delete(AuthUser user, HttpServletResponse res) {
    account.requestDeletion(user.id());
    cookies.clear(res);
    return Ok.OK;
  }
}
