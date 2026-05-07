package com.example.review.review;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AssignmentDraftSchemaTest {
    @Test
    void assignmentDraftSchemaIsWiredIntoOracleVerificationAndBootstrap() throws IOException {
        String migration = Files.readString(Path.of("..", "..", "database", "oracle", "017_assignment_drafts.sql"));
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String fullApply = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));

        assertTrue(migration.contains("CREATE TABLE ASSIGNMENT_DRAFT"));
        assertTrue(migration.contains("SEQ_ASSIGNMENT_DRAFT"));
        assertTrue(migration.contains("TRG_ASSIGNMENT_DRAFT_BI"));
        assertTrue(migration.contains("IDX_ASSIGNMENT_DRAFT_ROUND_STATUS"));
        assertTrue(migration.contains("IDX_ASSIGNMENT_DRAFT_REVIEWER"));

        assertTrue(verification.contains("ASSIGNMENT_DRAFT"));
        assertTrue(verification.contains("SEQ_ASSIGNMENT_DRAFT"));
        assertTrue(verification.contains("TRG_ASSIGNMENT_DRAFT_BI"));
        assertTrue(verification.contains("IDX_ASSIGNMENT_DRAFT_ROUND_STATUS"));
        assertTrue(verification.contains("Expected 31 tables"));
        assertTrue(verification.contains("Expected 30 sequences"));
        assertTrue(verification.contains("Expected 33 triggers"));
        assertTrue(verification.contains("Expected 50 indexes"));

        assertTrue(fullApply.contains("017_assignment_drafts.sql"));
        assertTrue(devUp.contains("017_assignment_drafts.sql"));
    }
}
