package com.atelier.media.web;

import com.atelier.media.exception.AccountSuspendedException;
import com.atelier.media.exception.ConflictException;
import com.atelier.media.exception.InvalidCredentialsException;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.exception.InvalidMediaException;
import com.atelier.media.exception.InvalidTokenException;
import com.atelier.media.exception.MediaNotFoundException;
import com.atelier.media.exception.PasswordChangeRequiredException;
import com.atelier.media.exception.RestaurantNotFoundException;
import com.atelier.media.exception.StorageNotConfiguredException;
import com.atelier.media.security.SecurityConfig;
import com.atelier.media.exception.TooManyAttemptsException;
import com.atelier.media.exception.UploadVerificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import software.amazon.awssdk.core.exception.SdkException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Returns RFC 9457 problem+json bodies with a human-readable {@code detail} the UI can show. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                fields.isEmpty() ? "Request is invalid." : "Please check: " + String.join(", ", fields.keySet()));
        pd.setProperty("fields", fields);
        return pd;
    }

    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public ProblemDetail onHandlerMethodValidation(org.springframework.web.method.annotation.HandlerMethodValidationException ex) {
        log.warn("Validation error on handler method parameter: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "Please check the submitted photos and angle slots.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ProblemDetail onUnreadable(Exception ex) {
        log.warn("Unreadable request: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Please check the information entered (dish details and pricing) and try again.");
    }

    @ExceptionHandler(InvalidFieldException.class)
    public ProblemDetail onInvalidField(InvalidFieldException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", ex.getMessage());
        pd.setProperty("fields", Map.of(ex.getField(), ex.getMessage()));
        return pd;
    }

    @ExceptionHandler(InvalidMediaException.class)
    public ProblemDetail onInvalidMedia(InvalidMediaException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Unsupported media", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail onInvalidCredentials(InvalidCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Sign-in failed", ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail onInvalidToken(InvalidTokenException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Sign-in required", ex.getMessage());
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ProblemDetail onSuspended(AccountSuspendedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Account suspended", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ProblemDetail onAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler(PasswordChangeRequiredException.class)
    public ProblemDetail onPasswordChangeRequired(PasswordChangeRequiredException ex) {
        return problem(HttpStatus.FORBIDDEN, SecurityConfig.PASSWORD_CHANGE_REQUIRED, ex.getMessage());
    }

    @ExceptionHandler({MediaNotFoundException.class, RestaurantNotFoundException.class})
    public ProblemDetail onNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail onConflict(ConflictException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "Already taken", ex.getMessage());
        if (ex.getField() != null) {
            pd.setProperty("fields", Map.of(ex.getField(), ex.getMessage()));
        }
        return pd;
    }

    @ExceptionHandler(UploadVerificationException.class)
    public ProblemDetail onVerification(UploadVerificationException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Upload could not be verified", ex.getMessage());
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ProblemDetail> onTooManyAttempts(TooManyAttemptsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.getRetryAfter().toSeconds())))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts", ex.getMessage()));
    }

    @ExceptionHandler(StorageNotConfiguredException.class)
    public ProblemDetail onStorageNotConfigured(StorageNotConfiguredException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Storage not configured", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail onConcurrentUpdate(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Changed concurrently",
                "This dish was changed at the same moment by another request. Please try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail onIntegrity(DataIntegrityViolationException ex) {
        log.warn("Integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict", "That change conflicts with existing data.");
    }

    @ExceptionHandler(SdkException.class)
    public ProblemDetail onAws(SdkException ex) {
        log.error("AWS SDK call failed", ex);
        return problem(HttpStatus.BAD_GATEWAY, "Storage unavailable",
                "AWS S3 did not respond as expected (check credentials, region and bucket). Please try again.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
