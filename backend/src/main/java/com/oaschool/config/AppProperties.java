package com.oaschool.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppProperties {
  @Value("${app.jwt-secret}")
  private String jwtSecret;

  @Value("${app.cors-origin}")
  private String corsOrigin;

  @Value("${app.llm-api-key}")
  private String llmApiKey;

  @Value("${app.llm-base-url}")
  private String llmBaseUrl;

  @Value("${app.llm-model}")
  private String llmModel;

  @Value("${app.llm-timeout-ms}")
  private long llmTimeoutMs;

  @Value("${app.repo-max-code-chars}")
  private int repoMaxCodeChars;

  @Value("${app.repo-clone-timeout-ms}")
  private long repoCloneTimeoutMs;

  public String jwtSecret() {
    return jwtSecret;
  }

  public List<String> corsOrigins() {
    return Arrays.stream(corsOrigin.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .toList();
  }

  public String llmApiKey() {
    return llmApiKey == null ? "" : llmApiKey.trim();
  }

  public String llmBaseUrl() {
    return llmBaseUrl.replaceAll("/+$", "");
  }

  public String llmModel() {
    return llmModel;
  }

  public Duration llmTimeout() {
    return Duration.ofMillis(llmTimeoutMs);
  }

  public int repoMaxCodeChars() {
    return repoMaxCodeChars;
  }

  public Duration repoCloneTimeout() {
    return Duration.ofMillis(repoCloneTimeoutMs);
  }
}
