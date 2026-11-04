package com.shikkhasathi.file.service;

import java.util.Arrays;
import java.util.Optional;

/** Storage buckets; each lives in the Supabase project of the data it belongs to (CLAUDE.md §0B). */
public enum Bucket {
  INVOICES("invoices", "core", false),
  VERIFICATION_DOCS("verification-docs", "core", false),
  AVATARS("avatars", "core", false),
  COURSE_DOCS("course-docs", "content", false),
  COURSE_COVERS("course-covers", "content", true);

  private final String id;
  private final String db;
  private final boolean isPublic;

  Bucket(String id, String db, boolean isPublic) {
    this.id = id;
    this.db = db;
    this.isPublic = isPublic;
  }

  /** bucket name as used in storage paths, e.g. {@code course-docs} */
  public String id() {
    return id;
  }

  /** {@code core} or {@code content} — which Supabase project holds it */
  public String db() {
    return db;
  }

  /** public buckets are readable without a signed URL (course covers only) */
  public boolean isPublic() {
    return isPublic;
  }

  /** Lookup by bucket name. */
  public static Optional<Bucket> of(String id) {
    return Arrays.stream(values()).filter(b -> b.id.equals(id)).findFirst();
  }
}
