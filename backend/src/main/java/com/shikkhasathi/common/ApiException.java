package com.shikkhasathi.common;

/** Thrown by services; rendered as {@code {"error":{"code","message","details"}}} by {@link ApiExceptionHandler}. */
public class ApiException extends RuntimeException {
  private final ErrorCode code;
  private final transient Object details;

  public ApiException(ErrorCode code, String message, Object details) {
    super(message != null ? message : code.message);
    this.code = code;
    this.details = details;
  }

  public ApiException(ErrorCode code, String message) {
    this(code, message, null);
  }

  public ApiException(ErrorCode code) {
    this(code, null, null);
  }

  public ErrorCode code() {
    return code;
  }

  public Object details() {
    return details;
  }

  public static ApiException badRequest(String m) { return new ApiException(ErrorCode.BAD_REQUEST, m); }
  public static ApiException badRequest(String m, Object d) { return new ApiException(ErrorCode.BAD_REQUEST, m, d); }
  public static ApiException unauthorized() { return new ApiException(ErrorCode.UNAUTHORIZED); }
  public static ApiException unauthorized(String m) { return new ApiException(ErrorCode.UNAUTHORIZED, m); }
  public static ApiException forbidden() { return new ApiException(ErrorCode.FORBIDDEN); }
  public static ApiException forbidden(String m) { return new ApiException(ErrorCode.FORBIDDEN, m); }
  public static ApiException notFound() { return new ApiException(ErrorCode.NOT_FOUND); }
  public static ApiException notFound(String m) { return new ApiException(ErrorCode.NOT_FOUND, m); }
  public static ApiException conflict(String m) { return new ApiException(ErrorCode.CONFLICT, m); }
  public static ApiException conflict(String m, Object d) { return new ApiException(ErrorCode.CONFLICT, m, d); }
  public static ApiException rateLimited(String m, Object d) { return new ApiException(ErrorCode.RATE_LIMITED, m, d); }
  public static ApiException validation(String m, Object d) { return new ApiException(ErrorCode.VALIDATION_FAILED, m, d); }
}
