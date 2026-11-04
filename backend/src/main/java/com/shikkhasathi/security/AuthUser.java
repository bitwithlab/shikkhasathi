package com.shikkhasathi.security;

import java.util.List;
import java.util.UUID;

/** The authenticated caller (from the access token). */
public record AuthUser(UUID id, List<String> roles, UUID sessionId) {
  public boolean has(String role) {
    return roles.contains(role);
  }

  public boolean isAdmin() {
    return has("admin");
  }
}
