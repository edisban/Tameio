# Model card: receipt field extraction

What reads the issuer's ΑΦΜ, the date, the amounts and the category of a Greek receipt from its OCR
text, and whether a small LLM does it better than the rules the service already uses.

| | |
| --- | --- |
| Task | OCR text of a receipt in, six fields out: `issuer_afm`, `issue_date`, `net_amount`, `vat_amount`, `total_amount`, `category` |
| In production | The **baseline** (rules + TF-IDF). The LLM variants are measured, not deployed. |
| Code | `ml/document-ai/extraction/`, dataset in `ml/document-ai/training/dataset.py` |
| Results | [`ml/document-ai/results/field-extraction/comparison.md`](../../ml/document-ai/results/field-extraction/comparison.md) and `metrics.json` |
| Errors | [`ml/document-ai/results/field-extraction/errors.md`](../../ml/document-ai/results/field-extraction/errors.md), from `python -m extraction.error_analysis` |

## Data

There is no public set of labelled Greek receipts, so the data is synthetic: the existing generator
(`training/receipts.py`) writes receipts from known values, so the right answer is known for every one.
It varies the layouts (keywords, date formats, Greek and English number formats, lower case) and adds
OCR errors (about 2% of characters swapped for look-alikes, such as `0`/`Ο`).

| Split | Receipts | Shops | Used for |
| --- | --- | --- | --- |
| train | 433 | 4–6 per category | fitting the TF-IDF classifier and the LoRA adapter |
| validation | 120 | 1 per category | the fine-tune's loss after each epoch |
| test (golden) | 247 | 2 per category | every number below |

- **Split by shop.** Every shop is in exactly one split, so every test receipt comes from a shop no
  model saw in training. A model that memorised "ΚΤΕΛ means transport" gets no credit on the test set.
- **Versioned and committed.** `data/field-extraction/v1/*.jsonl` with a manifest (seed, counts, shops,
  SHA-256 of every file). A test rebuilds the dataset and fails if it no longer matches byte for byte.
- **Golden set.** Each test line carries the six expected fields; a field counts only on an exact match
  (amounts to the cent, dates as ISO).

## Method

1. **Baseline**: the service's own code. Regular expressions find the ΑΦΜ (with its check digit), the
   date and the amounts; a TF-IDF character n-gram model with logistic regression picks the category,
   trained here on the train split only.
2. **Prompted LLM**: `Qwen/Qwen2.5-0.5B-Instruct` with a system prompt describing the JSON, and one
   worked example from the train split (one-shot). Greedy decoding. The reply is validated with a
   Pydantic schema (9-digit ΑΦΜ, ISO date, two-decimal amounts, known category, no extra keys); a reply
   that does not fit counts as **invalid JSON** and misses every field.
3. **Fine-tuned LLM**: the same model with a LoRA adapter (rank 16 on every linear layer, 2 epochs,
   learning rate 2e-4) trained by supervised fine-tuning (TRL `SFTTrainer`) on the 433 training receipts,
   with the loss on the JSON answer only. Trained on a free Colab T4 with
   [`notebooks/field_extraction_lora.ipynb`](../../ml/document-ai/notebooks/field_extraction_lora.ipynb).

The evaluation (`python -m extraction.evaluate`) measures exact match per field, the share of receipts
with all six right, the invalid-JSON rate, latency per document (p50, one document at a time) and the
size of the weights.

## Results

| Variant | ΑΦΜ | Date | Net | VAT | Total | Category | All 6 | Invalid JSON | Latency p50 | Size | Hardware |
|---|---|---|---|---|---|---|---|---|---|---|---|
| baseline | 95.1% | 92.7% | 96.8% | 97.6% | 97.2% | 85.4% | 69.2% | 0.0% | 2 ms | 1.2 MB | CPU, 16 threads |
| prompted | 67.2% | 43.3% | 0.0% | 47.0% | 23.9% | 7.7% | 0.0% | 32.8% | 12,414 ms | 1,976.1 MB | CPU, 8 threads |

