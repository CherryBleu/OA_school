package com.oaschool.auth;

import com.oaschool.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
  private final JdbcTemplate jdbc;
  private final JwtService jwtService;

  public AuthInterceptor(JdbcTemplate jdbc, JwtService jwtService) {
    this.jdbc = jdbc;
    this.jwtService = jwtService;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }

    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      throw new ApiException(401, "UNAUTHORIZED", "请先登录");
    }

    String token = header.substring(7);
    Map<String, Object> payload = jwtService.verify(token);
    UUID userId = UUID.fromString(String.valueOf(payload.get("sub")));
    UUID sessionId = UUID.fromString(String.valueOf(payload.get("sid")));

    var rows = jdbc.queryForList(
        """
        SELECT us.id AS session_id, us.revoked_at, u.id, u.username, u.name
        FROM user_session us
        JOIN sys_user u ON u.id = us.user_id
        WHERE us.id = ? AND us.user_id = ? AND us.token_hash = ?
        LIMIT 1
        """,
        sessionId,
        userId,
        tokenHash(token)
    );

    if (rows.isEmpty() || rows.getFirst().get("revoked_at") != null) {
      throw new ApiException(401, "SESSION_REVOKED", "此设备登录已失效");
    }

    Map<String, Object> row = rows.getFirst();
    jdbc.update("UPDATE user_session SET last_seen_at = now() WHERE id = ?", sessionId);
    request.setAttribute("authUser", new AuthUser(
        (UUID) row.get("id"),
        String.valueOf(row.get("username")),
        String.valueOf(row.get("name"))
    ));
    request.setAttribute("sessionId", sessionId);
    return true;
  }

  public static String tokenHash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }
}
