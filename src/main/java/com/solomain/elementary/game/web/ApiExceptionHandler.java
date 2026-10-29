package com.solomain.elementary.game.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Превращает исключения контроллеров в ответы {@link ApiError}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException exception) {
        return respond(exception.code(), exception.getMessage(), List.of());
    }

    /** Не прошла проверка {@code @Valid}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiError.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", errors);
    }

    /** Тело запроса не JSON или не той структуры. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return respond(ErrorCode.BAD_REQUEST, "Request body is missing or malformed", List.of());
    }

    private static ResponseEntity<ApiError> respond(ErrorCode code, String detail, List<ApiError.FieldError> errors) {
        HttpStatus status = code.status();
        var body = new ApiError("about:blank", status.getReasonPhrase(), status.value(), detail, code.name(), errors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
