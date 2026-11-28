from datetime import date
from types import SimpleNamespace
from typing import Any
from uuid import UUID

import numpy as np
import pytest

from assistant import agent, sample
from assistant.evals import AGENT, Case, hit_rate, judge
from assistant.knowledge import FastEmbedder, KnowledgeBase, passages
from assistant.tools import SPECS, account_overview, expenses_by_category, loan_status, runner

TODAY = date(2026, 11, 28)
JACKS = UUID(sample.JACKS_CAFE)


@pytest.fixture
def db():  # type: ignore[no-untyped-def]
    return sample.database(TODAY)


class Words:
    """A stand-in embedder: a vector of word counts, enough to test the search without the model."""

    def __init__(self) -> None:
        self.vocabulary: dict[str, int] = {}

    def embed(self, texts: list[str]) -> np.ndarray:
        rows = []
        for t in texts:
            words = [w.strip(".,;:«»()?").lower() for w in t.split()]
            for w in words:
                self.vocabulary.setdefault(w, len(self.vocabulary))
            rows.append(words)
        vectors = np.zeros((len(texts), 4096), dtype=np.float32)
        for i, words in enumerate(rows):
            for w in words:
                vectors[i, self.vocabulary[w] % 4096] += 1
        return vectors


# --- Tools: only the business that is signed in ---


def test_the_overview_shows_only_this_business(db) -> None:  # type: ignore[no-untyped-def]
    overview = account_overview(db, JACKS)
    assert [a["balance"] for a in overview["accounts"]] == [8420.50]
    assert [m["amount"] for m in overview["latestMovements"]] == [640.0, -1200.0]


def test_expenses_add_up_by_category_over_the_months_asked(db) -> None:  # type: ignore[no-untyped-def]
    three = expenses_by_category(db, JACKS, 3, today=TODAY)
    assert {c["category"]: c["total"] for c in three["categories"]} == {
        "RENT": 1200.0,
        "GROCERIES": 310.0,
        "UTILITIES": 95.4,
    }
    assert three["total"] == 1605.4
    # The equipment bought 200 days ago is within a year, and other businesses' expenses never are
    assert expenses_by_category(db, JACKS, 12, today=TODAY)["total"] == 2405.4


def test_the_months_are_kept_within_a_year(db) -> None:  # type: ignore[no-untyped-def]
    assert expenses_by_category(db, JACKS, 99, today=TODAY)["since"] == "2025-12-03"
    assert expenses_by_category(db, JACKS, 0, today=TODAY)["since"] == "2026-10-29"


def test_a_paid_out_loan_shows_its_next_instalment(db) -> None:  # type: ignore[no-untyped-def]
    loan = loan_status(db, JACKS)["loans"][0]
    assert loan["status"] == "DISBURSED"
    assert loan["nextInstalment"]["amount"] == 617.54
    assert loan_status(db, UUID(sample.OTHER_SHOP)) == {"loans": []}


def test_no_tool_takes_a_business_and_every_schema_is_strict() -> None:
    for spec in SPECS:
        assert spec["strict"] is True
        assert spec["input_schema"]["additionalProperties"] is False
        assert not any("business" in name for name in spec["input_schema"]["properties"])


# --- Retrieval ---


def test_each_section_of_the_documents_is_a_passage_with_its_source() -> None:
    found = passages()
    assert {p.source for p in found} == {"loans.md", "expenses.md", "account.md", "assistant.md"}
    rate = next(p for p in found if "επιτόκιο" in p.section)
    assert rate.source == "loans.md" and "7%" in rate.text


def test_the_search_returns_the_closest_passages_first() -> None:
    knowledge = KnowledgeBase(Words())
    best = knowledge.search("Ποιο είναι το ονομαστικό επιτόκιο;", k=1)[0]
    assert best.source == "loans.md"
    assert knowledge.search("   ") == []


def test_the_real_model_finds_the_right_document_for_almost_every_question() -> None:
    # The ONNX model (downloaded once, ~200 MB); the quality gate of the retrieval
    rate, misses = hit_rate(KnowledgeBase(FastEmbedder()))
    assert rate >= 0.9, misses


# --- The agent's loop, with a stand-in for Claude ---


def block(kind: str, **fields: Any) -> SimpleNamespace:
    return SimpleNamespace(type=kind, **fields)


def reply(stop: str, *content: SimpleNamespace) -> SimpleNamespace:
    return SimpleNamespace(
        stop_reason=stop, content=list(content), usage=SimpleNamespace(input_tokens=10, output_tokens=5)
    )


