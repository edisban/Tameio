"""Measures each variant on the golden test set and writes metrics.json and a markdown comparison.

    python -m extraction.evaluate --variants baseline
    python -m extraction.evaluate --variants prompted finetuned --adapter models/field-extraction-lora
    python -m extraction.evaluate --variants baseline --check

Results of variants not run this time are kept from the existing metrics.json, so CI can measure the
baseline alone while the LLM results, measured once on a machine that can run them, stay in the file.
With --check nothing is written: the run fails if the baseline no longer scores what the file says.
"""

import argparse
import json
import statistics
import sys
import time
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path
from typing import Protocol

from training.dataset import DATA_ROOT, FIELDS, VERSION, Example, load

RESULTS = Path("results/field-extraction")
VARIANTS = ("baseline", "prompted", "finetuned")
FIELD_LABELS = {
    "issuer_afm": "ΑΦΜ",
    "issue_date": "Date",
    "net_amount": "Net",
    "vat_amount": "VAT",
    "total_amount": "Total",
    "category": "Category",
}
# Another CPU or library build may read one receipt differently; more than that is a real change
CHECK_TOLERANCE = 0.005
PERCENTILE_95 = 18  # statistics.quantiles(n=20) returns 19 cut points; index 18 is the 95th percentile


class Reader(Protocol):
    name: str
    model_name: str
    hardware: str

    def size_mb(self) -> float: ...

    def read(self, text: str) -> dict[str, str | None] | None: ...


def score(reader: Reader, test: list[Example], clock: Callable[[], float] = time.perf_counter) -> dict:
    """Exact match per field; a reply that is not valid JSON in the schema misses every field."""
    hits = dict.fromkeys(FIELDS, 0)
    all_correct = invalid = 0
    latencies = []
    for example in test:
        started = clock()
        found = reader.read(example.text)
        latencies.append((clock() - started) * 1000)
        if found is None:
            invalid += 1
            continue
        correct = [name for name in FIELDS if found.get(name) == example.expected[name]]
        for name in correct:
            hits[name] += 1
        all_correct += len(correct) == len(FIELDS)
    total = len(test)
    return {
        "model": reader.model_name,
        "hardware": reader.hardware,
        "documents": total,
        "field_accuracy": {name: round(count / total, 4) for name, count in hits.items()},
        "all_fields_correct": round(all_correct / total, 4),
        "invalid_json_rate": round(invalid / total, 4),
        "latency_ms": {
            "mean": round(statistics.fmean(latencies), 1),
            "p50": round(statistics.median(latencies), 1),
            "p95": round(statistics.quantiles(latencies, n=20)[PERCENTILE_95], 1) if total > 1 else latencies[0],
        },
        "model_size_mb": round(reader.size_mb(), 1),
        "evaluated_at": datetime.now(UTC).isoformat(timespec="seconds"),
    }


def merge(existing: dict | None, results: dict[str, dict]) -> dict:
    """The new results over the old ones; results measured on another dataset version are dropped."""
    kept = existing["variants"] if existing and existing.get("dataset") == f"field-extraction-{VERSION}" else {}
    variants = {**kept, **results}
    return {
        "dataset": f"field-extraction-{VERSION}",
        "variants": {name: variants[name] for name in VARIANTS if name in variants},
    }


def table(metrics: dict) -> str:
    header = ["Variant", *FIELD_LABELS.values(), "All 6", "Invalid JSON", "Latency p50", "Size", "Hardware"]
    rows = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    for name, m in metrics["variants"].items():
        cells = [
            name,
            *(f"{m['field_accuracy'][field]:.1%}" for field in FIELD_LABELS),
            f"{m['all_fields_correct']:.1%}",
            f"{m['invalid_json_rate']:.1%}",
            f"{m['latency_ms']['p50']:,.0f} ms",
            f"{m['model_size_mb']:,.1f} MB",
            m["hardware"],
        ]
        rows.append("| " + " | ".join(cells) + " |")
    return (
        f"Field extraction on the golden test set ({metrics['dataset']}, receipts from shops never trained on). "
        "Exact match per field; an invalid reply misses every field.\n\n" + "\n".join(rows) + "\n"
    )


def build_reader(name: str, train: list[Example], model: str | None, adapter: Path | None) -> Reader:
    if name == "baseline":
        from extraction.baseline import Baseline

        return Baseline(train)
    from extraction.llm import BASE_MODEL, LlmReader

    if name == "finetuned" and adapter is None:
        raise SystemExit("The finetuned variant needs --adapter, the folder the notebook produced")
    if name == "finetuned":
        return LlmReader(model or BASE_MODEL, adapter)
    # The first training receipt as the worked example: from the train split, so nothing leaks from the test
    return LlmReader(model or BASE_MODEL, shown=train[0])


def check(results: dict[str, dict], committed: dict) -> list[str]:
    """Differences between fresh scores and the committed ones; latency is left out, it depends on the machine."""
    problems = []
    for name, fresh in results.items():
        old = committed.get("variants", {}).get(name)
        if old is None:
            problems.append(f"{name}: no committed results")
            continue
        pairs = [(f"field_accuracy.{f}", fresh["field_accuracy"][f], old["field_accuracy"][f]) for f in FIELDS]
        pairs += [(key, fresh[key], old[key]) for key in ("all_fields_correct", "invalid_json_rate")]
        for key, measured, committed in pairs:
            if abs(measured - committed) > CHECK_TOLERANCE:
                problems.append(f"{name}.{key}: committed {committed}, measured {measured}")
    return problems


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--variants", nargs="+", choices=VARIANTS, default=["baseline"])
    parser.add_argument("--model", help="the Hugging Face model id of the LLM variants")
    parser.add_argument("--adapter", type=Path, help="the LoRA adapter folder of the finetuned variant")
    parser.add_argument("--data", type=Path, default=DATA_ROOT)
    parser.add_argument("--out", type=Path, default=RESULTS)
    parser.add_argument("--check", action="store_true", help="compare with the committed results, write nothing")
    args = parser.parse_args()

    train, test = load("train", args.data), load("test", args.data)
    results = {}
    for name in args.variants:
        results[name] = score(build_reader(name, train, args.model, args.adapter), test)
        print(name, json.dumps({k: results[name][k] for k in ("field_accuracy", "invalid_json_rate")}))

    metrics_file = args.out / "metrics.json"
    existing = json.loads(metrics_file.read_text(encoding="utf-8")) if metrics_file.exists() else None
    if args.check:
        problems = check(results, existing or {})
        print("\n".join(problems) or "The committed results are up to date.")
        sys.exit(1 if problems else 0)

    metrics = merge(existing, results)
    args.out.mkdir(parents=True, exist_ok=True)
    metrics_file.write_text(json.dumps(metrics, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    (args.out / "comparison.md").write_text(table(metrics), encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
