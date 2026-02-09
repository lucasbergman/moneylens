# Money Lens

Software tools centered around [Moneydance](https://moneydance.com/), including an MCP
(Model Context Protocol) server that lets AI assistants read and write transactions in
your personal finance ledger.

## What it does

Money Lens runs as a Moneydance extension, exposing your financial data via MCP. This
enables AI assistants like Claude or Gemini to:

- **List accounts and categories** — search by name, type, or UUID
- **Query transactions** — filter by account, date range, and description
- **Create transactions** — including complex split transactions (e.g., paychecks with
  dozens of line items)

The server runs on `http://127.0.0.1:51234/mcp` by default.

## MCP Tools

| Tool                  | Description                                                                                                  |
| --------------------- | ------------------------------------------------------------------------------------------------------------ |
| `list_accounts`       | List bank accounts, credit cards, investments, etc. Supports filtering by `name`, `id`, `type`, and `limit`. |
| `list_categories`     | List income and expense categories. Same filtering options as accounts.                                      |
| `get_transactions`    | Get transactions for an account within a date range, optionally filtered by description.                     |
| `create_transactions` | Create one or more transactions with optional splits for categorization.                                     |

## Requirements

- [Moneydance](https://moneydance.com/), recent (2024+) build
- An MCP-compatible client (e.g., [Claude Code](https://claude.ai/claude-code), Claude
  Desktop, [Gemini CLI](https://geminicli.com/))

## Installation

Currently only building from source is supported.

### From Source

See [CONTRIBUTING.md](CONTRIBUTING.md) for build instructions.

## Usage

### With Claude Code

Add to your MCP settings:

```json
{
    "mcpServers": {
        "moneylens": {
            "type": "http",
            "url": "http://127.0.0.1:51234/mcp"
        }
    }
}
```

Then use natural language:

> "Show me my Bank of My State Checking transactions from the last 30 days"

> "Create a transaction in that account for $42.50 at Organic Market, category
> Groceries"

## Example: Complex Split Transaction

Money Lens handles complex transactions like paychecks with multiple splits:

```
User: Enter my paycheck from the attached paystub PDF

Claude: [Reads PDF, looks up category UUIDs, creates transaction with splits
        for gross pay, taxes, benefits, 401k, HSA, etc.]
```

You can create a custom slash command (e.g., `.claude/commands/paycheck.md`) with your
employer-specific category mappings for a repeatable workflow.

## License

[Apache 2.0](LICENSE)

Copyright 2026 Lucas Bergman

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under
the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the specific language governing
permissions and limitations under the License.

## Acknowledgments

- [Moneydance](https://moneydance.com/) by The Infinite Kind
- [Model Context Protocol](https://modelcontextprotocol.io/) by Anthropic
