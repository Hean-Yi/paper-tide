package com.example.review.review;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConflictCheckService {
    private final JdbcTemplate jdbcTemplate;
    private final ConflictCheckRepository conflictCheckRepository;

    public ConflictCheckService(JdbcTemplate jdbcTemplate, ConflictCheckRepository conflictCheckRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.conflictCheckRepository = conflictCheckRepository;
    }

    public void detectSameInstitutionConflict(long assignmentId, long manuscriptId, long versionId, long reviewerId) {
        String reviewerInstitution = jdbcTemplate.queryForObject(
                "SELECT INSTITUTION FROM SYS_USER WHERE USER_ID = ?",
                String.class,
                reviewerId
        );
        List<String> authorInstitutions = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT INSTITUTION
                FROM MANUSCRIPT_AUTHOR
                WHERE MANUSCRIPT_ID = ? AND VERSION_ID = ? AND INSTITUTION IS NOT NULL
                """,
                String.class,
                manuscriptId,
                versionId
        );
        if (reviewerInstitution != null && authorInstitutions.stream().anyMatch(reviewerInstitution::equals)) {
            conflictCheckRepository.insertSystemDetected(
                    conflictCheckRepository.nextConflictId(),
                    assignmentId,
                    manuscriptId,
                    reviewerId,
                    "SAME_INSTITUTION",
                    "Reviewer institution matches a manuscript author institution"
            );
        }
    }

    public void recordSelfDeclaredConflict(long assignmentId, long manuscriptId, long reviewerId, long declaredBy, String conflictDesc) {
        conflictCheckRepository.insertSelfDeclared(
                conflictCheckRepository.nextConflictId(),
                assignmentId,
                manuscriptId,
                reviewerId,
                "SELF_DECLARED_CONFLICT",
                conflictDesc,
                declaredBy,
                Timestamp.from(Instant.now())
        );
    }

    public List<ConflictCheckResponse> listByRound(long roundId) {
        return conflictCheckRepository.listByRound(roundId);
    }
}
