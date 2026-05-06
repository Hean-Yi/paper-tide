package com.example.review.registration;

class RegistrationValidationException extends RuntimeException {
    RegistrationValidationException(String message) {
        super(message);
    }
}

class RegistrationAccessException extends RuntimeException {
    RegistrationAccessException(String message) {
        super(message);
    }
}

class RegistrationNotFoundException extends RuntimeException {
    RegistrationNotFoundException(String message) {
        super(message);
    }
}
