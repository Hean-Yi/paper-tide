from app.agent_platform.config import AgentPlatformConfig


def test_config_loads_current_env_keys_and_normalizes_chat_completions_url(tmp_path) -> None:
    env_file = tmp_path / ".env"
    env_file.write_text(
        "\n".join(
            [
                "API=secret-key",
                "URL=https://api.siliconflow.cn/v1/chat/completions",
                "MODEL=Qwen/Qwen3-VL-32B-Thinking",
            ]
        )
    )

    config = AgentPlatformConfig.from_env(env_file=env_file)

    assert config.llm_api_key == "secret-key"
    assert config.llm_base_url == "https://api.siliconflow.cn/v1"
    assert config.llm_model == "Qwen/Qwen3-VL-32B-Thinking"
    assert config.has_llm_provider()
