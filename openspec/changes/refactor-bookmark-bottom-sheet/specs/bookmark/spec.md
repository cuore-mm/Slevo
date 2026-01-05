## ADDED Requirements
### Requirement: Bookmark sheet ViewModel
The system SHALL provide a dedicated ViewModel that owns bookmark sheet state and actions for bookmark editing.

#### Scenario: Open sheet from a thread
- **WHEN** the user opens the bookmark sheet from a thread screen
- **THEN** the shared ViewModel provides the groups list, selected group, and actions for that thread

### Requirement: Context-driven bookmark editing
The system SHALL support both single-item and bulk-edit contexts within the shared bookmark sheet ViewModel.

#### Scenario: Apply a group to selected items
- **WHEN** the user opens the sheet from the bookmark list with a bulk selection
- **THEN** applying a group updates all selected items and closes the sheet

### Requirement: Bookmark status exposure
The system SHALL expose bookmark status and selected group data for toolbar rendering independently of sheet visibility.

#### Scenario: Toolbar shows bookmarked state while sheet is closed
- **WHEN** a board or thread is bookmarked
- **THEN** the toolbar displays the bookmarked state without requiring the sheet to be open
