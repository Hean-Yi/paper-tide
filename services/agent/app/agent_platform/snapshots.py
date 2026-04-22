from __future__ import annotations

from types import MappingProxyType
from typing import Any, Mapping


def freeze_json_mapping(value: Mapping[str, Any]) -> Mapping[str, Any]:
    return MappingProxyType({key: freeze_json_value(nested) for key, nested in value.items()})


def freeze_json_value(value: Any) -> Any:
    if isinstance(value, Mapping):
        return freeze_json_mapping({str(key): nested for key, nested in value.items()})
    if isinstance(value, list | tuple):
        return tuple(freeze_json_value(item) for item in value)
    if isinstance(value, set | frozenset):
        return frozenset(freeze_json_value(item) for item in value)
    return value


def thaw_json_mapping(value: Mapping[str, Any]) -> dict[str, Any]:
    return {key: thaw_json_value(nested) for key, nested in value.items()}


def thaw_json_value(value: Any) -> Any:
    if isinstance(value, Mapping):
        return thaw_json_mapping(value)
    if isinstance(value, tuple):
        return [thaw_json_value(item) for item in value]
    if isinstance(value, frozenset):
        return [thaw_json_value(item) for item in value]
    return value
