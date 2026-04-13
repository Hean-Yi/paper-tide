package com.example.review.agent;

import com.example.review.agent.AgentDtos.AgentServiceCreateRequest;
import com.example.review.agent.AgentDtos.AgentServiceResult;
import com.example.review.agent.AgentDtos.AgentServiceTaskStatus;
import com.example.review.agent.AgentDtos.AgentServiceTaskSummary;

public interface AgentServiceClient {
    AgentServiceTaskSummary createTask(AgentServiceCreateRequest request);

    AgentServiceTaskStatus getTaskStatus(String externalTaskId);

    AgentServiceResult getTaskResult(String externalTaskId);
}