All on the 247 golden receipts. **All 6** is the share of receipts with every field right, the number that
decides how often a person has nothing to correct.

- **Prompted, 0.5B**: one reply in three does not fit the schema (this run did not break the reasons
  down; without the worked example, early trials wrote sums such as `"11.12 + 113.26 = 124.38"` in place
  of an amount). Among the valid replies the ΑΦΜ and the date are
  mostly right, because they are copied; the net amount, which often has to be worked out from the total
  and the VAT, never is, and the category is right for fewer than one receipt in ten, below a guess among ten
  categories (in the replies inspected by hand it was almost always `UTILITIES`). Twelve seconds a receipt on a CPU.
- **Fine-tuned**: its row is added by the Colab notebook, which measures it on the same golden set and
  writes it into `metrics.json`. It needs a GPU: on the laptop CPU one training step took 135 s, so the
  two epochs would take about four hours, against about ten minutes on a T4.

## What the mistakes show

`python -m extraction.error_analysis` reads the baseline's answer for each golden receipt and breaks the
mistakes down with pandas ([errors.md](../../ml/document-ai/results/field-extraction/errors.md)).

- **The ΑΦΜ and the date fail safe.** All 12 ΑΦΜ and all 18 date mistakes are fields found empty, never a wrong
  value. In every one the OCR read a digit as a letter that looks like it: `2Ο26` with a Greek Ο, `30-0S-2Ο25`,
  `962692S45`. The rules look for digits, find none and leave the field for the person on the review screen.
- **The category is where wrong values come from.** Its 36 mistakes are all wrong values, and 32 of them are on
  receipts that print only a generic line such as «ΔΙΑΦΟΡΑ ΕΙΔΗ», where the shop's name is the only clue: the
  category is wrong on 47% of those against 2% of itemised receipts. The usual confusions are professional
  services taken for office supplies and groceries and restaurants for each other.
- **What to fix first:** turn letters that look like digits back into digits (Ο→0, S→5, Ι→1, Β→8) inside tokens
  that are mostly digits, before the ΑΦΜ and date rules run. Tried on the golden set, it finds all 30 missing
  values and changes none that was right; the ΑΦΜ check digit still refuses a number read wrong. After that,
  the classifier needs more shops per category, or the review screen should ask for the category when the
  receipt has no item lines.

## Limits

- **Synthetic data.** The generator uses the layouts the rules were written for, which flatters the
  baseline. Real receipts (thermal paper, crumpled photos, unusual layouts) will score lower for all
  three; the corrections people make on the review screen are the real test set to build next.
- **One small model.** 0.5B parameters is what fits a laptop CPU and a free GPU. Larger models would
  do better at the prompted task and cost more.
- **Latency depends on the machine.** The baseline and the prompted model were measured on a laptop
  CPU; the fine-tune on the GPU it was trained on. Compare accuracies directly; compare latencies only
  within the same hardware.
- **No confidence.** The LLM gives one answer per field with no confidence, while the baseline's fields
  carry one that the review screen uses to point a person at doubtful values.

## Conclusion

**Today the baseline is enough, and the LLM is not connected to the app.** On these receipts the rules
win every field, answer in 2 ms instead of 12 s, weigh 1.2 MB instead of 2 GB, and give each field a
confidence the review screen uses. A prompted 0.5B model is not usable for this: it cannot do the
arithmetic, does not know the categories, and breaks the format a third of the time.

Fine-tuning is the fair test of the LLM: it teaches the format and the layouts from the 433 training
receipts, so the invalid replies should mostly disappear. The bar it has to clear is the baseline's
**All 6** on receipts from unseen shops, by a clear margin, at a latency the reading queue can absorb.
Only then would it go into the service, behind a configuration switch with the rules as the fallback.

When an LLM is worth it: when receipts stop looking like the ones the rules were written for (invoices
with several VAT rates and many lines, foreign or handwritten receipts, new layouts every week), writing
and maintaining rules costs more than running a model, and the real corrections from the review screen
give it data to learn from. Until then, a regular expression is cheaper, faster and easier to explain.
