package com.example.review.registration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationSchemaTest {
    @Test
    void registrationFoundationSchemaIsWiredIntoOracleVerificationAndBootstrap() throws IOException {
        String migration = Files.readString(Path.of("..", "..", "database", "oracle", "012_registration_foundation.sql"));
        String tokenIndexMigration = Files.readString(Path.of("..", "..", "database", "oracle", "013_registration_token_indexes.sql"));
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String fullApply = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));

        assertTrue(migration.contains("CREATE TABLE USER_ACADEMIC_PROFILE"));
        assertTrue(migration.contains("CREATE TABLE ROLE_APPLICATION"));
        assertTrue(migration.contains("CREATE TABLE EMAIL_VERIFICATION_TOKEN"));
        assertTrue(migration.contains("PENDING_EMAIL_VERIFICATION"));
        assertTrue(migration.contains("UK_ROLE_APPLICATION_USER_TYPE"));
        assertTrue(migration.contains("IDX_ROLE_APPLICATION_STATUS_TYPE"));
        assertTrue(migration.contains("IDX_EMAIL_VERIFICATION_USER_APP"));
        assertTrue(tokenIndexMigration.contains("IDX_EMAIL_VERIFICATION_EXPIRES"));
        assertTrue(tokenIndexMigration.contains("IDX_EMAIL_VERIFICATION_USER_PURPOSE"));

        assertTrue(verification.contains("USER_ACADEMIC_PROFILE"));
        assertTrue(verification.contains("ROLE_APPLICATION"));
        assertTrue(verification.contains("EMAIL_VERIFICATION_TOKEN"));
        assertTrue(verification.contains("Expected 28 tables"));
        assertTrue(verification.contains("Expected 27 sequences"));
        assertTrue(verification.contains("Expected 30 triggers"));
        assertTrue(verification.contains("Expected 42 indexes"));

        assertTrue(fullApply.contains("012_registration_foundation.sql"));
        assertTrue(fullApply.contains("013_registration_token_indexes.sql"));
        assertTrue(devUp.contains("012_registration_foundation.sql"));
        assertTrue(devUp.contains("013_registration_token_indexes.sql"));
    }
}
