package com.ecocode.scheduler.exception;

public class LlmGenerationException extends RuntimeException {

    public LlmGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmGenerationException(String message) {
        super(message);
    }
}
