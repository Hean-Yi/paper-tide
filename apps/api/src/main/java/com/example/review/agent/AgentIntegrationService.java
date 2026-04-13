package com.example.review.agent;

import com.example.review.agent.AgentDtos.AgentResultResponse;
import com.example.review.agent.AgentDtos.AgentServiceCreateRequest;
import com.example.review.agent.AgentDtos.AgentServiceTaskSummary;
import com.example.review.agent.AgentDtos.AgentTaskResponse;
import com.example.review.agent.AgentDtos.AgentVersionData;
import com.example.review.auth.CurrentUserPrincipal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentIntegrationService {
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
        requireChairOrAdmin(principal);
        AgentVersionData version = loadVersionWithPdf(manuscriptId, versionId);
        Map<String, Object> payload = basePayload(version);
        return createAndSubmitTask(version, null, taskType, payload, force);
    }

    public AgentTaskResponse createConflictAnalysis(CurrentUserPrincipal principal, long roundId, boolean force) {
        requireChairOrAdmin(principal);
        RoundData round = agentRepository.findRound(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review round not found"));
        AgentVersionData version = loadVersionWithPdf(round.manuscriptId(), round.versionId());
        Map<String, Object> payload = basePayload(version);
        payload.put("reviewReports", agentRepository.listReviewReports(roundId));
        return createAndSubmitTask(version, roundId, "DECISION_CONFLICT_ANALYSIS", payload, force);
    }

    public List<AgentResultResponse> listResults(CurrentUserPrincipal principal, long manuscriptId, long versionId) {
        if (hasRole(principal, "CHAIR") || hasRole(principal, "ADMIN")) {
            return agentRepository.listResults(manuscriptId, versionId, true);
        }
        if (hasRole(principal, "REVIEWER") && agentRepository.reviewerHasAssignment(principal.userId(), manuscriptId, versionId)) {
            return agentRepository.listResults(manuscriptId, versionId, false);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view agent results");
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

    private String normalizeStatus(String status) {
        return switch (status) {
            case "PROCESSING", "SUCCESS", "FAILED" -> status;
            default -> "PENDING";
        };
    }

    private void requireChairOrAdmin(CurrentUserPrincipal principal) {
        if (!hasRole(principal, "CHAIR") && !hasRole(principal, "ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chair or admin role is required");
        }
    }

    private boolean hasRole(CurrentUserPrincipal principal, String role) {
        return principal.roles().contains(role);
    }
}
