package com.example.review.agent;

import com.example.review.agent.AgentDtos.AgentResultResponse;
import com.example.review.agent.AgentDtos.AgentServiceCreateRequest;
import com.example.review.agent.AgentDtos.AgentServiceTaskSummary;
import com.example.review.agent.AgentDtos.AgentTaskListResponse;
import com.example.review.agent.AgentDtos.AgentTaskResponse;
import com.example.review.agent.AgentDtos.AgentVersionData;
import com.example.review.agent.AgentDtos.ReviewerAssistResponse;
import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentIntegrationService {
    private static final String REVIEW_ASSIST_ANALYSIS = "REVIEW_ASSIST_ANALYSIS";
    private static final List<String> REVIEWER_ASSIST_CREATE_STATUSES = List.of("ACCEPTED", "IN_REVIEW", "OVERDUE");
    private static final List<String> REVIEWER_ASSIST_QUERY_DENIED_STATUSES = List.of("DECLINED", "REASSIGNED", "CANCELLED");

    private final AgentRepository agentRepository;
    private final AgentServiceClient agentServiceClient;

    public AgentIntegrationService(AgentRepository agentRepository, AgentServiceClient agentServiceClient) {
        this.agentRepository = agentRepository;
        this.agentServiceClient = agentServiceClient;
    }

    public AgentTaskResponse createVersionTask(
            CurrentUserPrincipal principal,
            long manuscriptId,
            long versionId,
            String taskType,
            boolean force
    ) {
        RoleGuard.requireChairOrAdmin(principal);
        AgentVersionData version = loadVersionWithPdf(manuscriptId, versionId);
        Map<String, Object> payload = basePayload(version);
        return createAndSubmitTask(version, null, taskType, payload, force);
    }

    public AgentTaskResponse createConflictAnalysis(CurrentUserPrincipal principal, long roundId, boolean force) {
        RoleGuard.requireChairOrAdmin(principal);
        RoundData round = agentRepository.findRound(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        AgentVersionData version = loadVersionWithPdf(round.manuscriptId(), round.versionId());
        Map<String, Object> payload = basePayload(version);
        payload.put("reviewReports", agentRepository.listReviewReports(roundId));
        return createAndSubmitTask(version, roundId, "DECISION_CONFLICT_ANALYSIS", payload, force);
    }

    public List<AgentResultResponse> listResults(CurrentUserPrincipal principal, long manuscriptId, long versionId) {
        if (RoleGuard.hasRole(principal, "CHAIR") || RoleGuard.hasRole(principal, "ADMIN")) {
            return agentRepository.listResults(manuscriptId, versionId, true);
        }
        if (RoleGuard.hasRole(principal, "REVIEWER") && agentRepository.reviewerHasAssignment(principal.userId(), manuscriptId, versionId)) {
            return agentRepository.listResults(manuscriptId, versionId, false);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view agent results");
    }

    public AgentTaskResponse createReviewerAssist(CurrentUserPrincipal principal, long assignmentId, boolean force) {
        ReviewerAssistAssignmentData assignment = loadReviewerAssistAssignment(principal, assignmentId);
        if (!REVIEWER_ASSIST_CREATE_STATUSES.contains(assignment.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment state does not allow reviewer assist creation");
        }
        AgentVersionData version = assignment.toVersionData();
        if (version.pdfFile() == null || version.pdfFile().length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PDF is required before agent analysis");
        }
        Map<String, Object> payload = basePayload(version);
        payload.put("reviewerAssist", Map.of(
                "assignmentId", assignment.assignmentId(),
                "roundId", assignment.roundId(),
                "allowedOutput", "checklist_only",
                "forbiddenOutput", List.of("recommendation", "score", "fullReviewText")
        ));
        return createAndSubmitTask(version, assignment.roundId(), REVIEW_ASSIST_ANALYSIS, payload, force);
    }

    public ReviewerAssistResponse getReviewerAssist(CurrentUserPrincipal principal, long assignmentId) {
        ReviewerAssistAssignmentData assignment = loadReviewerAssistAssignment(principal, assignmentId);
        if (REVIEWER_ASSIST_QUERY_DENIED_STATUSES.contains(assignment.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assignment state does not allow reviewer assist access");
        }
        return agentRepository.findLatestTask(
                        assignment.manuscriptId(),
                        assignment.versionId(),
                        assignment.roundId(),
                        REVIEW_ASSIST_ANALYSIS
                )
                .map(task -> new ReviewerAssistResponse(task.toResponse(), agentRepository.listResultsForTask(task.taskId(), false)))
                .orElseGet(() -> new ReviewerAssistResponse(null, List.of()));
    }

    public List<AgentTaskListResponse> listTasks(CurrentUserPrincipal principal, String status, String taskType) {
        requireAdmin(principal);
        return agentRepository.listTasks(status, taskType);
    }

    private AgentTaskResponse createAndSubmitTask(
            AgentVersionData version,
            Long roundId,
            String taskType,
            Map<String, Object> payload,
            boolean force
    ) {
        if (!force) {
            var reusable = agentRepository.findReusableTask(version.manuscriptId(), version.versionId(), roundId, taskType);
            if (reusable.isPresent()) {
                return reusable.get().toResponse();
            }
        }
        long localTaskId = agentRepository.insertTask(version.manuscriptId(), version.versionId(), roundId, taskType, payload);
        try {
            AgentServiceTaskSummary externalTask = agentServiceClient.createTask(new AgentServiceCreateRequest(
                    taskType,
                    version.manuscriptId(),
                    version.versionId(),
                    roundId,
                    payload,
                    force,
                    version.pdfFileName(),
                    version.pdfFile()
            ));
            AgentTaskRow attached = agentRepository.attachExternalTaskId(
                    localTaskId,
                    externalTask.taskId(),
                    normalizeStatus(externalTask.status()),
                    externalTask.step()
            );
            return new AgentTaskResponse(
                    attached.taskId(),
                    attached.externalTaskId(),
                    attached.taskType(),
                    attached.taskStatus(),
                    externalTask.step()
            );
        } catch (AgentServiceException ex) {
            agentRepository.updateTaskStatus(localTaskId, "FAILED", ex.getMessage(), true);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    private AgentVersionData loadVersionWithPdf(long manuscriptId, long versionId) {
        AgentVersionData version = agentRepository.findVersion(manuscriptId, versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manuscript version not found"));
        if (version.pdfFile() == null || version.pdfFile().length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PDF is required before agent analysis");
        }
        return version;
    }

    private Map<String, Object> basePayload(AgentVersionData version) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", version.title());
        payload.put("abstract", version.abstractText());
        payload.put("keywords", version.keywordList());
        return payload;
    }

    private ReviewerAssistAssignmentData loadReviewerAssistAssignment(CurrentUserPrincipal principal, long assignmentId) {
        RoleGuard.requireRole(principal, "REVIEWER");
        ReviewerAssistAssignmentData assignment = agentRepository.findReviewerAssistAssignment(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review assignment not found"));
        if (assignment.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access reviewer assist");
        }
        return assignment;
    }

    private String normalizeStatus(String status) {
        return switch (status) {
            case "PROCESSING", "SUCCESS", "FAILED" -> status;
            default -> "PENDING";
        };
    }

    private void requireAdmin(CurrentUserPrincipal principal) {
        RoleGuard.requireRole(principal, "ADMIN");
    }
}
