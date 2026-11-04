package com.shikkhasathi.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import tools.jackson.databind.json.JsonMapper;

/** Opaque cursor = base64url(JSON [sortValue, id]) — identical encoding to the previous API. */
public final class Cursor {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private Cursor() {}

  public record Value(Object sort, String id) {
    public String sortString() {
      return String.valueOf(sort);
    }
  }

  public static String encode(Object sortValue, String id) {
    String json = JSON.writeValueAsString(List.of(sortValue, id));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  /** @return null for a missing cursor; throws BAD_REQUEST for a malformed one. */
  public static Value decode(String cursor) {
    if (cursor == null || cursor.isEmpty()) return null;
    try {
      List<?> v = JSON.readValue(Base64.getUrlDecoder().decode(cursor), List.class);
      if (v.size() == 2 && v.get(1) instanceof String id) return new Value(v.get(0), id);
    } catch (RuntimeException ignored) {
      // fallthrough
    }
    throw ApiException.badRequest("অবৈধ cursor");
  }

  /** Given limit+1 rows, return the page and next cursor. */
  public static <T> Page<T> page(List<T> rows, int limit, Function<T, Value> cursorOf) {
    boolean more = rows.size() > limit;
    List<T> items = more ? rows.subList(0, limit) : rows;
    String next = more && !items.isEmpty() ? encode(cursorOf.apply(items.get(items.size() - 1)).sort(), cursorOf.apply(items.get(items.size() - 1)).id()) : null;
    return new Page<>(List.copyOf(items), next);
  }

  public record Page<T>(List<T> items, String nextCursor) {
    public <R> Page<R> map(Function<T, R> f) {
      return new Page<>(items.stream().map(f).toList(), nextCursor);
    }
  }
}
