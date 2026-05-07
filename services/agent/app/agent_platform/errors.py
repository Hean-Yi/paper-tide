from __future__ import annotations


class ProviderExecutionError(RuntimeError):
    def __init__(self, message: str, *, category: str, retryable: bool) -> None:
        super().__init__(message)
        self.category = category
        self.retryable = retryable


PROVIDER_TRANSIENT = "PROVIDER_TRANSIENT"
PROVIDER_SCHEMA = "PROVIDER_SCHEMA"
BUSINESS_INPUT = "BUSINESS_INPUT"
UNEXPECTED_RUNTIME = "UNEXPECTED_RUNTIME"
