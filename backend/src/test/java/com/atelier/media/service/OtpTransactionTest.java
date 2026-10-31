package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.ForgotPasswordDto;
import com.atelier.media.dto.ResetPasswordOtpDto;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.exception.InvalidTokenException;
import com.atelier.media.model.PasswordResetOtp;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.repository.PasswordResetOtpRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/** Real commits and independent connections: no test-wide rollback or mocked repositories. */
@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.config.import=optional:classpath:otp-test-no-secrets.properties",
        "spring.datasource.url=jdbc:h2:mem:otp-transactions;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.locations=classpath:db/migration/h2",
        "atelier.password-reset.enabled=true"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({OtpService.class, OtpTransactionTest.Configuration.class})
class OtpTransactionTest {

    private static final String CODE = "654321";
    private static final String NEW_PASSWORD = "NewSecretPass@123";

    @Autowired private OtpService service;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private PasswordResetOtpRepository otps;
    @SpyBean private PasswordEncoder encoder;
    @Autowired private EmailSender emailSender;
    @Autowired private TransactionTemplate transaction;

    private Restaurant account;
    private PasswordResetOtp otp;

    @BeforeEach
    void createCommittedFixtures() {
        String identity = UUID.randomUUID().toString();
        account = restaurants.saveAndFlush(Restaurant.builder()
                .name("OTP transaction fixture").slug("otp-" + identity)
                .ownerName("Fixture owner").email(identity + "@otp.test")
                .passwordHash(encoder.encode("OldSecretPass@123"))
                .status(RestaurantStatus.ACTIVE).mustChangePassword(true).build());
        otp = otps.saveAndFlush(PasswordResetOtp.builder()
                .email(account.getEmail()).otpCode(encoder.encode(CODE))
                .expiresAt(Instant.now().plusSeconds(600))
                .createdAt(Instant.now().minusSeconds(120)).build());
    }

    @Test
    void wrongAttemptSurvivesErrorAndIndependentDatabaseReload() {
        assertThatThrownBy(() -> redeem("000000"))
                .isInstanceOf(InvalidFieldException.class).hasMessageContaining("4 attempt(s) remaining");

        PasswordResetOtp stored = otps.findById(otp.getId()).orElseThrow();
        assertThat(stored.getAttempts()).isEqualTo(1);
        assertThat(stored.getLastAttemptAt()).isNotNull();
        assertThat(stored.getUsed()).isFalse();
        assertAccountUnchanged();
    }

    @Test
    void fifthFailureCommitsLockoutAndCorrectCodeCannotRedeemIt() {
        otp.setAttempts(4);
        otps.saveAndFlush(otp);

        assertThatThrownBy(() -> redeem("000000"))
                .isInstanceOf(InvalidFieldException.class).hasMessageContaining("Too many failed attempts");
        PasswordResetOtp stored = otps.findById(otp.getId()).orElseThrow();
        assertThat(stored.getAttempts()).isEqualTo(5);
        assertThat(stored.getUsed()).isTrue();
        assertThatThrownBy(() -> redeem(CODE)).isInstanceOf(InvalidFieldException.class);
        assertAccountUnchanged();
    }

