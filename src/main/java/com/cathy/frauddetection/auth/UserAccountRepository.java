package com.cathy.frauddetection.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for authentication principals.
 *
 * <p>No @Repository annotation: Spring Data generates and registers the proxy
 * from the interface itself, so component scanning is not involved.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /**
     * Looks up a principal by its unique username.
     *
     * <p>Optional rather than a bare entity: "no such user" is a normal outcome
     * of a login attempt, and the caller must translate it into
     * UsernameNotFoundException rather than dereference a null.
     *
     * <p>The method name is parsed by Spring Data to derive the query; it must
     * match the entity's property name exactly (username, not userName).
     */
    Optional<UserAccount> findByUsername(String username);
}
