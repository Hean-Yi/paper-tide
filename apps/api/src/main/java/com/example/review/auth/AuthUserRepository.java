package com.example.review.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthUserRepository {
    private final JdbcTemplate jdbcTemplate;

    public AuthUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AuthUserRecord> findByUsername(String username) {
        List<AuthUserRecord> users = jdbcTemplate.query(
                """
                SELECT USER_ID, USERNAME, PASSWORD_HASH, STATUS
                FROM SYS_USER
                WHERE LOWER(USERNAME) = LOWER(?)
                """,
                (rs, rowNum) -> new AuthUserRecord(
                        rs.getLong("USER_ID"),
                        rs.getString("USERNAME"),
                        rs.getString("PASSWORD_HASH"),
                        rs.getString("STATUS"),
                        List.of()
                ),
                username
        );

        if (users.isEmpty()) {
            return Optional.empty();
        }

        AuthUserRecord user = users.getFirst();
        List<String> roles = jdbcTemplate.queryForList(
                """
                SELECT R.ROLE_CODE
                FROM SYS_USER_ROLE UR
                JOIN SYS_ROLE R ON R.ROLE_ID = UR.ROLE_ID
                WHERE UR.USER_ID = ?
                ORDER BY R.ROLE_ID
                """,
                String.class,
                user.userId()
        );

        return Optional.of(new AuthUserRecord(
                user.userId(),
                user.username(),
                user.passwordHash(),
                user.status(),
                List.copyOf(roles)
        ));
    }

    public void activatePendingEmailVerificationRegistration(long userId) {
        jdbcTemplate.update(
                """
                UPDATE SYS_USER
                SET STATUS = 'ACTIVE'
                WHERE USER_ID = ? AND STATUS = 'PENDING_EMAIL_VERIFICATION'
                """,
                userId
        );
        jdbcTemplate.update(
                """
                UPDATE ROLE_APPLICATION
                SET APPLICATION_STATUS = CASE
                      WHEN REGISTRATION_TYPE = 'AUTHOR' THEN 'APPROVED'
                      ELSE 'PENDING_ADMIN_APPROVAL'
                    END,
                    REVIEWED_BY = NULL,
                    REVIEWED_AT = NULL,
                    REJECTION_REASON = NULL
                WHERE USER_ID = ? AND APPLICATION_STATUS = 'PENDING_EMAIL_VERIFICATION'
                """,
                userId
        );
        jdbcTemplate.update(
                """
                MERGE INTO SYS_USER_ROLE UR
                USING (
                  SELECT ? AS USER_ID, R.ROLE_ID
                  FROM SYS_ROLE R
                  WHERE R.ROLE_CODE = 'AUTHOR'
                    AND EXISTS (
                      SELECT 1
                      FROM ROLE_APPLICATION RA
                      WHERE RA.USER_ID = ?
                        AND RA.REGISTRATION_TYPE = 'AUTHOR'
                        AND RA.APPLICATION_STATUS = 'APPROVED'
                    )
                ) S
                ON (UR.USER_ID = S.USER_ID AND UR.ROLE_ID = S.ROLE_ID)
                WHEN NOT MATCHED THEN
                  INSERT (USER_ROLE_ID, USER_ID, ROLE_ID)
                  VALUES (SEQ_SYS_USER_ROLE.NEXTVAL, S.USER_ID, S.ROLE_ID)
                """,
                userId,
                userId
        );
    }
}
