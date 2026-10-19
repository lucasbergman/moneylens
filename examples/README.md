# Example Agent Skills

This directory contains example skills and configuration for using AI (such as
[Google Antigravity](https://antigravity.google/) or
[Claude Code](https://claude.ai/claude-code)) with Money Lens and Moneydance.

If you have used Moneydance for years, you are likely used to manual register entry or
QIF/OFX imports. Money Lens adds another option: letting an AI assistant interact
directly with your personal ledger via MCP (Model Context Protocol).

## What is a Skill?

In modern AI agent software, a **skill** is a plain text instruction file (a `SKILL.md`)
that teaches an assistant how to perform a specific, repeatable accounting workflow.

Instead of re-explaining 20 lines of instructions every time you enter a paycheck or
parse an investment CSV, you write down the rules once:

- Which accounts and categories to use
- How to extract line items from PDFs or CSVs and map them to your ledger
- Sign conventions (e.g., deposits and debit charges from checking's perspective)
- Formulas for calculated amounts (like 401(k) matching or mutual fund pricing)

## Included Examples

| Skill                                                          | Description                                                 | Key Moneydance Concept                                                            |
| :------------------------------------------------------------- | :---------------------------------------------------------- | :-------------------------------------------------------------------------------- |
| [`paycheck`](.agents/skills/paycheck/SKILL.md)                 | Enters a paycheck from a paystub PDF.                       | Multi-split transactions (gross salary, taxes, insurance, 401k, HSA, transfers).  |
| [`retirement-sweep`](.agents/skills/retirement-sweep/SKILL.md) | Enters recurring sweep-and-buy orders from a brokerage CSV. | Investment transactions (`BUY_XFER`) with share precision and reciprocal pricing. |
| [`target`](.agents/skills/target/SKILL.md)                     | Splits store debit purchases across expense categories.     | Line-item categorization, transaction deduplication, and coupon offsets.          |

## Quick Start & Onboarding

### 1. Start Moneydance with Money Lens

Ensure the Money Lens extension is installed and running in Moneydance. The extension
starts an embedded MCP server running locally at:

```
http://127.0.0.1:51234/mcp
```

### 2. Connect Your AI Assistant

Your AI assistant needs to be configured to talk to Money Lens.

- **Google Antigravity**: Ensure [`.agents/mcp_config.json`](.agents/mcp_config.json) is
  present in your project root, or merge its contents into your settings.
- **Claude Code**: Add the `moneylens` server to your `.claude/settings.json` under
  `mcpServers`.

See [.agents/mcp_config.json](.agents/mcp_config.json) for the configuration snippet.

### 3. Customize the Skill

The skills in this folder use placeholders for categories, accounts, etc. To use them,
you need to adapt them to your own life and your own ledger.

1. Make a project directory for all the stuff you'll do with AI to your ledger. Mine is
   called `moneyslop`.
1. Copy the skill into your project's skills directory (e.g.,
   `.agents/skills/<skill-name>/SKILL.md`).
1. Fix the skill to work for you. You can normally just have the AI agent do it for you:
    > I got the skill Whatever from another person and need to adapt it to myself. Read
    > my paycheck and ask me questions about it. List my real accounts using Money Lens
    > and update the skill to work for me.

### 4. Run the Workflow

Once customized, you can run the skill by prompting your assistant with your input
files:

> "Enter my paycheck from data/paystub-2026-01-15.pdf using the paycheck skill."

### Safety

AI is unpredictable. No matter what your skill text says, the model might do something
you don't want.

- **Have good backups**: It's critical to be able to roll back from a mess.
- **Money Lens cannot edit existing transactions** by design: Money Lens can make new
  transactions but can't mutate existing ones so that its actions are more obvious. When
  I wrote this software, I didn't want AI to introduce subtle mistakes into my
  20-year-old ledger. Mistakes should be as obvious as possible! If the AI has Money
  Lens create bogus transactions, manually edit or delete those and have it try again.
