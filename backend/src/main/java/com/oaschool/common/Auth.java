package com.oaschool.common;

import com.oaschool.auth.AuthUser;
import jakarta.servlet.http.HttpServletRequest;

public final class Auth {
  private Auth() {}

  public static AuthUser user(HttpServletRequest request) {
    return (AuthUser) request.getAttribute("authUser");
  }
}
