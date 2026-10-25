-- =====================================================================
-- V9: Password reset OTP table hardening
-- =====================================================================

ALTER TABLE password_reset_otps ALTER COLUMN otp_code TYPE VARCHAR(255);
ALTER TABLE password_reset_otps ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;
ALTER TABLE password_reset_otps ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ;
