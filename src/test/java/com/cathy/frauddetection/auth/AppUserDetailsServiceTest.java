package com.cathy.frauddetection.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AppUserDetailsServiceTest {

    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQ";

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final AppUserDetailsService service = new AppUserDetailsService(repository);

    @Test
    void copiesUsernameAndHashFromTheAccount() {
        when(repository.findByUsername("analyst"))
                .thenReturn(Optional.of(new UserAccount("analyst", HASH, Role.ANALYST)));

        UserDetails details = service.loadUserByUsername("analyst");

        assertThat(details.getUsername()).isEqualTo("analyst");
        assertThat(details.getPassword()).isEqualTo(HASH);
    }

    @Test
    void prefixesTheStoredRoleWhenBuildingTheAuthority() {
        // The DB stores "ANALYST"; Spring Security expects "ROLE_ANALYST".
        // This is the only place that translation happens.
        when(repository.findByUsername("analyst"))
                .thenReturn(Optional.of(new UserAccount("analyst", HASH, Role.ANALYST)));

        UserDetails details = service.loadUserByUsername("analyst");

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ANALYST");
    }

    @Test
    void throwsWhenTheUsernameIsUnknown() {
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void marksDisabledAccountsAsDisabled() {
        // UserAccount has no way to build a disabled instance, so the input is
        // stubbed: the behaviour under test is the negation in the adapter.
        UserAccount locked = mock(UserAccount.class);
        when(locked.getUsername()).thenReturn("locked");
        when(locked.getPasswordHash()).thenReturn(HASH);
        when(locked.getRole()).thenReturn(Role.ADMIN);
        when(locked.isEnabled()).thenReturn(false);
        when(repository.findByUsername("locked")).thenReturn(Optional.of(locked));

        UserDetails details = service.loadUserByUsername("locked");

        assertThat(details.isEnabled()).isFalse();
    }
}
