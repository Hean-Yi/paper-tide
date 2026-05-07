package com.example.review.conference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConferenceReviewerBiddingSchemaTest {
    @Test
    void reviewerPoolAndBiddingSchemaIsWiredIntoOracleVerificationAndBootstrap() throws IOException {
        String migration = Files.readString(Path.of("..", "..", "database", "oracle", "016_reviewer_pool_bidding.sql"));
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String fullApply = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));

        assertTrue(migration.contains("CREATE TABLE CONFERENCE_REVIEWER"));
        assertTrue(migration.contains("CREATE TABLE REVIEWER_BID"));
        assertTrue(migration.contains("ALTER TABLE CONFLICT_CHECK_RECORD MODIFY"));
        assertTrue(migration.contains("IDX_CONFERENCE_REVIEWER_CONF_STATUS"));
        assertTrue(migration.contains("IDX_REVIEWER_BID_REVIEWER_CONF"));

        assertTrue(verification.contains("CONFERENCE_REVIEWER"));
        assertTrue(verification.contains("REVIEWER_BID"));
        assertTrue(verification.contains("SEQ_CONFERENCE_REVIEWER"));
        assertTrue(verification.contains("SEQ_REVIEWER_BID"));
        assertTrue(verification.contains("TRG_CONFERENCE_REVIEWER_BI"));
        assertTrue(verification.contains("TRG_REVIEWER_BID_BI"));
        assertTrue(verification.contains("IDX_CONFERENCE_REVIEWER_CONF_STATUS"));
        assertTrue(verification.contains("IDX_REVIEWER_BID_REVIEWER_CONF"));
        assertTrue(verification.contains("Expected 31 tables"));
        assertTrue(verification.contains("Expected 30 sequences"));
        assertTrue(verification.contains("Expected 33 triggers"));
        assertTrue(verification.contains("Expected 50 indexes"));

        assertTrue(fullApply.contains("016_reviewer_pool_bidding.sql"));
        assertTrue(devUp.contains("016_reviewer_pool_bidding.sql"));
    }
}
