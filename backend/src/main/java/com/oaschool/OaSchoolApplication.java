package com.oaschool;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OaSchoolApplication {
  public static void main(String[] args) {
    loadDotenv(Path.of("backend", ".env"));
    loadDotenv(Path.of(".env"));
    SpringApplication.run(OaSchoolApplication.class, args);
  }

  private static void loadDotenv(Path path) {
    if (!Files.exists(path)) return;
    try {
      for (String line : Files.readAllLines(path)) {
        String trimmed = line.trim();
        if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) continue;
        String[] pair = trimmed.split("=", 2);
        String key = pair[0].trim();
        String value = pair[1].trim();
        if (System.getenv(key) == null && System.getProperty(key) == null) {
          System.setProperty(key, value);
        }
      }
    } catch (Exception error) {
      throw new IllegalStateException("Failed to load " + path, error);
    }
  }
}
