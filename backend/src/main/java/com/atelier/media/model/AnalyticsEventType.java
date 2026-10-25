package com.atelier.media.model;

import com.atelier.media.exception.InvalidFieldException;

public enum AnalyticsEventType {
    MENU_VIEW,
    MODEL_3D_VIEW,
    AR_LAUNCH;

    public static AnalyticsEventType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidFieldException("eventType", "eventType is required");
        }
        String clean = value.trim().toUpperCase();
        if ("MODEL_VIEW".equals(clean)) {
            return MODEL_3D_VIEW;
        }
        try {
            return AnalyticsEventType.valueOf(clean);
        } catch (IllegalArgumentException e) {
            throw new InvalidFieldException("eventType", "Invalid eventType: " + value + ". Allowed: MENU_VIEW, MODEL_3D_VIEW, AR_LAUNCH");
        }
    }
}
