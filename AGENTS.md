
# Repository Guidelines

## Project Structure & Modules
- Module: `app` (Android application)
- Source: `app/src/main/java/com/websarva/wings/android/slevo/...`
- UI: Jetpack Compose under `ui/*` with MVVM (`ViewModel`s) and Hilt DI (`di/*`)
- Data: Room + repositories and data sources under `data/*` (local/remote/models/utils)
- Tests:
    - Unit: `app/src/test/...`
    - Instrumented: `app/src/androidTest/...`
- Resources: `app/src/main/res`
- Manifest: `app/src/main/AndroidManifest.xml`

## Coding Style & Naming
- Language: Kotlin (official style). Use Android Studio formatter.
- Indentation: 4 spaces. Prefer ~100–120 column soft wrapping where helpful.
- Files: one top-level class/composable per file when reasonable.
- Naming:
    - `PascalCase` for classes/composables
    - `camelCase` for methods/variables
    - `SCREAMING_SNAKE_CASE` for constants
- Compose:
    - Prefer small, previewable composables
    - Keep state in `ViewModel`
    - UI parameters should be immutable

## Architecture / Separation of Concerns
- Separate responsibilities into appropriate layers/files such as:
    - `ViewModel`, `UiState`, `Repository`, `DataSource`
- All screen/UI state MUST be modeled as `UiState` and owned/managed by the `ViewModel`.
- Avoid placing business logic in Composables. Keep Composables focused on rendering.

## Testing Guidelines
- Frameworks:
    - Unit: JUnit 4
    - Instrumented: AndroidX Test / Espresso / Compose Test
- Location:
    - Unit tests mirror packages under `app/src/test/...`
    - Instrumented tests under `app/src/androidTest/...`
- Naming: test files end with `Test` (e.g., `DatParserTest.kt`, `BoardRepositoryTest.kt`)
- Focus:
    - Pure logic (parsers in `data/util`, repositories) as unit tests
    - Navigation/UI with Compose rule as instrumented tests
- Prefer headless unit tests for CI speed.

## Security & Config
- API keys:
    - Set `imgbb.api.key` in `local.properties` (do not commit)
    - Access via `BuildConfig.IMGBB_API_KEY`
- Do not hardcode secrets. Keep only sample values/documentation.

# Build & Test Requirements (Mandatory)

- If you modify any code, you MUST ensure both build and unit tests pass before finishing.
- Keep fixing issues until build + tests succeed.

# Comment & Documentation Rules (Mandatory)

Goal: Comments MUST improve readability for people unfamiliar with this codebase.
Write "what it does / how it is structured", not motivation ("why").

## 0) Placement (REQUIRED)
- Doc comments (KDoc/Javadoc) MUST be placed **above all annotations**.
    - Do NOT put comments between annotations and the declaration.

## 1) Type docs (REQUIRED)
- Every **class** and **interface** MUST have a doc comment (KDoc/Javadoc style).
    - Includes: `data class`, `sealed class`, `sealed interface`, `enum class`,
      `object`, `annotation class`.
- Minimum: 1–3 sentences describing:
    - What the type represents / responsibility
    - How it is used (high level)
    - Key constraints/invariants (only if relevant)

## 2) Function docs (REQUIRED for non-trivial, with Preview exception)
- **Do NOT add doc comments to Compose Preview functions.**
    - Functions annotated with `@Preview` (and only used for previews) must remain comment-free.
- Every **non-trivial function** (except Preview functions) MUST have a short doc comment.
- A function is non-trivial if ANY apply:
    - Branching (`if/when`), loops, early returns
    - Parsing / validation / mapping / formatting
    - I/O: DB / network / filesystem / time
    - Orchestrates multiple calls across layers
    - Updates `UiState` or handles complex UI events
    - Has special-case or edge-case handling

### Allowed to omit (Trivial functions)
- Simple one-line delegation with obvious naming
- Simple getters/setters or wrappers
- Functions <= 5 lines with no branching and obvious meaning

## 3) Section headers for long functions (REQUIRED)
- Any function longer than ~30 lines MUST be divided into labeled sections.
    - Examples:
        - `// --- Parsing ---`
        - `// --- Validation ---`
        - `// --- Mapping ---`
        - `// --- Persistence ---`
        - `// --- UI state update ---`

## 4) Non-obvious control flow (REQUIRED)
Add brief comments for:
- Guard clauses / early returns
- Fallback / retry paths
- Special-case branches

## 5) Data transformations & invariants (REQUIRED)
- When mapping across layers (DTO -> Entity -> UiModel), add a brief comment stating:
    - What the output represents
    - Any important invariants (ordering, uniqueness, nullable rules) if they matter

## Prohibited (AVOID)
- Do NOT restate the code line-by-line.
- Avoid trivial comments (e.g., `// increment i`).

## Enforcement
- If required comments are missing, STOP and add them before finishing.
