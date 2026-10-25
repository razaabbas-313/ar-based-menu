package com.atelier.media.security;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.exception.TooManyAttemptsException;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaffeineLoginThrottleTest {

    private CaffeineLoginThrottle throttle;

    @BeforeEach
    void setUp() {
        AtelierProperties props = TestFixtures.atelierProperties();
        throttle = new CaffeineLoginThrottle(props);
    }

    @Test
    void lockoutOfEmailFromIp1DoesNotBlockIp2() {
        String email = "target@restaurant.com";
        String ip1 = "192.168.1.100";
        String ip2 = "192.168.1.200";

        // Fail 5 times from IP-1 (reaching threshold)
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(email, ip1);
        }

        // IP-1 should now be locked out for that email
        assertThatThrownBy(() -> throttle.checkAllowed(email, ip1))
                .isInstanceOf(TooManyAttemptsException.class);

        // IP-2 should still be allowed to attempt sign-in for that same email
        throttle.checkAllowed(email, ip2);
    }

    @Test
    void ipHitting20DifferentBadEmailsGetsLockedOutAcrossTheBoard() {
        String attackerIp = "10.10.10.10";

        // Attacker attempts 20 different emails from same IP
        for (int i = 0; i < 20; i++) {
            String victimEmail = "victim" + i + "@example.com";
            throttle.checkAllowed(victimEmail, attackerIp);
            throttle.recordFailure(victimEmail, attackerIp);
        }

        // 21st attempt with any email from attacker IP should be locked out
        assertThatThrownBy(() -> throttle.checkAllowed("anyone@example.com", attackerIp))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void progressiveDelayIsAppliedAfterThreeFailures() {
        String email = "delay-test@example.com";
        String ip = "172.16.0.5";

        // Failures 1 and 2: no progressive delay (< 100ms)
        long start = System.currentTimeMillis();
        throttle.recordFailure(email, ip);
        throttle.recordFailure(email, ip);
        long elapsed1and2 = System.currentTimeMillis() - start;
        assertThat(elapsed1and2).isLessThan(200);

        // Failure 3: 500ms progressive delay
        start = System.currentTimeMillis();
        throttle.recordFailure(email, ip);
        long elapsed3 = System.currentTimeMillis() - start;
        assertThat(elapsed3).isGreaterThanOrEqualTo(450);

        // Failure 4: 1000ms progressive delay
        start = System.currentTimeMillis();
        throttle.recordFailure(email, ip);
        long elapsed4 = System.currentTimeMillis() - start;
        assertThat(elapsed4).isGreaterThanOrEqualTo(950);
    }

    @Test
    void successClearsFailureCount() {
        String email = "reset@example.com";
        String ip = "192.168.1.50";

        // 4 failures
        for (int i = 0; i < 4; i++) {
            throttle.recordFailure(email, ip);
        }

        // Success clears
        throttle.recordSuccess(email, ip);

        // Next 4 failures should be allowed without locking out
        for (int i = 0; i < 4; i++) {
            throttle.checkAllowed(email, ip);
        }
    }
}
