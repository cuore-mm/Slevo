## Why

スレッドタブのメモリ状態を先に更新して一覧全体を非同期保存する現在の方式では、Room の古い Flow emission が保存途中の状態を上書きし、多数の保存済みタブが削除または復活する競合が発生し得る。DB を永続タブ一覧の正本として、初期読込・個別変更・Flow 反映を一つの整合性契約に統一する必要がある。

## What Changes

- スレッドタブ一覧の状態を「未初期化/読込中」「読込済み空」「読込済み非空」として区別し、初回 Room Flow を受信する前の空リストを正規状態として扱わない。
- 通常の追加、削除、pin 切替、タブ情報更新を、完了を報告する対象タブ単位の repository/DAO mutation に置き換える。
- 既存スレッドタブの ensure では、route 由来の未解決 `boardId = 0`、空文字、または初期 URL 表示名を解決済み ThreadState へ上書きせず、有効な入力フィールドだけを DB canonical metadata へマージする。
- スレッドタブ mutation intent を順序どおりに直列化し、`DatabaseWriteGate` と Room transaction による既存の DB 書き込み保護を維持する。
- Room Flow の結果だけを永続タブ一覧の canonical state とし、未完了 mutation は pending operation として合成する。楽観的メモリ一覧と Flow を順不同の書き手として競合させない。
- `saveOpenThreadTabs` による全件置換と `deleteNotIn` は通常操作から外し、明示的な一括復元などの bulk use case に限定して、初期読込完了後かつ専用ガード下でのみ利用できる契約にする。
- 1,252 件の cold start、初回 Flow の停止、古い 1,252 件 emission と新しい 1,253 件 emission の順序制御、読込済み空、連続 add/delete/pin を再現する決定的テストを追加する。
- 後続変更 `fix-thread-deep-link-selection-consistency` が待機できる readiness と mutation completion の公開契約を提供する。

## Capabilities

### New Capabilities

- `thread-tab-persistence-consistency`: DB 正本、明示的な読込状態、直列化された個別 mutation、pending operation と Room Flow の整合、bulk replacement の制約を規定する。

### Modified Capabilities

なし。

## Impact

- Production: `ThreadTabsCoordinator.kt`、`TabSessionStore.kt`、`ThreadTabCoordinator.kt`、`TabsRepository.kt`、`OpenThreadTabDao.kt` と関連モデル/API。
- Tests: `ThreadTabsCoordinatorTest.kt`、`ThreadTabCoordinatorTest.kt`、`TabSessionStoreTest.kt`、`TabsRepositoryThreadStateTest.kt`、必要な制御可能 fake/Flow fixture。既存タブへ placeholder metadata を再 ensure する Room 回帰テストを含む。
- DB schema は変更せず、既存 `open_thread_tabs` データ、並び順、pin、スクロール位置を保持する。`DatabaseWriteGate` と backup/restore の排他契約を維持する。
- 後続の `fix-thread-deep-link-selection-consistency` は本変更の readiness/completion 契約に依存し、本変更の完了後に実装する。
