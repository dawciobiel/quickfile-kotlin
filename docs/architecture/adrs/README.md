# Architecture Decision Records (ADRs)

ADRs capture architectural decisions and the reasoning behind them. The goal is to make future changes safer and faster.

## When to create an ADR

- Introducing a new module/layer or significantly changing module boundaries
- Choosing a state management approach in Compose
- Changing how filesystem operations are modeled or isolated
- Adding/choosing infrastructure patterns (logging, error model, command handling)
- Major refactors that are likely to be repeated later

## Naming

Use a numeric prefix to keep a clear ordering:

`0001-short-title.md`

## Format

See `adr-template.md` for the recommended sections.
