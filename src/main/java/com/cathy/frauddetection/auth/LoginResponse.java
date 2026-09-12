package com.cathy.frauddetection.auth;

/**
 * Login response body.
 *
 * <p>Carries only what the client needs to act: the token to attach to later
 * requests, and the identity to display. No internal id, no hash.
 */
public record LoginResponse(String token, String username, Role role) {
}
