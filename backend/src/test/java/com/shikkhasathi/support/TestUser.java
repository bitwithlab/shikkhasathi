package com.shikkhasathi.support;

import java.util.List;
import java.util.UUID;

/** A user created for a test, with a valid access token. */
public record TestUser(UUID id, String name, String email, String phone, List<String> roles, UUID sessionId, String token) {
  public String bearer() {
    return "Bearer " + token;
  }
}
