## Context

`TabToolBar` は展開時に `BottomActionsRow`、縮退時に `TabToolBarHeader` 内の左右 `CollapsedSideAction` を表示する。現状は板・スレとも「タブ一覧」を展開行と縮退時左側の両方に持ち、`TabTitleCard` の `Card(onClick)` は情報シートを開く。スレ画面だけが `ThreadToolbarOverflowMenu` とタブ単位の `showMoreSheet` 状態を持つ。

直近の BBS コントローラー再編で確定した展開高108dp、縮退高56dp、タイトルカード単位の Pager 移動、画面種別ボタンと操作行の Shared Transition を維持する必要がある。背景は `proposal.md`、外部振る舞いは `specs/bbs-toolbar-actions/spec.md` と `specs/board-thread-info-sheet/spec.md` を参照する。

## Goals / Non-Goals

**Goals:**

- タイトルカード本体に通常クリックと長押しを共存させ、内側のブックマーク・更新ボタンとは独立して処理する。
- 共通ツールバー API からタブ一覧専用の縮退ボタン契約を除去し、画面固有アクションの一覧からもタブ一覧を除去する。
- スレ画面のメニュー表示を壊さず、4つの共通遷移項目を板画面でも再利用する。
- 板画面のメニュー表示状態を他の板 UI 状態と同様にタブ単位で保持する。

**Non-Goals:**

- タブ一覧画面自体、情報シート内容、画面種別切替、ジェスチャー設定によるタブ一覧導線は変更しない。
- スレ画面の「表示設定」項目・設定シート・永続化処理は変更しない。
- ツールバーの高さ、スクロール縮退規則、Pager、Shared Transition、Navigation route 引数は変更しない。

## Decisions

### 1. `TabTitleCard` のカード本体を複合クリック領域にする

`ui/common/TabToolBar.kt` の `TabTitleCard` に `onTitleLongClick` を追加し、カード本体へ通常クリックと長押しを設定する。通常クリックはタブ一覧、長押しは情報シートへ委譲する。アクセシビリティには既存 `R.string.open_tablist` と、新規の「詳細を表示」文字列を操作ラベルとして設定する。

クリック可能な `Card` の単一 `onClick` のままジェスチャーを外付けするのではなく、非クリック版 `Card` と `Modifier.combinedClickable` を組み合わせ、通常クリック・長押し・semantics を一つの入力ノードで定義する。これにより二重クリックハンドラを避ける。内側の `ExpandedCardAction` は独立したクリックノードのまま維持し、その領域でブックマーク／更新を実行した際は親カードのクリックを発火させない。内側ボタンの長押しは既存 Tooltip を優先し、情報シートを開かない。

代案の「タイトル文字だけを複合クリック領域にする」は、ユーザーが認識するカード全体よりタップ領域が狭くなるため採用しない。

### 2. タブ一覧コールバックはタイトルカードの Pager slot へ配線する

`BbsRouteScaffold` が `bottomBar` slot に渡している `{ showTabListSheet = true }` と同じコールバックを、`titleCard` slot の第6引数 `openTabListSheet` として追加する。`PagerTitleCards` の `titleCard` 関数型にも同じ引数を追加し、各カードを描画する際に渡す。`BoardScaffold` / `ThreadScaffold` はこれを `BoardTabTitleCard` / `ThreadTabTitleCard` の `onTitleClick` へ渡し、`onTitleLongClick` は `BoardRouteViewModel.openBoardInfoSheet(selectedTab.boardUrl)` / `ThreadRouteViewModel.openThreadInfoSheet(selectedTab.id.value)` へ配線する。

タイトルカードは Pager 上で非選択カードも構成され得るため、詳細表示はコールバック引数の `BoardTabInfo` / `ThreadTabInfo` の stable key を必ず使用する。通常クリックのタブ一覧表示は現在の画面種別単位であり、選択タブを変更しない。

代案の「各 Scaffold で別のタブ一覧状態を直接操作する」は、既存の `BbsRouteScaffold` に集約されたシート表示経路を重複させるため採用しない。

### 3. 共通 `TabToolBar` からタブ一覧専用 API と左縮退アクションを削除する

`TabToolBar` / `TabToolBarHeader` から `onTabListClick` と `tabIconContentDescriptionRes` を削除し、ヘッダー左端の `CollapsedSideAction` も削除する。空の代替スロットは置かず、縮退時は解放された幅をタイトルカードが使用する。右端の投稿／スレッド作成 `CollapsedSideAction`、`TabDestinationIconButton`、高さ計算、アニメーション閾値は維持する。

`BoardToolBar` の actions は並び替え・検索・スレッド作成・その他の4件、`ThreadToolBar` は並び替え・検索・書き込み・自動スクロール・その他の5件とする。両 Composable から `onTabListClick` を削除し、板側には `onMoreClick` を追加する。アクション数に応じて `BottomActionsRow` が既存の均等配置を行うため、高さや Shared Bounds 対象は変更しない。

### 4. その他メニューの共通4項目を再利用可能にする

