package com.oaschool.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaschool.config.AppProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LlmService {
  private static final ObjectMapper mapper = new ObjectMapper();
  private final AppProperties properties;
  private final HttpClient httpClient;

  public LlmService(AppProperties properties) {
    this.properties = properties;
    this.httpClient = HttpClient.newBuilder().connectTimeout(properties.llmTimeout()).build();
  }

  public Map<String, Object> generateSkillQuiz(Map<String, Object> skill) {
    int level = skillLevel(skill);
    String requestSeed = UUID.randomUUID().toString();
    return chatJson(
        "你是校园项目协作系统里的技能核验官。只返回 JSON，不要 Markdown。",
        Map.of(
            "task", "为用户生成 3 道全新的实战核验题，不能复用上一套题。题目要能判断是否真的具备该技能。",
            "skill", skill,
            "selfLevel", level,
            "difficultyRule", "按 selfLevel 出题：1 星考基础识别和简单操作；2 星考常见任务；3 星考独立完成和排错；4 星考模块设计、协作和质量；5 星考架构权衡、性能优化和代码审查。",
            "requestSeed", requestSeed,
            "requestedAt", Instant.now().toString(),
            "outputShape", Map.of(
                "questions", List.of(Map.of(
                    "id", "q1",
                    "title", "题目标题",
                    "prompt", "题干",
                    "expectedKeywords", List.of("关键词")
                ))
            )
        ),
        () -> fallbackSkillQuiz(skill, level, requestSeed)
    );
  }

  public Map<String, Object> gradeSkillAnswers(Map<String, Object> skill, List<Map<String, Object>> answers) {
    return chatJson(
        "你是严格但直白的技能阅卷官。只返回 JSON，不要 Markdown。评语必须是中文，格式为【结论】+【失分证据】+【复习指令】，50 字左右。",
        Map.of(
            "task", "根据答题内容给出 0-100 分，并给出三段式直白评语。",
            "skill", skill,
            "answers", answers,
            "outputShape", Map.of("score", 82, "comment", "【结论】通过。【失分证据】...【复习指令】...")
        ),
        () -> {
          int len = answers.stream().map(item -> String.valueOf(item.getOrDefault("answer", ""))).mapToInt(String::length).sum();
          int score = Math.min(92, Math.max(35, len / 8));
          String comment = score >= 60
              ? "【结论】通过。【失分证据】细节还不够落地。【复习指令】补充一次真实项目复盘。"
              : "【结论】未通过。【失分证据】答案偏空，缺少实操证据。【复习指令】先完成基础案例练习。";
          return Map.of("score", score, "comment", comment);
        }
    );
  }

  public Map<String, Object> decomposeRequirement(String requirementText, List<Map<String, Object>> members) {
    return chatJson(
        "你是校园 OA 小组项目经理。把原始需求拆成可交付任务。只返回 JSON，不要 Markdown。",
        Map.of(
            "task", "拆解 WBS 任务。每个任务必须包含硬性功能清单 features，用于后续源码审查。",
            "requirementText", requirementText,
            "teamMembers", members,
            "outputShape", Map.of("tasks", List.of(Map.of(
                "title", "任务名",
                "description", "交付说明",
                "category", "前端/后端/AI/测试/数据库/产品",
                "estimatedDays", 2,
                "features", List.of("必须实现的功能点")
            )))
        ),
        () -> fallbackTasks(requirementText)
    );
  }

  public Map<String, Object> reviewRepositoryCode(Map<String, Object> task, String codeText, int fileCount) {
    List<String> features = asStringList(task.get("features_json"));
    return chatJson(
        "你是严格的源码交付审查官。只返回 JSON，不要 Markdown。若任一硬性功能未完全体现，必须判定不通过。",
        Map.of(
            "task", Map.of(
                "title", task.get("title"),
                "description", task.get("description"),
                "features", features
            ),
            "repositoryDigest", Map.of(
                "fileCount", fileCount,
                "codeText", codeText.substring(0, Math.min(codeText.length(), properties.repoMaxCodeChars()))
            ),
            "outputShape", Map.of(
                "passed", false,
                "score", 55,
                "residualProgress", 55,
                "missingFeatures", List.of("缺失功能"),
                "comment", "【结论】打回。【失分证据】缺少...【复习指令】补齐后重新提交。"
            )
        ),
        () -> fallbackReview(features, codeText)
    );
  }

  private Map<String, Object> chatJson(String system, Object user, Fallback fallback) {
    if (properties.llmApiKey().isBlank()) {
      return fallback.get();
    }

    try {
      Map<String, Object> body = Map.of(
          "model", properties.llmModel(),
          "temperature", 0.2,
          "response_format", Map.of("type", "json_object"),
          "messages", List.of(
              Map.of("role", "system", "content", system),
              Map.of("role", "user", "content", mapper.writeValueAsString(user))
          )
      );
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(properties.llmBaseUrl() + "/chat/completions"))
          .timeout(properties.llmTimeout())
          .header("Authorization", "Bearer " + properties.llmApiKey())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return fallback.get();
      }
      Map<String, Object> payload = mapper.readValue(response.body(), new TypeReference<>() {});
      List<Map<String, Object>> choices = (List<Map<String, Object>>) payload.getOrDefault("choices", List.of());
      if (choices.isEmpty()) return fallback.get();
      Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
      return cleanJson(String.valueOf(message.get("content")));
    } catch (Exception error) {
      return fallback.get();
    }
  }

  private Map<String, Object> cleanJson(String content) throws Exception {
    String text = content.trim()
        .replaceFirst("(?is)^```json\\s*", "")
        .replaceFirst("(?is)^```\\s*", "")
        .replaceFirst("(?is)```$", "")
        .trim();
    try {
      return mapper.readValue(text, new TypeReference<>() {});
    } catch (Exception ignored) {
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start >= 0 && end > start) {
        return mapper.readValue(text.substring(start, end + 1), new TypeReference<>() {});
      }
      throw ignored;
    }
  }

  private Map<String, Object> fallbackTasks(String requirementText) {
    String[] parts = requirementText.split("[。；;\\n]");
    List<String> seeds = new ArrayList<>();
    for (String part : parts) {
      String trimmed = part.trim();
      if (trimmed.length() > 8) seeds.add(trimmed);
      if (seeds.size() >= 6) break;
    }
    if (seeds.isEmpty()) {
      seeds = List.of("用户账号注册登录与会话管理", "项目空间创建与小组口令加入", "技能画像维护与核验流程", "需求拆解生成任务看板", "仓库链接提交与质量审查", "项目进度和成员贡献可视化");
    }

    String[] categories = {"后端", "前端", "AI", "测试", "数据库", "产品"};
    List<Map<String, Object>> tasks = new ArrayList<>();
    for (int i = 0; i < seeds.size(); i++) {
      String text = seeds.get(i);
      tasks.add(Map.of(
          "title", text.substring(0, Math.min(text.length(), 26)),
          "description", text,
          "category", categories[i % categories.length],
          "estimatedDays", i < 2 ? 1.5 : 2,
          "features", List.of(text.substring(0, Math.min(text.length(), 14)) + "可用", "输入校验完整", "异常状态有反馈")
      ));
    }
    return Map.of("tasks", tasks);
  }

  private Map<String, Object> fallbackReview(List<String> features, String codeText) {
    String lower = codeText.toLowerCase(Locale.ROOT);
    List<String> missing = features.stream()
        .filter(feature -> {
          String[] words = feature.toLowerCase(Locale.ROOT).split("[\\s,，。:：/\\\\-]+");
          for (String word : words) {
            if (word.length() >= 2 && lower.contains(word)) return false;
          }
          return true;
        })
        .toList();
    boolean passed = codeText.length() > 500 && missing.isEmpty();
    int residual = passed ? 100 : Math.max(20, Math.round(((features.size() - missing.size()) * 80f) / Math.max(features.size(), 1)));
    return Map.of(
        "passed", passed,
        "score", residual,
        "residualProgress", residual,
        "missingFeatures", missing,
        "comment", passed
            ? "【结论】通过。【失分证据】暂无硬性缺口。【复习指令】继续补充测试与文档。"
            : "【结论】打回。【失分证据】" + (missing.isEmpty() ? "核心代码证据不足" : missing.getFirst()) + "。【复习指令】补齐功能后重提。"
    );
  }

  private Map<String, Object> fallbackSkillQuiz(Map<String, Object> skill, int level, String requestSeed) {
    String tag = String.valueOf(skill.getOrDefault("skill_tag", skill.getOrDefault("skillTag", "该技能")));
    String suffix = requestSeed.substring(0, 8);
    List<Map<String, Object>> questions = switch (level) {
      case 1 -> List.of(
          quiz("q1-" + suffix, tag + " 基础识别", "请用自己的话解释 " + tag + " 是什么，并给出一个最小使用场景。"),
          quiz("q2-" + suffix, "入门操作", "如果要在课堂项目里第一次使用 " + tag + "，你会先完成哪 3 个准备步骤？"),
          quiz("q3-" + suffix, "概念边界", "列出一个适合使用 " + tag + " 的场景和一个不适合的场景，并说明原因。")
      );
      case 2 -> List.of(
          quiz("q1-" + suffix, tag + " 常见任务", "请描述你如何用 " + tag + " 完成一个常见功能，并写出关键步骤。"),
          quiz("q2-" + suffix, "配置与验证", "给出一次 " + tag + " 配置后如何验证它生效的检查清单。"),
          quiz("q3-" + suffix, "常见错误", "说出一个 " + tag + " 新手常见错误、表现现象和修复方法。")
      );
      case 3 -> List.of(
          quiz("q1-" + suffix, tag + " 独立交付", "请设计一个可独立交付的小功能，说明你会如何使用 " + tag + " 实现。"),
          quiz("q2-" + suffix, "问题定位", "描述一次 " + tag + " 相关问题的排查路径：你会先看什么、再验证什么？"),
          quiz("q3-" + suffix, "质量标准", "如果你提交一个 " + tag + " 任务，至少需要哪些测试或验收点？")
      );
      case 4 -> List.of(
          quiz("q1-" + suffix, tag + " 模块设计", "请为一个小组项目模块设计 " + tag + " 方案，说明边界、接口和协作方式。"),
          quiz("q2-" + suffix, "复杂排错", "当 " + tag + " 模块在线上出现间歇性问题时，你会如何收集证据并缩小范围？"),
          quiz("q3-" + suffix, "代码评审", "请列出评审同学 " + tag + " 代码时最关注的 5 个点，并说明风险。")
      );
      default -> List.of(
          quiz("q1-" + suffix, tag + " 架构权衡", "请比较两种 " + tag + " 技术方案，说明性能、维护性和团队成本取舍。"),
          quiz("q2-" + suffix, "优化方案", "一个 " + tag + " 模块变慢且难维护，你会如何制定优化计划和回滚预案？"),
          quiz("q3-" + suffix, "专家审查", "请给出一份 " + tag + " 高风险变更的审查清单，包含监控、测试和发布策略。")
      );
    };
    return Map.of("questions", questions, "requestSeed", requestSeed, "selfLevel", level);
  }

  private Map<String, Object> quiz(String id, String title, String prompt) {
    return Map.of("id", id, "title", title, "prompt", prompt, "expectedKeywords", List.of("项目", "方案", "质量"));
  }

  private int skillLevel(Map<String, Object> skill) {
    Object value = skill.containsKey("self_level") ? skill.get("self_level") : skill.get("selfLevel");
    if (value instanceof Number number) return Math.max(1, Math.min(5, number.intValue()));
    try {
      return Math.max(1, Math.min(5, Integer.parseInt(String.valueOf(value))));
    } catch (Exception ignored) {
      return 3;
    }
  }

  private List<String> asStringList(Object value) {
    if (value instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  @FunctionalInterface
  private interface Fallback {
    Map<String, Object> get();
  }
}
