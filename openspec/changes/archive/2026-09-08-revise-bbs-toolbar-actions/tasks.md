## 1. タイトルカード操作とタブ一覧配線

- [x] 1.1 `app/src/main/res/values/strings_common.xml` に長押し semantics 用の「詳細を表示」文字列を追加し、既存 `open_tablist` と合わせてカード操作ラベルとして利用できることを確認する。
- [x] 1.2 `ui/common/TabToolBar.kt` の `TabTitleCard` を非クリック版 `Card` + `Modifier.combinedClickable` に変更し、`onTitleLongClick`、通常クリックラベル、長押しラベルを追加する。ブックマーク／更新の子ボタンが親 callback を発火しない構造を維持する。
- [x] 1.3 `ui/board/components/BoardToolBar.kt` と `ui/thread/components/ThreadToolBar.kt` の `BoardTabTitleCard` / `ThreadTabTitleCard` に長押し callback を追加し、全 call site と Preview がコンパイル可能な引数へ更新されていることを確認する。
- [x] 1.4 `ui/bbsroute/BbsRouteScaffold.kt` の `titleCard` slot と `PagerTitleCards` の関数型へ `openTabListSheet` を追加し、既存の `{ showTabListSheet = true }` を各タイトルカードへ渡す。通常クリックでシートが一重に開くことを UI テストで確認する。
- [x] 1.5 `ui/board/screen/BoardScaffold.kt` と `ui/thread/screen/ThreadScaffold.kt` で、タイトル通常クリックを `openTabListSheet`、長押しを対象カードの stable key を使う `openBoardInfoSheet` / `openThreadInfoSheet` に配線する。

## 2. タブ一覧専用ボタンの削除と板アクション追加

- [x] 2.1 `ui/common/TabToolBar.kt` の `TabToolBar` / `TabToolBarHeader` から `onTabListClick`、`tabIconContentDescriptionRes`、左側の縮退 `CollapsedSideAction` を削除し、右側の投稿操作、画面種別ボタン、108dp／56dpの高さ計算が維持されることを確認する。
- [x] 2.2 `ui/board/components/BoardToolBar.kt` からタブ一覧 action・callback・不要 import を削除し、「その他」action と `onMoreClick` を追加して、操作行が並び替え・検索・スレッド作成・その他の4件になることを Preview または UI テストで確認する。
- [x] 2.3 `ui/thread/components/ThreadToolBar.kt` からタブ一覧 action・callback・不要 import を削除し、操作行が並び替え・検索・書き込み・自動スクロール・その他の5件になることを Preview または UI テストで確認する。

## 3. 共通その他メニューと板セッション状態

- [x] 3.1 `ui/common/BbsToolbarOverflowMenu.kt` を追加し、共通4項目と nullable な表示設定 callback を描画する `BbsToolbarOverflowMenu` / `BbsToolbarMenuContent`、KDoc、Preview を実装する。null の場合に「表示設定」が生成されないことを確認する。
- [x] 3.2 `ui/thread/dialog/ThreadToolbarOverflowMenu.kt` を共通 content の wrapper に変更し、表示設定を含む既存5項目、dismiss、全 callback の公開契約が維持されることを UI テストで確認する。
- [x] 3.3 `ui/board/dialog/BoardToolbarOverflowMenu.kt` を追加し、表示設定 callback を公開せず共通4項目だけを表示する wrapper と KDoc、Preview を実装する。
- [x] 3.4 `ui/tabs/session/BoardSessionState.kt` と `ui/board/state/BoardUiState.kt` に `showMoreSheet` を追加し、`ui/board/viewmodel/BoardRouteViewModel.kt` の `createUiStateFlow` で値を伝播させる。
- [x] 3.5 `BoardRouteViewModel` に対象 `tabKey` だけを更新する `openMoreSheet` / `closeMoreSheet` を追加し、`BoardRouteViewModelTest.kt` で開閉と別タブ非干渉を検証する。
- [x] 3.6 `BoardScaffold.kt` で `BoardToolBar.onMoreClick` を `openMoreSheet` に接続し、`optionalSheetContent` に `BoardToolbarOverflowMenu` を配置する。各項目で先に `closeMoreSheet` を実行してから `BookmarkList`、`ServiceList`、`HistoryList`、`SettingsHome` へ遷移することを確認する。

## 4. UI・アクセシビリティ回帰テスト

- [x] 4.1 `app/src/androidTest/.../ui/common/TabToolBarTest.kt` を更新し、展開・縮退の両方で独立したタブ一覧ボタンが存在しないこと、カードに通常クリック／長押し semantics があること、通常クリック・長押し・ブックマーク・更新が互いに誤発火しないことを検証する。
- [x] 4.2 板・スレのタイトルカードまたは Scaffold の Compose test を追加し、通常クリックで対応タブ一覧、長押しで対応情報シートが開き、通常クリックでは情報シートが開かないことを検証する。
- [x] 4.3 板用その他メニューの Compose test で4項目の表示、「表示設定」の非表示、dismiss と各 callback を検証し、スレ用メニューのテストで既存5項目を回帰確認する。
- [x] 4.4 既存 `TabToolBarTest` の展開108dp／縮退56dp、destination disabled semantics、`destinationModifier` / `actionsRowModifier` 到達、および `BbsRouteScaffoldTest` の stable key／Shared Transition 関連テストが引き続き成功するよう更新する。

## 5. ビルドと検証

- [x] 5.1 CI Run `34134560708` の `assembleCi` でビルドが成功したことを確認する。
- [x] 5.2 CI Run `34134560708` の `testCiUnitTest`（`testDebugUnitTest` を依存）で unit test が成功したことを確認する。
- [x] 5.3 CIワークフローにinstrumented test実行環境が含まれていないため、`TabToolBarTest`、BBS Scaffold、板／スレその他メニューのCompose instrumented testは未実行として記録する。
