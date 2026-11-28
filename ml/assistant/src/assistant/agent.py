"""The assistant as a LangGraph graph: Claude decides, the tools look things up, and the loop runs until Claude answers.

    START -> claude --(asked for tools)--> tools -> claude ... --(answered)--> END

The graph holds the control flow (the loop, a cap on turns, what happens on a refusal); each call to Claude goes
through the official Anthropic SDK. Tools run with the business the backend vouched for, never one the model names.
"""

import json
import time
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from assistant import metrics
from assistant.tools import SPECS

MAX_TURNS = 6
MAX_TOKENS = 16000

SYSTEM = """You are Tameio's assistant for a small business that banks with Tameio, a fictional Greek bank.
Answer in Greek, briefly and plainly, as a helpful bank employee would.

- For the business's own figures (balances, movements, expenses, loans), call a tool; never guess a number.
- For the bank's terms, rates, fees or how something works, call search_bank_documents and say which document
  the answer comes from. If the documents do not say, say you do not know.
- You can only read. You cannot move money, apply for or approve a loan, or change anything: say so and tell the
  user where in the app to do it.
- Do not give tax, legal or investment advice; suggest the business's accountant or the bank.
- You see only this business. If asked about another person or business, say you cannot see it."""


class State(TypedDict, total=False):
    messages: list[dict[str, Any]]
    turns: int
    last: Any
    tools_used: list[str]
    sources: list[dict[str, Any]]
    reply: str


@dataclass(frozen=True)
class Settings:
    model: str = "claude-opus-5-5"
    # Chat answers need little deliberation; the default on this model is medium
    effort: str = "low"
    # On a safety refusal, the API retries on the model it picks for that kind of refusal
    fallbacks: bool = True


@dataclass
class Answer:
    reply: str
    tools_used: list[str] = field(default_factory=list)
    sources: list[dict[str, Any]] = field(default_factory=list)


def build(client: Any, settings: Settings, run_tool: Callable[[str, dict[str, Any]], Any]) -> Any:
    """The compiled graph for one conversation; `client` is an `anthropic.Anthropic` (or a stand-in in tests)."""

    def claude(state: State) -> State:
        request: dict[str, Any] = {
            "model": settings.model,
            "max_tokens": MAX_TOKENS,
            "system": SYSTEM,
            "tools": SPECS,
            "messages": state["messages"],
            "output_config": {"effort": settings.effort},
            # The system prompt and tools are the same every time: cache them
            "cache_control": {"type": "ephemeral"},
        }
        if settings.fallbacks:
            request |= {"betas": ["server-side-fallback-2026-07-01"], "fallbacks": "default"}
        started = time.perf_counter()
        response = client.beta.messages.create(**request)
        metrics.observe_call(response, time.perf_counter() - started)
        # The whole content goes back, thinking blocks included, as the API expects on the next turn
        messages = [*state["messages"], {"role": "assistant", "content": response.content}]
        return {"messages": messages, "turns": state.get("turns", 0) + 1, "last": response}

    def tools(state: State) -> State:
        results, used, sources = [], list(state.get("tools_used", [])), list(state.get("sources", []))
        for block in state["last"].content:
            if block.type != "tool_use":
                continue
            used.append(block.name)
            metrics.TOOL_CALLS.labels(block.name).inc()
            try:
                output = run_tool(block.name, dict(block.input))
                if block.name == "search_bank_documents":
                    sources.extend({"source": p["source"], "section": p["section"]} for p in output)
                results.append(
                    {"type": "tool_result", "tool_use_id": block.id, "content": json.dumps(output, ensure_ascii=False)}
                )
            except Exception as error:  # a failed tool is told to Claude, not hidden
                results.append(
                    {"type": "tool_result", "tool_use_id": block.id, "content": str(error), "is_error": True}
                )
        # Every result of the turn in one message, as the API expects for parallel calls
        return {
            "messages": [*state["messages"], {"role": "user", "content": results}],
            "tools_used": used,
            "sources": sources,
        }

    def finish(state: State) -> State:
        response = state["last"]
        if response.stop_reason == "refusal":
            reply = (
                "Δεν μπορώ να βοηθήσω με αυτό. Για οτιδήποτε αφορά τον λογαριασμό σας, επικοινωνήστε με την τράπεζα."
            )
        else:
            reply = "".join(b.text for b in response.content if b.type == "text").strip()
            if not reply:
                reply = "Δεν κατάφερα να ολοκληρώσω την απάντηση. Δοκιμάστε να ρωτήσετε πιο απλά."
        return {"reply": reply}

    def next_step(state: State) -> str:
        if state["last"].stop_reason in ("tool_use", "pause_turn") and state["turns"] < MAX_TURNS:
            return "tools" if state["last"].stop_reason == "tool_use" else "claude"
        return "finish"

    graph = StateGraph(State)
    graph.add_node("claude", claude)
    graph.add_node("tools", tools)
    graph.add_node("finish", finish)
    graph.add_edge(START, "claude")
    graph.add_conditional_edges("claude", next_step, {"tools": "tools", "claude": "claude", "finish": "finish"})
    graph.add_edge("tools", "claude")
    graph.add_edge("finish", END)
    return graph.compile()


def answer(
    client: Any, settings: Settings, run_tool: Callable[[str, dict[str, Any]], Any], conversation: list[dict[str, str]]
) -> Answer:
    """Runs one turn of the conversation (the last message is the user's question)."""
    state = build(client, settings, run_tool).invoke({"messages": list(conversation), "turns": 0})
    sources = list({(s["source"], s["section"]): s for s in state.get("sources", [])}.values())
    return Answer(state["reply"], state.get("tools_used", []), sources)
