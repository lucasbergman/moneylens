---
name: retirement-sweep
description:
    Enter biweekly brokerage retirement sweep-and-buy transactions into Moneydance
disable-model-invocation: true
---

# Retirement Sweep Entry

Enter biweekly $1,000 sweep-and-buy transactions into Moneydance from a brokerage CSV
export.

## How It Works

Every two weeks, $1,000 is swept from Primary Savings into the taxable brokerage account
("Retirement Brokerage") and used to purchase INDEX_FUND (Broad Market Index Fund
Admiral Shares). Each cycle produces one BUY_XFER transaction combining the transfer and
purchase.

## Workflow

1. **Find the brokerage CSV** in `data/` (e.g., glob for `*brokerage*retirement*.csv`)
2. **Read the CSV** to extract Buy transactions for INDEX_FUND — each row has trade
   date, shares, share price, and principal amount
3. **Check existing transactions** in Retirement Brokerage to see what's already entered
4. **Identify new transactions** by comparing CSV dates against existing entries
5. **Present the proposed transactions** to the user for confirmation
6. **Create transactions** after user approval

## Accounts

> **Note**: Replace these example UUIDs with actual UUIDs from your Moneydance ledger
> (query them via `list_accounts` / `list_categories`).

- **Primary Savings**: `00000000-0000-0000-0000-000000000001`
- **Retirement Brokerage** (investment): `00000000-0000-0000-0000-000000000002`
- **INDEX_FUND Security** (under Retirement Brokerage):
  `00000000-0000-0000-0000-000000000003`

## CSV Structure

The brokerage CSV has multiple sections. The relevant one starts with headers:

```
Account Number,Trade Date,Settlement Date,Transaction Type,Transaction Description,Investment Name,Symbol,Shares,Share Price,Principal Amount,Commissions and Fees,Net Amount,Accrued Interest,Account Type,
```

Filter for rows where:

- **Transaction Type** = `Buy`
- **Symbol** = `INDEX_FUND`
- **Principal Amount** = `1000.00` (the regular sweep amount)

Ignore "Funds Received" rows (those are the cash side, handled by BUY_XFER
automatically), one-off lump-sum transfers, money market sweeps, dividends, and
reinvestments.

## Transaction Construction

Each transaction is a `BUY_XFER` on the investment account.

### Shares Precision

INDEX_FUND has `decimal_places = 4`, so multiply the CSV share count by 10,000.

Example: 20.0000 shares -> `200000`

### Price

Moneydance stores price as the reciprocal (shares per currency unit). Compute as:

```
price = shares_internal / amount_in_cents
```

Example: 200000 / 100000 = `2.0`

### Amount

The `amount` field is the dollar cost in cents: `100000` for $1,000.

### Date

Use the **Trade Date** from the CSV (the buy date), not the Settlement Date.

### Splits

Two splits, provided as hints for InvestFields to map to the security and transfer
account:

| Split           | Account ID                             | Amount                     |
| --------------- | -------------------------------------- | -------------------------- |
| INDEX_FUND      | `00000000-0000-0000-0000-000000000003` | shares_internal (positive) |
| Primary Savings | `00000000-0000-0000-0000-000000000001` | -100000                    |

## Example Transaction Call

Buy 20.000 shares of INDEX_FUND at $50.00 on 2026-01-15:

```json
{
    "date": "2026-01-15",
    "description": "",
    "account_id": "00000000-0000-0000-0000-000000000002",
    "amount": 100000,
    "invest_action": "BUY_XFER",
    "shares": 200000,
    "price": 2.0,
    "splits": [
        { "category_id": "00000000-0000-0000-0000-000000000003", "amount": 200000 },
        { "category_id": "00000000-0000-0000-0000-000000000001", "amount": -100000 }
    ]
}
```

## Notes

- **Amounts are in cents** when calling the MCP tool (e.g., $1,000.00 = 100000)
- **Typical frequency**: every 2 weeks, possibly a few months to enter at a time
- **Non-standard sweeps** (e.g. larger lump sums) should be flagged to the user rather
  than entered automatically
- **Description** is empty string, matching existing entries
