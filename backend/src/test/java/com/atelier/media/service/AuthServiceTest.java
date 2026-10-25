package com.atelier.media.service;

import com.atelier.media.dto.AuthResponseDto;
import com.atelier.media.dto.ChangeOwnPasswordDto;
import com.atelier.media.dto.LoginRequestDto;
import com.atelier.media.exception.AccountSuspendedException;
import com.atelier.media.exception.InvalidCredentialsException;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.exception.InvalidTokenException;
import com.atelier.media.exception.PasswordChangeRequiredException;
import com.atelier.media.exception.TooManyAttemptsException;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.Tenant;
import com.atelier.media.security.TokenService;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // fast cost for tests
    private final TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
    private final RestaurantAccess access = new RestaurantAccess(repository);
    private AuthService service;
    private Restaurant owner;

    @BeforeEach
    void setUp() {
        service = new AuthService(repository, encoder, tokenService,
                new LoginAttemptService(TestFixtures.atelierProperties()), access, tx);
        when(tokenService.issue(any(Restaurant.class)))
                .thenReturn(new TokenService.IssuedToken("signed.jwt.token", Instant.now().plusSeconds(3600), 3600));
        when(repository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        owner = TestFixtures.restaurant(5L, "bistro-a");
        owner.setEmail("owner@bistro.com");
        owner.setPasswordHash(encoder.encode("Temp-abcDEF2345"));
        owner.setMustChangePassword(true);
        when(repository.findByEmail("owner@bistro.com")).thenReturn(Optional.of(owner));
        when(repository.findById(5L)).thenReturn(Optional.of(owner));
    }

    // ---------------------------------------------------------------- login

    @Test
    void loginWithATemporaryPasswordFlagsTheForcedChange() {
        AuthResponseDto response = service.login(new LoginRequestDto("Owner@Bistro.com", "Temp-abcDEF2345"));

        assertThat(response.token()).isEqualTo("signed.jwt.token");
        assertThat(response.mustChangePassword()).isTrue();
        assertThat(response.restaurant().mustChangePassword()).isTrue();
    }

    @Test
    void loginSucceedsOnlyWithTheRightPassword() {
        owner.setMustChangePassword(false);

        AuthResponseDto ok = service.login(new LoginRequestDto("owner@bistro.com", "Temp-abcDEF2345"));
        assertThat(ok.mustChangePassword()).isFalse();
        assertThatThrownBy(() -> service.login(new LoginRequestDto("owner@bistro.com", "wrong-horse")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void suspendedRestaurantCannotSignInEvenWithTheRightPassword() {
        owner.setStatus(RestaurantStatus.SUSPENDED);

        assertThatThrownBy(() -> service.login(new LoginRequestDto("owner@bistro.com", "Temp-abcDEF2345")))
                .isInstanceOf(AccountSuspendedException.class);
    }

    @Test
    void softDeletedRestaurantLooksLikeAnUnknownAccount() {
        owner.setDeletedAt(Instant.now());

        assertThatThrownBy(() -> service.login(new LoginRequestDto("owner@bistro.com", "Temp-abcDEF2345")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void unknownEmailFailsLikeAWrongPassword() {
        when(repository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequestDto("nobody@x.com", "whatever-1")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void repeatedFailuresLockTheAccountTemporarily() {
        when(repository.findByEmail(anyString())).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequestDto("victim@x.com", "guess")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> service.login(new LoginRequestDto("victim@x.com", "guess")))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    // ---------------------------------------------------------------- forced password change

    @Test
    void changePasswordVerifiesTheCurrentOneWithBcryptStoresTheNewHashAndClearsTheFlag() {
        AuthResponseDto response = service.changePassword(new Tenant(5L, "bistro-a"),
                new ChangeOwnPasswordDto("Temp-abcDEF2345", "my-own-secret-1"));

        assertThat(encoder.matches("my-own-secret-1", owner.getPasswordHash())).isTrue();
        assertThat(encoder.matches("Temp-abcDEF2345", owner.getPasswordHash())).isFalse();
        assertThat(owner.getMustChangePassword()).isFalse();
        assertThat(owner.getPasswordChangedAt()).isNotNull();
        assertThat(response.mustChangePassword()).isFalse();
        assertThat(response.token()).isEqualTo("signed.jwt.token");
        verify(repository).save(owner);
    }

    @Test
    void wrongCurrentPasswordIsRejected() {
        assertThatThrownBy(() -> service.changePassword(new Tenant(5L, "bistro-a"),
                new ChangeOwnPasswordDto("not-the-temp", "my-own-secret-1")))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("incorrect");
        assertThat(owner.getMustChangePassword()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void theTemporaryPasswordCannotBeReused() {
        assertThatThrownBy(() -> service.changePassword(new Tenant(5L, "bistro-a"),
                new ChangeOwnPasswordDto("Temp-abcDEF2345", "Temp-abcDEF2345")))
                .isInstanceOf(InvalidFieldException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void supportSessionsCannotChangeTheOwnersPassword() {
        assertThatThrownBy(() -> service.changePassword(new Tenant(5L, "bistro-a", true),
                new ChangeOwnPasswordDto("Temp-abcDEF2345", "my-own-secret-1")))
                .isInstanceOf(InvalidFieldException.class);
    }

    @Test
    void studioStaysLockedUntilThePasswordIsChangedButProfileWorks() {
        Tenant tenant = new Tenant(5L, "bistro-a", false, 1);

        assertThat(service.me(tenant).mustChangePassword()).isTrue();
        assertThatThrownBy(() -> access.requireStudioAccess(tenant)).isInstanceOf(PasswordChangeRequiredException.class);
        // A super-admin support session is not blocked by the owner's pending reset.
        assertThat(access.requireStudioAccess(new Tenant(5L, "bistro-a", true, 1))).isSameAs(owner);

        service.changePassword(tenant, new ChangeOwnPasswordDto("Temp-abcDEF2345", "my-own-secret-1"));
        // Old token (version 1) is now revoked
        assertThatThrownBy(() -> access.requireStudioAccess(tenant)).isInstanceOf(InvalidTokenException.class);
        // New session with updated token version succeeds and unlocks studio
        assertThat(access.requireStudioAccess(new Tenant(5L, "bistro-a", false, owner.getTokenVersion()))).isSameAs(owner);
    }
}
