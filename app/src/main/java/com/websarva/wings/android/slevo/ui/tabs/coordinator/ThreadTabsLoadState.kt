package com.websarva.wings.android.slevo.ui.tabs.coordinator

import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo

/**
 * Loading state for the persisted thread-tab snapshot.
 * `Loaded(emptyList())` is a valid database state and is distinct from [Loading].
 */
sealed interface ThreadTabsLoadState {
    /** No initial Room snapshot has been received yet. */
    data object Loading : ThreadTabsLoadState

    /** The canonical Room snapshot has been received. */
    data class Loaded(val tabs: List<ThreadTabInfo>) : ThreadTabsLoadState
}
