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
                WHERE USERNAME = ?
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
}
