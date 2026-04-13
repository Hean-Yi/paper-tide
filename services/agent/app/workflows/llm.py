from __future__ import annotations

import json
from typing import Any


def request_json_from_model(state: dict[str, Any], *, instruction: str, fallback: dict[str, Any]) -> dict[str, Any]:
    client = state.get("llm_client")
    model = state.get("llm_model")
    if client is None or model is None:
        return fallback

    response = client.chat.completions.create(
        model=model,
        temperature=0,
        response_format={"type": "json_object"},
        messages=[
            {
                "role": "system",
                "content": (
                    "Return compact valid JSON only. Do not reveal author names, institutions, "
                    "acknowledgements, grants, self-citation clues, or author-history references."
                ),
            },
            {"role": "user", "content": instruction},
        ],
    )
    content = response.choices[0].message.content or "{}"
    parsed = json.loads(content)
    return {**fallback, **parsed}
