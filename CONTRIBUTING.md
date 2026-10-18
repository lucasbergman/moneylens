# Contributing to Money Lens

## Prerequisites

This is early development, hackers only. Expect to do some messing around to get things
to work.

### Development Environment Options

#### Option A: Nix (more hassle, maximum reproducibility)

If you are able, you can use the [Nix package manager](https://nixos.org/); that offers
the most reproducible build possible by pinning all compilers (Clang, OpenJDK) and
tooling from the nixpkgs repository. Install Nix (with flakes enabled), then:

```bash
nix develop
```

This drops you into a shell with all required tools (Bazel, JDK, Kotlin, etc.).

#### Option B: Standard Linux or macOS

If you are building without Nix, install:

- [Bazel](https://bazel.build/) (version 9+)
- A C++ toolchain (e.g. GCC or Clang, needed for Bazel's native helper binaries)

I've tested that the build works on Ubuntu 26.04 LTS. I think everything should work on
normie Linux and other Unix as long as Bazel is supported. I'm less certain about macOS
and Windows, but there's a fighting chance. Patches are very welcome.

## Building

You can build the Moneydance extension directly with Bazel:

```bash
bazel build //moneydance:moneylens_mxt  # non-Nix
bazel build --config=nix //moneydance:moneylens_mxt  # Nix
```

The output extension bundle is at `bazel-bin/moneydance/moneylens.mxt`. The first build
takes some time to download packages and toolchains; subsequent builds are fast.

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
