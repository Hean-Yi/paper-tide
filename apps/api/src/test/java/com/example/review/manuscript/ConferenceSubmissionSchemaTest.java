package com.example.review.manuscript;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConferenceSubmissionSchemaTest {
    @Test
    void conferenceScopedSubmissionMigrationIsWiredIntoOracleVerificationAndBootstrap() throws IOException {
        String migration = Files.readString(Path.of("..", "..", "database", "oracle", "015_conference_scoped_submission.sql"));
        String verification = Files.readString(Path.of("..", "..", "database", "oracle", "verify_schema.sql"));
        String fullApply = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));

        assertTrue(migration.contains("ALTER TABLE MANUSCRIPT ADD (CONFERENCE_ID NUMBER(19))"));
        assertTrue(migration.contains("legacy-platform-default"));
        assertTrue(migration.contains("FK_MANUSCRIPT_CONFERENCE"));
        assertTrue(migration.contains("IDX_MANUSCRIPT_CONFERENCE_STATUS"));
        assertTrue(migration.contains("UPDATE MANUSCRIPT"));

        assertTrue(verification.contains("CONFERENCE_ID"));
        assertTrue(verification.contains("FK_MANUSCRIPT_CONFERENCE"));
        assertTrue(verification.contains("IDX_MANUSCRIPT_CONFERENCE_STATUS"));

        assertTrue(fullApply.contains("015_conference_scoped_submission.sql"));
        assertTrue(devUp.contains("015_conference_scoped_submission.sql"));
    }

    @Test
    void legacyDefaultConferencePublicationMigrationIsWiredIntoBootstrap() throws IOException {
        Path migrationPath = Path.of("..", "..", "database", "oracle", "027_publish_legacy_default_conference.sql");
        assertTrue(Files.exists(migrationPath));

        String migration = Files.readString(migrationPath);
        String fullApply = Files.readString(Path.of("..", "..", "scripts", "oracle-schema-apply.sh"));
        String devUp = Files.readString(Path.of("..", "..", "scripts", "dev-up.sh"));
        String testAll = Files.readString(Path.of("..", "..", "scripts", "test-all.sh"));

        assertTrue(migration.contains("PUBLIC_SLUG = 'legacy-platform-default'"));
        assertTrue(migration.contains("CFP_PUBLISHED = 1"));
        assertTrue(fullApply.contains("027_publish_legacy_default_conference.sql"));
        assertTrue(devUp.contains("027_publish_legacy_default_conference.sql"));
        assertTrue(testAll.contains("027_publish_legacy_default_conference.sql"));
        assertTrue(devUp.contains("legacy_default_conference_published"));
        assertTrue(testAll.contains("legacy_default_conference_published"));
    }
}
