package com.shikkhasathi.common;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** One error envelope for every failure: {@code {"error":{"code","message(Bangla)","details"}}}. */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  public static ResponseEntity<Map<String, Object>> envelope(ErrorCode code, String message, Object details) {
    Map<String, Object> err = new LinkedHashMap<>();
    err.put("code", code.name());
    err.put("message", message != null ? message : code.message);
    if (details != null) err.put("details", details);
    return ResponseEntity.status(code.status).body(Map.of("error", err));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Map<String, Object>> api(ApiException e) {
    if (e.code() == ErrorCode.INTERNAL) log.error("internal error", e);
    return envelope(e.code(), e.getMessage(), e.details());
  }

  /** Bean Validation on @RequestBody — details mirror the previous API: [{path:"body.field", message}]. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException e) {
    List<Map<String, String>> details =
        e.getBindingResult().getFieldErrors().stream().map(f -> Map.of("path", "body." + f.getField(), "message", String.valueOf(f.getDefaultMessage()))).toList();
    String first = details.isEmpty() ? null : details.get(0).get("message");
    return envelope(ErrorCode.VALIDATION_FAILED, first, details);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<Map<String, Object>> invalidParams(HandlerMethodValidationException e) {
    var details = e.getParameterValidationResults().stream()
        .flatMap(r -> r.getResolvableErrors().stream().map(err -> Map.of("path", "query." + r.getMethodParameter().getParameterName(), "message", String.valueOf(err.getDefaultMessage()))))
        .toList();
    return envelope(ErrorCode.VALIDATION_FAILED, details.isEmpty() ? null : details.get(0).get("message"), details);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> violation(ConstraintViolationException e) {
    var details = e.getConstraintViolations().stream().map(v -> Map.of("path", String.valueOf(v.getPropertyPath()), "message", v.getMessage())).toList();
    return envelope(ErrorCode.VALIDATION_FAILED, details.isEmpty() ? null : details.get(0).get("message"), details);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<Map<String, Object>> unreadable(Exception e) {
    return envelope(ErrorCode.VALIDATION_FAILED, null, List.of(Map.of("path", "request", "message", String.valueOf(e.getMessage()))));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<Map<String, Object>> mediaType(HttpMediaTypeNotSupportedException e) {
    return envelope(ErrorCode.BAD_REQUEST, null, null);
  }

  @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
  public ResponseEntity<Map<String, Object>> notFound(Exception e) {
    return envelope(ErrorCode.NOT_FOUND, null, null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
    log.error("unhandled", e);
    return envelope(ErrorCode.INTERNAL, null, null);
  }
}
