## Why

共通 `BbsRouteScaffold` の選択欠落処理が板/スレッドという画面種別で分岐しており、板の初期読込では先頭ページへの暗黙 fallback、スレッドの pending 中では `currentTabInfo = null` という異なる不整合が起こり得る。選択 key の欠落原因を明示し、確定済み選択と表示ページを一貫させたまま、初期化・一時的不在・確定無効を同じ規則で処理する必要がある。

## What Changes

- `MissingSelectionPolicy { UseFirst, PreserveCurrentPage }` を廃止し、画面種別ではなく `loading / selected / pending-missing / empty` を表す共有の選択解決状態を `BbsRouteScaffold` に渡す API へ置き換える。
- 板/スレッド coordinator が、loaded な canonical tab 一覧と選択操作の原因に基づいて選択を解決する。存在する key は維持し、確定無効 key は既存の同位置隣接・末尾・先頭規則で有効 key へ補正し、0 件では `null` にする。
- pending operation、Deep Link 登録、Flow reconciliation などで key の欠落が一時的と判明している間だけ、選択 key を書き換えず現在の Pager page とその tab content を保持する。pending 中の Pager 同期は選択 callback を発火しない。
- 板の初期読込/復元では、loaded な一覧に対する有効選択を coordinator 側で確定してから表示状態を公開し、タブがあるのに空白 content または暗黙の page 0 表示になる中間状態を作らない。
- 板 Deep Link、選択タブ close、確定無効、0 tab、および既存スレッド pending/cancellation/FIFO/DB-canonical 契約を決定論的テストで固定する。
- 新しい文言、アイコン、テーマ、アクセシビリティ挙動は追加しない。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `tab-selection-source-of-truth`: 板/スレッド共通で、選択 key の有効性と欠落原因に基づく Pager 表示、pending 保持、確定無効の coordinator 補正、および 0 tab の規則を定義する。

## Impact

- 共有 UI/API: `ui/bbsroute/BbsRouteScaffold.kt` の選択状態モデル、表示 tab 導出、Pager 同期。
- 板経路: `ui/board/screen/BoardScaffold.kt`、`ui/tabs/BoardTabsCoordinator.kt`、`ui/tabs/TabSessionStore.kt` の初期化・復元・Deep Link・close 後の選択解決。
- スレッド経路: `ui/thread/screen/ThreadScaffold.kt`、`ui/tabs/ThreadTabsCoordinator.kt`、`ui/tabs/TabSessionStore.kt` の pending/confirmed 状態公開。既存の投影一覧、FIFO mutation queue、DB-canonical confirmation、cancellation 修正は変更しない。
- テスト: `BbsRouteScaffoldSelectionTest.kt`、coordinator/store/Deep Link の unit test、および共有 Pager の Compose test。
- 依存: `refactor-thread-tab-persistence-consistency` と `fix-thread-deep-link-selection-consistency` の契約を前提にし、両 change の artifact や実装をこの change に取り込まない。
- 永続 DB schema、保存形式、navigation route、外部 API の migration はない。共有 Composable の内部 API は同一リポジトリ内 call site を同時更新する。

## 後続統合変更との関係

`refactor-tab-controller-state-machine` は `TabPresentationState`、Loading/Selected/PendingMissing/Empty、隣接／先頭 repair、既存 pager behavior を継承する。presentation の producer/owner は Board／Thread 各 Controller の単一 logical state reducer として明確化される。本 change の UI 要件は supersede せず、統合変更の受入条件として参照する。
