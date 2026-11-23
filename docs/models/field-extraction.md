# Model card: receipt field extraction

What reads the issuer's ΑΦΜ, the date, the amounts and the category of a Greek receipt from its OCR
text, and whether a small LLM does it better than the rules the service already uses.

| | |
| --- | --- |
| Task | OCR text of a receipt in, six fields out: `issuer_afm`, `issue_date`, `net_amount`, `vat_amount`, `total_amount`, `category` |
| In production | The **baseline** (rules + TF-IDF). The LLM variants are measured, not deployed. |
| Code | `ml/document-ai/extraction/`, dataset in `ml/document-ai/training/dataset.py` |
| Results | [`ml/document-ai/results/field-extraction/comparison.md`](../../ml/document-ai/results/field-extraction/comparison.md) and `metrics.json` |
| Errors | [baseline](../../ml/document-ai/results/field-extraction/errors.md), [prompted](../../ml/document-ai/results/field-extraction/errors-prompted.md), [fine-tuned](../../ml/document-ai/results/field-extraction/errors-finetuned.md), from `python -m extraction.error_analysis` |

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
| prompted | 66.8% | 43.3% | 0.0% | 46.6% | 24.3% | 7.7% | 0.0% | 33.2% | 1,930 ms | 988.1 MB | GPU Tesla T4 |
| finetuned | 98.8% | 97.2% | 90.7% | 98.0% | 98.0% | 44.5% | 37.2% | 1.2% | 1,952 ms | 1,023.3 MB | GPU Tesla T4 |

All on the 247 golden receipts. **All 6** is the share of receipts with every field right, the number that
decides how often a person has nothing to correct. The two LLM rows were measured on the same Colab T4 GPU,
in half precision, so their sizes are half of the 2 GB the model takes on a CPU.

- **Prompted, 0.5B**: one reply in three does not fit the schema (without the worked example, early trials
  wrote sums such as `"11.12 + 113.26 = 124.38"` in place of an amount). Among the valid replies the ΑΦΜ and
  the date are mostly right, because they are copied; the net amount, which often has to be worked out from
  the total and the VAT, never is, and the category is right for fewer than one receipt in ten, below a guess
  among ten categories. On a laptop CPU it took 12 seconds a receipt, on the GPU 2 seconds; the accuracy was
  the same on both, to within one receipt per field.
- **Fine-tuned, the same 0.5B with LoRA**: two epochs, about ten minutes on the T4 (on the laptop CPU one
  training step took 135 s, so it would have taken about four hours). Invalid replies fell from one in three
  to 3 in 247, and the four fields it copies from the receipt (ΑΦΜ, date, VAT, total) are now right more
  often than with the rules. The net amount and above all the category hold it back: **All 6** is 37.2%,
  against the baseline's 69.2%.

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

The same analysis of the fine-tuned model ([errors-finetuned.md](../../ml/document-ai/results/field-extraction/errors-finetuned.md)):

- **It reads through the OCR errors.** Of the 12 ΑΦΜ the rules left empty because the OCR put a letter in
  place of a digit, it got 11 right, and 15 of the 18 dates. Its only empty fields are the 3 replies that were
  not valid JSON. It is not always safe, though: 4 dates came back as a wrong value, with the day and month
  mixed up (`2026-10-03` for `2026-10-31`), a mistake the rules never make.
- **It cannot do the arithmetic.** Its 20 wrong net amounts are the total minus the VAT worked out with one
  digit wrong: `1001.46` for `1101.46`, `203.85` for `303.85`, `109.05` for `110.05`. A language model writes
  numbers digit by digit; it does not subtract. Each of these breaks net + VAT = total, a check the service
  already runs, so the review screen would flag them.
- **The category does not carry over to new shops.** 134 of its 137 category mistakes are wrong values, not
  empty ones: rent taken for equipment (22 receipts), professional services for equipment (19), transport for
  rent (11). With 4–6 shops per category it learnt the training shops, not what makes a receipt rent or
  transport; the TF-IDF classifier, trained on the same 433 receipts, gets 85.4%.

## Limits

- **Synthetic data.** The generator uses the layouts the rules were written for, which flatters the
  baseline. Real receipts (thermal paper, crumpled photos, unusual layouts) will score lower for all
  three; the corrections people make on the review screen are the real test set to build next.
- **One small model.** 0.5B parameters is what fits a laptop CPU and a free GPU. Larger models would
  do better at the prompted task and cost more.
- **Latency depends on the machine.** The baseline was measured on a laptop CPU, the two LLMs on a T4 GPU.
  Compare accuracies directly; compare latencies only within the same hardware.
- **No confidence.** The LLM gives one answer per field with no confidence, while the baseline's fields
  carry one that the review screen uses to point a person at doubtful values.

## Conclusion

**The baseline stays, and the LLM is not connected to the app.** A prompted 0.5B model is not usable for
this: it cannot do the arithmetic, does not know the categories, and breaks the format a third of the time.

Fine-tuning made a real difference: the format is learnt, and on the fields it copies from the receipt the
fine-tuned model beats the rules, OCR errors included. It does not clear the bar that was set before it ran,
the baseline's **All 6** on receipts from unseen shops: 37.2% against 69.2%, because it guesses the category
of a new shop and gets the subtraction wrong. It also needs a GPU and 2 seconds a receipt, against 2 ms on a
CPU, and gives no confidence per field.

When an LLM is worth it: when receipts stop looking like the ones the rules were written for (invoices
with several VAT rates and many lines, foreign or handwritten receipts, new layouts every week), writing
and maintaining rules costs more than running a model, and the real corrections from the review screen
give it data to learn from. Until then, a regular expression is cheaper, faster and easier to explain.
