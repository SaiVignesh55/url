package com.url.shortener.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public class DatabaseStartupFailureLogger implements ApplicationListener<ApplicationFailedEvent> {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStartupFailureLogger.class);

    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        Throwable root = findSQLException(event.getException());
        if (root == null) {
            return;
        }

        log.error("Application startup failed because the database connection could not be established.", root);
        log.error("Verify DATABASE_URL, DATABASE_USERNAME and DATABASE_PASSWORD environment variables. " +
                "For Neon use: jdbc:postgresql://<host>:5432/<db>?sslmode=require");
    }

    private Throwable findSQLException(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof SQLException) {
                return cursor;
            }
            cursor = cursor.getCause();
        }
        return null;
    }
}

