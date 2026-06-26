package com.oaschool.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaschool.auth.AuthUser;
import com.oaschool.common.ApiException;
import com.oaschool.common.Auth;
import com.oaschool.common.Rows;
import com.oaschool.service.DashboardService;
import com.oaschool.service.HubWebSocketHandler;
import com.oaschool.service.LlmService;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/project", "/api/v1/projects"})
public class ProjectController {
  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final LlmService llm;
  private final DashboardService dashboardService;
  private final HubWebSocketHandler hub;

  public ProjectController(JdbcTemplate jdbc, TransactionTemplate tx, LlmService llm, DashboardService dashboardService, HubWebSocketHandler hub) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.llm = llm;
    this.dashboardService = dashboardService;
    this.hub = hub;
  }

  @GetMapping
  public Map<String, Object> list(HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    var projects = jdbc.queryForList(
        """
        SELECT p.*, pm.member_role,
               owner.name AS owner_name,
               (SELECT COUNT(*)::int FROM oa_project_member x WHERE x.project_id = p.id) AS members_count,
               (SELECT COUNT(*)::int FROM oa_task t WHERE t.project_id = p.id) AS tasks_count
        FROM oa_project p
        JOIN oa_project_member pm ON pm.project_id = p.id
        JOIN sys_user owner ON owner.id = p.owner_id
        WHERE pm.user_id = ?
        ORDER BY p.updated_at DESC
        """,
        user.id()
    ).stream().map(Rows::normalize).toList();
    return Map.of("projects", projects);
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    String name = text(body, "name");
    String description = text(body, "description");
    if (name.isBlank() || name.length() > 120) {
      throw new ApiException(422, "VALIDATION_ERROR", "项目名称不能为空");
    }

    String groupId = uniqueGroupId();
    UUID projectId = UUID.randomUUID();
    Map<String, Object> project = tx.execute(status -> {
      jdbc.update(
          """
          INSERT INTO oa_project (id, group_id, name, description, owner_id)
          VALUES (?, ?, ?, ?, ?)
          """,
          projectId,
          groupId,
          name,
          description,
          user.id()
      );
      jdbc.update(
          "INSERT INTO oa_project_member (project_id, user_id, member_role) VALUES (?, ?, 'leader')",
          projectId,
          user.id()
      );
      return jdbc.queryForMap("SELECT * FROM oa_project WHERE id = ?", projectId);
    });
    return ResponseEntity.status(201).body(Map.of("project", Rows.normalize(project)));
  }

  @GetMapping("/join-preview/{groupId}")
  public Map<String, Object> preview(@PathVariable String groupId) {
    var rows = jdbc.queryForList(
        """
        SELECT p.id, p.group_id, p.name, owner.name AS owner_name,
               (SELECT COUNT(*)::int FROM oa_project_member pm WHERE pm.project_id = p.id) AS members_count
        FROM oa_project p
        JOIN sys_user owner ON owner.id = p.owner_id
        WHERE p.group_id = upper(?)
        """,
        groupId
    );
    if (rows.isEmpty()) throw new ApiException(404, "NOT_FOUND", "未找到该小组号");
    return Map.of("preview", Rows.normalize(rows.getFirst()));
  }

  @GetMapping("/info/{groupId}")
  public Map<String, Object> info(@PathVariable String groupId) {
    return preview(groupId);
  }

  @PostMapping("/join")
  public Map<String, Object> join(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    String groupId = text(body, "groupId");
    var projects = jdbc.queryForList("SELECT * FROM oa_project WHERE group_id = upper(?)", groupId);
    if (projects.isEmpty()) throw new ApiException(404, "NOT_FOUND", "未找到该小组号");

    Integer verified = jdbc.queryForObject(
        "SELECT COUNT(*)::int FROM user_skill WHERE user_id = ? AND is_verified = true",
        Integer.class,
        user.id()
    );
    if (verified == null || verified == 0) {
      throw new ApiException(409, "SKILL_VERIFICATION_REQUIRED", "请先完成至少一个 AI 技能核验后再入组");
    }

    UUID projectId = Rows.uuid(projects.getFirst(), "id");
    jdbc.update(
        """
        INSERT INTO oa_project_member (project_id, user_id, member_role)
        VALUES (?, ?, 'member')
        ON CONFLICT (project_id, user_id) DO NOTHING
        """,
        projectId,
        user.id()
    );
    return loadProjectPayload(projectId, user.id());
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable UUID id, HttpServletRequest request) {
    return loadProjectPayload(id, Auth.user(request).id());
  }

  @GetMapping("/{id}/dashboard")
  public Map<String, Object> dashboard(@PathVariable UUID id, HttpServletRequest request) {
    ensureMembership(id, Auth.user(request).id());
    return Map.of("dashboard", dashboardService.dashboard(id));
  }

  @PostMapping("/{id}/wbs")
  public ResponseEntity<Map<String, Object>> generateWbs(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    ensureLeader(id, Auth.user(request).id());
    String requirementText = text(body, "requirementText");
    if (requirementText.length() < 10 || requirementText.length() > 12000) {
      throw new ApiException(422, "VALIDATION_ERROR", "需求文本长度不正确");
    }

    List<Map<String, Object>> members = loadMembers(id);
    Map<String, Object> wbs = llm.decomposeRequirement(requirementText, members);
    List<Map<String, Object>> tasks = (List<Map<String, Object>>) wbs.getOrDefault("tasks", List.of());
    if (tasks.isEmpty()) {
      throw new ApiException(502, "AI_WBS_EMPTY", "AI 未能拆出任务，请调整需求文本");
    }

    Map<UUID, Integer> loads = new HashMap<>();
    List<Map<String, Object>> inserted = tx.execute(status -> {
      jdbc.update("UPDATE oa_project SET requirement_text = ?, updated_at = now() WHERE id = ?", requirementText, id);
      List<Map<String, Object>> rows = new ArrayList<>();
      for (Map<String, Object> task : tasks.stream().limit(24).toList()) {
        UUID assigneeId = selectAssignee(task, members, loads);
        rows.add(insertTask(id, task, assigneeId));
      }
      return rows;
    });

    dashboardService.recalculate(id);
    hub.metricsUpdated(id, dashboardService.dashboard(id));
    return ResponseEntity.status(201).body(Map.of("tasks", inserted));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
    ensureLeader(id, Auth.user(request).id());
    jdbc.update("DELETE FROM oa_project WHERE id = ?", id);
    return ResponseEntity.noContent().build();
  }

  private Map<String, Object> loadProjectPayload(UUID projectId, UUID userId) {
    Map<String, Object> project = ensureMembership(projectId, userId);
    boolean leader = isLeader(project, userId);
    return Map.of(
        "project", Rows.normalize(project),
        "members", loadMembers(projectId),
        "tasks", loadTasks(projectId, userId, leader),
        "dashboard", dashboardService.dashboard(projectId)
    );
  }

  private Map<String, Object> ensureMembership(UUID projectId, UUID userId) {
    var rows = jdbc.queryForList(
        """
        SELECT p.*, pm.member_role
        FROM oa_project p
        JOIN oa_project_member pm ON pm.project_id = p.id
        WHERE p.id = ? AND pm.user_id = ?
        """,
        projectId,
        userId
    );
    if (rows.isEmpty()) throw new ApiException(403, "FORBIDDEN", "你不在该项目组中");
    return rows.getFirst();
  }

  private Map<String, Object> ensureLeader(UUID projectId, UUID userId) {
    Map<String, Object> project = ensureMembership(projectId, userId);
    if (!isLeader(project, userId)) {
      throw new ApiException(403, "FORBIDDEN", "只有组长可以执行该操作");
    }
    return project;
  }

  private boolean isLeader(Map<String, Object> project, UUID userId) {
    return userId.equals(project.get("owner_id")) || "leader".equals(project.get("member_role"));
  }

  private List<Map<String, Object>> loadMembers(UUID projectId) {
    List<Map<String, Object>> members = new ArrayList<>();
    for (Map<String, Object> row : jdbc.queryForList(
        """
        SELECT u.id, u.username, u.name, pm.member_role, pm.joined_at
        FROM oa_project_member pm
        JOIN sys_user u ON u.id = pm.user_id
        WHERE pm.project_id = ?
        ORDER BY pm.member_role DESC, pm.joined_at ASC
        """,
        projectId
    )) {
      members.add(new LinkedHashMap<>(Rows.normalize(row)));
    }

    for (Map<String, Object> member : members) {
      UUID userId = (UUID) member.get("id");
      List<Map<String, Object>> skills = jdbc.queryForList(
          """
          SELECT skill_tag, self_level, is_verified, dynamic_score
          FROM user_skill
          WHERE user_id = ?
          ORDER BY is_verified DESC, dynamic_score DESC
          """,
          userId
      ).stream().map(row -> {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("skillTag", row.get("skill_tag"));
        mapped.put("selfLevel", row.get("self_level"));
        mapped.put("isVerified", row.get("is_verified"));
        mapped.put("dynamicScore", Rows.dbl(row, "dynamic_score"));
        return mapped;
      }).toList();
      member.put("skills", skills);
    }
    return members;
  }

  private List<Map<String, Object>> loadTasks(UUID projectId, UUID userId, boolean leader) {
    String memberFilter = leader ? "" : "AND t.assignee_id = ?";
    Object[] args = leader ? new Object[] {projectId} : new Object[] {projectId, userId};
    return jdbc.queryForList(
        ("""
        SELECT t.*, u.name AS assignee_name
        FROM oa_task t
        LEFT JOIN sys_user u ON u.id = t.assignee_id
        WHERE t.project_id = ?
        %s
        ORDER BY t.created_at ASC
        """).formatted(memberFilter),
        args
    ).stream().map(Rows::normalize).toList();
  }

  private Map<String, Object> insertTask(UUID projectId, Map<String, Object> task, UUID assigneeId) {
    try {
      String title = text(task, "title");
      if (title.isBlank()) title = "未命名任务";
      String description = text(task, "description");
      String category = text(task, "category");
      if (category.isBlank()) category = "通用";
      double estimatedDays = Math.max(0.5, decimal(task.get("estimatedDays"), 1));
      String features = MAPPER.writeValueAsString(asStringList(task.get("features")));

      Map<String, Object> row = jdbc.queryForMap(
          """
          INSERT INTO oa_task
            (project_id, title, description, category, features_json, estimated_days, assignee_id, status)
          VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, 'todo')
          RETURNING *
          """,
          projectId,
          title.length() > 120 ? title.substring(0, 120) : title,
          description,
          category.length() > 40 ? category.substring(0, 40) : category,
          features,
          estimatedDays,
          assigneeId
      );
      return Rows.normalize(row);
    } catch (Exception error) {
      throw new ApiException(500, "TASK_INSERT_FAILED", "任务写入失败");
    }
  }

  private UUID selectAssignee(Map<String, Object> task, List<Map<String, Object>> members, Map<UUID, Integer> loads) {
    return members.stream()
        .map(member -> Map.entry((UUID) member.get("id"), scoreMember(task, member, loads)))
        .max(Comparator.comparingInt(Map.Entry::getValue))
        .map(entry -> {
          UUID id = entry.getKey();
          loads.put(id, loads.getOrDefault(id, 0) + 1);
          return id;
        })
        .orElse(null);
  }

  private int scoreMember(Map<String, Object> task, Map<String, Object> member, Map<UUID, Integer> loads) {
    String taskText = (text(task, "title") + " " + text(task, "description") + " " + text(task, "category")).toLowerCase(Locale.ROOT);
    List<Map<String, Object>> skills = (List<Map<String, Object>>) member.getOrDefault("skills", List.of());
    int verified = 0;
    int matched = 0;
    for (Map<String, Object> skill : skills) {
      if (Boolean.TRUE.equals(skill.get("isVerified"))) {
        verified += 1;
        String tag = String.valueOf(skill.get("skillTag")).toLowerCase(Locale.ROOT);
        if (taskText.contains(tag)) matched = 1;
      }
    }
    return matched * 20 + verified * 3 - loads.getOrDefault((UUID) member.get("id"), 0) * 2;
  }

  private String uniqueGroupId() {
    for (int attempt = 0; attempt < 8; attempt += 1) {
      StringBuilder value = new StringBuilder();
      for (int i = 0; i < 7; i += 1) {
        value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
      }
      Integer count = jdbc.queryForObject("SELECT COUNT(*)::int FROM oa_project WHERE group_id = ?", Integer.class, value.toString());
      if (count == null || count == 0) return value.toString();
    }
    throw new ApiException(500, "GROUP_ID_FAILED", "小组号生成失败，请重试");
  }

  private String text(Map<String, Object> body, String key) {
    Object value = body.get(key);
    return value == null ? "" : String.valueOf(value).trim();
  }

  private double decimal(Object value, double fallback) {
    if (value instanceof Number number) return number.doubleValue();
    try {
      return value == null ? fallback : Double.parseDouble(String.valueOf(value));
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private List<String> asStringList(Object value) {
    if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
    return List.of();
  }
}
