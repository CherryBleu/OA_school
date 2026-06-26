package com.oaschool.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaschool.common.ApiException;
import com.oaschool.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private static final ObjectMapper mapper = new ObjectMapper();
  private final AppProperties properties;

  public JwtService(AppProperties properties) {
    this.properties = properties;
  }

  public String sign(String userId, String sessionId, String deviceId) {
    Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
    Map<String, Object> payload = new HashMap<>();
    payload.put("sub", userId);
    payload.put("sid", sessionId);
    payload.put("did", deviceId);
    payload.put("exp", Instant.now().plusSeconds(7 * 24 * 3600).getEpochSecond());

    String unsigned = encodeJson(header) + "." + encodeJson(payload);
    return unsigned + "." + hmac(unsigned);
  }

  public Map<String, Object> verify(String token) {
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new ApiException(401, "TOKEN_INVALID", "登录已过期，请重新登录");
    }

    String unsigned = parts[0] + "." + parts[1];
    if (!constantEquals(parts[2], hmac(unsigned))) {
      throw new ApiException(401, "TOKEN_INVALID", "登录已过期，请重新登录");
    }

    try {
      Map<String, Object> payload = mapper.readValue(
          Base64.getUrlDecoder().decode(parts[1]),
          new TypeReference<>() {}
      );
      long exp = ((Number) payload.get("exp")).longValue();
      if (Instant.now().getEpochSecond() > exp) {
        throw new ApiException(401, "TOKEN_EXPIRED", "登录已过期，请重新登录");
      }
      return payload;
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException(401, "TOKEN_INVALID", "登录已过期，请重新登录");
    }
  }

  private String encodeJson(Object value) {
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mapper.writeValueAsBytes(value));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  private String hmac(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  private boolean constantEquals(String left, String right) {
    return java.security.MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8),
        right.getBytes(StandardCharsets.UTF_8)
    );
  }
}
