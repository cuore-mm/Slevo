## 1. データ永続化

- [x] 1.1 `OpenBoardTabEntity` と `OpenThreadTabEntity` に `isPinned: Boolean = false` を追加する
- [x] 1.2 Room database version を更新し、`open_board_tabs` / `open_thread_tabs` に `isPinned INTEGER NOT NULL DEFAULT 0` を追加する migration を実装する
- [x] 1.3 migration 登録箇所に新 migration を追加する
- [x] 1.4 `OpenBoardTabDao` と `OpenThreadTabDao` の取得結果に `isPinned` を含める
- [x] 1.5 DAO のタブ一覧取得順を既存通り `sortOrder ASC` のまま維持する

## 2. タブモデルと Repository

- [x] 2.1 `BoardTabInfo` と `ThreadTabInfo` に `isPinned: Boolean = false` を追加する
- [x] 2.2 `TabsRepository.observeOpenBoardTabs()` / `observeOpenThreadTabs()` で固定状態を UI モデルへ反映する
- [x] 2.3 `TabsRepository.saveOpenBoardTabs()` / `saveOpenThreadTabs()` で固定状態を永続化する
- [x] 2.4 固定状態を切り替えてもタブ一覧の表示順が既存 `sortOrder` に従うことを確認する

## 3. Coordinator / ViewModel 状態管理

- [x] 3.1 `BoardTabsCoordinator` に板タブ固定切替処理を追加する
- [x] 3.2 `ThreadTabsCoordinator` にスレッドタブ固定切替処理を追加する
- [x] 3.3 `TabsViewModel` に板/スレッドタブの固定切替 API を追加する
- [x] 3.4 `TabsUiState` に長押し選択中タブ、アンカー位置、選択タブ再描画用 bounds、詳細 BottomSheet 表示対象を表す状態を追加する
- [x] 3.5 `TabsViewModel` に長押し選択開始、選択解除、詳細表示、メニュー操作完了の状態更新処理を追加する
- [x] 3.6 `TabsViewModel` の選択解除処理を overlay タップ、メニュー dismissal、戻るキー、ページ切替、選択中タブ消失から共通利用できるようにする
- [ ] 3.7 詳細 BottomSheet 表示用の板/スレッドタブ state を長押し選択 state から分離し、詳細選択時に選択中タブをコピーしてから長押し選択を解除する
- [ ] 3.8 BottomSheet dismiss 時に詳細表示用 state と表示フラグを消し、通常の長押し選択解除処理では詳細表示用 state を誤って消さないようにする

## 4. タブ専用アクションメニュー

- [x] 4.1 `AnchoredOverlayMenu` を再利用した `AnchoredTabActionMenu` を新規作成する
- [x] 4.2 メニューに「詳細」「タブを固定 / タブの固定を解除」「タブを閉じる」を表示する
- [x] 4.3 「タブを閉じる」を破壊的操作として赤字で表示する
- [x] 4.4 メニュー外タップ時に選択状態を解除する dismissal を接続する
- [x] 4.5 `AnchoredTabActionMenu` の Preview を追加する
- [ ] 4.6 `AnchoredOverlayMenu` / position provider に縦方向の自動配置（上側/下側の空きが大きい方）と非重なり gap を指定できる API を追加する
- [ ] 4.7 `AnchoredTabActionMenu` をタブ左端揃え（`HorizontalAnchorAlignment.Start` 相当）かつ上下自動配置にし、長押しタブとメニューが重ならないようにする
- [ ] 4.8 `AnchoredTabActionMenu` の各項目先頭に意味を表すアイコンを表示し、破壊的操作ではアイコン色もテキスト色に揃える

## 5. タブ一覧 UI 接続

