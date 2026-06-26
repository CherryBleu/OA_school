package com.oaschool.config;

import com.oaschool.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.oaschool.service.HubWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebConfig implements WebMvcConfigurer, WebSocketConfigurer {
  private final AppProperties properties;
  private final AuthInterceptor authInterceptor;
  private final HubWebSocketHandler hubWebSocketHandler;

  public WebConfig(AppProperties properties, AuthInterceptor authInterceptor, HubWebSocketHandler hubWebSocketHandler) {
    this.properties = properties;
    this.authInterceptor = authInterceptor;
    this.hubWebSocketHandler = hubWebSocketHandler;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOrigins(properties.corsOrigins().toArray(String[]::new))
        .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor)
        .addPathPatterns("/api/v1/**")
        .excludePathPatterns("/api/v1/health", "/api/v1/auth/login", "/api/v1/auth/register");
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(hubWebSocketHandler, "/ws/hub")
        .setAllowedOrigins(properties.corsOrigins().toArray(String[]::new));
  }
}
