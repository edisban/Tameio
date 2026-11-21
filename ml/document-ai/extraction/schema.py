"""What an LLM must answer, checked with Pydantic: a reply that does not fit counts as invalid JSON."""

import json
from datetime import date
from decimal import Decimal
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from training.receipts import CATEGORIES

Category = StrEnum("Category", {name: name for name in CATEGORIES})


class ReceiptFields(BaseModel):
    model_config = ConfigDict(extra="forbid")

    issuer_afm: str | None = Field(pattern=r"^\d{9}$")
    issue_date: date | None
    net_amount: Decimal | None = Field(decimal_places=2)
    vat_amount: Decimal | None = Field(decimal_places=2)
    total_amount: Decimal | None = Field(decimal_places=2)
    category: Category | None

    def as_text(self) -> dict[str, str | None]:
        """The same text form as the golden set, so fields compare as plain strings."""
        return {
            "issuer_afm": self.issuer_afm,
            "issue_date": self.issue_date.isoformat() if self.issue_date else None,
            "net_amount": f"{self.net_amount:.2f}" if self.net_amount is not None else None,
            "vat_amount": f"{self.vat_amount:.2f}" if self.vat_amount is not None else None,
            "total_amount": f"{self.total_amount:.2f}" if self.total_amount is not None else None,
            "category": self.category.value if self.category else None,
        }


def parse_reply(reply: str) -> ReceiptFields | None:
    """The JSON object in a model's reply, or None. Small models often wrap it in ``` fences or add a line."""
    start, end = reply.find("{"), reply.rfind("}")
    if start < 0 or end < start:
        return None
    try:
        return ReceiptFields.model_validate(json.loads(reply[start : end + 1]))
    except (json.JSONDecodeError, ValidationError):
        return None
