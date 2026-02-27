## ADDED Requirements

### Requirement: Content area drag SHALL NOT switch adjacent tabs
In BBS route screens that host board and thread content, drag gestures originating from the content area SHALL NOT trigger adjacent tab switching in the pager.

#### Scenario: Vertical response scroll does not switch tab
- **WHEN** the user drags vertically in the thread response list and the drag includes minor horizontal movement
- **THEN** the current tab index remains unchanged

#### Scenario: Vertical board list scroll does not switch tab
- **WHEN** the user drags vertically in the board content list and the drag includes minor horizontal movement
- **THEN** the current tab index remains unchanged

### Requirement: Dedicated tab-switch interaction paths SHALL remain available
Tab switching SHALL remain possible through dedicated non-content interaction paths, including bottom bar swipe and existing explicit tab actions.

#### Scenario: Bottom bar swipe switches to adjacent tab
- **WHEN** the user performs a valid adjacent-tab swipe interaction in the bottom bar area
- **THEN** the pager transitions to the target adjacent tab

#### Scenario: Explicit tab actions switch tabs
- **WHEN** the user triggers an existing explicit tab-switch action such as tab list selection or next/previous tab command
- **THEN** the pager transitions to the requested tab

### Requirement: Content interaction behavior SHALL be preserved
Preventing accidental tab switching SHALL NOT disable existing content interactions, including vertical list scrolling and configured directional gesture actions.

#### Scenario: Thread directional gesture action still executes
- **WHEN** directional gesture input in thread content matches a configured action
- **THEN** the configured action is dispatched as before

#### Scenario: Thread list remains vertically scrollable
- **WHEN** the user performs a vertical drag in thread content
- **THEN** the visible response range updates according to normal list scroll behavior
