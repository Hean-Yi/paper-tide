import json

from app.agent_platform.config import AgentPlatformConfig
from app.agent_platform.provider_executor import ProviderExecutor


class FakeChatCompletions:
    def __init__(self) -> None:
        self.calls: list[dict[str, object]] = []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        content = {
            "taskType": "SCREENING_ANALYSIS",
            "manuscriptId": "11",
            "versionId": "21",
            "status": "SUCCESS",
            "topicCategory": "AI systems",
            "scopeFit": "FIT",
            "formatRisks": ["Check formatting against venue rules."],
            "blindnessRisks": [],
            "screeningSummary": "The submission is ready for chair screening.",
            "confidence": 0.74,
        }
        return type(
            "FakeResponse",
            (),
            {
                "choices": [
                    type(
                        "FakeChoice",
                        (),
                        {"message": type("FakeMessage", (), {"content": json.dumps(content)})()},
                    )()
                ]
            },
        )()


class FakeClient:
    def __init__(self) -> None:
        self.chat = type("FakeChat", (), {"completions": FakeChatCompletions()})()


def test_provider_executor_calls_configured_model_with_strict_json_schema() -> None:
    client = FakeClient()
    executor = ProviderExecutor(
        AgentPlatformConfig(
            llm_api_key="secret-key",
            llm_base_url="https://api.siliconflow.cn/v1",
            llm_model="Qwen/Qwen3-VL-32B-Thinking",
        ),
        client=client,
    )

    result = executor.run_screening(
        {
            "title": "Agent Review",
            "abstract": "A paper about agent-assisted review.",
            "keywords": ["AI systems"],
            "screening": {"manuscriptId": 11, "versionId": 21},
        }
    )

    assert result["screeningSummary"] == "The submission is ready for chair screening."
    call = client.chat.completions.calls[0]
    assert call["model"] == "Qwen/Qwen3-VL-32B-Thinking"
    assert call["response_format"]["type"] == "json_schema"
    assert call["response_format"]["json_schema"]["strict"] is True


def test_provider_executor_truncates_large_pdf_payload_before_prompting() -> None:
    client = FakeClient()
    executor = ProviderExecutor(
        AgentPlatformConfig(
            llm_api_key="secret-key",
            llm_base_url="https://api.siliconflow.cn/v1",
            llm_model="Qwen/Qwen3-VL-32B-Thinking",
        ),
        client=client,
    )

    executor.run_screening(
        {
            "title": "Long Paper",
            "abstract": "A paper with a long extracted body.",
            "keywords": ["AI systems"],
            "pdfText": "x" * 20000,
            "sections": {"method": "y" * 12000},
            "screening": {"manuscriptId": 11, "versionId": 21},
        }
    )

    prompt = client.chat.completions.calls[0]["messages"][1]["content"]
    assert len(prompt) < 9000
    assert "[truncated" in prompt
