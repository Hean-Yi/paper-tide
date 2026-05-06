package com.example.review.registration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRegistrationRepository implements RegistrationRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcRegistrationRepository.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    JdbcRegistrationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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
                MERGE INTO USER_ACADEMIC_PROFILE TARGET
                USING (
                  SELECT ? USER_ID,
                         ? HOMEPAGE_URL,
                         ? ORCID,
                         ? DBLP_URL,
                         ? GOOGLE_SCHOLAR_URL,
                         ? REPRESENTATIVE_WORKS_JSON,
                         ? CONFLICT_DOMAINS_JSON,
                         ? DEFAULT_MAX_LOAD
                  FROM DUAL
                ) SOURCE
                ON (TARGET.USER_ID = SOURCE.USER_ID)
                WHEN MATCHED THEN UPDATE SET
                  TARGET.HOMEPAGE_URL = SOURCE.HOMEPAGE_URL,
                  TARGET.ORCID = SOURCE.ORCID,
                  TARGET.DBLP_URL = SOURCE.DBLP_URL,
                  TARGET.GOOGLE_SCHOLAR_URL = SOURCE.GOOGLE_SCHOLAR_URL,
                  TARGET.REPRESENTATIVE_WORKS_JSON = SOURCE.REPRESENTATIVE_WORKS_JSON,
                  TARGET.CONFLICT_DOMAINS_JSON = SOURCE.CONFLICT_DOMAINS_JSON,
                  TARGET.DEFAULT_MAX_LOAD = SOURCE.DEFAULT_MAX_LOAD,
                  TARGET.UPDATED_AT = CURRENT_TIMESTAMP
                WHEN NOT MATCHED THEN INSERT (
                  PROFILE_ID,
                  USER_ID,
                  HOMEPAGE_URL,
                  ORCID,
                  DBLP_URL,
                  GOOGLE_SCHOLAR_URL,
                  REPRESENTATIVE_WORKS_JSON,
                  CONFLICT_DOMAINS_JSON,
                  DEFAULT_MAX_LOAD,
                  UPDATED_AT
                ) VALUES (
                  SEQ_USER_ACADEMIC_PROFILE.NEXTVAL,
                  SOURCE.USER_ID,
                  SOURCE.HOMEPAGE_URL,
                  SOURCE.ORCID,
                  SOURCE.DBLP_URL,
                  SOURCE.GOOGLE_SCHOLAR_URL,
                  SOURCE.REPRESENTATIVE_WORKS_JSON,
                  SOURCE.CONFLICT_DOMAINS_JSON,
                  SOURCE.DEFAULT_MAX_LOAD,
                  CURRENT_TIMESTAMP
                )
                """,
                userId,
                profile.homepageUrl(),
                profile.orcid(),
                profile.dblpUrl(),
                profile.googleScholarUrl(),
                String.join("\n", profile.representativeWorks() == null ? List.of() : profile.representativeWorks()),
                String.join("\n", profile.conflictDomains() == null ? List.of() : profile.conflictDomains()),
                profile.defaultMaxLoad() == null ? 3 : profile.defaultMaxLoad()
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
    public boolean consumeVerificationToken(long tokenId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE EMAIL_VERIFICATION_TOKEN
                SET CONSUMED_AT = CURRENT_TIMESTAMP
                WHERE TOKEN_ID = ? AND CONSUMED_AT IS NULL
                """,
                tokenId
        );
        return updated == 1;
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
    public List<RoleApplicationDetail> listPendingAdminApplicationDetails() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT ra.APPLICATION_ID, ra.USER_ID, ra.REGISTRATION_TYPE, ra.APPLICATION_STATUS,
                       ra.REJECTION_REASON, ra.SUBMITTED_AT, ra.SUBMITTED_PAYLOAD_JSON,
                       u.USERNAME, u.REAL_NAME, u.EMAIL, u.INSTITUTION
                FROM ROLE_APPLICATION ra
                JOIN SYS_USER u ON u.USER_ID = ra.USER_ID
                WHERE ra.APPLICATION_STATUS = 'PENDING_ADMIN_APPROVAL'
                ORDER BY ra.APPLICATION_ID
                """
        );
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = rows.stream().map(r -> ((Number) r.get("USER_ID")).longValue()).toList();
        Map<Long, List<ResearchAreaSummary>> areasByUser = loadResearchAreas(userIds);

        List<RoleApplicationDetail> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            long userId = ((Number) row.get("USER_ID")).longValue();
            long applicationId = ((Number) row.get("APPLICATION_ID")).longValue();
            String payloadJson = readClob(row.get("SUBMITTED_PAYLOAD_JSON"));
            PayloadSummary summary = parsePayload(applicationId, payloadJson);
            result.add(new RoleApplicationDetail(
                    applicationId,
                    userId,
                    (String) row.get("REGISTRATION_TYPE"),
                    (String) row.get("APPLICATION_STATUS"),
                    readClob(row.get("REJECTION_REASON")),
                    timestampToInstant((Timestamp) row.get("SUBMITTED_AT")),
                    (String) row.get("USERNAME"),
                    (String) row.get("REAL_NAME"),
                    (String) row.get("EMAIL"),
                    (String) row.get("INSTITUTION"),
                    summary.homepageUrl,
                    summary.orcid,
                    summary.dblpUrl,
                    summary.googleScholarUrl,
                    summary.representativeWorks,
                    summary.conflictDomains,
                    summary.plannedConferenceTitle,
                    areasByUser.getOrDefault(userId, List.of())
            ));
        }
        return result;
    }

    private Map<Long, List<ResearchAreaSummary>> loadResearchAreas(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", userIds.stream().map(id -> "?").toList());
        List<Map<String, Object>> areaRows = jdbcTemplate.queryForList(
                "SELECT USER_ID, AREA_CODE, AREA_NAME FROM USER_RESEARCH_AREA WHERE USER_ID IN (" + placeholders + ")",
                userIds.toArray()
        );
        Map<Long, List<ResearchAreaSummary>> grouped = new HashMap<>();
        for (Map<String, Object> area : areaRows) {
            long userId = ((Number) area.get("USER_ID")).longValue();
            grouped.computeIfAbsent(userId, ignored -> new ArrayList<>())
                    .add(new ResearchAreaSummary((String) area.get("AREA_CODE"), (String) area.get("AREA_NAME")));
        }
        return grouped;
    }

    private PayloadSummary parsePayload(long applicationId, String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return PayloadSummary.EMPTY;
        }
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            JsonNode profile = root.path("academicProfile");
            return new PayloadSummary(
                    textOrNull(profile, "homepageUrl"),
                    textOrNull(profile, "orcid"),
                    textOrNull(profile, "dblpUrl"),
                    textOrNull(profile, "googleScholarUrl"),
                    stringArray(profile, "representativeWorks"),
                    stringArray(profile, "conflictDomains"),
                    textOrNull(profile, "plannedConferenceTitle")
            );
        } catch (IOException ex) {
            LOGGER.warn("Failed to parse role-application payload for application {}", applicationId, ex);
            return PayloadSummary.EMPTY;
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private List<String> stringArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(value, new TypeReference<List<String>>() {});
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    private String readClob(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str;
        }
        return value.toString();
    }

    private record PayloadSummary(
            String homepageUrl,
            String orcid,
            String dblpUrl,
            String googleScholarUrl,
            List<String> representativeWorks,
            List<String> conflictDomains,
            String plannedConferenceTitle
    ) {
        private static final PayloadSummary EMPTY =
                new PayloadSummary(null, null, null, null, List.of(), List.of(), null);
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

    @Override
    public Optional<ExistingUserSummary> findUserByEmail(String email) {
        List<ExistingUserSummary> matches = jdbcTemplate.query(
                """
                SELECT USER_ID, USERNAME, EMAIL, STATUS
                FROM SYS_USER
                WHERE LOWER(EMAIL) = LOWER(?)
                """,
                (rs, rowNum) -> new ExistingUserSummary(
                        rs.getLong("USER_ID"),
                        rs.getString("USERNAME"),
                        rs.getString("EMAIL"),
                        rs.getString("STATUS")
                ),
                email
        );
        return matches.stream().findFirst();
    }

    @Override
    public Optional<RoleApplicationRecord> findApplicationByUserAndType(long userId, String registrationType) {
        List<RoleApplicationRecord> matches = jdbcTemplate.query(
                """
                SELECT APPLICATION_ID, USER_ID, REGISTRATION_TYPE, APPLICATION_STATUS,
                       REVIEWED_BY, REVIEWED_AT, REJECTION_REASON
                FROM ROLE_APPLICATION
                WHERE USER_ID = ? AND REGISTRATION_TYPE = ?
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
                userId,
                registrationType
        );
        return matches.stream().findFirst();
    }

    @Override
    public void resetUserForResubmit(long userId, String passwordHash, String realName, String institution) {
        jdbcTemplate.update(
                """
                UPDATE SYS_USER
                SET PASSWORD_HASH = ?,
                    REAL_NAME = ?,
                    INSTITUTION = ?,
                    STATUS = 'PENDING_EMAIL_VERIFICATION'
                WHERE USER_ID = ?
                """,
                passwordHash,
                realName,
                institution,
                userId
        );
    }

    @Override
    public void resetRoleApplicationToPending(long applicationId, String payloadSnapshot) {
        jdbcTemplate.update(
                """
                UPDATE ROLE_APPLICATION
                SET APPLICATION_STATUS = 'PENDING_EMAIL_VERIFICATION',
                    SUBMITTED_PAYLOAD_JSON = ?,
                    SUBMITTED_AT = CURRENT_TIMESTAMP,
                    REVIEWED_BY = NULL,
                    REVIEWED_AT = NULL,
                    REJECTION_REASON = NULL
                WHERE APPLICATION_ID = ?
                """,
                payloadSnapshot,
                applicationId
        );
    }

    @Override
    public void clearUnconsumedVerificationTokens(long applicationId) {
        jdbcTemplate.update(
                """
                UPDATE EMAIL_VERIFICATION_TOKEN
                SET CONSUMED_AT = CURRENT_TIMESTAMP
                WHERE ROLE_APPLICATION_ID = ? AND CONSUMED_AT IS NULL
                """,
                applicationId
        );
    }

    @Override
    public int deleteExpiredTokens(Instant cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM EMAIL_VERIFICATION_TOKEN WHERE EXPIRES_AT < ?",
                Timestamp.from(cutoff)
        );
    }

    private Instant timestampToInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
