package com.example.review.agent;

import com.example.review.agent.AgentDtos.AgentServiceCreateRequest;
import com.example.review.agent.AgentDtos.AgentServiceResult;
import com.example.review.agent.AgentDtos.AgentServiceTaskStatus;
import com.example.review.agent.AgentDtos.AgentServiceTaskSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpAgentServiceClient implements AgentServiceClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String internalApiKey;
    private final Duration timeout;

    public HttpAgentServiceClient(
            ObjectMapper objectMapper,
            @Value("${review.agent.base-url:http://localhost:8001}") String baseUrl,
            @Value("${review.agent.internal-api-key:local-dev-internal-key}") String internalApiKey,
            @Value("${review.agent.timeout-seconds:30}") long timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalApiKey = internalApiKey;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public AgentServiceTaskSummary createTask(AgentServiceCreateRequest request) {
        try {
            String boundary = "----review-agent-" + UUID.randomUUID();
            byte[] body = multipartBody(boundary, request);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/agent/tasks"))
                    .timeout(timeout)
                    .header("X-Agent-Api-Key", internalApiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            Map<String, Object> response = sendJson(httpRequest);
            return new AgentServiceTaskSummary(
                    response.get("taskId").toString(),
                    response.get("status").toString(),
                    response.get("step").toString()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AgentServiceException(503, "Failed to create agent task", ex);
        } catch (IOException ex) {
            throw new AgentServiceException(503, "Failed to create agent task", ex);
        }
    }

    @Override
    public AgentServiceTaskStatus getTaskStatus(String externalTaskId) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/agent/tasks/" + externalTaskId))
                    .timeout(timeout)
                    .header("X-Agent-Api-Key", internalApiKey)
                    .GET()
                    .build();
            Map<String, Object> response = sendJson(httpRequest);
            return new AgentServiceTaskStatus(
                    response.get("taskId").toString(),
                    response.get("taskType").toString(),
                    response.get("status").toString(),
                    response.get("step").toString(),
                    response.get("error") == null ? null : response.get("error").toString()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AgentServiceException(503, "Failed to fetch agent task status", ex);
        } catch (IOException ex) {
            throw new AgentServiceException(503, "Failed to fetch agent task status", ex);
        }
    }

    @Override
    public AgentServiceResult getTaskResult(String externalTaskId) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/agent/tasks/" + externalTaskId + "/result"))
                    .timeout(timeout)
                    .header("X-Agent-Api-Key", internalApiKey)
                    .GET()
                    .build();
            Map<String, Object> response = sendJson(httpRequest);
            return new AgentServiceResult(
                    response.get("resultType").toString(),
                    castMap(response.get("rawResult")),
                    castMap(response.get("redactedResult"))
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AgentServiceException(503, "Failed to fetch agent task result", ex);
        } catch (IOException ex) {
            throw new AgentServiceException(503, "Failed to fetch agent task result", ex);
        }
    }

    private Map<String, Object> sendJson(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AgentServiceException(response.statusCode(), "Agent service returned HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), new TypeReference<>() {
        });
    }

    private byte[] multipartBody(String boundary, AgentServiceCreateRequest request) throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("taskType", request.taskType());
        metadata.put("manuscriptId", request.manuscriptId());
        metadata.put("versionId", request.versionId());
        metadata.put("roundId", request.roundId());
        metadata.put("requestPayload", request.requestPayload());
        metadata.put("force", request.force());
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"metadata\"\r\n\r\n"
                + metadataJson + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + request.fileName() + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes = tail.getBytes(StandardCharsets.UTF_8);
        byte[] pdfBytes = request.pdfBytes();
        byte[] body = new byte[headBytes.length + pdfBytes.length + tailBytes.length];
        System.arraycopy(headBytes, 0, body, 0, headBytes.length);
        System.arraycopy(pdfBytes, 0, body, headBytes.length, pdfBytes.length);
        System.arraycopy(tailBytes, 0, body, headBytes.length + pdfBytes.length, tailBytes.length);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}

