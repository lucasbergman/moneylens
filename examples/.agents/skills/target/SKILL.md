---
name: target
description: Enter Target debit card purchases into Moneydance from scraped receipt JSON
disable-model-invocation: true
---

# Target Receipt Entry

Enter Target debit card purchases into Moneydance from scraped receipt JSON.

## Prerequisites

You need a scraped receipt file. Check whether a recent one already exists by globbing
for `data/target-orders/target-receipts-*.json`. If a fresh scrape is needed:

1. Confirm with the user that Chrome is running with `--remote-debugging-port=9222` and
   that they are logged into target.com. For example:
   `google-chrome-stable --remote-debugging-port=9222 --remote-debugging-host=127.0.0.1 --user-data-dir=/tmp/chrome-target-profile`
2. Ask the user for a `--since` date if they haven't provided one
3. Run the receipt scraper (e.g., using a scraping helper script):

```bash
python3 ./scripts/scrape_target.py --since YYYY-MM-DD
```

The scraper writes to `data/target-orders/target-receipts-YYYYMMDD.json` and logs
progress to stderr. If it produces empty orders or invoices, re-run with `--debug` and
inspect `data/debug/*.html` to diagnose selector issues.

## JSON Structure

Each order contains one or more invoices. **Each invoice = one bank transaction.** A
single Target order may produce multiple invoices (e.g. in-store pickup vs. shipped
items billed separately). `total_cents` is the **actual bank charge** (invoice total
minus any coupons) and should match the debit to Primary Checking exactly. The
convention is that all invoices from the same order share the order's date.

```json
{
    "orders": [
        {
            "order_id": "9120000000000",
            "order_date": "2026-03-15",
            "invoices": [
                {
                    "invoice_id": "123456",
                    "invoice_url": "https://www.target.com/orders/9120000000000/invoices/123456",
                    "total_cents": 5167,
                    "coupon_cents": 100,
                    "items": [
                        {
                            "name": "Paper Towels 12-pack",
                            "quantity": 1,
                            "price_cents": 1299
                        }
                    ]
                }
            ]
        }
    ]
}
```

## Workflow

1. **Find the receipt JSON** — glob for `data/target-orders/target-receipts-*.json` and
   use the most recent file
2. **Look up existing Target transactions** in Primary Checking for two purposes:
    - Learn the sign convention and description format from a real example
    - Identify which invoices have already been entered (deduplication)
3. **Determine what's new** — for each invoice in the JSON, check whether a transaction
   already exists in Primary Checking with a matching amount and a date within ±3 days.
   Skip invoices that already have a match.
4. **Ask the user how far back to process** — if the backlog is large, confirm before
   proposing many transactions at once
5. **For each new invoice**, build a split transaction:
    - Ask the user to categorize line items (or groups of similar items)
    - Propose the full split structure for confirmation
6. **Create transactions** after user approval, one invoice at a time
7. **Build up the category map** — as the user assigns categories, note the patterns so
   you can suggest them for similar items in the same session

## Account

> **Note**: Replace this example UUID with the actual UUID from your Moneydance ledger
> (query via `list_accounts`).

- **Primary Checking**: `10000000-0000-0000-0000-000000000001`

Target debit card purchases debit Primary Checking directly.

## Sign Convention

For a debit purchase, both the transaction amount and all expense splits are
**NEGATIVE**:

- **Transaction amount**: negative (e.g., `-5267` for a $52.67 charge)
- **Expense category splits**: negative
- Sum of all splits = transaction amount

Example for a $52.67 purchase split across two categories:

- Transaction amount: `-5267`
- Grocery split: `-3200`
- Household split: `-2067`
- Sum of splits: `-5267` ✓

## Categorizing Items

This is the core judgment work. Infer categories from product names. When in doubt, ask
the user.

> **Note**: Replace these example UUIDs with actual category UUIDs from your Moneydance
> ledger (found via `list_categories`).

| Item type                                          | Category               | UUID                                   |
| -------------------------------------------------- | ---------------------- | -------------------------------------- |
| Clothing, apparel, shoes, socks, undergarments     | Personal:Clothing      | `30000000-0000-0000-0000-000000000001` |
| Cosmetics, hair care, dry shampoo, beauty products | Personal:Personal Care | `30000000-0000-0000-0000-000000000002` |
| Food, produce, pantry staples, beverages           | Personal:Groceries     | `30000000-0000-0000-0000-000000000003` |
| Cleaning products, paper goods, household supplies | Personal:Household     | `30000000-0000-0000-0000-000000000004` |
| Prescription & OTC medications, allergy meds       | Medical                | `30000000-0000-0000-0000-000000000005` |
| Fitness equipment (dumbbells, weights)             | Personal:Personal Care | `30000000-0000-0000-0000-000000000002` |
| Children's toys, books, games; gift cards          | Personal:Gifts         | `30000000-0000-0000-0000-000000000006` |
| Seasonal/holiday supplies, craft kits              | Personal               | `30000000-0000-0000-0000-000000000007` |
| Electronics, home goods                            | Ask user               |                                        |
| RedCard savings / promotions / coupons             | Ask user               |                                        |

When items span multiple categories, round to the nearest cent and ensure splits sum
exactly to the negative of the transaction amount. Adjust the largest split by a penny
if needed to make it balance.

## Deduplication

Match an existing transaction if:

- Description contains "TARGET" (case-insensitive)
- Amount is within $0.01 of the invoice total
- Date is within 3 calendar days (Target debit posts same day or next day)

If a close but not exact match exists, flag it to the user rather than skipping
silently.

## Transaction Structure

One transaction per invoice. Use the order date (not any date on the invoice page).
**Memo format**: `order NNNN, invoice NNNN` — always include both IDs separated by a
comma.

```json
{
    "date": "2026-03-15",
    "description": "Target",
    "account_id": "10000000-0000-0000-0000-000000000001",
    "amount": -5267,
    "memo": "order 9120000000000, invoice 123456",
    "splits": [
        { "category_id": "30000000-0000-0000-0000-000000000003", "amount": -3200 },
        { "category_id": "30000000-0000-0000-0000-000000000004", "amount": -2067 }
    ]
}
```

## Notes

- **Amounts are in cents** (e.g., -$52.67 = -5267 for debit charges)
- **Date**: use the order date from the JSON, not the bank posting date
- **Multi-item categories**: group similar items into one split rather than one split
  per line item (e.g., all groceries into one Grocery split)
- **Coupons**: if `coupon_cents > 0`, add a positive split of `+coupon_cents` to
  `Personal:Miscellaneous` — this offsets the category splits so the net equals the
  actual charge. Example: $106.83 invoice with $1.00 coupon → `total_cents` = 10583,
  transaction amount = -10583, category splits sum to -10683, Miscellaneous split = +100
- **Split memos**: leave blank — putting item names in split memos makes transactions
  too busy
- **Mismatched invoice totals**: if an invoice's `total_cents` is significantly larger
  than the sum of its `price_cents` items, flag it to the user and skip it rather than
  entering a transaction with unexplained amounts
- **Returns / credits**: if an order total is negative (rare), the transaction amount
  will be positive and splits will be positive
- **Partial matches**: Target sometimes charges in two separate transactions (e.g., if
  items ship separately). If the JSON total doesn't match any single bank charge, flag
  it and ask the user
- **Scraper accuracy**: item names from the scraper may be truncated or slightly
  garbled. Use your best judgment on categorization.
