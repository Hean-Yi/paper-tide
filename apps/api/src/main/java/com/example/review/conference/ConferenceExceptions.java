package com.example.review.conference;

class ConferenceValidationException extends RuntimeException {
    ConferenceValidationException(String message) {
        super(message);
    }
}

class ConferenceAccessException extends RuntimeException {
    ConferenceAccessException(String message) {
        super(message);
    }
}

class ConferenceNotFoundException extends RuntimeException {
    ConferenceNotFoundException(String message) {
        super(message);
    }
}

class ConferenceStateException extends RuntimeException {
    ConferenceStateException(String message) {
        super(message);
    }
}
