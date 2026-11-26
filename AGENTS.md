# AGENTS.md

Guidance for AI coding agents working on Money Lens.

## Overview

Money Lens is a [Moneydance](https://moneydance.com/) extension written in Kotlin that
embeds a Streamable HTTP MCP server (Jetty + official MCP Java SDK) on
`http://127.0.0.1:51234/mcp`.

- `moneydance/src/main/kotlin/Main.kt` — Moneydance `FeatureModule` entry point.
- `moneydance/src/main/kotlin/McpServer.kt` — Embedded Jetty + MCP server wiring and
  Jackson setup.
- `moneydance/src/main/kotlin/AccountRepository.kt` — Domain models, enums, date
  helpers, and the `AccountRepository` interface (keep Moneydance SDK types out of this
  interface so tools stay decoupled and testable).
- `moneydance/src/main/kotlin/MoneydanceAccountRepository.kt` — Moneydance SDK
  implementation.
- `moneydance/src/main/kotlin/tools/` — One file per MCP tool (`*Tool.kt`).
- `schema/` — Self-contained JSON Schemas (Draft 2020-12) for tool requests and
  responses.

## Build, Test, and Format

```bash
# Build the Moneydance extension bundle (.mxt)
bazel build //moneydance:moneylens_mxt              # non-Nix
bazel build --config=nix //moneydance:moneylens_mxt # Nix

# Run tests (once test targets exist)
bazel test //...

# Format all files (Kotlin via ktlint, Nix, etc.)
nix fmt
```

Do not modify `MODULE.bazel.lock`, `.bazelrc`, `.gitignore`, or `meta_info.dict` unless
the task explicitly requires a build or release change.

## Code Conventions

- **As Bjarne Stroustrup says, Do not a general type when a specific type will do:** The
  type system is our friend for keeping code readable and correct. Specific examples
  follow.
- **Typed MCP tool requests:** Deserialize `request.arguments()` into a private
  `@JsonNaming` data class using `objectMapper.convertValue(...)` (see
  `tools/GetTransactionsTool.kt`). Do not manually cast values out of
  `request.arguments()` maps.
- **Dates:** Use `java.time.LocalDate` in domain models, tool requests, and
  `AccountRepository`. Convert to/from Moneydance `YYYYMMDD` integers only at the
  `MoneydanceAccountRepository` boundary using `LocalDate.toMoneydanceDateInt()` and
  `Int.toLocalDate()`.
- **Enums:** Use Kotlin enums with `@JsonProperty` (`AccountKind`, `TransactionStatus`,
  or Moneydance's `InvestTxnType`) instead of raw strings.
- **Amounts & IDs:**
    - Represent monetary amounts and share counts as integer minor units (`Long`) paired
      with `decimal_places`, matching Moneydance internals (see `CONTRIBUTING.md`).
    - Use UUIDs (`account.uuid`, `security.uuid`) for entity identifiers rather than
      legacy numeric IDs or `idString`.
    - Keep Moneydance's reciprocal security price convention (`rate = 1.0 / price`)
      isolated inside `MoneydanceAccountRepository`, and respect
      `security.relativeCurrency ?: currencies.baseType`.
- **Moneydance helpers:** Reuse `Account.descendantsAndSelf()` and `ParentTxn.splits` in
  `MoneydanceAccountRepository.kt` instead of writing new recursive tree walks or
  `(0 until txn.splitCount)` index loops.
- **Privacy:** Avoid exposing sensitive fields (such as raw bank/card `account_number`)
  by default.
- **Style:** Explicit types on public APIs; inferred types are fine for locals. Format
  with ktlint (`nix fmt`).

## Adding a New MCP Tool

1. Add request and response schemas in `schema/<tool-name>-request.schema.json` and
   `schema/<tool-name>-response.schema.json` (`snake_case` fields, self-contained
   `$defs`).
2. Register both schema basenames in `_SCHEMAS` in
   `moneydance/src/main/resources/BUILD.bazel`.
3. Add domain models and repository method(s) to `AccountRepository.kt` and implement
   them in `MoneydanceAccountRepository.kt`.
4. Implement `tools/<ToolName>Tool.kt` using `SchemaLoader` and a typed request data
   class, and add the source file to `moneydance/src/main/kotlin/BUILD.bazel`.
5. Wire the tool in `McpServer.kt` and document it in `README.md`.
