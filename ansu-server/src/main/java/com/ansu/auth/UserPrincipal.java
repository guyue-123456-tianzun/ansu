package com.ansu.auth;

public record UserPrincipal(Long id, String role, Long dormId, String displayName) {
}
