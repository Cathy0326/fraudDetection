package com.cathy.frauddetection.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exchanges a username and password for a signed token.
 *
 * <p>Credential checking is delegated to the AuthenticationManager rather than
 * comparing hashes here, so account state (disabled, locked) and the framework's
 * normalisation of "no such user" into "bad credentials" both apply for free.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository repository;
    private final JwtService jwtService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserAccountRepository repository,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.repository = repository;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));
        } catch (AuthenticationException e) {
            // One response for every failure mode: wrong password, unknown user,
            // disabled account. Distinguishing them would let a caller enumerate
            // valid usernames.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Reached only after successful authentication, so the row exists.
        UserAccount account = repository.findByUsername(request.username()).orElseThrow();

        return ResponseEntity.ok(new LoginResponse(
                jwtService.issue(account.getUsername(), account.getRole()),
                account.getUsername(),
                account.getRole()));
    }
}
