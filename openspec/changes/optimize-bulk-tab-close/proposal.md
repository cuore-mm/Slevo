## Why

`add-bulk-delete-tabs` の一括クローズは既存の単体クローズを対象件数分反復するため、大量タブではDB transaction、Room canonical確認、projection更新、ThreadState GCが繰り返され、完了と表示更新が著しく遅くなる。Issue #497のUIと対象境界を維持したまま、専用bulk mutationとして一度に投影・永続化する。

## What Changes

- `add-bulk-delete-tabs` 完了実装を前提に、性能と原子性だけを扱う独立changeとして実施する。
- Board/ThreadのCoordinatorへ専用bulk intent/operationを追加し、対象集合をpending projectionから1操作で除外する。
- 表示中ページの未固定タブIDだけを受理時に確定し、固定タブと反対ページを変更しない。
- SQLiteのbind変数上限を避けるため対象IDを最大900件のchunkへ分割し、全chunkをbulk操作単位の1つのRoom transaction内で削除する。
- ThreadStateの遅延GCをbulk操作につき1回だけ、同じtransaction内で実行する。
- 対象のBoard/Thread session holderをbulk処理の一段階でmapから除去し、各holderを正確に1回破棄する。
- bulk後の最終選択を、同じ対象を既存の一覧順で単体クローズした結果と一致させる。
- 既存のfull replacement API、Issue #497のボタン・メニュー・文言・確認なしのUIは変更しない。

## Capabilities

### New Capabilities

- `bulk-tab-close-performance`: Issue #497の一括クローズについて、1操作のprojection、chunk化した対象ID削除、bulk単位の原子性、holder破棄、最終選択等価性を定義する。

### Modified Capabilities

- `tab-controller-state-machine`: 対象行単位の専用bulk command、集合pending projection、canonical確認、競合・失敗時収束の要件を追加する。
- `thread-tab-persistence-consistency`: chunk化した集合DELETEを単一write permit・Room transactionで実行し、ThreadState GCを1回に制限する要件を追加する。

## Impact

- 状態機械: `BoardTabsCoordinator.kt`、`ThreadTabsCoordinator.kt`、`ThreadTabsProjection.kt`、`TabProjectionPrimitives.kt`
- セッション: `TabSessionStore.kt` とBoard/Thread session holder map
- 永続化: `OpenBoardTabDao.kt`、`OpenThreadTabDao.kt`、`TabsRepository.kt`
- テスト: primitive、Coordinator、Store、Repository/Room、bind上限、競合・失敗・ライフサイクル・大量件数回帰
- `add-bulk-delete-tabs` のUIファイル、DBスキーマ、外部依存関係に変更はない。
- change依存: `add-bulk-delete-tabs` の実装と仕様が先に存在すること。両changeをarchiveする場合は依存順を維持する。
