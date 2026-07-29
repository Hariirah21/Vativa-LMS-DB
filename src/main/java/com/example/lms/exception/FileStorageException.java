package com.example.lms.exception;

import org.springframework.http.HttpStatus;

public class FileStorageException extends ApiException {

    public FileStorageException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public FileStorageException(String message, HttpStatus status) {
        super(message, status);
    }
}
