package com.oaschool.auth;

import java.util.UUID;

public record AuthUser(UUID id, String username, String name) {}
