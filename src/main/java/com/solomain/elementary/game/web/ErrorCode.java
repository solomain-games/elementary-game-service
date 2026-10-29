package com.solomain.elementary.game.web;

import org.springframework.http.HttpStatus;

/** Машинные коды ошибок REST (протокол, 4.1) и их HTTP-статусы. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_HOST(HttpStatus.FORBIDDEN),
    NOT_IN_ROOM(HttpStatus.FORBIDDEN),
    KICKED_FROM_ROOM(HttpStatus.FORBIDDEN),
    CASE_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROOM_FULL(HttpStatus.CONFLICT),
    GAME_ALREADY_STARTED(HttpStatus.CONFLICT),
    LANGUAGE_NOT_SUPPORTED(HttpStatus.CONFLICT);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
