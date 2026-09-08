## Why

Android 15 以降では edge-to-edge が強制される一方、現在の UI はルート Scaffold、画面 Scaffold、Material 3 コンポーネント、個別 Modifier の間で Insets の責務が分散しており、背景やスクロール領域がシステムバー手前で切れる画面がある。システムバー、下部ナビゲーション、IME の各領域について単一の所有者を定め、重要な UI を保護しながら全画面で一貫した edge-to-edge 表示にする。

## What Changes

- `MainActivity` の `enableEdgeToEdge()` を維持し、IME の `adjustResize` を Manifest で宣言する。
- ルート Scaffold をアプリ下部ナビゲーションの配置と占有領域通知に限定し、画面固有の system bar Insets を各画面に委譲する。
- Material 3 の `NavigationBar`、`TopAppBar`、`BottomAppBar`、`FlexibleBottomAppBar` では標準 Insets を優先し、外側の重複・補助 padding と固定 56dp 高を除去する。
- Lazy リストでは Scaffold の padding を `contentPadding` として適用し、背景とスクロール領域をシステムバーやアプリバーの背後まで延長する。
- 通常のスクロール Column、固定操作 UI、オーバーレイ、全幅 Dialog、IME 入力 UI ごとに安全領域の適用方法を統一する。
- 通常画面の status/navigation bar アイコン外観と navigation bar contrast を一元管理し、画像ビューア固有のシステムバー制御・復元と競合させない。
- ジェスチャーナビゲーション、3ボタンナビゲーション、IME、画面回転、カットアウト、ライト／ダークテーマを対象とする回帰テストを追加する。

## Capabilities

### New Capabilities

- `edge-to-edge-layout`: 背景・スクロール領域の edge-to-edge 描画、重要 UI の安全領域、Material 3 Insets、IME、システムバー外観に関する全画面共通要件を定義する。

### Modified Capabilities

なし。

## Impact

- `MainActivity.kt`、`AndroidManifest.xml`、`AppScaffold.kt`、下部ナビゲーションコンポーネント。
- Board、Thread、Tabs、Bookmark、History、BBS 一覧、設定、About、ライセンス画面の Scaffold とスクロールコンテナ。
- 検索 BottomBar、投稿 UI、全幅 Dialog、画像ビューアのシステムバー連携。
- Insets 合成用の共通 UI ユーティリティと Compose instrumented tests。
- 公開 API、永続データ、ネットワーク仕様への影響はない。
