# ADR: UI localization and i18n

Status: Accepted

Date: 2026-04-07

## Context

QuickFile is a desktop application with a plugin architecture. The host UI already has a clear architectural direction:
- `:app-ui` owns the Compose Desktop UI (ADR `0007`)
- `:core` and `:plugin-runtime` stay Compose-free
- plugins extend file sources and operations, but do not own the host UI directly (ADR `0002`, ADR `0007`)

We need to decide how **user-visible text** is modeled and resolved:

1. How the host application stores and looks up translated UI strings.
2. Whether plugins can provide their own translated strings for plugin-specific UI surfaces.
3. Who owns locale selection and fallback behavior.
4. Whether localization is a plugin responsibility or a host responsibility.

This is worth deciding early because internationalization tends to spread across the whole codebase. Retrofitting it later would require touching many UI components and extension contracts.

Constraints:
- The application is Kotlin/JVM + Compose Desktop.
- The host UI must remain functional even if no plugins are installed.
- Plugins may eventually contribute settings pages, actions, or other host-controlled UI extension points.
- The localization model should remain testable and should not require a running plugin process for ordinary host UI strings.

## Decision

QuickFile adopts a **host-owned i18n model**.

- The host application owns locale selection, string lookup, fallback logic, and the API used by UI code to obtain translated text.
- Host UI code resolves text by **key**, not by hardcoded user-facing strings.
- Plugins may contribute **translation bundles** for their own plugin-scoped keys, but they do not replace or control the host i18n mechanism.
- Localization is **not** implemented as a dedicated plugin.

## What this means in practice

### 1. Host strings live in host-managed resource bundles

The host stores its own translated strings in application resources, for example:

```text
messages.properties
messages_pl.properties
messages_de.properties
```

UI code requests strings through a host service, for example:

```kotlin
strings["panel.left.title"]
strings["jobs.empty"]
```

The exact API shape (`operator get`, `t(key)`, typed wrapper, etc.) is an implementation detail, but the architectural rule is that **UI code addresses text by key**.

### 2. Plugins may contribute translation bundles, not translation logic

A plugin may include its own translation resources inside its package, for example:

```text
i18n/messages.properties
i18n/messages_pl.properties
```

Those files are loaded by the **host**, not by the plugin process.

The host merges plugin-provided bundles into the global lookup mechanism in a controlled way. The plugin is responsible only for supplying translation data, not for deciding:
- which locale is active,
- how fallback works,
- when strings are looked up,
- how text is rendered in the host UI.

### 3. Plugin keys must be namespaced

To avoid collisions, plugin-owned translation keys must be namespaced using the plugin ID.

Example:

```text
plugin.io.example.sftp.settings.title
plugin.io.example.sftp.auth.username
plugin.io.example.sftp.action.connect
```

Plugins must not override host keys such as:

```text
panel.left.title
jobs.empty
settings.general.language
```

This keeps ownership clear and prevents one plugin from accidentally or intentionally modifying host UI text.

### 4. Locale selection and fallback are host-controlled

The host owns the active locale and fallback rules.

For a selected locale such as `pl-PL`, the lookup order should follow standard resource-bundle fallback:

1. locale-specific variant, e.g. `messages_pl_PL.properties`
2. language-only variant, e.g. `messages_pl.properties`
3. default bundle, e.g. `messages.properties`

If a plugin does not provide a translation for the current locale, the host falls back using the same rules.

### 5. Host UI must not depend on plugin runtime availability for basic text

Host-visible strings required to render the application shell must always be available from host resources.

Plugin-provided translations are optional and limited to plugin-owned UI surfaces or plugin-owned metadata displayed by the host.

This ensures:
- the application can start without plugins,
- ordinary UI rendering does not depend on plugin process lifecycle,
- i18n remains testable without IPC.

## Rejected alternative

### Dedicated translation plugin

We reject the idea of implementing localization as a dedicated plugin that resolves all UI strings for the application.

Reasons:
- it would make basic UI rendering depend on plugin infrastructure,
- it would make startup and fallback behavior more fragile,
- it would complicate testing for ordinary host UI,
- it would move a core host responsibility into a plugin layer where it does not belong,
- such a plugin would effectively become mandatory infrastructure rather than a true optional extension.

## Consequences

- `:app-ui` needs a central string lookup abstraction owned by the host.
- New UI work should prefer translation keys instead of embedding permanent user-facing text directly in composables.
- Plugin packaging may later define a standard optional `i18n/` resource location for plugin translation bundles.
- Future plugin extension contracts should assume plugin-contributed UI text is supplied via namespaced translation keys.
- A future implementation task should decide where the selected locale is stored (settings, OS default, or both), but that does not affect the core architectural decision here.
