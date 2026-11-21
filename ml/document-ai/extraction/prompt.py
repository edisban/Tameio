"""The prompt of both LLM variants. The prompted model also sees one worked example from the train split;
the fine-tune learns the answer format from all of them, so it is trained and asked without one."""

import json

from training.dataset import Example
from training.receipts import CATEGORIES

SYSTEM = (
    "You read the OCR text of a Greek receipt or invoice and return its fields as one JSON object, with no "
    "other text. Keys:\n"
    '- "issuer_afm": the issuer\'s ΑΦΜ (Greek tax number), exactly 9 digits\n'
    '- "issue_date": the issue date as YYYY-MM-DD\n'
    '- "net_amount", "vat_amount", "total_amount": the amounts printed on the receipt, in euro, as strings '
    "with a dot before the two decimals; the total includes VAT, and net plus VAT equals the total\n"
    f'- "category": one of {", ".join(CATEGORIES)}\n'
    "Use null for a field the text does not show. The text may contain OCR errors."
)


def messages(text: str, shown: Example | None = None) -> list[dict[str, str]]:
    """The chat for one receipt; with a shown example, its text and correct answer come first (one-shot)."""
    shot = []
    if shown is not None:
        shot = [{"role": "user", "content": shown.text}, {"role": "assistant", "content": answer(shown.expected)}]
    return [{"role": "system", "content": SYSTEM}, *shot, {"role": "user", "content": text}]


def answer(expected: dict[str, str]) -> str:
    """The reply a perfect model gives: the training target of the fine-tune."""
    return json.dumps(expected, ensure_ascii=False)


def training_example(example: Example) -> dict[str, list[dict[str, str]]]:
    """Prompt and completion in chat form, so the trainer learns only from the completion."""
    return {
        "prompt": messages(example.text),
        "completion": [{"role": "assistant", "content": answer(example.expected)}],
    }
