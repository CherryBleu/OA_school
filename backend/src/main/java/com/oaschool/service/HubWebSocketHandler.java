package com.oaschool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaschool.auth.AuthInterceptor;
import com.oaschool.auth.JwtService;
import com.oaschool.common.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class HubWebSocketHandler extends TextWebSocketHandler {
  private static final ObjectMapper mapper = new ObjectMapper();

  private final JwtService jwtService;
  private final JdbcTemplate jdbc;
  private final Map<UUID, Set<WebSocketSession>> projectRooms = new ConcurrentHashMap<>();
  private final Map<UUID, Set<WebSocketSession>> sessionRooms = new ConcurrentHashMap<>();

  public HubWebSocketHandler(JwtService jwtService, JdbcTemplate jdbc) {
    this.jwtService = jwtService;
    this.jdbc = jdbc;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String token = queryParam(session.getUri(), "token");
    Map<String, Object> payload = jwtService.verify(token);
    UUID userId = UUID.fromString(String.valueOf(payload.get("sub")));
    UUID sessionId = UUID.fromString(String.valueOf(payload.get("sid")));
    Integer active = jdbc.queryForObject(
        "SELECT COUNT(*)::int FROM user_session WHERE id = ? AND user_id = ? AND token_hash = ? AND revoked_at IS NULL",
        Integer.class,
        sessionId,
        userId,
        AuthInterceptor.tokenHash(token)
    );
    if (active == null || active == 0) {
      session.close(CloseStatus.NOT_ACCEPTABLE.withReason("SESSION_REVOKED"));
      return;
    }
    session.getAttributes().put("userId", userId);
    session.getAttributes().put("sessionId", sessionId);
    sessionRooms.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(session);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    Map<String, Object> payload = mapper.readValue(message.getPayload(), Map.class);
    String type = String.valueOf(payload.getOrDefault("type", ""));
    if ("JOIN_PROJECT".equals(type)) {
      UUID projectId = UUID.fromString(String.valueOf(payload.get("projectId")));
      UUID userId = (UUID) session.getAttributes().get("userId");
      Integer member = jdbc.queryForObject(
          "SELECT COUNT(*)::int FROM oa_project_member WHERE project_id = ? AND user_id = ?",
          Integer.class,
          projectId,
          userId
      );
      if (member != null && member > 0) {
        projectRooms.computeIfAbsent(projectId, key -> ConcurrentHashMap.newKeySet()).add(session);
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessionRooms.values().forEach(sessions -> sessions.remove(session));
    projectRooms.values().forEach(sessions -> sessions.remove(session));
  }

  public void metricsUpdated(UUID projectId, Map<String, Object> dashboard) {
    broadcastProject(projectId, Map.of(
        "type", "METRICS_UPDATED",
        "projectId", projectId,
        "dashboard", dashboard
    ));
  }

  public void taskRejected(UUID projectId, UUID taskId, String comment) {
    broadcastProject(projectId, Map.of(
        "type", "TASK_REJECTED",
        "projectId", projectId,
        "taskId", taskId,
        "comment", comment
    ));
  }

  public void forceLogout(UUID sessionId, String reason) {
    Set<WebSocketSession> sessions = sessionRooms.getOrDefault(sessionId, Set.of());
    for (WebSocketSession session : List.copyOf(sessions)) {
      send(session, Map.of("type", "FORCE_LOGOUT", "reason", reason));
      try {
        session.close(CloseStatus.NORMAL.withReason("FORCE_LOGOUT"));
      } catch (IOException ignored) {
      }
    }
  }

  private void broadcastProject(UUID projectId, Map<String, Object> payload) {
    Set<WebSocketSession> sessions = projectRooms.getOrDefault(projectId, Set.of());
    for (WebSocketSession session : List.copyOf(sessions)) {
      send(session, payload);
    }
  }

  private void send(WebSocketSession session, Map<String, Object> payload) {
    if (!session.isOpen()) return;
    try {
      session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
    } catch (IOException ignored) {
    }
  }

  private String queryParam(URI uri, String name) {
    if (uri == null || uri.getQuery() == null) {
      throw new ApiException(401, "UNAUTHORIZED", "请先登录");
    }
    for (String part : uri.getQuery().split("&")) {
      String[] pair = part.split("=", 2);
      if (pair.length == 2 && name.equals(pair[0])) {
        return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
      }
    }
    throw new ApiException(401, "UNAUTHORIZED", "请先登录");
  }
}
