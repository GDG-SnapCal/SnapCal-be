package com.snapcal.snapcalbackend.common;

import com.snapcal.snapcalbackend.exception.DuplicateEmailException;
import com.snapcal.snapcalbackend.exception.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorBody handleDuplicateEmail(DuplicateEmailException e) {
        return new ErrorBody("CONFLICT", e.getMessage(), null);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorBody handleInvalidCredentials(InvalidCredentialsException e) {
        return new ErrorBody("UNAUTHORIZED", e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorBody handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String field = fieldError != null ? fieldError.getField() : null;
        String message = fieldError != null ? fieldError.getDefaultMessage() : "Invalid request.";
        return new ErrorBody("VALIDATION_ERROR", message, field);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : "Request could not be processed.";
        String code = Integer.toString(e.getStatusCode().value());
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorBody(code, message, null));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorBody handleException(Exception e) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Unhandled exception", e);
        return new ErrorBody("SERVER_ERROR", "Server error occurred.", null);
    }

    public record ErrorBody(String code, String message, String field) {}
}
