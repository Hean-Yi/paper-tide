package com.example.review.manuscript;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthorRepository {
    private final JdbcTemplate jdbcTemplate;

    public AuthorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAuthors(long manuscriptId, long versionId, List<AuthorRequest> authors) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO MANUSCRIPT_AUTHOR (
                  MANUSCRIPT_AUTHOR_ID,
                  MANUSCRIPT_ID,
                  VERSION_ID,
                  USER_ID,
                  AUTHOR_NAME,
                  EMAIL,
                  INSTITUTION,
                  AUTHOR_ORDER,
                  IS_CORRESPONDING,
                  IS_EXTERNAL
                ) VALUES (
                  SEQ_MANUSCRIPT_AUTHOR.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                """,
                authors,
                authors.size(),
                (ps, author) -> {
                    ps.setLong(1, manuscriptId);
                    ps.setLong(2, versionId);
                    if (author.userId() == null) {
                        ps.setNull(3, java.sql.Types.NUMERIC);
                    } else {
                        ps.setLong(3, author.userId());
                    }
                    ps.setString(4, author.authorName());
                    ps.setString(5, author.email());
                    ps.setString(6, author.institution());
                    ps.setInt(7, author.authorOrder());
                    ps.setInt(8, Boolean.TRUE.equals(author.isCorresponding()) ? 1 : 0);
                    ps.setInt(9, Boolean.TRUE.equals(author.isExternal()) ? 1 : 0);
                }
        );
    }
}
