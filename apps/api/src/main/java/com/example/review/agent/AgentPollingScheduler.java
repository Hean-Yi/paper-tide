package com.example.review.agent;

import com.example.review.agent.AgentDtos.AgentServiceResult;
import com.example.review.agent.AgentDtos.AgentServiceTaskStatus;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("agentPollingScheduler")
public class AgentPollingScheduler {
    private final AgentRepository agentRepository;
    private final AgentServiceClient agentServiceClient;
    private final Duration pollingTimeout;

    public AgentPollingScheduler(
            AgentRepository agentRepository,
            AgentServiceClient agentServiceClient,
            @Value("${review.agent.polling-timeout-minutes:10}") long pollingTimeoutMinutes
    ) {
        this.agentRepository = agentRepository;
        this.agentServiceClient = agentServiceClient;
        this.pollingTimeout = Duration.ofMinutes(pollingTimeoutMinutes);
    }

    @Scheduled(
            fixedDelayString = "${review.agent.polling-delay-ms:5000}",
            initialDelayString = "${review.agent.polling-delay-ms:5000}"
    )
    public void pollScheduled() {
        pollOnce();
    }

    public void pollOnce() {
        Instant now = Instant.now();
        for (AgentTaskRow task : agentRepository.listPollableTasks()) {
            if (Duration.between(task.createdAt(), now).compareTo(pollingTimeout) > 0) {
                agentRepository.updateTaskStatus(task.taskId(), "FAILED", "Agent task timed out", true);
                continue;
            }
            try {
                AgentServiceTaskStatus status = agentServiceClient.getTaskStatus(task.externalTaskId());
                if ("SUCCESS".equals(status.status())) {
                    AgentServiceResult result = agentServiceClient.getTaskResult(task.externalTaskId());
                    agentRepository.insertResultIfAbsent(task, result);
                    agentRepository.updateTaskStatus(task.taskId(), "SUCCESS", status.step(), true);
                } else if ("FAILED".equals(status.status())) {
                    agentRepository.updateTaskStatus(task.taskId(), "FAILED", status.error(), true);
                } else {
                    agentRepository.updateTaskStatus(task.taskId(), "PROCESSING", status.step(), false);
                }
            } catch (AgentServiceException ex) {
                agentRepository.updateTaskStatus(task.taskId(), "FAILED", ex.getMessage(), true);
            }
        }
    }
}
