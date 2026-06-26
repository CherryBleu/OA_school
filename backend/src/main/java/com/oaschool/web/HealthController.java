package com.oaschool.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  @GetMapping("/api/health")
  public Map<String, Object> health() {
    return Map.of("ok", true, "service", "oa-school-api");
  }

  @GetMapping("/api/v1/health")
  public Map<String, Object> healthV1() {
    return health();
  }
}
