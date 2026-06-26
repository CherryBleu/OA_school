package com.oaschool.web;

import com.oaschool.auth.AuthUser;
import com.oaschool.common.ApiException;
import com.oaschool.common.Auth;
import com.oaschool.common.Rows;
import com.oaschool.service.DashboardService;
import com.oaschool.service.LlmService;
import com.oaschool.service.RepositoryInspector;
import com.oaschool.service.RepositoryInspector.RepoCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/task", "/api/v1/tasks"})
public class TaskController {
  private static final Set<String> STATUSES = Set.of("todo", "in_progress", "review", "done");
  private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final DashboardService dashboardService;
  private final RepositoryInspector inspector;
  private final LlmService llm;
  private final com.oaschool.service.HubWebSocketHandler hub;

  public TaskController(JdbcTemplate jdbc, TransactionTemplate tx, DashboardService dashboardService, RepositoryInspector inspector, LlmService llm, com.oaschool.service.HubWebSocketHandler hub) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.dashboardService = dashboardService;
    this.inspector = inspector;
    this.llm = llm;
    this.hub = hub;
  }

  @PatchMapping("/{id}")
  public Map<String, Object> patch(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    Map<String, Object> task = loadTaskContext(id, user.id());

    if (body.containsKey("status")) {
      String status = text(body, "status");
      if (!STATUSES.contains(status)) throw new ApiException(422, "VALIDATION_ERROR", "任务状态不正确");
      ensureCanChangeStatus(task, user.id(), status);
      jdbc.update(
          """
          UPDATE oa_task
          SET status = ?,
              residual_progress = CASE WHEN ? = 'done' THEN 100 ELSE residual_progress END,
              updated_at = now()
          WHERE id = ?
          """,
          status,
          status,
          id
      );
    }

    if (body.containsKey("assigneeId")) {
      ensureLeader(task, user.id(), "只有组长可以分配任务");
      UUID assigneeId = nullableUuid(body.get("assigneeId"));
      String reason = text(body, "reason");
      ensureCanChangeAssignee(task, assigneeId, reason);
      ensureProjectMember(Rows.uuid(task, "project_id"), assigneeId);
      jdbc.update("UPDATE oa_task SET assignee_id = ?, updated_at = now() WHERE id = ?", assigneeId, id);
      logTaskEvent(id, Rows.uuid(task, "project_id"), user.id(), "ASSIGNEE_CHANGED", stringUuid(Rows.uuid(task, "assignee_id")), stringUuid(assigneeId), reason);
    }

    double progress = dashboardService.recalculate((UUID) task.get("project_id"));
    hub.metricsUpdated((UUID) task.get("project_id"), dashboardService.dashboard((UUID) task.get("project_id")));
    return Map.of("task", loadTask(id));
  }

  @PostMapping("/{id}/dispatch")
  public Map<String, Object> dispatchOne(@PathVariable UUID id, HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    Map<String, Object> task = loadTaskContext(id, user.id());
    ensureDispatchable(task, user.id());
    Map<String, Object> updated = jdbc.queryForMap(
        """
        UPDATE oa_task
        SET status = 'in_progress', updated_at = now()
        WHERE id = ?
        RETURNING *
        """,
        id
    );
    UUID projectId = Rows.uuid(task, "project_id");
    logTaskEvent(id, projectId, user.id(), "TASK_DISPATCHED", "todo", "in_progress", "");
    double progress = dashboardService.recalculate(projectId);
    hub.metricsUpdated(projectId, dashboardService.dashboard(projectId));
    return Map.of("task", Rows.normalize(updated), "progress", progress);
  }

  @PostMapping("/dispatch")
  public Map<String, Object> dispatchProjectTasks(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    UUID projectId = nullableUuid(body.get("projectId"));
    if (projectId == null) projectId = nullableUuid(body.get("project_id"));
    if (projectId == null) throw new ApiException(422, "VALIDATION_ERROR", "projectId 不能为空");
    ensureProjectLeader(projectId, user.id());
    List<Map<String, Object>> dispatchingTasks = jdbc.queryForList(
        """
        SELECT id
        FROM oa_task
        WHERE project_id = ?
          AND status = 'todo'
          AND assignee_id IS NOT NULL
        """,
        projectId
    );
    int dispatched = jdbc.update(
        """
        UPDATE oa_task
        SET status = 'in_progress', updated_at = now()
        WHERE project_id = ?
          AND status = 'todo'
          AND assignee_id IS NOT NULL
        """,
        projectId
    );
    for (Map<String, Object> task : dispatchingTasks) {
      logTaskEvent(Rows.uuid(task, "id"), projectId, user.id(), "TASK_DISPATCHED", "todo", "in_progress", "批量派发");
    }
    double progress = dashboardService.recalculate(projectId);
    hub.metricsUpdated(projectId, dashboardService.dashboard(projectId));
    return Map.of("dispatched", dispatched, "progress", progress);
  }

  @PostMapping("/submit")
  public ResponseEntity<Map<String, Object>> submitDirect(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    UUID taskId = nullableUuid(body.get("taskId"));
    if (taskId == null) taskId = nullableUuid(body.get("task_id"));
    if (taskId == null) throw new ApiException(422, "VALIDATION_ERROR", "taskId 不能为空");
    Object sourceUrl = body.containsKey("sourceUrl") ? body.get("sourceUrl") : body.get("repoUrl");
    return submit(taskId, Map.of("repoUrl", sourceUrl == null ? "" : sourceUrl), request);
  }

  @PostMapping("/{id}/submit")
  public ResponseEntity<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    Map<String, Object> task = loadTaskContext(id, user.id());
    ensureCanSubmit(task, user.id());

    String repoUrl = text(body, "repoUrl");
    if (repoUrl.isBlank() || repoUrl.length() > 500) {
      throw new ApiException(422, "VALIDATION_ERROR", "仓库链接不能为空");
    }
    inspector.detectProvider(repoUrl);
    jdbc.update(
        """
        UPDATE oa_task
        SET status = 'review',
            ai_review_status = 'pending',
            ai_review_comment = NULL,
            updated_at = now()
        WHERE id = ?
        """,
        id
    );
    logTaskEvent(id, Rows.uuid(task, "project_id"), user.id(), "TASK_SUBMITTED", "in_progress", "review", repoUrl);

    RepoCode repo;
    Map<String, Object> review;
    try {
      repo = inspector.inspect(repoUrl);
      review = llm.reviewRepositoryCode(task, repo.codeText(), repo.fileCount());
    } catch (RuntimeException error) {
      jdbc.update("UPDATE oa_task SET status = 'in_progress', updated_at = now() WHERE id = ?", id);
      throw error;
    }

    String comment = stringValue(firstValue(review, "comment", "feedback", "reason", "message"));
    int parsedScore = number(firstValue(review, "score", "totalScore", "total_score", "grade", "aiScore", "ai_score"), -1);
    Boolean aiPassed = boolValue(firstValue(review, "passed", "pass", "verified", "isVerified", "is_verified"));
    Boolean commentVerdict = verdictFromComment(comment);
    boolean hasMissingFeatures = hasMissingFeatures(review);
    boolean rejectedByComment = looksRejected(comment);
    boolean passed = aiPassed != null
        ? aiPassed
        : (commentVerdict != null
            ? commentVerdict
            : (!hasMissingFeatures && !rejectedByComment && (parsedScore >= 60 || looksPassed(comment))));
    if (aiPassed == null && commentVerdict == null && (hasMissingFeatures || rejectedByComment)) {
      passed = false;
    }
    int residual = passed
        ? 100
        : Math.max(0, Math.min(99, number(firstValue(review, "residualProgress", "residual_progress", "progress", "score"), 0)));
    int score = parsedScore >= 0 ? clampScore(parsedScore) : residual;
    final boolean reviewPassed = passed;
    final int reviewResidual = residual;
    final int reviewScore = score;
    final String reviewComment = comment;
    final RepoCode inspectedRepo = repo;

    Map<String, Object> result = tx.execute(status -> {
      Map<String, Object> submission = jdbc.queryForMap(
          """
          INSERT INTO oa_task_submission
            (task_id, user_id, repo_url, submit_type, ai_passed, ai_score, ai_comment, residual_progress)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          RETURNING *
          """,
          id,
          user.id(),
          repoUrl,
          inspectedRepo.submitType(),
          reviewPassed,
          reviewScore,
          reviewComment,
          reviewResidual
      );

      Map<String, Object> updated = jdbc.queryForMap(
          """
          UPDATE oa_task
          SET status = ?,
              residual_progress = ?,
              ai_review_status = ?,
              ai_review_comment = ?,
              updated_at = now()
          WHERE id = ?
          RETURNING *
          """,
          reviewPassed ? "done" : "in_progress",
          reviewResidual,
          reviewPassed ? "passed" : "rejected",
          reviewComment,
          id
      );
      logTaskEvent(
          id,
          Rows.uuid(task, "project_id"),
          user.id(),
          reviewPassed ? "AI_REVIEW_PASSED" : "AI_REVIEW_REJECTED",
          "review",
          reviewPassed ? "done" : "in_progress",
          reviewComment
      );

      if (reviewPassed && task.get("assignee_id") != null) {
        jdbc.update(
            """
            UPDATE user_skill
            SET dynamic_score = dynamic_score + ?, updated_at = now()
            WHERE user_id = ? AND lower(skill_tag) = lower(?)
            """,
            Rows.dbl(task, "estimated_days"),
            task.get("assignee_id"),
            task.get("category")
        );
      }

      return Map.of(
          "submission", Rows.normalize(submission),
          "task", Rows.normalize(updated)
      );
    });

    double progress = dashboardService.recalculate((UUID) task.get("project_id"));
    hub.metricsUpdated((UUID) task.get("project_id"), dashboardService.dashboard((UUID) task.get("project_id")));
    if (!reviewPassed) {
      hub.taskRejected((UUID) task.get("project_id"), id, reviewComment);
    }
    return ResponseEntity.status(201).body(Map.of(
        "submission", result.get("submission"),
        "task", result.get("task"),
        "progress", progress
    ));
  }

  private Map<String, Object> loadTaskContext(UUID taskId, UUID userId) {
    var rows = jdbc.queryForList(
        """
        SELECT t.*, p.owner_id, p.name AS project_name, pm.member_role
        FROM oa_task t
        JOIN oa_project p ON p.id = t.project_id
        JOIN oa_project_member pm ON pm.project_id = p.id
        WHERE t.id = ? AND pm.user_id = ?
        """,
        taskId,
        userId
    );
    if (rows.isEmpty()) throw new ApiException(404, "NOT_FOUND", "任务不存在或你不在项目中");
    return Rows.normalize(rows.getFirst());
  }

  private Map<String, Object> loadTask(UUID taskId) {
    return Rows.normalize(jdbc.queryForMap(
        """
        SELECT t.*, u.name AS assignee_name
        FROM oa_task t
        LEFT JOIN sys_user u ON u.id = t.assignee_id
        WHERE t.id = ?
        """,
        taskId
    ));
  }

  private void ensureLeader(Map<String, Object> task, UUID userId, String message) {
    if (isLeader(task, userId)) return;
    throw new ApiException(403, "FORBIDDEN", message);
  }

  private void ensureCanSubmit(Map<String, Object> task, UUID userId) {
    if (!isAssignee(task, userId)) {
      throw new ApiException(403, "FORBIDDEN", "只有任务负责人可以提交审查");
    }
    String status = String.valueOf(task.getOrDefault("status", ""));
    if ("todo".equals(status)) {
      throw new ApiException(409, "TASK_NOT_DISPATCHED", "任务待组长派发后才能提交");
    }
    if ("done".equals(status)) {
      throw new ApiException(409, "TASK_ALREADY_DONE", "任务已完成，无需重复提交");
    }
    if ("review".equals(status)) {
      throw new ApiException(409, "TASK_UNDER_REVIEW", "任务正在审查中");
    }
    if (!"in_progress".equals(status)) {
      throw new ApiException(409, "TASK_STATUS_INVALID", "只有进行中的任务可以提交审查");
    }
  }

  private void ensureCanChangeStatus(Map<String, Object> task, UUID userId, String status) {
    if (!isLeader(task, userId)) {
      throw new ApiException(403, "FORBIDDEN", "只有组长可以调整任务状态");
    }
    String currentStatus = String.valueOf(task.getOrDefault("status", ""));
    if ("todo".equals(currentStatus) && "in_progress".equals(status)) {
      throw new ApiException(409, "DISPATCH_REQUIRED", "请使用组长派发启动任务");
    }
    if ("review".equals(status) || "done".equals(status)) {
      throw new ApiException(409, "REVIEW_FLOW_REQUIRED", "请通过提交审查流转任务状态");
    }
  }

  private void ensureCanChangeAssignee(Map<String, Object> task, UUID assigneeId, String reason) {
    String status = String.valueOf(task.getOrDefault("status", ""));
    if ("done".equals(status)) {
      throw new ApiException(409, "TASK_DONE_LOCKED", "任务已完成，不能修改负责人");
    }
    if ("review".equals(status)) {
      throw new ApiException(409, "TASK_UNDER_REVIEW", "任务审查中，不能修改负责人");
    }
    if ("in_progress".equals(status) && reason.isBlank()) {
      throw new ApiException(422, "CHANGE_REASON_REQUIRED", "进行中任务修改负责人必须填写理由");
    }
    UUID currentAssigneeId = Rows.uuid(task, "assignee_id");
    if ((currentAssigneeId == null && assigneeId == null) || (currentAssigneeId != null && currentAssigneeId.equals(assigneeId))) {
      throw new ApiException(409, "ASSIGNEE_NOT_CHANGED", "负责人没有变化");
    }
  }

  private void ensureDispatchable(Map<String, Object> task, UUID userId) {
    ensureLeader(task, userId, "只有组长可以派发任务");
    if (!"todo".equals(String.valueOf(task.getOrDefault("status", "")))) {
      throw new ApiException(409, "TASK_NOT_TODO", "只有待开始任务可以派发");
    }
    if (Rows.uuid(task, "assignee_id") == null) {
      throw new ApiException(422, "ASSIGNEE_REQUIRED", "请先分配负责人再派发");
    }
  }

  private boolean isLeader(Map<String, Object> task, UUID userId) {
    return userId.equals(Rows.uuid(task, "owner_id")) || "leader".equals(task.get("member_role"));
  }

  private boolean isAssignee(Map<String, Object> task, UUID userId) {
    return userId.equals(Rows.uuid(task, "assignee_id"));
  }

  private void ensureProjectMember(UUID projectId, UUID assigneeId) {
    if (assigneeId == null) return;
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*)::int FROM oa_project_member WHERE project_id = ? AND user_id = ?",
        Integer.class,
        projectId,
        assigneeId
    );
    if (count == null || count == 0) {
      throw new ApiException(422, "VALIDATION_ERROR", "负责人必须是当前项目成员");
    }
  }

  private void logTaskEvent(UUID taskId, UUID projectId, UUID actorId, String eventType, String fromValue, String toValue, String reason) {
    jdbc.update(
        """
        INSERT INTO oa_task_event
          (task_id, project_id, actor_id, event_type, from_value, to_value, reason)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        taskId,
        projectId,
        actorId,
        eventType,
        fromValue,
        toValue,
        reason == null ? "" : reason
    );
  }

  private String stringUuid(UUID value) {
    return value == null ? "" : value.toString();
  }

  private void ensureProjectLeader(UUID projectId, UUID userId) {
    var rows = jdbc.queryForList(
        """
        SELECT p.owner_id, pm.member_role
        FROM oa_project p
        JOIN oa_project_member pm ON pm.project_id = p.id
        WHERE p.id = ? AND pm.user_id = ?
        """,
        projectId,
        userId
    );
    if (rows.isEmpty()) throw new ApiException(403, "FORBIDDEN", "你不在该项目组中");
    Map<String, Object> project = rows.getFirst();
    if (!userId.equals(project.get("owner_id")) && !"leader".equals(project.get("member_role"))) {
      throw new ApiException(403, "FORBIDDEN", "只有组长可以派发任务");
    }
  }

  private String text(Map<String, Object> body, String key) {
    Object value = body.get(key);
    return value == null ? "" : String.valueOf(value).trim();
  }

  private int number(Object value, int fallback) {
    if (value instanceof Number number) return number.intValue();
    try {
      if (value == null) return fallback;
      Matcher matcher = NUMBER_PATTERN.matcher(String.valueOf(value));
      if (!matcher.find()) return fallback;
      return Math.round(Float.parseFloat(matcher.group()));
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private int clampScore(int score) {
    return Math.max(0, Math.min(100, score));
  }

  private Object firstValue(Map<String, Object> body, String... keys) {
    for (String key : keys) {
      if (body.containsKey(key) && body.get(key) != null) return body.get(key);
    }
    for (String key : keys) {
      for (Map.Entry<String, Object> entry : body.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(key) && entry.getValue() != null) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private Boolean boolValue(Object value) {
    if (value instanceof Boolean bool) return bool;
    if (value == null) return null;
    String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    if (text.isBlank()) return null;
    if (looksRejected(text)) return false;
    if (text.equals("true") || text.equals("1") || text.equals("yes") || looksPassed(text)) return true;
    if (text.equals("false") || text.equals("0") || text.equals("no")) return false;
    return null;
  }

  private boolean hasMissingFeatures(Map<String, Object> review) {
    Object value = firstValue(review, "missingFeatures", "missing_features", "missing");
    if (value instanceof List<?> list) {
      return list.stream()
          .map(String::valueOf)
          .map(String::trim)
          .anyMatch(item -> !item.isBlank() && !"无".equals(item) && !"none".equalsIgnoreCase(item));
    }
    if (value == null) return false;
    String text = String.valueOf(value).trim();
    return !text.isBlank() && !"[]".equals(text) && !"无".equals(text);
  }

  private boolean looksPassed(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    if (looksRejected(lower)) return false;
    return lower.contains("pass")
        || lower.contains("通过")
        || lower.contains("合格")
        || lower.contains("达标");
  }

  private boolean looksRejected(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    return lower.contains("fail")
        || lower.contains("reject")
        || lower.contains("not pass")
        || lower.contains("未通过")
        || lower.contains("不通过")
        || lower.contains("打回")
        || lower.contains("失败")
        || lower.contains("不合格");
  }

  private Boolean verdictFromComment(String comment) {
    if (comment == null || comment.isBlank()) return null;
    String text = comment.trim();
    int conclusionStart = text.indexOf("【结论】");
    if (conclusionStart >= 0) {
      int conclusionEnd = text.indexOf('【', conclusionStart + "【结论】".length());
      String conclusion = conclusionEnd > conclusionStart
          ? text.substring(conclusionStart, conclusionEnd)
          : text.substring(conclusionStart, Math.min(text.length(), conclusionStart + 36));
      if (looksRejected(conclusion)) return false;
      if (looksPassed(conclusion)) return true;
    }
    String leading = text.substring(0, Math.min(text.length(), 48));
    if (looksRejected(leading)) return false;
    if (looksPassed(leading)) return true;
    return null;
  }

  private UUID nullableUuid(Object value) {
    if (value == null || String.valueOf(value).isBlank()) return null;
    return UUID.fromString(String.valueOf(value));
  }
}
