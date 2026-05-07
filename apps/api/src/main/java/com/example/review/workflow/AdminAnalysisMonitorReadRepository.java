package com.example.review.workflow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAnalysisMonitorReadRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminAnalysisMonitorReadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminAnalysisMonitorPage findPage(AdminAnalysisMonitorRequest request) {
        FilteredSql filteredSql = filteredSql(request);
        MapSqlParameterSource params = filteredSql.params()
                .addValue("offset", (request.page() - 1) * request.size())
                .addValue("limit", request.size());

        List<AdminAnalysisMonitorItem> items = jdbcTemplate.query(
                """
                SELECT I.INTENT_ID,
                       I.ANALYSIS_TYPE,
                       I.BUSINESS_STATUS,
                       I.EXECUTION_JOB_ID,
                       I.BUSINESS_ANCHOR_TYPE,
                       CASE
                         WHEN I.BUSINESS_ANCHOR_TYPE = 'ASSIGNMENT' THEN 'Assignment #' || I.BUSINESS_ANCHOR_ID
                         WHEN I.BUSINESS_ANCHOR_TYPE = 'ROUND' THEN 'Round #' || I.BUSINESS_ANCHOR_ID
                         WHEN I.BUSINESS_ANCHOR_TYPE = 'MANUSCRIPT_VERSION' THEN 'Manuscript #' || I.BUSINESS_ANCHOR_ID || ' / Version #' || I.BUSINESS_ANCHOR_VERSION_ID
                         ELSE 'Anchor #' || I.BUSINESS_ANCHOR_ID
                       END AS ANCHOR_LABEL,
                       P.SUMMARY_TEXT,
                       P.UPDATED_AT AS PROJECTION_UPDATED_AT
                FROM ANALYSIS_INTENT I
                LEFT JOIN ANALYSIS_PROJECTION P ON P.INTENT_ID = I.INTENT_ID
                %s
                ORDER BY I.INTENT_ID DESC
                OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
                """.formatted(filteredSql.whereClause()),
                params,
                new AdminMonitorItemRowMapper()
        );
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ANALYSIS_INTENT I %s".formatted(filteredSql.whereClause()),
                filteredSql.params(),
                Long.class
        );
        return new AdminAnalysisMonitorPage(items, request.page(), request.size(), total == null ? 0L : total);
    }

    private FilteredSql filteredSql(AdminAnalysisMonitorRequest request) {
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (request.analysisType() != null && !request.analysisType().isBlank()) {
            appendWhere(where, "I.ANALYSIS_TYPE = :analysisType");
            params.addValue("analysisType", request.analysisType());
        }
        if (request.businessStatus() != null && !request.businessStatus().isBlank()) {
            appendWhere(where, "I.BUSINESS_STATUS = :businessStatus");
            params.addValue("businessStatus", request.businessStatus());
        }
        return new FilteredSql(where.toString(), params);
    }

    private void appendWhere(StringBuilder where, String predicate) {
        if (where.isEmpty()) {
            where.append("WHERE ");
        } else {
            where.append(" AND ");
        }
        where.append(predicate);
    }

    static final class AdminMonitorItemRowMapper implements RowMapper<AdminAnalysisMonitorItem> {
        @Override
        public AdminAnalysisMonitorItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AdminAnalysisMonitorItem(
                    rs.getLong("INTENT_ID"),
                    rs.getString("ANALYSIS_TYPE"),
                    rs.getString("BUSINESS_STATUS"),
                    rs.getString("EXECUTION_JOB_ID"),
                    rs.getString("BUSINESS_ANCHOR_TYPE"),
                    rs.getString("ANCHOR_LABEL"),
                    rs.getString("SUMMARY_TEXT"),
                    rs.getTimestamp("PROJECTION_UPDATED_AT")
            );
        }
    }

    private record FilteredSql(String whereClause, MapSqlParameterSource params) {
    }
}
