"""What Prometheus scrapes from the assistant: calls to Claude, tokens, tool use, refusals and latency.

Grafana already reads Prometheus for the backend; these show what the assistant costs and how it behaves.
"""

from typing import Any

from prometheus_client import Counter, Histogram

CLAUDE_CALLS = Counter("assistant_claude_calls_total", "Calls to the Claude API", ["stop_reason"])
TOKENS = Counter("assistant_tokens_total", "Tokens billed by the Claude API", ["kind"])
TOOL_CALLS = Counter("assistant_tool_calls_total", "Tools the assistant called", ["tool"])
CLAUDE_SECONDS = Histogram("assistant_claude_seconds", "Time a Claude API call took")
CONVERSATION_SECONDS = Histogram("assistant_answer_seconds", "Time to answer one question, tools included")


def observe_call(response: Any, seconds: float) -> None:
    CLAUDE_CALLS.labels(str(response.stop_reason)).inc()
    CLAUDE_SECONDS.observe(seconds)
    usage = getattr(response, "usage", None)
    for kind in ("input_tokens", "output_tokens", "cache_read_input_tokens", "cache_creation_input_tokens"):
        value = getattr(usage, kind, None) if usage is not None else None
        if isinstance(value, int) and value > 0:
            TOKENS.labels(kind).inc(value)
