-- =====================================================================
-- V9: Password reset OTP table hardening
-- =====================================================================

ALTER TABLE password_reset_otps ALTER COLUMN otp_code VARCHAR(255);
ALTER TABLE password_reset_otps ADD COLUMN IF NOT EXISTS attempts INT DEFAULT 0 NOT NULL;
ALTER TABLE password_reset_otps ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP WITH TIME ZONE;
