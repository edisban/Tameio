"""What the assistant can look up: read-only questions to the database, always for one business, and the bank's
documents (RAG).

The business is never a tool argument: the service puts in the business the backend says is signed in, so the model
cannot ask about another one, however it is prompted. The database role the service connects as can only read.
"""

from collections.abc import Callable
from datetime import date, timedelta
from decimal import Decimal
from typing import Any
from uuid import UUID

from sqlalchemy import Engine, text

from assistant.knowledge import KnowledgeBase

MAX_MONTHS = 12
LAST_MOVEMENTS = 5


def _money(value: Decimal | None) -> float:
    return float(value or 0)


def account_overview(engine: Engine, business_id: UUID) -> dict[str, Any]:
    """The business's accounts with their balances, and its latest movements."""
    with engine.connect() as db:
        accounts = db.execute(
            text(
                "SELECT id, name, iban, balance, status FROM bank_account "
                "WHERE business_id = :business AND kind = 'CUSTOMER' ORDER BY created_at"
            ),
            {"business": str(business_id)},
        ).all()
        movements = db.execute(
            text(
                "SELECT p.booked_at, e.description, p.amount FROM posting p "
                "JOIN journal_entry e ON e.id = p.journal_entry_id "
                "JOIN bank_account a ON a.id = p.account_id "
                "WHERE a.business_id = :business ORDER BY p.booked_at DESC LIMIT :n"
            ),
            {"business": str(business_id), "n": LAST_MOVEMENTS},
        ).all()
    return {
        "accounts": [
            {"name": a.name, "iban": a.iban, "balance": _money(a.balance), "status": a.status} for a in accounts
        ],
        "latestMovements": [
            {"date": str(m.booked_at)[:10], "description": m.description, "amount": _money(m.amount)} for m in movements
        ],
    }


def expenses_by_category(engine: Engine, business_id: UUID, months: int, today: date | None = None) -> dict[str, Any]:
    """Confirmed expenses of the last `months` months, by category, with the total and the VAT paid."""
    months = max(1, min(int(months), MAX_MONTHS))
    since = (today or date.today()) - timedelta(days=30 * months)
    with engine.connect() as db:
        rows = db.execute(
            text(
                "SELECT category, COUNT(*) AS documents, SUM(total_amount) AS total, SUM(vat_amount) AS vat "
                "FROM expense WHERE business_id = :business AND issue_date >= :since "
                "GROUP BY category ORDER BY total DESC"
            ),
            {"business": str(business_id), "since": since},
        ).all()
    categories = [
        {"category": r.category, "documents": r.documents, "total": _money(r.total), "vat": _money(r.vat)} for r in rows
    ]
    return {
        "since": since.isoformat(),
        "categories": categories,
        "total": round(sum(c["total"] for c in categories), 2),
        "vat": round(sum(c["vat"] for c in categories), 2),
    }


def loan_status(engine: Engine, business_id: UUID) -> dict[str, Any]:
    """The business's loan applications, and for a paid-out loan its next instalment."""
    with engine.connect() as db:
        loans = db.execute(
            text(
                "SELECT id, amount, term_months, purpose, monthly_instalment, status, submitted_at "
                "FROM loan_application WHERE business_id = :business ORDER BY created_at DESC"
            ),
            {"business": str(business_id)},
        ).all()
        result = []
        for loan in loans:
            following = db.execute(
                text(
                    "SELECT due_on, amount FROM loan_instalment "
                    "WHERE loan_application_id = :loan AND status = 'SCHEDULED' ORDER BY due_on LIMIT 1"
                ),
                {"loan": str(loan.id)},
            ).first()
            result.append(
                {
                    "amount": _money(loan.amount),
                    "termMonths": loan.term_months,
                    "purpose": loan.purpose,
                    "monthlyInstalment": _money(loan.monthly_instalment),
                    "status": loan.status,
                    "nextInstalment": {"dueOn": str(following.due_on), "amount": _money(following.amount)}
                    if following
                    else None,
                }
            )
    return {"loans": result}


# What Claude sees: names, what each is for, and strict input schemas. No tool takes a business.
SPECS: list[dict[str, Any]] = [
    {
        "name": "account_overview",
        "description": "The signed-in business's accounts (balance, IBAN, status) and its five latest movements.",
        "input_schema": {"type": "object", "properties": {}, "required": [], "additionalProperties": False},
        "strict": True,
    },
    {
        "name": "expenses_by_category",
        "description": "The business's confirmed expenses over the last N months (1 to 12): per category the number "
        "of documents, total and VAT, and the overall total and VAT.",
        "input_schema": {
            "type": "object",
            "properties": {"months": {"type": "integer", "description": "How many months back, 1 to 12"}},
            "required": ["months"],
            "additionalProperties": False,
        },
        "strict": True,
    },
    {
        "name": "loan_status",
        "description": "The business's loan applications: amount, term, purpose, instalment, status, and the next "
        "instalment of a loan that was paid out.",
        "input_schema": {"type": "object", "properties": {}, "required": [], "additionalProperties": False},
        "strict": True,
    },
    {
        "name": "search_bank_documents",
        "description": "Searches the bank's own documents (loan terms, rates, expenses and VAT, the account, security, "
        "what this assistant can do) and returns the most relevant passages with their source.",
        "input_schema": {
            "type": "object",
            "properties": {"query": {"type": "string", "description": "What to look for, in Greek"}},
            "required": ["query"],
            "additionalProperties": False,
        },
        "strict": True,
    },
]


def runner(engine: Engine, knowledge: KnowledgeBase, business_id: UUID) -> Callable[[str, dict[str, Any]], Any]:
    """The tools bound to one business: what the agent calls by name with Claude's arguments."""

    def run(name: str, arguments: dict[str, Any]) -> Any:
        if name == "account_overview":
            return account_overview(engine, business_id)
        if name == "expenses_by_category":
            return expenses_by_category(engine, business_id, arguments.get("months", 3))
        if name == "loan_status":
            return loan_status(engine, business_id)
        if name == "search_bank_documents":
            return [p.as_dict() for p in knowledge.search(str(arguments.get("query", "")))]
        raise ValueError(f"No tool named {name}")

    return run
