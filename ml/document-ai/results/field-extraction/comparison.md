Field extraction on the golden test set (field-extraction-v1, receipts from shops never trained on). Exact match per field; an invalid reply misses every field.

| Variant | ΑΦΜ | Date | Net | VAT | Total | Category | All 6 | Invalid JSON | Latency p50 | Size | Hardware |
|---|---|---|---|---|---|---|---|---|---|---|---|
| baseline | 95.1% | 92.7% | 96.8% | 97.6% | 97.2% | 85.4% | 69.2% | 0.0% | 2 ms | 1.2 MB | CPU, 16 threads |
| prompted | 67.2% | 43.3% | 0.0% | 47.0% | 23.9% | 7.7% | 0.0% | 32.8% | 12,414 ms | 1,976.1 MB | CPU, 8 threads |