    @Test
    void simultaneousValidRedemptionsHaveExactlyOneWinner() throws Exception {
        assertThat(concurrentRedemptions(CODE)).isEqualTo(1);
        Restaurant stored = restaurants.findById(account.getId()).orElseThrow();
        assertThat(stored.getTokenVersion()).isEqualTo(account.getTokenVersion() + 1);
        assertThat(encoder.matches(NEW_PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(stored.getMustChangePassword()).isFalse();
        assertThat(otps.findById(otp.getId()).orElseThrow().getUsed()).isTrue();
        assertThatThrownBy(() -> new RestaurantAccess(restaurants).requireAccount(
                new Tenant(account.getId(), account.getSlug(), false, account.getTokenVersion())))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void simultaneousWrongAttemptsAreNotLost() throws Exception {
        assertThat(concurrentRedemptions("000000")).isZero();
        assertThat(otps.findById(otp.getId()).orElseThrow().getAttempts()).isEqualTo(4);
        assertAccountUnchanged();
    }

    @Test
    void expiredCodeCannotChangePassword() {
        otp.setExpiresAt(Instant.now().minusSeconds(1));
        otps.saveAndFlush(otp);
        assertThatThrownBy(() -> redeem(CODE)).isInstanceOf(InvalidFieldException.class);
        assertAccountUnchanged();
    }

    @Test
    void usedCodeCannotChangePassword() {
        otp.setUsed(true);
        otps.saveAndFlush(otp);
        assertThatThrownBy(() -> redeem(CODE)).isInstanceOf(InvalidFieldException.class);
        assertAccountUnchanged();
    }

    @Test
    void preexistingAttemptLimitIsCommittedAsUsed() {
        otp.setAttempts(5);
        otps.saveAndFlush(otp);
        assertThatThrownBy(() -> redeem(CODE)).isInstanceOf(InvalidFieldException.class);
        assertThat(otps.findById(otp.getId()).orElseThrow().getUsed()).isTrue();
        assertAccountUnchanged();
    }

    @Test
    void successfulConsumptionCannotBeReplayedAndInvalidatesSiblingCodes() {
        PasswordResetOtp sibling = otps.saveAndFlush(PasswordResetOtp.builder()
                .email(account.getEmail()).otpCode(encoder.encode("123456"))
                .createdAt(Instant.now().minusSeconds(240))
                .expiresAt(Instant.now().plusSeconds(600)).build());
        redeem(CODE);
        assertThat(otps.findById(sibling.getId()).orElseThrow().getUsed()).isTrue();
        assertThatThrownBy(() -> redeem(CODE)).isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> redeem("123456")).isInstanceOf(InvalidFieldException.class);
        assertThat(restaurants.findById(account.getId()).orElseThrow().getTokenVersion())
                .isEqualTo(account.getTokenVersion() + 1);
    }

    @Test
    void persistenceFailureRollsBackConsumptionAndPasswordChangeTogether() {
        // Force a real SQL column-length violation at commit, not a mocked transaction rollback.
        doReturn("x".repeat(256)).when(encoder).encode(NEW_PASSWORD);
        assertThatThrownBy(() -> redeem(CODE))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("22001");
        assertThat(otps.findById(otp.getId()).orElseThrow().getUsed()).isFalse();
        assertAccountUnchanged();
    }

    @Test
    void callerRollbackCannotEraseCommittedWrongAttempt() {
        transaction.executeWithoutResult(status -> {
            assertThatThrownBy(() -> redeem("000000")).isInstanceOf(InvalidFieldException.class);
            status.setRollbackOnly();
        });
        assertThat(otps.findById(otp.getId()).orElseThrow().getAttempts()).isEqualTo(1);
    }

    @Test
    void suspendedAccountCannotRedeemOrReceiveNewCode() {
        account.setStatus(RestaurantStatus.SUSPENDED);
        restaurants.saveAndFlush(account);
        assertThatThrownBy(() -> redeem(CODE)).isInstanceOf(InvalidFieldException.class);
        assertThat(service.sendPasswordResetOtp(new ForgotPasswordDto(account.getEmail()), null).message())
                .isEqualTo(OtpService.GENERIC_OTP_SENT_MESSAGE);
        assertThat(otps.findById(otp.getId()).orElseThrow().getUsed()).isFalse();
        assertAccountUnchanged();
    }

    @Test
    void deletedAccountCannotRedeemCode() {
        account.setDeletedAt(Instant.now());
        restaurants.saveAndFlush(account);
        assertThatThrownBy(() -> redeem(CODE)).isInstanceOf(InvalidFieldException.class);
        assertAccountUnchanged();
    }

    @ParameterizedTest
    @EnumSource(value = RestaurantStatus.class, names = {"ACTIVE", "TRIAL", "PENDING"})
    void resetPreservesPermittedAccountStatus(RestaurantStatus status) {
        account.setStatus(status);
        restaurants.saveAndFlush(account);
        redeem(CODE);
        Restaurant stored = restaurants.findById(account.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(status);
        assertThat(stored.getPasswordChangedAt()).isNotNull();
        assertThat(encoder.matches(NEW_PASSWORD, stored.getPasswordHash())).isTrue();
    }

    @Test
    void resendCommitsReplacementBeforeCallingDeliveryAndRejectsOldCode() {
        AtomicReference<String> delivered = new AtomicReference<>();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(otps.findById(otp.getId()).orElseThrow().getUsed()).isTrue();
            delivered.set(call.getArgument(1));
            return null;
        }).when(emailSender).sendPasswordResetOtp(eq(account.getEmail()), anyString());
        service.sendPasswordResetOtp(new ForgotPasswordDto(account.getEmail()), null);
        assertThat(delivered.get()).matches("[0-9]{6}");
        // A newly generated six-digit code can coincidentally equal the previous code.
        if (!CODE.equals(delivered.get())) {
            assertThatThrownBy(() -> redeem(CODE)).isInstanceOf(InvalidFieldException.class);
        }
        redeem(delivered.get());
    }

    @Test
    void simultaneousResendsIssueOnlyOneReplacement() throws Exception {
        AtomicInteger deliveries = new AtomicInteger();
        doAnswer(call -> { deliveries.incrementAndGet(); return null; })
                .when(emailSender).sendPasswordResetOtp(eq(account.getEmail()), anyString());
        Callable<Boolean> resend = () -> {
            try {
                service.sendPasswordResetOtp(new ForgotPasswordDto(account.getEmail()), null);
                return true;
            } catch (ResponseStatusException rejected) {
                assertThat(rejected.getStatusCode().value()).isEqualTo(429);
                return false;
            }
        };
        assertThat(runTogether(List.of(resend, resend))).containsExactlyInAnyOrder(true, false);
        assertThat(deliveries.get()).isEqualTo(1);
        assertThat(otps.findById(otp.getId()).orElseThrow().getUsed()).isTrue();
    }

    @Test
    void concurrentResendAndRedemptionDoNotReviveOldCode() throws Exception {
        AtomicReference<String> delivered = new AtomicReference<>();
        doAnswer(call -> { delivered.set(call.getArgument(1)); return null; })
                .when(emailSender).sendPasswordResetOtp(eq(account.getEmail()), anyString());
        Callable<Boolean> redeemOld = () -> {
            try { redeem(CODE); return true; }
            catch (InvalidFieldException rejected) { return false; }
        };
        Callable<Boolean> resend = () -> {
            service.sendPasswordResetOtp(new ForgotPasswordDto(account.getEmail()), null);
            return true;
        };
        List<Boolean> results = runTogether(List.of(redeemOld, resend));
        assertThat(otps.findById(otp.getId()).orElseThrow().getUsed()).isTrue();
        var replacement = otps.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(account.getEmail(), Instant.now());
        int expectedChanges = results.get(0) ? 1 : 0;
        if (replacement.isPresent()) {
            assertThat(encoder.matches(delivered.get(), replacement.get().getOtpCode())).isTrue();
            redeem(delivered.get());
            expectedChanges++;
        } else {
            // Only possible if the generated value coincided with CODE and was redeemed.
            assertThat(results.get(0)).isTrue();
            assertThat(delivered.get()).isEqualTo(CODE);
        }
        assertThat(restaurants.findById(account.getId()).orElseThrow().getTokenVersion())
                .isEqualTo(account.getTokenVersion() + expectedChanges);
        assertThatThrownBy(() -> redeem(CODE)).isInstanceOf(InvalidFieldException.class);
    }

    private int concurrentRedemptions(String code) throws Exception {
        Callable<Boolean> redeem = () -> {
            try { redeem(code); return true; }
            catch (InvalidFieldException rejected) { return false; }
        };
        return (int) runTogether(List.of(redeem, redeem, redeem, redeem)).stream()
                .filter(Boolean::booleanValue).count();
    }

    private <T> List<T> runTogether(List<Callable<T>> calls) throws Exception {
        int callers = calls.size();
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(callers);
        try {
            var outcomes = new ArrayList<Future<T>>();
            for (Callable<T> call : calls) {
                outcomes.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    return call.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = new ArrayList<T>();
            for (Future<T> outcome : outcomes) results.add(outcome.get(20, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void redeem(String code) {
        service.resetPasswordWithOtp(new ResetPasswordOtpDto(account.getEmail(), code, NEW_PASSWORD));
    }

    private void assertAccountUnchanged() {
        Restaurant stored = restaurants.findById(account.getId()).orElseThrow();
        assertThat(stored.getPasswordHash()).isEqualTo(account.getPasswordHash());
        assertThat(stored.getTokenVersion()).isEqualTo(account.getTokenVersion());
        assertThat(stored.getMustChangePassword()).isTrue();
    }

    @TestConfiguration
    @EnableConfigurationProperties(AtelierProperties.class)
    static class Configuration {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean EmailSender emailSender() { return mock(EmailSender.class); }
    }
}
