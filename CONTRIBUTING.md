# Contributing to Money Lens

## Prerequisites

This project uses Nix for reproducible development environments. Install Nix (with
flakes enabled), then:

```bash
nix develop
```

This drops you into a shell with all required tools (Bazel, JDK, Kotlin, etc.).

## Building

Build the Moneydance extension:

```bash
bazel build //moneydance:moneylens_mxt
```

The output is at `bazel-bin/moneydance/moneylens.mxt`.

## Code Formatting

**Always check formatting before committing:**

```bash
nix fmt
```

This formats all supported files (Nix, Kotlin, etc.) in place.

### Kotlin Style

- The project uses [ktlint](https://ktlint.github.io/) via the Nix formatter
- Prefer explicit types for public API, inferred types are fine for local variables

### JSON Schema Style

- Use `snake_case` for field names
- Amounts are integer cents (matching Moneydance internals)
- Schemas live in `schema/` and are self-contained (use `$defs`, not `$ref` to external
  files)

## Project Structure

```
├── build/               # Bazel extras
├── docs/                # Documentation
├── moneydance/          # Moneydance extension (Kotlin)
├── nix/                 # Nix extras
└── schema/              # JSON schema definitions
```
