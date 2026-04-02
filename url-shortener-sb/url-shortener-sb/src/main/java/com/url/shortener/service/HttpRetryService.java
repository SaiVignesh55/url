package com.url.shortener.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.function.Supplier;

@Service
public class HttpRetryService {

    @Value("${http.client.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${http.client.retry.initial-delay-ms:250}")
    private long initialDelayMs;

    public <T> ResponseEntity<T> execute(Supplier<ResponseEntity<T>> supplier) {
        long delay = Math.max(50, initialDelayMs);
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            try {
                ResponseEntity<T> response = supplier.get();
                if (shouldRetryStatus(response.getStatusCode())) {
                    throw new RetryableStatusException();
                }
                return response;
            } catch (HttpStatusCodeException ex) {
                if (!shouldRetryStatus(ex.getStatusCode()) || attempt >= maxAttempts) {
                    throw ex;
                }
                lastException = ex;
            } catch (RetryableStatusException ex) {
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                lastException = ex;
            }

            sleep(delay);
            delay = Math.min(delay * 2, 4000);
        }

        throw lastException == null ? new RuntimeException("HTTP request failed") : lastException;
    }

    private boolean shouldRetryStatus(HttpStatusCode statusCode) {
        int code = statusCode.value();
        return code == 429 || code >= 500;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", ex);
        }
    }

    private static class RetryableStatusException extends RuntimeException {
    }
}

