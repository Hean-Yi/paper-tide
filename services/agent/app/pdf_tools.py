from __future__ import annotations

import re
from io import BytesIO
from typing import Any


class PdfExtractionError(ValueError):
    pass


def extract_pdf_text(pdf_bytes: bytes) -> str:
    try:
        from pypdf import PdfReader
    except ImportError as exc:  # pragma: no cover - dependency is declared for runtime.
        raise PdfExtractionError("pypdf is required for PDF extraction") from exc

    try:
        reader = PdfReader(BytesIO(pdf_bytes))
        page_text = [page.extract_text() or "" for page in reader.pages]
    except Exception as exc:
        raise PdfExtractionError("Failed to read PDF upload") from exc

    return normalize_text("\n".join(page_text))


def split_sections(text: str) -> dict[str, str]:
    normalized = normalize_text(text)
    sections = {
        "introduction": "",
        "method": "",
        "experiment": "",
        "conclusion": "",
    }
    if not normalized:
        return sections

    patterns = {
        "introduction": r"\b(?:abstract\s+)?introduction\b",
        "method": r"\b(?:method|methodology|approach|proposed method)\b",
        "experiment": r"\b(?:experiment|experiments|evaluation|results)\b",
        "conclusion": r"\b(?:conclusion|conclusions|discussion)\b",
    }
    matches: list[tuple[int, str]] = []
    lower_text = normalized.lower()
    for name, pattern in patterns.items():
        match = re.search(pattern, lower_text)
        if match:
            matches.append((match.start(), name))

    if not matches:
        chunk = normalized[:2000]
        sections["introduction"] = chunk
        sections["method"] = chunk
        return sections

    matches.sort()
    for index, (start, name) in enumerate(matches):
        end = matches[index + 1][0] if index + 1 < len(matches) else len(normalized)
        sections[name] = normalized[start:end].strip()[:4000]

    if not sections["introduction"]:
        sections["introduction"] = normalized[:2000]
    return sections


def build_pdf_payload(
    *,
    metadata_payload: dict[str, Any] | None,
    pdf_bytes: bytes,
    file_name: str,
    file_size: int | None = None,
) -> dict[str, Any]:
    extracted_text = extract_pdf_text(pdf_bytes)
    sections = split_sections(extracted_text)
    payload = dict(metadata_payload or {})
    payload["pdfText"] = extracted_text
    payload["sections"] = sections
    payload["pdfExtraction"] = {
        "fileName": file_name,
        "fileSize": file_size if file_size is not None else len(pdf_bytes),
        "status": "EXTRACTED" if extracted_text else "EMPTY_TEXT",
    }
    payload.setdefault("reviewReports", [])
    return payload


def normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", value or "").strip()
