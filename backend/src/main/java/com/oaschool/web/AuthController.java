package com.oaschool.web;

import com.oaschool.auth.AuthInterceptor;
import com.oaschool.auth.AuthUser;
import com.oaschool.auth.JwtService;
import com.oaschool.common.ApiException;
import com.oaschool.common.Auth;
import com.oaschool.common.Rows;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final JdbcTemplate jdbc;
  private final JwtService jwtService;
  private final com.oaschool.service.HubWebSocketHandler hub;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

  public AuthController(JdbcTemplate jdbc, JwtService jwtService, com.oaschool.service.HubWebSocketHandler hub) {
    this.jdbc = jdbc;
    this.jwtService = jwtService;
    this.hub = hub;
  }

  @PostMapping("/register")
  public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    String username = text(body, "username");
    String password = text(body, "password");
    String name = text(body, "name");
    validateCredentials(username, password);
    if (name.isBlank()) name = username;

    UUID userId = UUID.randomUUID();
    try {
      jdbc.update(
          "INSERT INTO sys_user (id, username, name, password_hash) VALUES (?, ?, ?, ?)",
          userId,
          username,
          name,
          encoder.encode(password)
      );
    } catch (DuplicateKeyException error) {
      throw new ApiException(409, "USERNAME_EXISTS", "该学号/账号已被注册");
    }

    Map<String, Object> user = publicUser(userId, username, name);
    String token = issueToken(userId, deviceId(body, request), request);
    return ResponseEntity.status(201).body(Map.of("token", token, "user", user));
  }

  @PostMapping("/login")
  public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    String username = text(body, "username");
    String password = text(body, "password");
    validateCredentials(username, password);

    var rows = jdbc.queryForList(
        "SELECT id, username, name, password_hash FROM sys_user WHERE username = ?",
        username
    );
    if (rows.isEmpty() || !encoder.matches(password, String.valueOf(rows.getFirst().get("password_hash")))) {
      throw new ApiException(401, "BAD_CREDENTIALS", "账号或密码错误");
    }

    Map<String, Object> row = rows.getFirst();
    UUID userId = (UUID) row.get("id");
    Map<String, Object> user = publicUser(userId, String.valueOf(row.get("username")), String.valueOf(row.get("name")));
    String token = issueToken(userId, deviceId(body, request), request);
    return Map.of("token", token, "user", user);
  }

  @GetMapping("/me")
  public Map<String, Object> me(HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    return Map.of("user", publicUser(user.id(), user.username(), user.name()));
  }

  @GetMapping("/sessions")
  public Map<String, Object> sessions(HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    UUID sessionId = (UUID) request.getAttribute("sessionId");
    var sessions = jdbc.queryForList(
        """
        SELECT id, device_id, user_agent, last_seen_at, created_at,
               CASE WHEN id = ? THEN true ELSE false END AS current
        FROM user_session
        WHERE user_id = ? AND revoked_at IS NULL
        ORDER BY last_seen_at DESC
        """,
        sessionId,
        user.id()
    ).stream().map(Rows::normalize).toList();
    return Map.of("sessions", sessions);
  }

  @DeleteMapping("/sessions/{id}")
  public ResponseEntity<Void> revokeSession(@PathVariable UUID id, HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    jdbc.update(
        "UPDATE user_session SET revoked_at = now() WHERE id = ? AND user_id = ?",
        id,
        user.id()
    );
    hub.forceLogout(id, "该设备已被下线");
    return ResponseEntity.noContent().build();
  }

  private String issueToken(UUID userId, String deviceId, HttpServletRequest request) {
    UUID sessionId = UUID.randomUUID();
    String token = jwtService.sign(userId.toString(), sessionId.toString(), deviceId);
    jdbc.update(
        """
        INSERT INTO user_session (id, user_id, device_id, user_agent, token_hash)
        VALUES (?, ?, ?, ?, ?)
        """,
        sessionId,
        userId,
        deviceId,
        request.getHeader("User-Agent") == null ? "" : request.getHeader("User-Agent"),
        AuthInterceptor.tokenHash(token)
    );
    return token;
  }

  private Map<String, Object> publicUser(UUID id, String username, String name) {
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", id);
    user.put("username", username);
    user.put("name", name);
    return user;
  }

  private String text(Map<String, Object> body, String key) {
    Object value = body.get(key);
    return value == null ? "" : String.valueOf(value).trim();
  }

  private String deviceId(Map<String, Object> body, HttpServletRequest request) {
    String fromBody = text(body, "deviceId");
    if (!fromBody.isBlank()) return fromBody;
    String snakeCase = text(body, "device_uuid");
    if (!snakeCase.isBlank()) return snakeCase;
    String fromHeader = request.getHeader("X-Device-Id");
    return fromHeader == null || fromHeader.isBlank() ? UUID.randomUUID().toString() : fromHeader;
  }

  private void validateCredentials(String username, String password) {
    if (username.length() < 2 || username.length() > 64 || password.length() < 6 || password.length() > 128) {
      throw new ApiException(422, "VALIDATION_ERROR", "账号或密码格式不正确");
    }
  }
}
