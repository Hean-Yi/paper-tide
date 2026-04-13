package com.example.review.agent;

import com.example.review.agent.AgentDtos.AgentResultResponse;
import com.example.review.agent.AgentDtos.AgentServiceResult;
import com.example.review.agent.AgentDtos.AgentTaskResponse;
import com.example.review.agent.AgentDtos.AgentVersionData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<AgentVersionData> findVersion(long manuscriptId, long versionId) {
        List<AgentVersionData> rows = jdbcTemplate.query(
                """
                SELECT MANUSCRIPT_ID, VERSION_ID, TITLE, ABSTRACT, KEYWORDS, PDF_FILE, PDF_FILE_NAME, PDF_FILE_SIZE
                FROM MANUSCRIPT_VERSION
                WHERE MANUSCRIPT_ID = ? AND VERSION_ID = ?
                """,
                (rs, rowNum) -> new AgentVersionData(
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getString("TITLE"),
                        rs.getString("ABSTRACT"),
                        rs.getString("KEYWORDS"),
                        rs.getBytes("PDF_FILE"),
                        rs.getString("PDF_FILE_NAME"),
                        rs.getObject("PDF_FILE_SIZE", Long.class)
                ),
                manuscriptId,
                versionId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<AgentTaskRow> findReusableTask(long manuscriptId, long versionId, Long roundId, String taskType) {
        if (roundId == null) {
            return findReusableTaskWithoutRound(manuscriptId, versionId, taskType);
        }
        List<AgentTaskRow> rows = jdbcTemplate.query(
                """
                SELECT TASK_ID, MANUSCRIPT_ID, VERSION_ID, ROUND_ID, TASK_TYPE, TASK_STATUS, EXTERNAL_TASK_ID, CREATED_AT
                FROM AGENT_ANALYSIS_TASK
                WHERE MANUSCRIPT_ID = ?
                  AND VERSION_ID = ?
                  AND TASK_TYPE = ?
                  AND ROUND_ID = ?
                  AND TASK_STATUS IN ('PENDING', 'PROCESSING', 'SUCCESS')
                ORDER BY TASK_ID
                FETCH FIRST 1 ROWS ONLY
                """,
                (rs, rowNum) -> mapTaskRow(rs.getLong("TASK_ID")),
                manuscriptId,
                versionId,
                taskType,
                roundId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private Optional<AgentTaskRow> findReusableTaskWithoutRound(long manuscriptId, long versionId, String taskType) {
        List<AgentTaskRow> rows = jdbcTemplate.query(
                """
                SELECT TASK_ID, MANUSCRIPT_ID, VERSION_ID, ROUND_ID, TASK_TYPE, TASK_STATUS, EXTERNAL_TASK_ID, CREATED_AT
                FROM AGENT_ANALYSIS_TASK
                WHERE MANUSCRIPT_ID = ?
                  AND VERSION_ID = ?
                  AND TASK_TYPE = ?
                  AND ROUND_ID IS NULL
                  AND TASK_STATUS IN ('PENDING', 'PROCESSING', 'SUCCESS')
                ORDER BY TASK_ID
                FETCH FIRST 1 ROWS ONLY
                """,
                (rs, rowNum) -> mapTaskRow(rs.getLong("TASK_ID")),
                manuscriptId,
                versionId,
                taskType
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public long insertTask(long manuscriptId, long versionId, Long roundId, String taskType, Map<String, Object> requestPayload) {
        long taskId = jdbcTemplate.queryForObject("SELECT SEQ_AGENT_ANALYSIS_TASK.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO AGENT_ANALYSIS_TASK (TASK_ID, MANUSCRIPT_ID, VERSION_ID, ROUND_ID, TASK_TYPE, TASK_STATUS, REQUEST_PAYLOAD, RESULT_SUMMARY, EXTERNAL_TASK_ID, RETRY_COUNT, CREATED_AT, FINISHED_AT)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, NULL, NULL, 0, CURRENT_TIMESTAMP, NULL)
                """,
                taskId,
                manuscriptId,
                versionId,
                roundId,
                taskType,
                toJson(requestPayload)
        );
        return taskId;
    }

    public AgentTaskRow findTask(long taskId) {
        return mapTaskRow(taskId);
    }

    public Optional<AgentTaskRow> findByExternalTaskId(String externalTaskId) {
        List<AgentTaskRow> rows = jdbcTemplate.query(
                """
                SELECT TASK_ID
                FROM AGENT_ANALYSIS_TASK
                WHERE EXTERNAL_TASK_ID = ?
                """,
                (rs, rowNum) -> mapTaskRow(rs.getLong("TASK_ID")),
                externalTaskId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public AgentTaskRow attachExternalTaskId(long taskId, String externalTaskId, String status, String step) {
        Optional<AgentTaskRow> existing = findByExternalTaskId(externalTaskId);
        if (existing.isPresent()) {
            if (existing.get().taskId() != taskId) {
                deleteTask(taskId);
            }
            return existing.get();
        }
        try {
            jdbcTemplate.update(
                    """
                    UPDATE AGENT_ANALYSIS_TASK
                    SET EXTERNAL_TASK_ID = ?, TASK_STATUS = ?, RESULT_SUMMARY = ?
                    WHERE TASK_ID = ?
                    """,
                    externalTaskId,
                    status,
                    step,
                    taskId
            );
            return mapTaskRow(taskId);
        } catch (DuplicateKeyException ex) {
            Optional<AgentTaskRow> duplicate = findByExternalTaskId(externalTaskId);
            if (duplicate.isPresent()) {
                if (duplicate.get().taskId() != taskId) {
                    deleteTask(taskId);
                }
                return duplicate.get();
            }
            throw ex;
        }
    }

    public List<AgentTaskRow> listPollableTasks() {
        return jdbcTemplate.query(
                """
                SELECT TASK_ID
                FROM AGENT_ANALYSIS_TASK
                WHERE TASK_STATUS IN ('PENDING', 'PROCESSING')
                  AND EXTERNAL_TASK_ID IS NOT NULL
                ORDER BY TASK_ID
                """,
                (rs, rowNum) -> mapTaskRow(rs.getLong("TASK_ID"))
        );
    }

    public void updateTaskStatus(long taskId, String status, String summary, boolean finished) {
        jdbcTemplate.update(
                """
                UPDATE AGENT_ANALYSIS_TASK
                SET TASK_STATUS = ?, RESULT_SUMMARY = ?, FINISHED_AT = CASE WHEN ? = 1 THEN CURRENT_TIMESTAMP ELSE FINISHED_AT END
                WHERE TASK_ID = ?
                """,
                status,
                summary,
                finished ? 1 : 0,
                taskId
        );
    }

    public void insertResultIfAbsent(AgentTaskRow task, AgentServiceResult result) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM AGENT_ANALYSIS_RESULT WHERE TASK_ID = ?",
                Integer.class,
                task.taskId()
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO AGENT_ANALYSIS_RESULT (RESULT_ID, TASK_ID, MANUSCRIPT_ID, VERSION_ID, RESULT_TYPE, RAW_RESULT_JSON, REDACTED_RESULT_JSON, CREATED_AT)
                VALUES (SEQ_AGENT_ANALYSIS_RESULT.NEXTVAL, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                task.taskId(),
                task.manuscriptId(),
                task.versionId(),
                result.resultType(),
                toJson(result.rawResult()),
                toJson(result.redactedResult())
        );
    }

    public List<AgentResultResponse> listResults(long manuscriptId, long versionId, boolean includeRaw) {
        return jdbcTemplate.query(
                """
                SELECT RESULT_ID, RESULT_TYPE, RAW_RESULT_JSON, REDACTED_RESULT_JSON
                FROM AGENT_ANALYSIS_RESULT
                WHERE MANUSCRIPT_ID = ? AND VERSION_ID = ?
                ORDER BY RESULT_ID
                """,
                (rs, rowNum) -> new AgentResultResponse(
                        rs.getLong("RESULT_ID"),
                        rs.getString("RESULT_TYPE"),
                        includeRaw ? fromJson(rs.getString("RAW_RESULT_JSON")) : null,
                        fromJson(rs.getString("REDACTED_RESULT_JSON"))
                ),
                manuscriptId,
                versionId
        );
    }

    public boolean reviewerHasAssignment(long userId, long manuscriptId, long versionId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM REVIEW_ASSIGNMENT
                WHERE REVIEWER_ID = ? AND MANUSCRIPT_ID = ? AND VERSION_ID = ?
                """,
                Integer.class,
                userId,
                manuscriptId,
                versionId
        );
        return count != null && count > 0;
    }

    public Optional<RoundData> findRound(long roundId) {
        List<RoundData> rows = jdbcTemplate.query(
                """
                SELECT ROUND_ID, MANUSCRIPT_ID, VERSION_ID
                FROM REVIEW_ROUND
                WHERE ROUND_ID = ?
                """,
                (rs, rowNum) -> new RoundData(
                        rs.getLong("ROUND_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID")
                ),
                roundId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<Map<String, Object>> listReviewReports(long roundId) {
        return jdbcTemplate.queryForList(
                """
                SELECT REVIEW_ID AS "reviewId",
                       NOVELTY_SCORE AS "noveltyScore",
                       METHOD_SCORE AS "methodScore",
                       EXPERIMENT_SCORE AS "experimentScore",
                       WRITING_SCORE AS "writingScore",
                       OVERALL_SCORE AS "overallScore",
                       CONFIDENCE_LEVEL AS "confidenceLevel",
                       STRENGTHS AS "strengths",
                       WEAKNESSES AS "weaknesses",
                       COMMENTS_TO_AUTHOR AS "commentsToAuthor",
                       COMMENTS_TO_CHAIR AS "commentsToChair",
                       RECOMMENDATION AS "recommendation"
                FROM REVIEW_REPORT
                WHERE ROUND_ID = ?
                ORDER BY REVIEW_ID
                """,
                roundId
        );
    }

    private AgentTaskRow mapTaskRow(long taskId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT TASK_ID, MANUSCRIPT_ID, VERSION_ID, ROUND_ID, TASK_TYPE, TASK_STATUS, EXTERNAL_TASK_ID, CREATED_AT
                FROM AGENT_ANALYSIS_TASK
                WHERE TASK_ID = ?
                """,
                (rs, rowNum) -> new AgentTaskRow(
                        rs.getLong("TASK_ID"),
                        rs.getLong("MANUSCRIPT_ID"),
                        rs.getLong("VERSION_ID"),
                        rs.getObject("ROUND_ID", Long.class),
                        rs.getString("TASK_TYPE"),
                        rs.getString("TASK_STATUS"),
                        rs.getString("EXTERNAL_TASK_ID"),
                        rs.getTimestamp("CREATED_AT").toInstant()
                ),
                taskId
        );
    }

    private void deleteTask(long taskId) {
        jdbcTemplate.update("DELETE FROM AGENT_ANALYSIS_TASK WHERE TASK_ID = ? AND EXTERNAL_TASK_ID IS NULL", taskId);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize agent JSON", ex);
        }
    }

    private Map<String, Object> fromJson(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to parse agent JSON", ex);
        }
    }
}

record AgentTaskRow(
        long taskId,
        long manuscriptId,
        long versionId,
        Long roundId,
        String taskType,
        String taskStatus,
        String externalTaskId,
        Instant createdAt
) {
    AgentTaskResponse toResponse() {
        return new AgentTaskResponse(taskId, externalTaskId, taskType, taskStatus, null);
    }
}

record RoundData(long roundId, long manuscriptId, long versionId) {
}
