package com.oaschool.common;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Map<String, Object>> api(ApiException error) {
    return ResponseEntity.status(error.status()).body(body(error.code(), error.getMessage()));
  }

  @ExceptionHandler({
      IllegalArgumentException.class,
      MethodArgumentNotValidException.class
  })
  public ResponseEntity<Map<String, Object>> badRequest(Exception error) {
    return ResponseEntity.status(422).body(body("VALIDATION_ERROR", error.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> internal(Exception error) {
    error.printStackTrace();
    return ResponseEntity.status(500).body(body("INTERNAL_ERROR", "服务器开小差了，请稍后再试"));
  }

  private Map<String, Object> body(String code, String message) {
    return Map.of("error", Map.of("code", code, "message", message));
  }
}
