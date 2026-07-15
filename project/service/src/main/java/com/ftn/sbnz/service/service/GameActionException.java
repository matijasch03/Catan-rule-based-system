package com.ftn.sbnz.service.service;

import org.springframework.http.HttpStatus;

public class GameActionException extends RuntimeException {

    private final HttpStatus status;

    public GameActionException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
