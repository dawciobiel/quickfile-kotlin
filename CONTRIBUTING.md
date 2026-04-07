# Contributing to QuickFile

Thank you for your interest in contributing to **QuickFile**.

We welcome bug reports, feature requests, documentation improvements, and code contributions.

---

## Reporting Bugs

Before creating a bug report:

* Ensure you are using the latest version.
* Search existing Issues to avoid duplicates.

When submitting a bug report, please include:

* Operating system and version
* Kotlin version
* Project version or commit SHA
* Steps to reproduce
* Expected behavior
* Actual behavior
* Stack trace (if applicable)

Use the provided bug report template.

---

## Suggesting Enhancements

We appreciate ideas that improve usability, API design, performance, or documentation.

When proposing a new feature:

* Clearly describe the problem it solves
* Provide a proposed API or usage example (if possible)
* Explain why it fits the project scope

Use the provided feature request template.

---

## Pull Requests

### Workflow

1. Fork the repository
2. Create a new branch from `main`
3. Implement your changes
4. Add or update tests if applicable
5. Ensure the project builds successfully with `./gradlew :app-ui:build`
6. Open a Pull Request

### Guidelines

* Keep changes focused and minimal
* Follow existing code style and naming conventions
* Avoid breaking public API without discussion
* Write clear commit messages

Example commit message:

```
Add support for multi-selection menu
Fix NPE in SelectionMenu builder
```

---

## Code Style

* Use JDK 21
* Use the project's configured Kotlin version
* Follow existing formatting
* Prefer clear, readable APIs over clever solutions

---

## Questions

If you are unsure about a design decision, open an Issue first. If GitHub Discussions are enabled for the repository, you can use a Discussion for broader design topics.

Thank you for helping improve QuickFile.
