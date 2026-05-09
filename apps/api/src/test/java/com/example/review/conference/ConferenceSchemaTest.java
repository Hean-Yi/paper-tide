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

        assertTrue(fullApply.contains("014_conference_cfp_lifecycle.sql"));
        assertTrue(devUp.contains("014_conference_cfp_lifecycle.sql"));
    }

    @Test
    void conferenceRejectionFeedbackSchemaIsWiredIntoOracleVerificationAndBootstrap() throws IOException {
        String migration = Files.readString(Path.of("..", "..", "database", "oracle", "028_conference_rejection_feedback.sql"));
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String fullApply = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));
        String testAll = Files.readString(Path.of("..", "..", "scripts", "test-all.sh"));

        assertTrue(migration.contains("REJECTED_BY"));
        assertTrue(migration.contains("REJECTED_AT"));
        assertTrue(migration.contains("REJECTION_REASON"));
        assertTrue(migration.contains("FK_CONFERENCE_REJECTED_BY"));

        assertTrue(verification.contains("REJECTED_BY"));
        assertTrue(verification.contains("REJECTED_AT"));
        assertTrue(verification.contains("REJECTION_REASON"));
        assertTrue(verification.contains("FK_CONFERENCE_REJECTED_BY"));

        assertTrue(fullApply.contains("028_conference_rejection_feedback.sql"));
        assertTrue(devUp.contains("028_conference_rejection_feedback.sql"));
        assertTrue(devUp.contains("conference_rejection_feedback_exists"));
        assertTrue(devUp.contains("oracle_foreign_key_exists"));
        assertTrue(testAll.contains("028_conference_rejection_feedback.sql"));
        assertTrue(testAll.contains("oracle_foreign_key_exists"));
    }

    @Test
    void abstractSubmissionDeadlineSchemaIsWiredIntoOracleVerificationAndBootstrap() throws IOException {
        String migration = Files.readString(Path.of("..", "..", "database", "oracle", "029_abstract_submission_deadline.sql"));
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String fullApply = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));
        String testAll = Files.readString(Path.of("..", "..", "scripts", "test-all.sh"));

        assertTrue(migration.contains("ABSTRACT_SUBMISSION_CLOSE_AT"));
        assertTrue(migration.contains("CK_CONFERENCE_PHASE_ORDER"));
        assertTrue(verification.contains("ABSTRACT_SUBMISSION_CLOSE_AT"));
        assertTrue(verification.contains("IDX_CONFERENCE_PHASE_ABSTRACT_CLOSE"));

        assertTrue(fullApply.contains("029_abstract_submission_deadline.sql"));
        assertTrue(devUp.contains("029_abstract_submission_deadline.sql"));
        assertTrue(devUp.contains("abstract_submission_deadline_exists"));
        assertTrue(testAll.contains("029_abstract_submission_deadline.sql"));
    }
}
