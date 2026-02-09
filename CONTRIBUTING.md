# Contributing to Money Lens

## Prerequisites

This is early development, hackers only. Expect to do some messing around to get things
to work.

I use [NixOS](https://nixos.org/) on my computers and Nix for reproducible development
environments. You don't have to use NixOS to use Nix tools. Install Nix (with flakes
enabled), then:

```bash
nix develop
```

This drops you into a shell with all required tools (Bazel, JDK, Kotlin, etc.).

I think everything should work on normie Linux and other Unix as long as
[Bazel](https://bazel.build/) is supported. I'm less certain about macOS and Windows,
but there's a fighting chance. Patches are very welcome.

## Building

Build the Moneydance extension:

```bash
# Add --config=nix on NixOS
bazel build //moneydance:moneylens_mxt
```

The output is at `bazel-bin/moneydance/moneylens.mxt`. The first build takes ages
because it downloads lots of packages, including entire Java, Kotlin, and possibly C++
toolchains. Subsequent builds are very fast.

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
