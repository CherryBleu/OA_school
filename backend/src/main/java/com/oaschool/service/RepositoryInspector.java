package com.oaschool.service;

import com.oaschool.common.ApiException;
import com.oaschool.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class RepositoryInspector {
  private static final Map<String, String> HOSTS = Map.of(
      "github.com", "GITHUB",
      "www.github.com", "GITHUB",
      "gitee.com", "GITEE",
      "www.gitee.com", "GITEE"
  );
  private static final Set<String> SKIPPED_DIRS = Set.of(
      ".git", "node_modules", "target", "dist", "build", "coverage",
      ".next", ".nuxt", ".cache", "vendor", "bin", "obj", "__pycache__"
  );
  private static final Set<String> ALLOWED_EXTS = new HashSet<>(List.of(
      ".js", ".jsx", ".ts", ".tsx", ".vue", ".java", ".py", ".go", ".rs",
      ".php", ".cs", ".html", ".css", ".scss", ".sql", ".json", ".yml", ".yaml", ".md"
  ));

  private final AppProperties properties;

  public RepositoryInspector(AppProperties properties) {
    this.properties = properties;
  }

  public String detectProvider(String repoUrl) {
    URI uri;
    try {
      uri = URI.create(repoUrl);
    } catch (Exception error) {
      throw new ApiException(422, "INVALID_REPO_URL", "仓库链接格式不正确");
    }

    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new ApiException(422, "INVALID_REPO_URL", "仅支持 HTTPS GitHub/Gitee 仓库链接");
    }
    String provider = HOSTS.get(uri.getHost().toLowerCase(Locale.ROOT));
    if (provider == null) {
      throw new ApiException(422, "INVALID_REPO_URL", "仅支持 GitHub 或 Gitee 仓库链接");
    }
    if (uri.getPath() == null || uri.getPath().split("/").length < 3) {
      throw new ApiException(422, "INVALID_REPO_URL", "仓库链接缺少 owner/repo 路径");
    }
    return provider;
  }

  public RepoCode inspect(String repoUrl) {
    String submitType = detectProvider(repoUrl);
    Path tmpRoot = null;
    try {
      tmpRoot = Files.createTempDirectory("oa-review-" + Instant.now().toEpochMilli());
      Path cloneDir = tmpRoot.resolve("repo");
      Process process = new ProcessBuilder("git", "clone", "--depth", "1", repoUrl, cloneDir.toString())
          .redirectErrorStream(true)
          .start();
      boolean finished = process.waitFor(properties.repoCloneTimeout().toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new ApiException(400, "REPO_CLONE_FAILED", "仓库拉取超时，请确认仓库公开可访问");
      }
      if (process.exitValue() != 0) {
        throw new ApiException(400, "REPO_CLONE_FAILED", "仓库拉取失败，请确认链接公开可访问且分支存在");
      }

      StringBuilder code = new StringBuilder();
      int[] fileCount = {0};
      collect(cloneDir, cloneDir, code, fileCount);
      if (code.toString().trim().isBlank()) {
        throw new ApiException(422, "EMPTY_REPO_CODE", "仓库中没有可审查的源码文件");
      }
      return new RepoCode(submitType, code.toString(), fileCount[0]);
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException(400, "REPO_CLONE_FAILED", "仓库拉取失败，请确认链接公开可访问且分支存在");
    } finally {
      deleteQuietly(tmpRoot);
    }
  }

  private void collect(Path root, Path dir, StringBuilder code, int[] fileCount) throws IOException {
    try (var stream = Files.list(dir)) {
      for (Path path : stream.toList()) {
        if (Files.isDirectory(path)) {
          if (!SKIPPED_DIRS.contains(path.getFileName().toString())) {
            collect(root, path, code, fileCount);
          }
          continue;
        }

        if (!Files.isRegularFile(path)) continue;
        String name = path.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXTS.contains(ext)) continue;
        if (Files.size(path) > 200000 || code.length() >= properties.repoMaxCodeChars()) continue;

        String text = Files.readString(path, StandardCharsets.UTF_8);
        int remaining = properties.repoMaxCodeChars() - code.length();
        code.append("\n\n--- FILE: ")
            .append(root.relativize(path))
            .append(" ---\n")
            .append(text, 0, Math.min(text.length(), remaining));
        fileCount[0] += 1;
      }
    }
  }

  private void deleteQuietly(Path path) {
    if (path == null) return;
    try (var stream = Files.walk(path)) {
      stream.sorted(Comparator.reverseOrder()).forEach(item -> {
        try {
          Files.deleteIfExists(item);
        } catch (IOException ignored) {
        }
      });
    } catch (IOException ignored) {
    }
  }

  public record RepoCode(String submitType, String codeText, int fileCount) {}
}
