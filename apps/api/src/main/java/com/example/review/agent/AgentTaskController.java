package com.example.review.agent;

import com.example.review.agent.AgentDtos.AgentResultResponse;
import com.example.review.agent.AgentDtos.AgentTaskListResponse;
import com.example.review.agent.AgentDtos.AgentTaskResponse;
import com.example.review.agent.AgentDtos.CreateAgentTaskRequest;
import com.example.review.agent.AgentDtos.CreateReviewerAssistRequest;
import com.example.review.agent.AgentDtos.ReviewerAssistResponse;
import com.example.review.auth.CurrentUserPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AgentTaskController {
    private final AgentIntegrationService agentIntegrationService;

    public AgentTaskController(AgentIntegrationService agentIntegrationService) {
        this.agentIntegrationService = agentIntegrationService;
    }

    @PostMapping("/manuscripts/{manuscriptId}/versions/{versionId}/agent-tasks")
    public AgentTaskResponse createVersionTask(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @PathVariable long versionId,
            @RequestBody CreateAgentTaskRequest request
    ) {
        return agentIntegrationService.createVersionTask(
                principal,
                manuscriptId,
                versionId,
                request.taskType(),
                request.forceRequested()
        );
    }

    @PostMapping("/review-rounds/{roundId}/conflict-analysis")
    public AgentTaskResponse createConflictAnalysis(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long roundId,
            @RequestBody(required = false) CreateAgentTaskRequest request
    ) {
        return agentIntegrationService.createConflictAnalysis(
                principal,
                roundId,
                request != null && request.forceRequested()
        );
    }

    @GetMapping("/manuscripts/{manuscriptId}/versions/{versionId}/agent-results")
    public List<AgentResultResponse> listResults(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long manuscriptId,
            @PathVariable long versionId
    ) {
        return agentIntegrationService.listResults(principal, manuscriptId, versionId);
    }

    @PostMapping("/review-assignments/{assignmentId}/agent-assist")
    public AgentTaskResponse createReviewerAssist(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId,
            @RequestBody(required = false) CreateReviewerAssistRequest request
    ) {
        return agentIntegrationService.createReviewerAssist(
                principal,
                assignmentId,
                request != null && request.forceRequested()
        );
    }

    @GetMapping("/review-assignments/{assignmentId}/agent-assist")
    public ReviewerAssistResponse getReviewerAssist(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId
    ) {
        return agentIntegrationService.getReviewerAssist(principal, assignmentId);
    }

    @GetMapping("/agent-tasks")
    public List<AgentTaskListResponse> listTasks(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType
    ) {
        return agentIntegrationService.listTasks(principal, status, taskType);
    }
}
