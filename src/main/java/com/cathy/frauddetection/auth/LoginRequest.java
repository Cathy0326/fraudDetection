package com.cathy.frauddetection.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Login request body.
 *
 * <p>A dedicated record rather than the UserAccount entity: the entity carries
 * an id, a hash and a creation timestamp, none of which a caller may supply.
 * The wire shape is defined by the endpoint, not by the table.
 */
public record LoginRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password) {
}
