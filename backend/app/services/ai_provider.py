from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import httpx

from app.config import settings


@dataclass
class AIResult:
    text: str
    model: str
    provider: str
    tokens_in: int = 0
    tokens_out: int = 0
    raw: dict[str, Any] | None = None


class ProviderError(Exception):
    pass


class _BaseProvider:
    name: str = "base"

    def chat(self, *, system: str, user: str, model: str, max_tokens: int = 1500, temperature: float = 0.85) -> AIResult:
        raise NotImplementedError


class DeepSeekProvider(_BaseProvider):
    name = "deepseek"
    BASE = "https://api.deepseek.com/v1/chat/completions"

    def chat(self, *, system, user, model, max_tokens=1500, temperature=0.85) -> AIResult:
        if not settings.DEEPSEEK_API_KEY:
            raise ProviderError("DeepSeek API key not configured")
        headers = {"Authorization": f"Bearer {settings.DEEPSEEK_API_KEY}"}
        body = {
            "model": model or "deepseek-chat",
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "max_tokens": max_tokens,
            "temperature": temperature,
        }
        with httpx.Client(timeout=120) as client:
            r = client.post(self.BASE, headers=headers, json=body)
            r.raise_for_status()
            data = r.json()
        msg = data["choices"][0]["message"]["content"]
        usage = data.get("usage") or {}
        return AIResult(
            text=msg,
            model=body["model"],
            provider=self.name,
            tokens_in=usage.get("prompt_tokens", 0),
            tokens_out=usage.get("completion_tokens", 0),
            raw=data,
        )


class AnthropicProvider(_BaseProvider):
    name = "anthropic"
    BASE = "https://api.anthropic.com/v1/messages"

    def chat(self, *, system, user, model, max_tokens=1500, temperature=0.85) -> AIResult:
        if not settings.ANTHROPIC_API_KEY:
            raise ProviderError("Anthropic API key not configured")
        headers = {
            "x-api-key": settings.ANTHROPIC_API_KEY,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        }
        body = {
            "model": model or "claude-sonnet-4-6",
            "max_tokens": max_tokens,
            "temperature": temperature,
            "system": system,
            "messages": [{"role": "user", "content": user}],
        }
        with httpx.Client(timeout=180) as client:
            r = client.post(self.BASE, headers=headers, json=body)
            r.raise_for_status()
            data = r.json()
        text = "".join(block.get("text", "") for block in data.get("content", []))
        usage = data.get("usage") or {}
        return AIResult(
            text=text,
            model=body["model"],
            provider=self.name,
            tokens_in=usage.get("input_tokens", 0),
            tokens_out=usage.get("output_tokens", 0),
            raw=data,
        )


class OpenRouterProvider(_BaseProvider):
    name = "openrouter"
    BASE = "https://openrouter.ai/api/v1/chat/completions"

    def chat(self, *, system, user, model, max_tokens=1500, temperature=0.85) -> AIResult:
        if not settings.OPENROUTER_API_KEY:
            raise ProviderError("OpenRouter API key not configured")
        headers = {"Authorization": f"Bearer {settings.OPENROUTER_API_KEY}"}
        body = {
            "model": model or "deepseek/deepseek-chat",
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "max_tokens": max_tokens,
            "temperature": temperature,
        }
        with httpx.Client(timeout=180) as client:
            r = client.post(self.BASE, headers=headers, json=body)
            r.raise_for_status()
            data = r.json()
        msg = data["choices"][0]["message"]["content"]
        usage = data.get("usage") or {}
        return AIResult(
            text=msg,
            model=body["model"],
            provider=self.name,
            tokens_in=usage.get("prompt_tokens", 0),
            tokens_out=usage.get("completion_tokens", 0),
            raw=data,
        )


_PROVIDERS: dict[str, _BaseProvider] = {
    "deepseek": DeepSeekProvider(),
    "anthropic": AnthropicProvider(),
    "openrouter": OpenRouterProvider(),
}


def get_provider(name: str | None = None) -> _BaseProvider:
    key = (name or settings.AI_PROVIDER or "deepseek").lower()
    if key not in _PROVIDERS:
        raise ProviderError(f"Unknown provider: {key}")
    return _PROVIDERS[key]