`ThreadToolbarOverflowMenu.kt` の5項目をそのまま複製せず、`ui/common/BbsToolbarOverflowMenu.kt` を新設する。同ファイルの `BbsToolbarMenuContent` はブックマーク・板一覧・履歴・設定の4 callbackと、nullable な `onDisplaySettingsClick` を受ける。非null の場合だけ先頭に「表示設定」を描画する。`ThreadToolbarOverflowMenu` はこれを非null で呼び、既存5項目の配置と処理を維持する。`ui/board/dialog/BoardToolbarOverflowMenu.kt` は表示設定 callback を公開せず、null で共通 content を呼ぶ。新規 Composable にはリポジトリ規約どおり KDoc と意味のある `@Preview` を追加する。

板画面で項目を選択した場合は、まずメニューを閉じてから既存 route（`AppRoute.BookmarkList`、`AppRoute.ServiceList`、`AppRoute.HistoryList`、`AppRoute.SettingsHome`）へ `NavController` で遷移する。スレ画面も同じ「閉じてから遷移」の順序を維持する。

代案の「板用メニューを完全複製する」は表示・アクセシビリティ文言の差分が生まれやすいため採用しない。

### 5. 板のメニュー可視性は `BoardSessionState` で管理する

`BoardSessionState` と `BoardUiState` に `showMoreSheet` を追加し、`BoardRouteViewModel.createUiStateFlow` のセッション状態マッピングへ含める。`openMoreSheet(tabKey)` / `closeMoreSheet(tabKey)` は `updateBoardSessionState` で対象タブだけを更新する。`BoardScaffold` の `optionalSheetContent` で対象タブの状態に応じて板用メニューを表示する。

画面ローカルの `remember` を使う代案は、Pager のタブ切替時に表示対象と状態所有者がずれるため採用しない。

## Implementation Contract

1. `TabTitleCard`、`BoardTabTitleCard`、`ThreadTabTitleCard` の引数を通常クリックと長押しの2系統にし、全 call site と Preview を更新する。カード内ボタンのクリック・長押しが親操作を発火しないことを Compose test で確認する。
2. `BbsRouteScaffold` のタイトルカード slot へ既存の `openTabListSheet` を安全に渡し、板・スレの通常クリックへ接続する。詳細表示は選択カード自身の key を ViewModel に渡す。
3. `TabToolBar`、`BoardToolBar`、`ThreadToolBar` からタブ一覧ボタン関連引数・import・action を削除し、板側の「その他」action と callback を追加する。展開108dp／縮退56dpと Shared Transition modifier を変更しない。
4. 共通4項目を一つの menu content 実装に集約する。板 wrapper から表示設定 callback を受け取らず、UI に「表示設定」を生成しない。スレ wrapper は表示設定を先頭に含む既存5項目を維持する。
5. `BoardSessionState` → `BoardRouteViewModel.createUiStateFlow` → `BoardUiState` → `BoardScaffold` の順で `showMoreSheet` を伝播し、open/close を対象 `tabKey` に限定する。
6. 新規・変更した型と非自明関数には KDoc を付け、KDoc は annotation より前に置く。Preview 関数にはコメントを追加しない。

## Error Cases / Compatibility

- タイトルカードが Pager ドラッグ中に移動しても、通常クリックは既存のタブ一覧を一重に開き、長押しは引数で受けたカードの stable key に対応する情報だけを開く。
- メニュー外タップ・戻る操作では `closeMoreSheet` が呼ばれ、対象板タブの状態だけが false になる。
- メニュー項目選択時に遷移しても、先に可視状態を閉じるため戻った際にメニューが再表示されない。
- 検索モード、縮退アニメーション中のボタン無効化、画面種別ボタン disabled semantics、ブックマーク・更新処理は既存互換を維持する。
- UI 操作の割り当て変更は後方互換ではないため、旧「タイトルカードタップで詳細」は残さない。詳細の別導線であるタブ一覧内の「詳細」や板一覧項目長押しは維持する。

## Testing Strategy

- `TabToolBarTest` に、展開・縮退の両方でタブ一覧アイコンが存在しないこと、タイトルカードが通常クリックと長押し semantics を持つこと、通常クリック／長押し／内側ボタンが各1回だけ対応 callback を呼ぶことを追加する。
- 板・スレの Scaffold またはタイトルカード UI テストで、通常クリックがタブ一覧、長押しが対応情報シートを開くことを検証する。
- 板用その他メニューの Compose test で4項目の表示、「表示設定」の非表示、dismiss と各 callback を検証する。スレ用メニューでは5項目が維持される回帰テストを追加する。
- `BoardRouteViewModelTest` で `openMoreSheet` / `closeMoreSheet` の対象タブ単位の状態遷移を検証する。既存の情報シート open/close テストも維持する。
- 既存の `TabToolBarTest` にある108dp／56dp測定、destination disabled semantics、modifier 到達テスト、および `BbsRouteScaffoldTest` の stable key／Shared Transition テストを回帰確認する。
- 実装後に `./gradlew assembleDebug` と `./gradlew testDebugUnitTest` を実行し、必要な Compose instrumented test は接続済み端末または emulator で実行する。

## Migration Plan

永続データや API の移行は不要。共通 Composable の引数変更、板状態追加、画面配線、テスト更新を同一変更で適用する。問題が発生した場合は当該コミットを戻すことで、旧タブ一覧ボタンとタイトルタップ導線へ復帰できる。
