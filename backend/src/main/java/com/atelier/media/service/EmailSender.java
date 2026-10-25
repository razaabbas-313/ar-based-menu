package com.atelier.media.service;

public interface EmailSender {
    void sendPasswordResetOtp(String toEmail, String otpCode);
}
