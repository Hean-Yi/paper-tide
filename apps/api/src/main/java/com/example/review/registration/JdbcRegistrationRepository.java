package com.example.review.registration;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRegistrationRepository implements RegistrationRepository {
    private final JdbcTemplate jdbcTemplate;

    JdbcRegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean usernameExists(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SYS_USER WHERE LOWER(USERNAME) = LOWER(?)",
                Integer.class,
                username
        );
        return count != null && count > 0;
    }

    @Override
    public boolean emailExists(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SYS_USER WHERE LOWER(EMAIL) = LOWER(?)",
                Integer.class,
                email
        );
        return count != null && count > 0;
    }

    @Override
    public long createUser(UserRegistrationDraft draft) {
        Long userId = jdbcTemplate.queryForObject("SELECT SEQ_SYS_USER.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO SYS_USER (USER_ID, USERNAME, PASSWORD_HASH, REAL_NAME, EMAIL, INSTITUTION, STATUS, CREATED_AT)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                userId,
                draft.username(),
                draft.passwordHash(),
                draft.realName(),
                draft.email(),
                draft.institution(),
                draft.status()
        );
        return userId;
    }

    @Override
    public void saveAcademicProfile(long userId, AcademicProfileRequest profile) {
        jdbcTemplate.update(
                """
                INSERT INTO USER_ACADEMIC_PROFILE (
                  PROFILE_ID, USER_ID, HOMEPAGE_URL, ORCID, DBLP_URL, GOOGLE_SCHOLAR_URL,
                  REPRESENTATIVE_WORKS_JSON, CONFLICT_DOMAINS_JSON, DEFAULT_MAX_LOAD, PLANNED_CONFERENCE_TITLE,
                  UPDATED_AT
                ) VALUES (
                  SEQ_USER_ACADEMIC_PROFILE.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
                )
                """,
                userId,
                profile.homepageUrl(),
                profile.orcid(),
                profile.dblpUrl(),
                profile.googleScholarUrl(),
                String.join("\n", profile.representativeWorks() == null ? List.of() : profile.representativeWorks()),
                String.join("\n", profile.conflictDomains() == null ? List.of() : profile.conflictDomains()),
                profile.defaultMaxLoad() == null ? 3 : profile.defaultMaxLoad(),
                profile.plannedConferenceTitle()
        );
    }

    @Override
    public void replaceResearchAreas(long userId, List<ResearchAreaRequest> researchAreas) {
        jdbcTemplate.update("DELETE FROM USER_RESEARCH_AREA WHERE USER_ID = ?", userId);
        for (ResearchAreaRequest area : researchAreas) {
            jdbcTemplate.update(
                    """
                    INSERT INTO USER_RESEARCH_AREA (USER_RESEARCH_AREA_ID, USER_ID, AREA_CODE, AREA_NAME)
                    VALUES (SEQ_USER_RESEARCH_AREA.NEXTVAL, ?, ?, ?)
                    """,
                    userId,
                    area.areaCode(),
                    area.areaName()
            );
        }
    }

    @Override
    public long createRoleApplication(RoleApplicationDraft draft) {
        Long applicationId = jdbcTemplate.queryForObject("SELECT SEQ_ROLE_APPLICATION.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO ROLE_APPLICATION (
                  APPLICATION_ID, USER_ID, REGISTRATION_TYPE, APPLICATION_STATUS, SUBMITTED_PAYLOAD_JSON, SUBMITTED_AT
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                applicationId,
                draft.userId(),
                draft.registrationType(),
                draft.status(),
                draft.submittedPayloadSnapshot()
        );
        return applicationId;
    }

    @Override
    public void createEmailVerificationToken(EmailVerificationTokenDraft draft) {
        jdbcTemplate.update(
                """
                INSERT INTO EMAIL_VERIFICATION_TOKEN (
                  TOKEN_ID, USER_ID, ROLE_APPLICATION_ID, TOKEN_HASH, TOKEN_PURPOSE, EXPIRES_AT, CREATED_AT
                ) VALUES (
                  SEQ_EMAIL_VERIFICATION_TOKEN.NEXTVAL, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
                )
                """,
                draft.userId(),
                draft.roleApplicationId(),
                draft.tokenHash(),
                draft.purpose(),
                Timestamp.from(draft.expiresAt())
        );
    }

    @Override
    public Optional<EmailVerificationTokenRecord> findVerificationToken(String tokenHash) {
        List<EmailVerificationTokenRecord> tokens = jdbcTemplate.query(
                """
                SELECT TOKEN_ID, USER_ID, ROLE_APPLICATION_ID, TOKEN_HASH, TOKEN_PURPOSE, EXPIRES_AT, CONSUMED_AT
                FROM EMAIL_VERIFICATION_TOKEN
                WHERE TOKEN_HASH = ?
                """,
                (rs, rowNum) -> new EmailVerificationTokenRecord(
                        rs.getLong("TOKEN_ID"),
                        rs.getLong("USER_ID"),
                        rs.getLong("ROLE_APPLICATION_ID"),
                        rs.getString("TOKEN_HASH"),
                        rs.getString("TOKEN_PURPOSE"),
                        rs.getTimestamp("EXPIRES_AT").toInstant(),
                        rs.getTimestamp("CONSUMED_AT") != null
                ),
                tokenHash
        );
        return tokens.stream().findFirst();
    }

    @Override
    public void consumeVerificationToken(long tokenId) {
        jdbcTemplate.update(
                "UPDATE EMAIL_VERIFICATION_TOKEN SET CONSUMED_AT = CURRENT_TIMESTAMP WHERE TOKEN_ID = ?",
                tokenId
        );
    }

    @Override
    public void activateUser(long userId) {
        jdbcTemplate.update("UPDATE SYS_USER SET STATUS = 'ACTIVE' WHERE USER_ID = ?", userId);
    }

    @Override
    public Optional<RoleApplicationRecord> findRoleApplication(long applicationId) {
        List<RoleApplicationRecord> applications = jdbcTemplate.query(
                """
                SELECT APPLICATION_ID, USER_ID, REGISTRATION_TYPE, APPLICATION_STATUS, REVIEWED_BY, REVIEWED_AT, REJECTION_REASON
                FROM ROLE_APPLICATION
                WHERE APPLICATION_ID = ?
                """,
                (rs, rowNum) -> new RoleApplicationRecord(
                        rs.getLong("APPLICATION_ID"),
                        rs.getLong("USER_ID"),
                        rs.getString("REGISTRATION_TYPE"),
                        rs.getString("APPLICATION_STATUS"),
                        rs.getObject("REVIEWED_BY", Long.class),
                        timestampToInstant(rs.getTimestamp("REVIEWED_AT")),
                        rs.getString("REJECTION_REASON")
                ),
                applicationId
        );
        return applications.stream().findFirst();
    }

    @Override
    public List<RoleApplicationRecord> listPendingAdminApplications() {
        return jdbcTemplate.query(
                """
                SELECT APPLICATION_ID, USER_ID, REGISTRATION_TYPE, APPLICATION_STATUS, REVIEWED_BY, REVIEWED_AT, REJECTION_REASON
                FROM ROLE_APPLICATION
                WHERE APPLICATION_STATUS = 'PENDING_ADMIN_APPROVAL'
                ORDER BY APPLICATION_ID
                """,
                (rs, rowNum) -> new RoleApplicationRecord(
                        rs.getLong("APPLICATION_ID"),
                        rs.getLong("USER_ID"),
                        rs.getString("REGISTRATION_TYPE"),
                        rs.getString("APPLICATION_STATUS"),
                        rs.getObject("REVIEWED_BY", Long.class),
                        timestampToInstant(rs.getTimestamp("REVIEWED_AT")),
                        rs.getString("REJECTION_REASON")
                )
        );
    }

    @Override
    public void updateRoleApplicationStatus(long applicationId, String status, Long reviewedBy, String rejectionReason) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    UPDATE ROLE_APPLICATION
                    SET APPLICATION_STATUS = ?,
                        REVIEWED_BY = ?,
                        REVIEWED_AT = CASE WHEN ? IS NULL THEN REVIEWED_AT ELSE CURRENT_TIMESTAMP END,
                        REJECTION_REASON = ?
                    WHERE APPLICATION_ID = ?
                    """
            );
            ps.setString(1, status);
            if (reviewedBy == null) {
                ps.setNull(2, Types.NUMERIC);
                ps.setNull(3, Types.NUMERIC);
            } else {
                ps.setLong(2, reviewedBy);
                ps.setLong(3, reviewedBy);
            }
            ps.setString(4, rejectionReason);
            ps.setLong(5, applicationId);
            return ps;
        });
    }

    @Override
    public void grantRole(long userId, String roleCode) {
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER_ROLE UR
                USING (
                  SELECT ? AS USER_ID, R.ROLE_ID
                  FROM SYS_ROLE R
                  WHERE R.ROLE_CODE = ?
                ) S
                ON (UR.USER_ID = S.USER_ID AND UR.ROLE_ID = S.ROLE_ID)
                WHEN NOT MATCHED THEN
                  INSERT (USER_ROLE_ID, USER_ID, ROLE_ID)
                  VALUES (SEQ_SYS_USER_ROLE.NEXTVAL, S.USER_ID, S.ROLE_ID)
                """,
                userId,
                roleCode
        );
    }

    private Instant timestampToInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
