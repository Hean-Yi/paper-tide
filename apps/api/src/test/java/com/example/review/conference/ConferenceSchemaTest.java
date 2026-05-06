package com.example.review.conference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConferenceSchemaTest {
    @Test
    void conferenceCfpLifecycleSchemaIsWiredIntoOracleVerificationAndBootstrap() throws IOException {
        String migration = Files.readString(Path.of("..", "..", "database", "oracle", "014_conference_cfp_lifecycle.sql"));
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String fullApply = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));

        assertTrue(migration.contains("CREATE TABLE CONFERENCE"));
        assertTrue(migration.contains("CREATE TABLE CONFERENCE_PHASE"));
        assertTrue(migration.contains("CK_CONFERENCE_STATUS"));
        assertTrue(migration.contains("CK_CONFERENCE_BLIND_MODE"));
        assertTrue(migration.contains("UK_CONFERENCE_SLUG"));
        assertTrue(migration.contains("IDX_CONFERENCE_PUBLIC_STATUS"));
        assertTrue(migration.contains("IDX_CONFERENCE_PHASE_SUBMISSION_CLOSE"));

        assertTrue(verification.contains("CONFERENCE"));
        assertTrue(verification.contains("CONFERENCE_PHASE"));
        assertTrue(verification.contains("SEQ_CONFERENCE"));
        assertTrue(verification.contains("SEQ_CONFERENCE_PHASE"));
        assertTrue(verification.contains("TRG_CONFERENCE_BI"));
        assertTrue(verification.contains("TRG_CONFERENCE_PHASE_BI"));
        assertTrue(verification.contains("IDX_CONFERENCE_PUBLIC_STATUS"));
        assertTrue(verification.contains("IDX_CONFERENCE_ORGANIZER_STATUS"));
        assertTrue(verification.contains("Expected 28 tables"));
        assertTrue(verification.contains("Expected 27 sequences"));
        assertTrue(verification.contains("Expected 30 triggers"));
        assertTrue(verification.contains("Expected 42 indexes"));

        assertTrue(fullApply.contains("014_conference_cfp_lifecycle.sql"));
        assertTrue(devUp.contains("014_conference_cfp_lifecycle.sql"));
    }
}
