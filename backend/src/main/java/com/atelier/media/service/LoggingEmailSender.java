package com.atelier.media.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Primary
public class LoggingEmailSender implements EmailSender {

    private final Environment env;

    public LoggingEmailSender(Environment env) {
        this.env = env;
    }

    @Override
    public void sendPasswordResetOtp(String toEmail, String otpCode) {
        if (env.acceptsProfiles(Profiles.of("local", "test"))) {
            log.debug("[DEV OTP] Password reset verification code for {}: {}", toEmail, otpCode);
        } else {
            log.debug("Password reset verification code dispatched to {}", toEmail);
        }
    }
}
