Field extraction on the golden test set (field-extraction-v1, receipts from shops never trained on). Exact match per field; an invalid reply misses every field.

| Variant | ΑΦΜ | Date | Net | VAT | Total | Category | All 6 | Invalid JSON | Latency p50 | Size | Hardware |
|---|---|---|---|---|---|---|---|---|---|---|---|
| baseline | 95.1% | 92.7% | 96.8% | 97.6% | 97.2% | 85.4% | 69.2% | 0.0% | 2 ms | 1.2 MB | CPU, 16 threads |
| prompted | 66.8% | 43.3% | 0.0% | 46.6% | 24.3% | 7.7% | 0.0% | 33.2% | 1,930 ms | 988.1 MB | GPU Tesla T4 |
| finetuned | 98.8% | 97.2% | 90.7% | 98.0% | 98.0% | 44.5% | 37.2% | 1.2% | 1,952 ms | 1,023.3 MB | GPU Tesla T4 |
