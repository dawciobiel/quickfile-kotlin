# Architecture Documentation

This folder contains documentation that describes the architecture and the major design choices of the application (Kotlin + Compose Desktop + Gradle).

## Contents (by topic)

`diagrams/`
: Architecture diagrams (C4, sequences, etc.).

`modules/`
: Module and layer structure (what belongs where, responsibilities).

`plugin-api/`
: Public plugin API contracts (capabilities, envelopes, versioning).

`domain/`
: Domain concepts, core models, and domain rules.

`flows/`
: Key user/system workflows (how features work end-to-end).

`patterns/`
: Reusable patterns used across the codebase.

`adrs/`
: Architecture Decision Records (why decisions were made).

`logging/`, `errors/`, `security/`
: Cross-cutting concerns and conventions (observability, error handling, filesystem safety).

`testing/`
: Test strategy and recommended test coverage boundaries.

## Conventions

- Prefer short, decision-focused documents.
- Use `adrs/` for non-trivial architectural decisions (new modules, changes in state management, new abstractions, major refactors).
- Keep docs in ASCII-compatible text to make them easy to view in any environment.