- [x] 5.1 `TabListCard` に長押し、固定済み表示用パラメータを追加する
- [x] 5.2 `TabListCard` で未固定タブは閉じるアイコン、固定済みタブは固定アイコンを右上に表示する
- [x] 5.3 固定済みタブ右上の固定アイコンを表示専用にし、タップ時の動作を持たせない
- [x] 5.4 `TabListCard` で長押し時のアンカー位置を取得し、ViewModel へ通知できるようにする
- [x] 5.5 `OpenBoardsList` と `OpenThreadsList` で長押し選択状態、固定状態、メニュー操作をカードへ渡す
- [x] 5.6 削除アニメーション中のカードでは長押し選択とメニュー操作を開始しないようにする
- [x] 5.7 `TabScreenContent` に長押し選択中だけ表示する全画面 `LongPressDimOverlay` を追加し、下部操作群を含む選択タブ以外の領域を暗くする
- [x] 5.8 `LongPressDimOverlay` のタップで長押し選択状態を解除し、下部操作群上のタップも下部操作ではなく選択解除として扱う
- [x] 5.9 長押し対象タブを overlay より上に `SelectedTabFloatingCard` として再描画し、選択タブのタップでは解除処理を実行しない
- [x] 5.10 dim overlay は `hazeSource` の子に入れず、`hazeSource` と `hazeEffect` の兄弟関係を維持する
- [x] 5.11 長押し選択中の元カードを `alpha(0f)` で透明化し、レイアウト位置だけ保持する
- [x] 5.12 元カード側の `isSelected` scale アニメーションを廃止し、選択中の視覚状態は floating card 側だけで表現する
- [x] 5.13 `SelectedTabFloatingCard` の `padding(horizontal = 12.dp)` を削除し、元カードと同じ幅にする
- [x] 5.14 floating card に `graphicsLayer` + `TransformOrigin.Center` で scale（1.00 → 1.04）アニメーションを追加し、layout 位置を保ったまま中心基準で拡大する
- [x] 5.15 floating card の親 `Box` の window 座標を取得し、`boundsInWindow()` から差分を引いて親基準のローカル座標に変換する
- [ ] 5.16 `TabListCard` の `headerTrailingContent` slot を型付き表示モデル（例: `None` / `ThreadResCount`）へ置き換え、ヘッダー右側の描画を `TabListCard` 内に集約する
- [ ] 5.17 `OpenThreadCard` と `ThreadTabFloatingCard` が同じ `ThreadResCount(resCount, newResCount)` を渡すようにし、長押し中の floating card でも新着バッジを通常カードと同じ見た目で表示する
- [ ] 5.18 `TabListCard` 右上の固定アイコンと閉じるアイコンで、`IconButton` の表示領域、`Icon` 本体サイズ、padding を統一する
- [ ] 5.19 `TabScreenContent` 側で直前の選択タブと bounds をローカル保持し、長押し選択解除後も退場アニメーション完了まで floating card を描画する
- [ ] 5.20 退場アニメーション中のタブ ID を一覧へ渡し、元カードを透明化したまま維持して floating card との二重表示を避ける
- [ ] 5.21 退場アニメーション完了後にローカル保持した選択タブ、bounds、退場中フラグを破棄し、元カードを通常表示へ戻す

## 6. 下部操作群と詳細 BottomSheet

- [x] 6.1 長押し選択中も `TabListBottomControls` の既存ボタン表示を変更しないことを確認する
- [x] 6.2 長押し選択中の `TabListBottomControls` は dim overlay の下に通常時と同じ構造で表示し、タップ時は選択解除になることを確認する
- [x] 6.3 長押し選択中もページ切替表示を通常時と同じ条件で維持する
- [x] 6.4 長押し選択中もスレッド更新進捗インジケータを通常時と同じ条件で維持する
- [x] 6.5 `TabScreenContent` から板タブ詳細として `BoardInfoBottomSheet` を表示する
- [x] 6.6 `TabScreenContent` からスレッドタブ詳細として `ThreadInfoBottomSheet` を表示する
- [ ] 6.7 `BoardInfoBottomSheet` / `ThreadInfoBottomSheet` が長押し選択 state ではなく詳細表示用 state を参照して表示されるようにする
- [ ] 6.8 詳細ボタン押下時にアクションメニューと長押し選択状態が閉じても、対象タブの BottomSheet が表示され続けることを確認する

## 7. テストと検証

- [x] 7.1 `BoardTabsCoordinator` の固定切替 unit test を追加する
- [x] 7.2 `ThreadTabsCoordinator` の固定切替 unit test を追加する
- [x] 7.3 `TabsRepository` の固定状態保存/復元 test を追加する
- [x] 7.4 Room migration test で既存タブが未固定として移行されることを確認する
- [x] 7.5 固定状態を切り替えてもタブ表示順が変わらないことを確認する test を追加する
- [ ] 7.6 可能なら Compose UI test で長押しメニュー表示、固定/解除ラベル、閉じる項目、固定アイコン表示専用動作を確認する
- [ ] 7.7 可能なら Compose UI test で長押し中の全画面減光、下部操作群上タップでの選択解除、選択タブタップでの選択維持を確認する
- [x] 7.8 Android CI workflow または指定された検証手順で build と unit test を確認する
