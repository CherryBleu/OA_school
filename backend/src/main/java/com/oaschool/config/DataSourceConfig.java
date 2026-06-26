package com.oaschool.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DataSourceConfig {
  @Bean
  public DataSource dataSource(Environment env) {
    String raw = env.getProperty(
        "DATABASE_URL",
        "jdbc:postgresql://localhost:5432/oa_school"
    );
    HikariConfig config = new HikariConfig();

    if (raw.startsWith("postgres://") || raw.startsWith("postgresql://")) {
      URI uri = URI.create(raw);
      String[] userInfo = uri.getUserInfo() == null ? new String[] {"", ""} : uri.getUserInfo().split(":", 2);
      String username = decode(userInfo.length > 0 ? userInfo[0] : "");
      String password = decode(userInfo.length > 1 ? userInfo[1] : "");
      String jdbcUrl = "jdbc:postgresql://" + uri.getHost()
          + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
          + uri.getPath()
          + "?sslmode=require";
      config.setJdbcUrl(jdbcUrl);
      config.setUsername(username);
      config.setPassword(password);
    } else {
      config.setJdbcUrl(raw);
      config.setUsername(env.getProperty("DB_USERNAME", "postgres"));
      config.setPassword(env.getProperty("DB_PASSWORD", "postgres"));
    }

    config.setMaximumPoolSize(Integer.parseInt(env.getProperty("DB_POOL_SIZE", "5")));
    return new HikariDataSource(config);
  }

  private String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
