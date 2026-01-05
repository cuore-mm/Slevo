## Context
Bookmark editing flows exist in three ViewModels and duplicate group-edit logic. A shared bookmark sheet ViewModel should unify these operations while keeping toolbar bookmark status accurate.

## Goals / Non-Goals
- Goals:
  - Single source of truth for bookmark sheet state and actions
  - Support single-item (board/thread) and bulk-edit (bookmark list selection) flows
  - Keep bookmark status available for toolbar icons even when the sheet is closed
- Non-Goals:
  - Redesign bookmark UI/UX
  - Change repository APIs or database schema

## Decisions
- Decision: Create `BookmarkBottomSheetViewModel` with a `mode` field (`Single` or `Bulk`) and a single `UiState` that covers sheet/dialog state plus bookmark status.
  - Rationale: Minimizes duplication and keeps UI bindings consistent across screens.
- Decision: Instantiate the ViewModel in UI scope (screen-level) and bind context on demand.
  - Rationale: Fits Compose usage and keeps bookmark status observable while the sheet is closed.
- Decision: Replace `SingleBookmarkViewModel` and its usage in `BoardViewModel`/`ThreadViewModel`.
  - Rationale: Avoid parallel state sources after refactor.

## Alternatives considered
- Keep `SingleBookmarkViewModel` and add an adapter layer for `BookmarkListViewModel`.
  - Rejected: Still keeps two code paths and duplicated group-edit logic.
- Create a repository-level use case layer and keep current ViewModels.
  - Rejected: Does not reduce UI/state duplication or bottom sheet orchestration.

## Risks / Trade-offs
- A single ViewModel with multiple modes can grow complex; mitigate with sealed mode types and clear state sections.
- UI-scoped instantiation requires consistent lifecycle management; ensure it is tied to the screen and not recreated on each recomposition.

## Migration Plan
1. Introduce the shared ViewModel and wire it to repositories.
2. Update board/thread/list scaffolds to use the shared ViewModel state.
3. Remove `SingleBookmarkViewModel` and related wiring once references are gone.

## Open Questions
- Should bulk mode allow mixed board + thread selection, or enforce one type at a time?
- Does the bookmark status shown in toolbars need to update while the sheet is closed (current behavior)?
