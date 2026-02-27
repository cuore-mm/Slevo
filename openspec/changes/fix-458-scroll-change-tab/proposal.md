## Why

Issue #458 では、スレッド本文を縦スクロールしている最中に意図せず隣接タブへ切り替わることがある。これは閲覧の連続性を損ない、隣接タブ切り替えはボトムバーのスワイプで行うという期待挙動に反する。

## What Changes

- 本文領域からのドラッグ入力が Pager のタブ切り替えへ伝播しないように、タブスワイプの入力境界を制限する。
- スレッド画面・板画面の既存の縦スクロール挙動と本文内ジェスチャー処理を維持する。
- 本文外の導線（ボトムバーのスワイプや既存の明示的なタブ操作）によるタブ切り替えは従来通り利用可能に保つ。
- 本文スクロール中の誤タブ切り替え防止と、意図した切り替え導線の維持を回帰テストで担保する。

## Capabilities

### New Capabilities
- `tab-swipe-interaction-scope`: BBS ルート画面で、どの領域のドラッグ入力が隣接タブ切り替えを許可されるかを定義し、強制する。

### Modified Capabilities
- （なし）

## Impact

- 影響する UI レイヤー: `app/src/main/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffold.kt`
- 影響する統合ポイント: 本文用 Modifier とジェスチャーハンドラーを渡している thread/board 側の連携処理
- 影響するテスト: タブ切り替えとスクロール挙動に関する Compose/Instrumented テスト
