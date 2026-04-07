# ADR: State management for :app-ui

Status: Proposed

Date: 2026-04-04

## Context

ADR `0003-architecture-overview` established **UDF-lite per feature** as the direction for UI/state:
- Immutable UI state per feature (browser panels, transfers/jobs, settings)
- Explicit intents/actions drive state changes
- Side effects handled outside reducers, reporting back via typed results/events

ADR `0006-job-model-and-lifecycle` established `JobRegistry` exposing `StateFlow<JobSnapshot>` — the UI only needs to collect and render it.

Before implementing `:app-ui` we need to decide:

1. **Store/ViewModel structure** — one global store vs. per-feature stores; how they are scoped.
2. **Library choice** — use an existing UDF/MVI library or implement thin wrappers around Kotlin `StateFlow` + coroutines directly.
3. **Navigation** — how multi-panel navigation state is modeled and who owns it.
4. **Intent routing** — how UI events (key press, click) flow down to state mutations and side effects.

Constraints:
- Kotlin/JVM, Compose Desktop (not Android — Android-specific libraries may not apply).
- No hot-reload in v1 (simplifies scoping/lifecycle).
- `:core` and `:plugin-runtime` must not depend on Compose; `:app-ui` is the only Compose module.
- State must be unit-testable outside Compose (plain `StateFlow` assertion is sufficient).
- Multi-panel layout: 2+ independent panels, each with its own location/selection state.

## Options considered

### Option A — Plain `StateFlow` + coroutines (no library)

Each feature has a dedicated **store class** holding `MutableStateFlow<FeatureState>` and a coroutine scope. Intents are suspend functions or `Channel` entries. Side effects (plugin calls) are launched as coroutines within that scope.

```kotlin
class PanelStore(
    private val fileSource: FileSourceGateway,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PanelState.empty())
    val state: StateFlow<PanelState> = _state.asStateFlow()

    fun onIntent(intent: PanelIntent) { /* mutate _state, launch side effects */ }
}
```

**Pros:**
- Zero extra dependencies; pure Kotlin stdlib + coroutines.
- Easy to test: create store, send intents, assert `StateFlow` values.
- No magic; full control over state transitions.
- Works identically on Desktop and in unit tests.

**Cons:**
- Boilerplate grows with feature count (manual wiring of intents, effects, state).
- No built-in time-travel debugging or structured intent logging.
- Team must agree on and enforce conventions (intent naming, effect boundaries) manually.

---

### Option B — Decompose (Arkadiy Ivanov)

Decompose is a Kotlin Multiplatform library providing component lifecycle, navigation stack, and child component trees. Each "screen" is a `ComponentContext`-aware class. Compose integration via `@Composable` extension functions.

```kotlin
class PanelComponent(
    componentContext: ComponentContext,
    private val fileSource: FileSourceGateway,
) : ComponentContext by componentContext {
    private val _state = MutableStateFlow(PanelState.empty())
    val state: StateFlow<PanelState> = _state
    // ...
}
```

**Pros:**
- Structured lifecycle and back-stack navigation built in.
- Well-suited for multi-panel/multi-screen Compose Desktop apps.
- KMP-ready (Desktop + future Android/iOS if needed).
- Active community, good documentation.

**Cons:**
- Learning curve: `ComponentContext`, `InstanceKeeper`, `StateKeeper`, `BackHandler` concepts.
- Navigation model is stack-based — multi-panel (two independent panels side-by-side) needs custom composition of components, not just a stack.
- Adds a meaningful dependency; upgrades may require API migration.

---

### Option C — Circuit (Slack)

Circuit is an MVI framework (Presenter + UI separation) originally for Android/Compose, with KMP support. Presenters are plain classes producing `StateFlow`; UI is a pure `@Composable`.

**Pros:**
- Clean Presenter/UI separation; Presenters are easily testable without Compose.
- Good fit for UDF-lite: explicit `CircuitUiState`, `CircuitUiEvent`.
- Well-maintained by Slack; mature.

**Cons:**
- Android-first heritage; Desktop support exists but less battle-tested.
- Opinionated about navigation (uses its own `Navigator`/`BackStack`); may conflict with multi-panel layout needs.
- Another dependency with its own abstractions to learn.

---

### Option D — Ballast (copper-leaf)

Ballast is a KMP MVI library with explicit `InputHandler` (reducer + side effects), `EventHandler`, and `SideJobScope`. Designed specifically around strict input/output/side-job separation.

**Pros:**
- Very explicit about where side effects live (`SideJobScope`).
- KMP including Desktop.
- Strict separation prevents accidental side effects in reducers.

**Cons:**
- Smaller community than Decompose or Circuit.
- More ceremony per feature (InputHandler + EventHandler + SideJobScope classes).
- Less Compose Desktop usage in the wild — fewer examples to reference.

---

## Open questions (to resolve before accepting this ADR)

This ADR remains **Proposed** until the following questions are answered:

1. **Navigation model** — Is stack-based navigation (back button) needed for the multi-panel layout, or is the layout essentially static (panels are always visible, only content changes)? The answer strongly favours or disfavours Decompose.

2. **Team familiarity** — Is the team (or author) already familiar with Decompose, Circuit, or Ballast? If not, Option A (plain `StateFlow`) minimises onboarding cost and has no risk of library churn.

3. **KMP future plans** — Will the UI ever target Android or iOS? If yes, a KMP-aware library (Decompose, Ballast, Circuit) pays off earlier.

4. **Debugging needs** — Is structured intent logging or time-travel debugging important enough to justify the overhead of a library that provides it?

5. **Compose Desktop maturity** — Have we validated that the chosen library works well with the specific Compose Desktop version used in the project (not just Compose for Android)?

## Preliminary lean

Based on current project context (Desktop-only, small team, no existing framework preference, plain Kotlin/JVM):

> **Option A (plain `StateFlow`)** is the lowest-risk starting point for v1. It can be adopted immediately without learning a new framework, and the store abstraction is thin enough to be replaced later. If navigation complexity grows, Decompose can be introduced incrementally for the navigation layer only.

This is a lean, not a decision. Update status to `Accepted` once the open questions above are resolved.

## Consequences (anticipated, pending decision)

- **Option A adopted:**
  - `:app-ui` owns a set of feature store classes (`PanelStore`, `JobsStore`, `SettingsStore`).
  - Stores are instantiated and wired in a top-level `AppComponent` (plain class, not a library component).
  - Compose UI collects `StateFlow` via `collectAsState()`.
  - Navigation between views is a simple `MutableStateFlow<Screen>` in `AppComponent`.

- **Option B (Decompose) adopted:**
  - `:app-ui` introduces a Decompose dependency; each panel and screen becomes a `ComponentContext`.
  - Navigation handled by Decompose `ChildStack` or custom slot composition for side-by-side panels.
  - Requires a concrete plan for how two independent panels share a parent `ComponentContext`.

- **All options:**
  - `:core` and `:plugin-runtime` remain Compose-free; state is surfaced as `StateFlow` to `:app-ui`.
  - Unit tests for stores/presenters do not require Compose runtime.
