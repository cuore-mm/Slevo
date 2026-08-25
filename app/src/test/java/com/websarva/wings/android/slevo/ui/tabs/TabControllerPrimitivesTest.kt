package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.ui.bbsroute.TabSelectionResolution
import com.websarva.wings.android.slevo.ui.tabs.controller.IndexedTabOperation
import com.websarva.wings.android.slevo.ui.tabs.controller.foldEffectiveTabs
import com.websarva.wings.android.slevo.ui.tabs.controller.resolveTabPresentation
import com.websarva.wings.android.slevo.ui.tabs.controller.reorderTabs
import com.websarva.wings.android.slevo.ui.tabs.controller.selectionAfterTabRemoval
import com.websarva.wings.android.slevo.ui.tabs.controller.selectionAfterTabRemovals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Board／Thread が共有する pure projection と selection primitive を検証する。
 * Room、Coroutine、presentation の副作用を持たず、pending 順序と state invariant を固定する。
 */
class TabControllerPrimitivesTest {
    /** stable keyを中間位置へ移動し、要求外の新規keyを末尾へ残すことを確認する。 */
    @Test
    fun reorderPrimitives_moveAndMergeKeysWithoutDuplicates() {
        assertEquals(listOf("b", "c", "a"), moveKeyBeforeTarget(listOf("a", "b", "c"), "a", "c"))
        assertEquals(
            listOf("c", "a", "b", "new"),
            reorderTabs(
                tabs = listOf("a", "b", "c", "new"),
                requestedKeys = listOf("c", "a", "missing", "c"),
                keyOf = { it },
            ),
        )
    }

    /** 最新表示モデルへdraft順を適用し、消失keyを除外することを確認する。 */
    @Test
    fun applyReorderDraft_usesLatestItems() {
        val draft = ReorderDraft(originalOrder = listOf("a", "b"), currentOrder = listOf("b", "a"))
        assertEquals(
            listOf("b:new", "a:new", "c:new"),
            applyReorderDraft(listOf("a:new", "b:new", "c:new"), draft) { it.substringBefore(':') },
        )
    }

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

    /** 複数keyの除去が一つのprojection operationで固定タブを含む一覧を保つことを確認する。 */
    @Test
    fun foldEffectiveTabs_removesMultipleKeysInOneOperation() {
        val result = foldEffectiveTabs(
            canonicalTabs = listOf("a", "fixed", "b", "c"),
            operations = listOf(
                IndexedTabOperation(
                    key = "a",
                    remove = true,
                    removeKeys = setOf("a", "b", "c"),
                ) { it },
            ),
            keyOf = { it },
        )

        assertEquals(listOf("fixed"), result)
    }

    /** 複数削除でも単体closeを一覧順にfoldした最終選択と一致することを確認する。 */
    @Test
    fun selectionAfterTabRemovals_matchesSequentialSelectionForAllSubsets() {
        val tabs = listOf("a", "b", "c", "d", "e")
        val selectedKeys = listOf<String?>(null, "a", "c", "e")

        selectedKeys.forEach { selectedKey ->
            (0 until (1 shl tabs.size)).forEach { mask ->
                val removedKeys = tabs.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
                var remainingTabs = tabs
                var expected = selectedKey
                removedKeys.forEach { removedKey ->
                    val removedIndex = remainingTabs.indexOf(removedKey)
                    remainingTabs = remainingTabs.filterNot { it == removedKey }
                    expected = selectionAfterTabRemoval(
                        selectedKey = expected,
                        removedKey = removedKey,
                        removedIndex = removedIndex,
                        remainingTabs = remainingTabs,
                        keyOf = { it },
                    )
                }

                assertEquals(
                    expected,
                    selectionAfterTabRemovals(
                        selectedKey = selectedKey,
                        tabs = tabs,
                        removedKeys = removedKeys,
                        keyOf = { it },
                    ),
                )
            }
        }
    }
}
