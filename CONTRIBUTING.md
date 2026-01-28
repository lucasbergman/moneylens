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

### Protobuf Style

- Use `snake_case` for field names
- Use `SCREAMING_SNAKE_CASE` for enum values
- Prefix enum values with the enum name (e.g., `STATUS_CLEARED` in `Status`)

## Project Structure

```
├── build/               # Bazel extras
├── docs/                # Documentation
├── moneydance/          # Moneydance extension (Kotlin)
├── nix/                 # Nix extras
└── proto/               # Protocol buffer definitions
```
