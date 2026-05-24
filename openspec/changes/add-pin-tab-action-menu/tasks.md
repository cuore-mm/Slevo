## 1. データ永続化

- [ ] 1.1 `OpenBoardTabEntity` と `OpenThreadTabEntity` に `isPinned: Boolean = false` を追加する
- [ ] 1.2 Room database version を更新し、`open_board_tabs` / `open_thread_tabs` に `isPinned INTEGER NOT NULL DEFAULT 0` を追加する migration を実装する
- [ ] 1.3 migration 登録箇所に新 migration を追加する
- [ ] 1.4 `OpenBoardTabDao` と `OpenThreadTabDao` の取得結果に `isPinned` を含める
- [ ] 1.5 DAO のタブ一覧取得順を既存通り `sortOrder ASC` のまま維持する

## 2. タブモデルと Repository

- [ ] 2.1 `BoardTabInfo` と `ThreadTabInfo` に `isPinned: Boolean = false` を追加する
- [ ] 2.2 `TabsRepository.observeOpenBoardTabs()` / `observeOpenThreadTabs()` で固定状態を UI モデルへ反映する
- [ ] 2.3 `TabsRepository.saveOpenBoardTabs()` / `saveOpenThreadTabs()` で固定状態を永続化する
- [ ] 2.4 固定状態を切り替えてもタブ一覧の表示順が既存 `sortOrder` に従うことを確認する

## 3. Coordinator / ViewModel 状態管理

- [ ] 3.1 `BoardTabsCoordinator` に板タブ固定切替処理を追加する
- [ ] 3.2 `ThreadTabsCoordinator` にスレッドタブ固定切替処理を追加する
- [ ] 3.3 `TabsViewModel` に板/スレッドタブの固定切替 API を追加する
- [ ] 3.4 `TabsUiState` に長押し選択中タブ、アンカー位置、詳細 BottomSheet 表示対象を表す状態を追加する
- [ ] 3.5 `TabsViewModel` に長押し選択開始、選択解除、詳細表示、メニュー操作完了の状態更新処理を追加する

## 4. タブ専用アクションメニュー

- [ ] 4.1 `AnchoredOverlayMenu` を再利用した `AnchoredTabActionMenu` を新規作成する
- [ ] 4.2 メニューに「詳細」「タブを固定 / タブの固定を解除」「タブを閉じる」を表示する
- [ ] 4.3 「タブを閉じる」を破壊的操作として赤字で表示する
- [ ] 4.4 メニュー外タップ時に選択状態を解除する dismissal を接続する
- [ ] 4.5 `AnchoredTabActionMenu` の Preview を追加する

## 5. タブ一覧 UI 接続

- [ ] 5.1 `TabListCard` に長押し、選択強調、非選択減光、固定済み表示用パラメータを追加する
- [ ] 5.2 `TabListCard` で未固定タブは閉じるアイコン、固定済みタブは固定アイコンを右上に表示する
- [ ] 5.3 固定済みタブ右上の固定アイコンを表示専用にし、タップ時の動作を持たせない
- [ ] 5.4 `TabListCard` で長押し時のアンカー位置を取得し、ViewModel へ通知できるようにする
- [ ] 5.5 `OpenBoardsList` と `OpenThreadsList` で長押し選択状態、固定状態、メニュー操作をカードへ渡す
- [ ] 5.6 削除アニメーション中のカードでは長押し選択とメニュー操作を開始しないようにする

## 6. 下部操作群と詳細 BottomSheet

- [ ] 6.1 長押し選択中も `TabListBottomControls` の既存ボタン表示を変更しないことを確認する
- [ ] 6.2 長押し選択中もページ切替表示を通常時と同じ条件で維持する
- [ ] 6.3 長押し選択中もスレッド更新進捗インジケータを通常時と同じ条件で維持する
- [ ] 6.4 `TabScreenContent` から板タブ詳細として `BoardInfoBottomSheet` を表示する
- [ ] 6.5 `TabScreenContent` からスレッドタブ詳細として `ThreadInfoBottomSheet` を表示する

## 7. テストと検証

- [ ] 7.1 `BoardTabsCoordinator` の固定切替 unit test を追加する
- [ ] 7.2 `ThreadTabsCoordinator` の固定切替 unit test を追加する
- [ ] 7.3 `TabsRepository` の固定状態保存/復元 test を追加する
- [ ] 7.4 Room migration test で既存タブが未固定として移行されることを確認する
- [ ] 7.5 固定状態を切り替えてもタブ表示順が変わらないことを確認する test を追加する
- [ ] 7.6 可能なら Compose UI test で長押しメニュー表示、固定/解除ラベル、閉じる項目、固定アイコン表示専用動作を確認する
- [ ] 7.7 Android CI workflow または指定された検証手順で build と unit test を確認する
