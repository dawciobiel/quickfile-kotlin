# Changelog

All notable changes to this project will be documented in this file.

The format is inspired by Keep a Changelog and follows the project's actual milestone progression rather than published releases only.

## [Unreleased]

### Added
- `:app-ui`, `:core`, and `:plugin-runtime` host modules
- Compose Desktop application shell with a Decompose-based component tree
- initial `AppComponent`, panel components, jobs component, and settings component
- contribution templates for bug reports, feature requests, and pull requests
- helper scripts for build, run, and desktop uber-jar packaging
- v1 implementation roadmap in `docs/architecture/roadmap/v1-roadmap.md`

### Changed
- accepted ADR `0007` for app UI state management with `Decompose`
- expanded ADR set with decisions for plugin packaging, job lifecycle, typed errors, and plugin API compatibility
- upgraded the protobuf Gradle plugin to `0.9.6`
- enabled Gradle configuration cache

### Fixed
- corrected repository helper scripts so they call valid Gradle tasks
- stopped tracking personal notes under `docs/notes/`

## [0.1.0-SNAPSHOT] - 2026-04-07

### Added
- initial multi-project Gradle setup with `:plugin-api`
- draft gRPC/protobuf contracts for plugin communication
- architecture flows, ADR tracker, and initial ADR set

### Notes
- this is the current development baseline, not a production-ready release
