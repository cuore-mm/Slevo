## Why

`BbsRouteScaffold.kt` 内の `HorizontalPager`、`remember`、`DisposableEffect` はすべて `tabKey = getKey(tab)` を基準にタブの同一性を判定しているが、タブ初期化用の `LaunchedEffect` だけが `tab` オブジェクトを直接キーにしている。`tab` は data class なので内容変化（タイトル更新など）で再生成されると `LaunchedEffect` が不要にリセットされ、他の Effect・remember とライフサイクルがずれる可能性がある。

## What Changes

- `BbsRouteScaffold.kt` 内の `LaunchedEffect(isActive, tab)` を `LaunchedEffect(isActive, tabKey)` に変更する
- これにより Pager key、`remember(tabKey)`、`DisposableEffect(tabKey)` と同一のタブ同一性基準で `LaunchedEffect` が動作する

## Capabilities

### New Capabilities

（なし。実装詳細の統一のみ。）

### Modified Capabilities

（なし。仕様レベルの変更はない。）

## Impact

- `app/src/main/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffold.kt`：1行変更
- タブオブジェクトの再生成による `LaunchedEffect` の不要リセットが防止される
- スクロール位置保存・タブ初期化のライフサイクルが `tabKey` 基準で統一され、動作が安定する
