package com.oaschool.web;

import com.oaschool.auth.AuthUser;
import com.oaschool.common.ApiException;
import com.oaschool.common.Auth;
import com.oaschool.common.Rows;
import com.oaschool.service.LlmService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {
  private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

  private final JdbcTemplate jdbc;
  private final LlmService llm;

  public SkillController(JdbcTemplate jdbc, LlmService llm) {
    this.jdbc = jdbc;
    this.llm = llm;
  }

  @GetMapping
  public Map<String, Object> list(HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    var skills = jdbc.queryForList(
        """
        SELECT id, skill_tag, self_level, is_verified, ai_score, ai_comment, dynamic_score, updated_at
        FROM user_skill
        WHERE user_id = ?
        ORDER BY is_verified DESC, dynamic_score DESC, updated_at DESC
        """,
        user.id()
    ).stream().map(Rows::normalize).toList();
    return Map.of("skills", skills);
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    AuthUser user = Auth.user(request);
    String tag = text(body, "skillTag");
    int level = number(body.get("selfLevel"), 3);
    if (tag.isBlank() || tag.length() > 60 || level < 1 || level > 5) {
      throw new ApiException(422, "VALIDATION_ERROR", "技能标签或星级不正确");
    }

    var existing = jdbc.queryForList(
        "SELECT * FROM user_skill WHERE user_id = ? AND lower(skill_tag) = lower(?)",
        user.id(),
        tag
    );

    Map<String, Object> skill;
    if (existing.isEmpty()) {
      skill = jdbc.queryForMap(
          """
          INSERT INTO user_skill (user_id, skill_tag, self_level)
          VALUES (?, ?, ?)
          RETURNING *
          """,
          user.id(),
          tag,
          level
      );
      return ResponseEntity.status(201).body(Map.of("skill", Rows.normalize(skill)));
    }

    Map<String, Object> old = existing.getFirst();
    boolean changed = Rows.integer(old, "self_level") != level;
    skill = jdbc.queryForMap(
        """
        UPDATE user_skill
        SET skill_tag = ?,
            self_level = ?,
            is_verified = CASE WHEN ? THEN false ELSE is_verified END,
            ai_score = CASE WHEN ? THEN NULL ELSE ai_score END,
            ai_comment = CASE WHEN ? THEN NULL ELSE ai_comment END,
            updated_at = now()
        WHERE id = ?
        RETURNING *
        """,
        tag,
        level,
        changed,
        changed,
        changed,
        old.get("id")
    );
    return ResponseEntity.ok(Map.of("skill", Rows.normalize(skill)));
  }

  @PostMapping("/{id}/quiz")
  public Map<String, Object> quiz(@PathVariable UUID id, HttpServletRequest request) {
    Map<String, Object> skill = getOwnedSkill(id, Auth.user(request).id());
    return llm.generateSkillQuiz(Rows.normalize(skill));
  }

  @PostMapping("/{id}/verify")
  public Map<String, Object> verify(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    Map<String, Object> skill = getOwnedSkill(id, Auth.user(request).id());
    List<Map<String, Object>> answers = (List<Map<String, Object>>) body.getOrDefault("answers", List.of());
    if (answers.isEmpty()) {
      throw new ApiException(422, "VALIDATION_ERROR", "请提交答题内容");
    }

    Map<String, Object> result = llm.gradeSkillAnswers(Rows.normalize(skill), answers);
    int parsedScore = number(firstValue(result, "score", "totalScore", "total_score", "grade", "aiScore", "ai_score"), -1);
    String comment = stringValue(firstValue(result, "comment", "feedback", "reason", "message"));
    Boolean aiPassed = boolValue(firstValue(result, "passed", "pass", "verified", "isVerified", "is_verified"));
    boolean commentPassed = looksPassed(comment);
    int score = parsedScore >= 0
        ? clampScore(parsedScore)
        : (Boolean.TRUE.equals(aiPassed) || commentPassed ? 60 : 0);
    boolean verified = Boolean.TRUE.equals(aiPassed) || score >= 60 || (parsedScore < 0 && commentPassed);
    if (Boolean.FALSE.equals(aiPassed) && parsedScore < 60) {
      verified = false;
    }
    Map<String, Object> updated = jdbc.queryForMap(
        """
        UPDATE user_skill
        SET is_verified = ?,
            ai_score = ?,
            ai_comment = ?,
            updated_at = now()
        WHERE id = ?
        RETURNING *
        """,
        verified,
        score,
        comment,
        id
    );
    return Map.of("skill", Rows.normalize(updated));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
    jdbc.update("DELETE FROM user_skill WHERE id = ? AND user_id = ?", id, Auth.user(request).id());
    return ResponseEntity.noContent().build();
  }

  private Map<String, Object> getOwnedSkill(UUID skillId, UUID userId) {
    var rows = jdbc.queryForList("SELECT * FROM user_skill WHERE id = ? AND user_id = ?", skillId, userId);
    if (rows.isEmpty()) throw new ApiException(404, "NOT_FOUND", "技能不存在");
    return rows.getFirst();
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

  private boolean looksPassed(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    if (looksRejected(lower)) return false;
    return lower.contains("pass")
        || lower.contains("\u901a\u8fc7")
        || lower.contains("\u5408\u683c")
        || lower.contains("\u8fbe\u6807");
  }

  private boolean looksRejected(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    return lower.contains("fail")
        || lower.contains("reject")
        || lower.contains("not pass")
        || lower.contains("\u672a\u901a\u8fc7")
        || lower.contains("\u4e0d\u901a\u8fc7")
        || lower.contains("\u6253\u56de")
        || lower.contains("\u5931\u8d25")
        || lower.contains("\u4e0d\u5408\u683c");
  }
}
