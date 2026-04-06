package com.url.shortener.exception;

public class InvalidAliasException extends RuntimeException {
    public InvalidAliasException(String message) {
        super(message);
    }
}
