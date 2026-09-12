package com.cathy.frauddetection.auth;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Adapts a persisted UserAccount to the UserDetails contract Spring Security
 * consumes during login.
 *
 * <p>This class is the only place the two models meet, which is why the entity
 * itself does not implement UserDetails: the table shape and the framework
 * contract can then change independently.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;

    public AppUserDetailsService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        // The contract requires an exception, not null: returning null surfaces
        // later as an NPE inside the framework, far from its cause.
        // Spring Security converts this to BadCredentialsException before it
        // reaches the client, so a failed login cannot reveal whether the
        // username exists.
        UserAccount account = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(account.getRole().authority())))
                .disabled(!account.isEnabled())
                .build();
    }
}
