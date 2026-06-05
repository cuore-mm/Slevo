## 1. 依存箇所の整理

- [ ] 1.1 `TabsViewModel` の参照箇所を、セッション状態参照、セッション操作、navigation 正規化、URL検証状態に分類する
- [ ] 1.2 `TabsUiState` の各フィールド利用箇所を洗い出し、`TabSessionStore` の個別 `StateFlow` または画面ローカル状態への移行先を確定する
- [ ] 1.3 `MainActivity`、`AppScaffold`、`AppNavGraph` で `TabsViewModel` を渡している経路を図示し、同じ経路で `TabSessionStore` へ置き換える単位を決める

## 2. セッション状態取得経路の置き換え

- [ ] 2.1 `MainActivity` から `TabsViewModel` を生成・保持する処理を削除し、`TabSessionStore` を上位から利用できる構造へ変更する
- [ ] 2.2 `AppScaffold` と `AppNavGraph` の引数を `TabsViewModel` から `TabSessionStore` へ置き換える
- [ ] 2.3 `BoardScaffold` が `tabsViewModel.uiState` ではなく `TabSessionStore.boardLoaded` と `TabSessionStore.openBoardTabs` を収集するように変更する
- [ ] 2.4 `ThreadScaffold` が `tabsViewModel.uiState` ではなく `TabSessionStore.threadLoaded` と `TabSessionStore.openThreadTabs` を収集するように変更する
- [ ] 2.5 `TabScreenContent` が `tabsViewModel.sessionUiState` ではなく `TabSessionStore` のセッション状態を画面上位で収集するように変更する
- [ ] 2.6 `TabsScaffold` と `TabsBottomSheet` が既存の `TabsViewModel` 引数の代わりに `TabSessionStore` を受け取り、`TabListViewModel` と明示的に接続するように変更する

## 3. セッション操作と navigation の置き換え

- [ ] 3.1 タブ追加、タブ削除、固定切替、スレッドタブ更新、更新キャンセル、ページ切替、最終選択ページ保存を `TabsViewModel` 経由から `TabSessionStore` 経由へ置き換える
- [ ] 3.2 子 ViewModel 取得と解放を `TabsViewModel` 経由から `TabSessionStore` 経由へ置き換える
- [ ] 3.3 `navigateToBoard` / `navigateToThread` などの navigation helper が `TabsViewModel?` を受け取らないようにし、`TabSessionStore?` を受け取るように変更する
- [ ] 3.4 Deep Link、履歴、ブックマーク、BBSルート、板画面、スレッド画面からの遷移で、タブ確保とルート正規化が従来通り実行されるように接続する

## 4. URL検証状態の分離

- [ ] 4.1 `BbsRouteScaffold` の URL入力ダイアログ検証中状態を `TabsUiState.isUrlValidating` から `rememberSaveable` の画面ローカル状態へ移す
- [ ] 4.2 `BbsRouteScaffold` から `tabsViewModel.startUrlValidation()` と `tabsViewModel.finishUrlValidation()` の呼び出しを削除する
- [ ] 4.3 タブ一覧画面の URL入力ダイアログが引き続き `TabListViewModel` の検証状態を使うことを確認する

## 5. `TabsViewModel` / `TabsUiState` の削除

- [ ] 5.1 すべての `TabsViewModel` 参照が削除されていることを検索で確認する
- [ ] 5.2 すべての `TabsUiState` 参照が削除されていることを検索で確認する
- [ ] 5.3 `TabsViewModel.kt` を削除する
- [ ] 5.4 `TabsUiState.kt` を削除する
- [ ] 5.5 削除後に不要になった import、KDoc、テストヘルパー、Hilt 関連記述を整理する

## 6. テスト更新

- [ ] 6.1 `TabSessionStoreTest` を、削除後の公開 API と lifecycle に合わせて更新する
- [ ] 6.2 `TabListViewModelTest` が `TabsViewModel` / `TabsUiState` に依存していないことを確認する
- [ ] 6.3 `BoardScaffold` / `ThreadScaffold` 相当のタブ読み込み状態利用について、可能な範囲で unit test または UI test を更新する
- [ ] 6.4 navigation helper の引数変更に合わせ、Deep Link / URL入力 / route normalization 関連テストを更新する

## 7. 検証

- [ ] 7.1 CI の build と unit test を実行し、成功を確認する
- [ ] 7.2 タブ一覧画面で検索、長押し選択、詳細 BottomSheet、固定、削除、スレッドタブ更新が従来通り動作することを確認する
- [ ] 7.3 板画面とスレッド画面で開いているタブ一覧、読み込み状態、ページ状態が維持されることを確認する
- [ ] 7.4 BBSルート画面の URL入力ダイアログで検証中表示、エラー表示、板/スレッド遷移が従来通り動作することを確認する
- [ ] 7.5 Deep Link、履歴、ブックマーク経由の遷移でタブ確保とルート正規化が従来通り動作することを確認する
