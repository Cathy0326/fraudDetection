package com.cathy.frauddetection.config;

import com.cathy.frauddetection.auth.JwtAuthenticationFilter;
import com.cathy.frauddetection.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Replaces Spring Security's servlet defaults with a stateless JWT chain.
 *
 * <p>The defaults assume a server-rendered app with a session cookie and a
 * login form; this application is a JSON API consumed by a separate frontend.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF defends against the browser attaching credentials on its
                // own. Bearer tokens are set explicitly by JS and are never sent
                // automatically, so the attack this guards against cannot occur.
                // This holds only while the token stays out of cookies.
                .csrf(csrf -> csrf.disable())

                // No session is created and none is read: each request carries
                // its own proof. Without this, Spring would still allocate a
                // session and two authentication mechanisms would coexist.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Rules match top to bottom and the first hit wins, so the
                // catch-all must come last or everything below it is dead code.
                .authorizeHttpRequests(auth -> auth
                        // Called before a token exists.
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Container health probe and metrics scraping: no
                        // credentials available at either call site.
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/error").permitAll()
                        // The SPA's own files; the API calls they make are still guarded.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/vite.svg").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Deny by default: a new endpoint added without a rule
                        // should fail closed, not open.
                        .anyRequest().authenticated())

                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                // Must run before the username/password filter so the context is
                // already populated when authorization is evaluated.
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    /**
     * BCrypt salts each hash internally, so identical passwords produce
     * different stored values and precomputed tables are useless. The salt
     * lives inside the 60-character output, which is why the column is sized
     * exactly that wide.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes the manager Spring Security builds from the UserDetailsService
     * and PasswordEncoder, so the login endpoint can delegate credential
     * checking rather than comparing hashes itself.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
