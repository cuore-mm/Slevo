package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.ui.bbsroute.TabSelectionResolution
import com.websarva.wings.android.slevo.ui.tabs.controller.IndexedTabOperation
import com.websarva.wings.android.slevo.ui.tabs.controller.foldEffectiveTabs
import com.websarva.wings.android.slevo.ui.tabs.controller.resolveTabPresentation
import com.websarva.wings.android.slevo.ui.tabs.controller.selectionAfterTabRemoval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Board／Thread が共有する pure projection と selection primitive を検証する。
 * Room、Coroutine、presentation の副作用を持たず、pending 順序と state invariant を固定する。
 */
class TabControllerPrimitivesTest {
    /** ensure、pin、delete を一回の ordered fold で適用し、順序と key 一意性を確認する。 */
    @Test
    fun foldEffectiveTabs_appliesOperationsInAcceptanceOrder() {
        val result = foldEffectiveTabs(
            canonicalTabs = listOf("a:0", "b:1"),
            operations = listOf(
                IndexedTabOperation("a") { "a:1" },
                IndexedTabOperation("b", remove = true) { it },
                IndexedTabOperation("c") { "c:2" },
            ),
            keyOf = { it.substringBefore(':') },
        )

        assertEquals(listOf("a:1", "c:2"), result)
        assertEquals(result.size, result.map { it.substringBefore(':') }.toSet().size)
    }

    /** stale canonical snapshot が届いても pending projection が target を維持することを確認する。 */
    @Test
    fun foldEffectiveTabs_keepsPendingProjectionOverStaleCanonical() {
        val result = foldEffectiveTabs(
            canonicalTabs = listOf("old"),
            operations = listOf(IndexedTabOperation("target") { "target" }),
            keyOf = { it },
        )

        assertEquals(listOf("old", "target"), result)
    }

    /** Loading、PendingMissing、Empty、Selected の presentation 解決を確認する。 */
    @Test
    fun resolveTabPresentation_preservesAtomicSelectionRules() {
        assertTrue(
            resolveTabPresentation(listOf("a"), false, "a", null) { it }.selection is TabSelectionResolution.Loading,
        )
        assertEquals(
            TabSelectionResolution.PendingMissing("missing"),
            resolveTabPresentation(listOf("a"), true, "missing", "missing") { it }.selection,
        )
        assertEquals(
            TabSelectionResolution.Empty,
            resolveTabPresentation(emptyList(), true, null, null) { it }.selection,
        )
        assertEquals(
            TabSelectionResolution.Selected("a"),
            resolveTabPresentation(listOf("a"), true, null, null) { it }.selection,
        )
    }

    /** 選択中 tab close 後の隣接、末尾、空一覧の repair 規則を確認する。 */
    @Test
    fun selectionAfterTabRemoval_repairsAdjacentAndEmptyCases() {
        assertEquals("b", selectionAfterTabRemoval("a", "a", 0, listOf("b", "c")) { it })
        assertEquals("c", selectionAfterTabRemoval("c", "a", 0, listOf("b", "c")) { it })
        assertEquals(null, selectionAfterTabRemoval("a", "a", 0, emptyList<String>()) { it })
    }

    /** 1,252 canonical rowsと100 pending commandが安定順序で完了し、変換回数が command 数だけになることを確認する。 */
    @Test
    fun foldEffectiveTabs_largeSnapshotUsesOneTransformationPerPendingCommand() {
        val canonical = (0 until 1_252).map { "tab-$it" }
        var transformationCount = 0
        val operations: List<IndexedTabOperation<String, String>> = (0 until 100).map { index ->
            IndexedTabOperation<String, String>("tab-$index") { _: String? ->
                transformationCount += 1
                "tab-$index-updated"
            }
        }

        val result = foldEffectiveTabs(canonical, operations) { it.substringBefore("-updated") }

        assertEquals(1_252, result.size)
        assertEquals(100, transformationCount)
        assertEquals(1_252, result.map { it.substringBefore("-updated") }.toSet().size)
    }
}
