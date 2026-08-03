## Why

板一覧で保存済み既読状態から新着レス数が表示されていても、スレッド初回ロードでは全レスが単一の初期グループになり、新着バーが表示されない。板一覧の新着判定とスレッド画面の表示境界を整合させ、利用者が最初の未読レスを識別できるようにする必要がある。

## What Changes

- 保存済みの `lastReadResNo` を初回ロード時の画面セッション境界として取り込み、既読レス群と未読レス群へ分割する。
- 未読レス群を初回ロードにおける最新到着グループとして扱い、既存の新着バーをその先頭に表示する。
- 初回ロード後に既読位置が進んでも、同じ画面セッション内の新着バー位置を変更しない。
- 追加更新時のグループ追加、最新グループ先頭だけに表示する規則、NUMBER/TREE・検索・NGフィルターでの表示処理を維持する。
- 未訪問、全件既読、または保存境界が取得レス範囲外の場合は初回ロードで新着バーを表示しない。
- 新しい画面、文言、アイコン、操作、アクセシビリティ挙動は追加しない。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `thread-response-order`: 保存済み既読境界を持つ初回ロードをレスグループとして復元し、新着バーを未読グループ先頭に表示する要件を追加する。
- `thread-state-sync`: 履歴由来の既読位置をスレッド画面セッション開始時に取り込み、以後の既読更新から独立した表示境界として保持する要件を追加する。

## Impact

- `ThreadRouteViewModel` のタブメタデータ初期化、コンテンツ状態、初回レスグループ構築。
- `ThreadTabInfo` から `lastReadResNo` を画面セッション状態へ受け渡す経路。
- `ThreadVisiblePostsUseCase` と既存表示変換の入力となる `ThreadPostGroup` / `latestArrivalGroupIndex`（表示ロジック自体は維持）。
- `ThreadRouteViewModelTest`、`ThreadVisiblePostsUseCaseTest`、`ThreadDisplayTransformersTest`、`ThreadNewResCalculatorTest`、板一覧変換テストの回帰検証。
- データベーススキーマ、永続化形式、ナビゲーション引数、外部APIへの変更はない。
