# Safe sample files

`customer-template.pdf` is an AcroForm template with the text fields
`fullName` and `customerNumber`. For every valid sample, use this mapping:

| CSV column | PDF field |
| --- | --- |
| `name` | `fullName` |
| `customerId` | `customerNumber` |

Use `{customerId}-{name}.pdf` as the output filename pattern.

## Valid samples

| File | Delimiter | Rows | Purpose |
| --- | --- | ---: | --- |
| `customers.csv` | Comma | 3 | Small default example |
| `customers-semicolon.csv` | Semicolon | 5 | Semicolon detection |
| `customers-tab.tsv` | Tab | 5 | Tab detection |
| `customers-special-characters.csv` | Comma | 6 | UTF-8, punctuation, and a quoted comma |
| `customers-duplicate-output.csv` | Comma | 3 | Duplicate output filename handling |
| `customers-25-rows.csv` | Comma | 25 | Medium-size regression sample |
| `customers-26-rows.csv` | Comma | 26 | Confirms the former edition limit is gone |

For `customers-duplicate-output.csv`, the ZIP should contain three PDFs with
unique names even though all filename values are identical.

## Intentionally invalid samples

| File | Expected validation code | Purpose |
| --- | --- | --- |
| `invalid-duplicate-header.csv` | `DUPLICATE_CSV_HEADER` | Duplicate column name |
| `invalid-empty-header.csv` | `EMPTY_CSV_HEADER` | Missing column name |
| `invalid-inconsistent-row.csv` | `INCONSISTENT_CSV_ROW` | Data row has too many values |

The invalid samples are expected to be rejected during upload inspection.

All names are historical public figures or explicit test placeholders. The
files contain no customer or production data. Regenerate the PDF with
`./scripts/generate-sample.sh`.
