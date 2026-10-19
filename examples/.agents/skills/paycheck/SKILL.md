---
name: paycheck
description: Enter an employer paycheck into Moneydance from a paystub PDF
disable-model-invocation: true
---

# Paycheck Entry

Enter an employer paycheck into Moneydance from a paystub PDF.

## Workflow

1. **Find the paystub PDF** in the current directory (glob for `*.pdf` with "Paystub" or
   employer name)
2. **Read the PDF** to extract pay details
3. **Look up a recent fully-entered paycheck** for reference (e.g. description "Acme
   Corp" in Primary Checking, with multiple split items)
4. **Map paystub values** to the split structure below
5. **Ask the user questions** if some values don't have a straightforward match
6. **Present the proposed transaction** to the user for confirmation
7. **Create the transaction** after user approval

## Account

> **Note**: Replace these example UUIDs with actual UUIDs from your Moneydance ledger
> (query them via `list_accounts` / `list_categories`).

- **Primary Checking**: `10000000-0000-0000-0000-000000000001`

The transaction amount is the **Net Pay Distribution to Checking (xxxxxxxxx1234)** from
the paystub, NOT the total net pay (which includes transfers to savings).

## Sign Convention

**IMPORTANT**: The transaction amount must be **NEGATIVE** (it's a deposit/credit to
checking).

For splits:

- **Positive amounts** = money coming IN (income, reimbursements)
- **Negative amounts** = money going OUT (taxes, deductions, transfers)

The sum of all split amounts should equal the **negative** of the transaction amount.

## Split Structure

Core splits (present on most paychecks):

| Category                    | Category ID                            | Memo                      | Sign |
| --------------------------- | -------------------------------------- | ------------------------- | ---- |
| Salary:Paycheck             | `20000000-0000-0000-0000-000000000001` | Acme Corp                 | +    |
| Tax:Federal Income          | `20000000-0000-0000-0000-000000000002` |                           | -    |
| Tax:State Income            | `20000000-0000-0000-0000-000000000003` |                           | -    |
| Tax:Medicare                | `20000000-0000-0000-0000-000000000004` |                           | -    |
| Tax:Social Security         | `20000000-0000-0000-0000-000000000005` |                           | -    |
| Tax:Local Income            | `20000000-0000-0000-0000-000000000006` |                           | -    |
| Tax:Additional State Income | `20000000-0000-0000-0000-000000000007` |                           | -    |
| Salary                      | `20000000-0000-0000-0000-000000000008` | group term life income    | +    |
| Bills:Insurance             | `20000000-0000-0000-0000-000000000009` | group term life           | -    |
| Bills:Insurance             | `20000000-0000-0000-0000-000000000009` | dental                    | -    |
| Bills:Insurance             | `20000000-0000-0000-0000-000000000009` | medical                   | -    |
| Bills:Insurance             | `20000000-0000-0000-0000-000000000009` | vision                    | -    |
| Bills:Insurance             | `20000000-0000-0000-0000-000000000009` | long-term disability      | -    |
| Bills:Insurance             | `20000000-0000-0000-0000-000000000009` | extra term life insurance | -    |
| Primary Savings             | `10000000-0000-0000-0000-000000000002` | Acme Corp                 | -    |
| Secondary Checking          | `10000000-0000-0000-0000-000000000003` | Acme Corp                 | -    |
| HSA                         | `10000000-0000-0000-0000-000000000004` |                           | -    |
| 401(k)                      | `10000000-0000-0000-0000-000000000005` | 401(k) after-tax          | -    |
| 401(k)                      | `10000000-0000-0000-0000-000000000005` | (pretax, no memo)         | -    |
| Salary                      | `20000000-0000-0000-0000-000000000008` | 401(k) matching           | +    |
| 401(k)                      | `10000000-0000-0000-0000-000000000005` | (matching transfer)       | -    |
| Reimbursable Expenses       | `20000000-0000-0000-0000-000000000010` | parking                   | -    |

Bonus splits (Annual Bonus and ad-hoc bonuses throughout the year):

| Category     | Category ID                            | Memo      | Sign |
| ------------ | -------------------------------------- | --------- | ---- |
| Salary:Bonus | `20000000-0000-0000-0000-000000000011` | Acme Corp | +    |

- No regular deductions (insurance, HSA, parking) on bonus paychecks
- 401(k) deductions use "Bonus 401K AT" and "Bonus 401K Pre" line items (same 401(k)
  account)
- 401(k) matching = 50% of pretax contribution

Variable splits (not on every paycheck):

| Category              | Category ID                            | Memo                   | Sign | When                              |
| --------------------- | -------------------------------------- | ---------------------- | ---- | --------------------------------- |
| Reimbursable Expenses | `20000000-0000-0000-0000-000000000010` | internet reimbursement | +    | Once per month                    |
| Personal:Automobile   | `20000000-0000-0000-0000-000000000012` | parking cash-out       | +    | When parking space is offered up  |
| Salary                | `20000000-0000-0000-0000-000000000008` | HSA employer           | +    | First paycheck of year (see note) |
| HSA                   | `10000000-0000-0000-0000-000000000004` | HSA employer           | -    | First paycheck of year (see note) |

## Paystub Field Mappings

| Split                         | Paystub Field(s)                                           |
| ----------------------------- | ---------------------------------------------------------- |
| Salary:Paycheck               | Regular Pay (sum of regular pay lines, e.g. ~$5,000)       |
| Tax:Federal Income            | Federal Income Tax (Current)                               |
| Tax:State Income              | State Income Tax (Current)                                 |
| Tax:Medicare                  | Employee Medicare (Current)                                |
| Tax:Social Security           | Social Security Employee Tax (Current)                     |
| Tax:Local Income              | Local / County Income Tax (Current)                        |
| Tax:Additional State Income   | Non-resident / Additional State Income Tax (Current)       |
| Salary (GTL)                  | Group Term Life under Earnings (Current)                   |
| Bills:Insurance (GTL)         | Group Term Life under Deductions (Current) - same as above |
| Bills:Insurance (dental)      | Dental (Current)                                           |
| Bills:Insurance (medical)     | Medical (Current)                                          |
| Bills:Insurance (vision)      | Vision (Current)                                           |
| Bills:Insurance (LTD)         | LongTerm Dis (Current)                                     |
| Bills:Insurance (vol life)    | Vol Life EE (Current)                                      |
| Primary Savings               | Net Pay Distribution to Savings (xxxxxxxxx5678)            |
| Secondary Checking            | Net Pay Distribution to Checking (xxxxxxxxx9012)           |
| HSA (employee)                | HSA Employee (Current)                                     |
| 401(k) after-tax              | 401K After-Tax (Current)                                   |
| 401(k) pretax                 | 401K Pretax (Current)                                      |
| 401(k) matching               | 401K Pretax Employer Current (50% of pretax, floor)        |
| Reimbursable Exp (parking)    | Park Before Tax + Park After Tax (Current)                 |
| Reimbursable Exp (internet)   | InternetStipend under Earnings (Current) - monthly         |
| Personal:Automobile (parking) | Parking CashOut under Earnings (Current) - variable        |
| HSA employer (income)         | HSA ER Seed under Earnings (Current) - first check of year |
| HSA employer (transfer)       | HSA ER Update under Deductions (Current) - same amount     |

## Notes

- **Social Security** is deducted early in the year until the annual max is reached,
  then drops to $0 for the rest of the year
- **401(k) matching** is 50% of the pretax contribution, rounded down to the penny.
  Record as: Salary income (memo: "401(k) matching") + transfer to 401(k) account
- **HSA employer seed** appears on the first paycheck of the year as offsetting entries:
  HSA ER Seed (Earnings) and HSA ER Update (Deductions) for the same amount (e.g.,
  $1,000). Record as: Salary income (memo: "HSA employer") + transfer to HSA (memo: "HSA
  employer")
- **Amounts are in cents** when calling the MCP tool (e.g., $2,142.50 = -214250)
- **Verify the math**: Sum of splits should equal the negative of the transaction amount
- **Merge duplicates**: Moneydance can merge hand-entered transactions with
  auto-imported ones, which serves as a nice verification check

## Example Transaction Call

```json
{
    "date": "2026-01-15",
    "description": "Acme Corp",
    "account_id": "10000000-0000-0000-0000-000000000001",
    "amount": -214250,
    "memo": "ACME CORP PAYROLL PPD ID: X123456789",
    "splits": [
        {
            "category_id": "20000000-0000-0000-0000-000000000001",
            "amount": 500000,
            "memo": "Acme Corp"
        },
        { "category_id": "20000000-0000-0000-0000-000000000002", "amount": -60000 },
        { "category_id": "20000000-0000-0000-0000-000000000003", "amount": -20000 },
        { "category_id": "20000000-0000-0000-0000-000000000004", "amount": -7250 },
        { "category_id": "20000000-0000-0000-0000-000000000005", "amount": -31000 },
        { "category_id": "20000000-0000-0000-0000-000000000006", "amount": -5000 },
        { "category_id": "20000000-0000-0000-0000-000000000007", "amount": -10000 },
        {
            "category_id": "20000000-0000-0000-0000-000000000008",
            "amount": 2000,
            "memo": "group term life income"
        },
        {
            "category_id": "20000000-0000-0000-0000-000000000009",
            "amount": -2000,
            "memo": "group term life"
        },
        {
            "category_id": "10000000-0000-0000-0000-000000000002",
            "amount": -50000,
            "memo": "Acme Corp"
        },
        {
            "category_id": "20000000-0000-0000-0000-000000000009",
            "amount": -1500,
            "memo": "dental"
        },
        {
            "category_id": "20000000-0000-0000-0000-000000000009",
            "amount": -12000,
            "memo": "medical"
        },
        {
            "category_id": "20000000-0000-0000-0000-000000000009",
            "amount": -500,
            "memo": "vision"
        },
        {
            "category_id": "20000000-0000-0000-0000-000000000009",
            "amount": -2500,
            "memo": "long-term disability"
        },
        {
            "category_id": "20000000-0000-0000-0000-000000000009",
            "amount": -1000,
            "memo": "extra term life insurance"
        },
        {
            "category_id": "10000000-0000-0000-0000-000000000005",
            "amount": -25000,
            "memo": "401(k) after-tax"
        },
        {
            "category_id": "10000000-0000-0000-0000-000000000003",
            "amount": -20000,
            "memo": "Acme Corp"
        },
        { "category_id": "10000000-0000-0000-0000-000000000004", "amount": -10000 },
        { "category_id": "10000000-0000-0000-0000-000000000005", "amount": -25000 },
        {
            "category_id": "20000000-0000-0000-0000-000000000008",
            "amount": 12500,
            "memo": "401(k) matching"
        },
        { "category_id": "10000000-0000-0000-0000-000000000005", "amount": -12500 },
        {
            "category_id": "20000000-0000-0000-0000-000000000010",
            "amount": -10000,
            "memo": "parking"
        },
        {
            "category_id": "20000000-0000-0000-0000-000000000012",
            "amount": 5000,
            "memo": "parking cash-out"
        },
        {
            "category_id": "20000000-0000-0000-0000-000000000008",
            "amount": 100000,
            "memo": "HSA employer"
        },
        {
            "category_id": "10000000-0000-0000-0000-000000000004",
            "amount": -100000,
            "memo": "HSA employer"
        }
    ]
}
```
