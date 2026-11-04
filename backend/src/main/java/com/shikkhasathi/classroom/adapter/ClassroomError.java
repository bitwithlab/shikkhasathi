package com.shikkhasathi.classroom.adapter;

/** A classified Google Classroom failure: reauth | workspace | quota | not_found | other. */
public class ClassroomError extends RuntimeException {
  public enum Kind { REAUTH, WORKSPACE, QUOTA, NOT_FOUND, OTHER;
    /** lower-case name as stored in sync runs / returned by runSync ({@code reauth}, {@code not_found}, …) */
    public String key() {
      return name().toLowerCase();
    }
  }

  private final Kind kind;
  private final Integer status;

  public ClassroomError(Kind kind, String message, Integer status) {
    super(message);
    this.kind = kind;
    this.status = status;
  }

  public ClassroomError(Kind kind, String message) {
    this(kind, message, null);
  }

  public Kind kind() {
    return kind;
  }

  public Integer status() {
    return status;
  }
}
