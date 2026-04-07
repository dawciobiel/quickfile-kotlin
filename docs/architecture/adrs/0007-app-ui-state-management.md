# ADR: State management for :app-ui

Status: Accepted

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

## Decision

We adopt **Option B — Decompose** for the structure of `:app-ui`.

Rationale:
- QuickFile is better modeled as a set of **long-lived, cooperating UI components** (left panel, right panel, jobs/transfers area, dialogs, settings) than as a sequence of full-screen views.
- We want explicit ownership and lifecycle for those UI parts; Decompose provides that structure directly.
- We still want a disciplined event flow, but we do **not** want to invent our own UI framework around `StateFlow`.
- Circuit is less natural for the file-manager style multi-panel layout, and Ballast adds more ceremony than the project currently needs.

## How this decision should be interpreted

Decompose is the **structural framework** for `:app-ui`, not a replacement for architecture rules.

This ADR also establishes the following application rules:

1. **UI is organized as a component tree**
   - `:app-ui` has a top-level `AppComponent`.
   - The app is composed from child components such as `LeftPanelComponent`, `RightPanelComponent`, `JobsComponent`, `SettingsComponent`, and dialog-specific components.
   - Each component owns its own UI state and exposes it to Compose for rendering.

2. **State remains explicit and testable**
   - Component state is represented as immutable state objects and exposed via `StateFlow` (or equivalent Decompose-compatible observable state).
   - State logic must remain unit-testable without Compose runtime.

3. **Event flow stays strict**
   - Compose UI emits intents/events only to its owning component.
   - Components call `:core` services/gateways; they do not talk directly to plugin processes.
   - `:core` and `:plugin-runtime` remain the only layers that coordinate plugin communication and job execution.
   - External results are translated into state updates inside the owning component.

4. **Plugins do not own host UI**
   - A plugin must not inject a live Compose component directly into the host UI tree.
   - If plugins extend the UI, they do so through a host-controlled extension contract defined separately.
   - Rendering, lifecycle, and placement in the UI remain under host control.

5. **Navigation model for v1**
   - The main file-manager layout is effectively **static**: the primary panels stay visible while their content changes.
   - Decompose is used mainly for component composition and lifecycle.
   - Stack-style navigation is reserved for secondary flows such as dialogs, settings sections, or future wizard-like screens.

## Consequences

- `:app-ui` will introduce a Decompose dependency.
- The implementation should start with a top-level `AppComponent` that owns the long-lived child components for the main layout.
- Side-by-side panels are modeled as sibling child components under the same parent, not as entries in a back stack.
- Compose UI functions remain thin: they render component state and send user intents upward.
- If a future plugin extension system needs UI contribution points, it must be designed as a host-defined contract, not as arbitrary plugin-owned Compose content.
- `:core` and `:plugin-runtime` remain Compose-free and continue exposing data through plain Kotlin APIs and `StateFlow`.
