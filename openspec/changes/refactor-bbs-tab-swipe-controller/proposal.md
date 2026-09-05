## Why

板・スレッド画面では同種タブ用 Pager の各ページが本文と下部ツールバーをまとめて保持しているため、本文上の横スワイプ、タブ切替中のツール群移動、ドラッグ途中の選択確定が結合している。下部コントローラーだけをタブ切替の操作面にし、本文とタイトルカードだけが指に追従する一貫した操作へ改める。

## What Changes

- `AppRoute.Board` と `AppRoute.Thread` を別 destination のまま維持し、各画面内の同種タブ切替には既存の `HorizontalPager` と同じ `PagerState` を使用する。
- 本文上の横スワイプを無効化し、固定された下部コントローラー全体の横ドラッグで本文 Pager を直接操作する。
- タイトルカードを本文と同じ Pager offset から描画し、ブックマーク、タイトル、更新、カード下端のロード進捗を本文と同期して移動させる。タイトル専用の PagerState は作成しない。
- 下部ツール群と画面種別ボタンを固定し、Board ではタイトルカード右に「スレ」、Thread では左に「板」を配置する。
- Board の「スレ」は `TabSessionStore.threadPresentationState` の現在選択済み Thread を通常の push navigation で開く。Thread の「板」は現在 Thread の親 Board を通常の push navigation で開く。
- Pager が settle した時点で選択タブを確定し、ドラッグ途中では `TabSessionStore` の selected key を更新しない。
- Pager ページ内の Scaffold/BottomBar を共通ホストへ再編し、検索モード、ツールバー縮退、シート・ポップアップ、タブ別スクロール位置保存を維持する。
- 本文側で不要になる横スワイプ抑制用 pointer input を削除する。

## Capabilities

### New Capabilities

- `bbs-tab-swipe-controller`: 板・スレッド画面の外部 Pager 操作領域、固定コントローラー、Pager 連動タイトルカード、ロード進捗表示を定義する。

### Modified Capabilities

- `tab-selection-source-of-truth`: Pager 操作による selected key 更新をドラッグ途中ではなく settle 完了時に確定する。
- `separated-board-thread-tab-navigation`: 固定コントローラーの「スレ」「板」ボタンから行う通常 push navigation を追加する。
- `bbs-toolbar-scroll-visibility`: Pager から独立した固定ツール群に対して既存の展開・縮退挙動を維持する。

## Impact

- 主対象: `BbsRouteScaffold.kt`、`BbsRouteBottomBar.kt`、`TabToolBar.kt`、`BoardScaffold.kt`、`ThreadScaffold.kt`、`ThreadToolBar.kt`、`ScrollPositionPersistence.kt`
- UI 構造: ページ単位の Scaffold/BottomBar から、本文 Pager と固定コントローラーを持つ単一 Scaffold へ変更する。
- 状態連携: 描画中の Pager offset と確定済みページを分離し、選択確定・固定ツール操作・スクロール位置保存を settled page 基準へ揃える。
- テスト: Compose UI テストでドラッグ追従、固定領域、settle 確定、検索・縮退・シート、通常 navigation を追加し、既存 ViewModel・TabSessionStore テストを維持する。
- 依存ライブラリや navigation route の公開引数は変更しない。
