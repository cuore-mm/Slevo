## Why

Board タブはメモリ先行更新と全件 fire-and-forget 保存、Thread タブは FIFO intent と Room Flow 確認待ちを組み合わせており、同じ選択・永続化契約を異なる失敗／キャンセル意味論で実装している。特に Board Deep Link は保存失敗後も presentation の `Selected` を待ち続け得るため、DB を canonical state とする Controller／reducer 契約へ統合し、成功・失敗を有限時間で呼び出し元へ返せる構造が必要である。

## What Changes

- Board／Thread ごとに Activity-retained な domain Controller を維持し、load phase、canonical tabs、pending commands、selected key、atomic presentation、command results を単一の論理 state として reducer で遷移させる。
- タブ種別を巨大な generic Controller に統合せず、純粋な reducer primitive、command lifecycle、result 契約だけを共有する。
- `TabSessionStore` を Flow 公開と command 委譲だけを行う薄い facade／lifetime owner にし、list mutation、presentation 観測による成功推論、fire-and-forget 永続化を禁止する。
- Board の通常操作を全件 snapshot 保存から targeted suspend mutation へ移行し、Board／Thread repository command が明示的な成功／失敗を返すようにする。
- accepted command の所有権を Controller へ移し、caller cancellation は待機／navigation だけを中止し、受理済み mutation は Controller teardown まで継続する。
- committed pending operation を対応する Room canonical snapshot まで投影する一方、Flow confirmation は後続 DB command の処理を塞がず、後続 command は canonical + pending の effective state から導出する。
- Deep Link は presentation の特定状態を無期限に観測せず、Controller の明示 command result を待って navigation 可否を決定する。Board 永続化失敗も terminal failure として返す。
- Loading と loaded-empty、metadata merge、隣接／先頭 selection repair、retained close ownership、既存の表示タイミングを維持し、characterization test から段階的に移行して旧 confirmation blocking／phase machinery と重複 state を最後に削除する。
- 既存 active change は削除・archive せず、要件／回帰テストの履歴資産として残す。本変更は `refactor-thread-tab-persistence-consistency` と `fix-thread-deep-link-selection-consistency` の実装設計を supersede し、`fix-last-thread-tab-close-persistence`、`fix-tab-list-close-persistence`、`unify-tab-missing-selection-behavior` の要件を継承する。

## Capabilities

### New Capabilities

- `tab-controller-state-machine`: Board／Thread Controller の state、event、command result、pending projection、永続化、失敗、キャンセル、性能、段階移行の統合契約。

### Modified Capabilities

- `handle-deep-link`: Board／Thread Deep Link が presentation 観測ではなく明示 command result を待ち、失敗時に navigation せず終端する契約へ変更する。

## Impact

- Production: `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/` の `TabSessionStore`、Board／Thread coordinator/controller、projection／selection primitive、`ui/navigation/DeepLinkHandler`、`data/repository/TabsRepository`、Board／Thread tab DAO。
- Tests: `app/src/test/.../ui/tabs/`、`app/src/test/.../ui/navigation/DeepLinkHandlerTest.kt`、`app/src/test/.../ui/bbsroute/`、`app/src/androidTest/.../data/repository/TabsRepositoryThreadStateTest.kt`、`app/src/androidTest/.../ui/bbsroute/BbsRouteScaffoldTest.kt`。
- Compatibility: DB schema と既存 UI text／layout／interaction は変更しない。既存の selection、close、restore、metadata、large-tab 回帰契約を維持する。
- OpenSpec: 上記五つの active change は履歴として存続し、本変更の proposal／design と各 change の関係注記で dependency／supersession を管理する。