class ScriptedClaude:
    """Answers each call with the next scripted response and keeps the requests it was sent."""

    def __init__(self, *responses: SimpleNamespace) -> None:
        self.responses = list(responses)
        self.requests: list[dict[str, Any]] = []
        self.beta = SimpleNamespace(messages=SimpleNamespace(create=self.create))

    def create(self, **request: Any) -> SimpleNamespace:
        self.requests.append(request)
        return self.responses.pop(0)


def ask(claude: ScriptedClaude, db, question: str = "Πόσο ξόδεψα;") -> agent.Answer:  # type: ignore[no-untyped-def]
    run_tool = runner(db, KnowledgeBase(Words()), JACKS)
    return agent.answer(claude, agent.Settings(), run_tool, [{"role": "user", "content": question}])


def test_claude_calls_a_tool_gets_the_figures_and_answers(db) -> None:  # type: ignore[no-untyped-def]
    claude = ScriptedClaude(
        reply("tool_use", block("tool_use", id="t1", name="expenses_by_category", input={"months": 3})),
        reply("end_turn", block("text", text="Ξοδέψατε 1.605,40 € τους τελευταίους 3 μήνες.")),
    )
    answer = ask(claude, db)

    assert answer.reply == "Ξοδέψατε 1.605,40 € τους τελευταίους 3 μήνες."
    assert answer.tools_used == ["expenses_by_category"]
    result = claude.requests[1]["messages"][-1]["content"][0]
    assert result["tool_use_id"] == "t1" and "RENT" in result["content"] and "7777" not in result["content"]


def test_each_request_carries_the_model_effort_fallbacks_and_strict_tools(db) -> None:  # type: ignore[no-untyped-def]
    claude = ScriptedClaude(reply("end_turn", block("text", text="Γεια σας.")))
    ask(claude, db, "Γεια")

    sent = claude.requests[0]
    assert sent["model"] == "claude-opus-5-5"
    assert sent["output_config"] == {"effort": "low"}
    assert sent["fallbacks"] == "default" and sent["betas"] == ["server-side-fallback-2026-07-01"]
    assert all(t["strict"] for t in sent["tools"])


def test_a_refusal_gets_a_polite_answer_rather_than_nothing(db) -> None:  # type: ignore[no-untyped-def]
    answer = ask(ScriptedClaude(reply("refusal")), db)
    assert answer.reply.startswith("Δεν μπορώ να βοηθήσω")


def test_a_failing_tool_is_told_to_claude_as_an_error(db) -> None:  # type: ignore[no-untyped-def]
    claude = ScriptedClaude(
        reply("tool_use", block("tool_use", id="t1", name="no_such_tool", input={})),
        reply("end_turn", block("text", text="Δεν βρήκα κάτι.")),
    )
    ask(claude, db)
    result = claude.requests[1]["messages"][-1]["content"][0]
    assert result["is_error"] is True


def test_the_loop_stops_after_a_few_turns_of_tool_calls(db) -> None:  # type: ignore[no-untyped-def]
    endless = [reply("tool_use", block("tool_use", id=f"t{i}", name="loan_status", input={})) for i in range(10)]
    claude = ScriptedClaude(*endless)
    answer = ask(claude, db)
    assert len(claude.requests) == agent.MAX_TURNS
    assert answer.reply  # a fallback sentence, not an empty answer


def test_document_answers_carry_their_sources(db) -> None:  # type: ignore[no-untyped-def]
    claude = ScriptedClaude(
        reply(
            "tool_use", block("tool_use", id="t1", name="search_bank_documents", input={"query": "επιτόκιο δανείου"})
        ),
        reply("end_turn", block("text", text="Το επιτόκιο είναι 7%.")),
    )
    answer = ask(claude, db, "Τι επιτόκιο έχετε;")
    assert any(s["source"] == "loans.md" for s in answer.sources)


# --- The judge of the agent evaluation ---


def test_the_judge_reads_figures_with_or_without_thousands_separators() -> None:
    case = Case("q", tools=("account_overview",), contains=("8.420",), absent=("99.999",))
    assert judge(case, "Έχετε 8420,50 €.", ["account_overview"]) == []
    assert judge(case, "Έχετε 99.999 €.", ["account_overview"]) == ["no '8.420' in the reply", "'99.999' in the reply"]


def test_every_agent_case_says_what_it_checks() -> None:
    assert all(c.tools or c.contains or c.absent or c.no_data_tools for c in AGENT)
