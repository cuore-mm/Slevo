## ADDED Requirements
### Requirement: Shared bookmark actions delegation
BoardViewModel and ThreadViewModel SHALL expose bookmark actions through a shared interface implemented via Kotlin interface delegation to a common bookmark actions delegate.

#### Scenario: Bookmark actions invoked from shared UI
- **WHEN** the bookmark UI requests save, unbookmark, or group edit actions
- **THEN** the calls are routed through the shared interface without concrete ViewModel casts
