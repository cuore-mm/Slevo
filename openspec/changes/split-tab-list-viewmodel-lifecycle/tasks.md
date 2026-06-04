## 1. 状態責務の整理

- [ ] 1.1 `TabsViewModel` と `TabsUiState` の各フィールド・イベントを、タブセッション状態、タブ一覧画面固有状態、純粋な画面 ViewModel 責務に分類する
- [ ] 1.2 タブ一覧画面以外から参照される `TabsViewModel` の公開 API を洗い出し、非 ViewModel のタブセッション管理コンポーネントへ移す操作を確定する
- [ ] 1.3 タブセッション管理コンポーネントの名称、公開 API、Hilt スコープを決める
- [ ] 1.4 タブ一覧画面専用 ViewModel の取得位置を決め、タブセッション管理コンポーネントとの接続方法を分離する

## 2. タブセッション管理コンポーネントの追加

- [ ] 2.1 `TabSessionController` / `TabSessionStore` 相当の非 ViewModel コンポーネントを追加し、タブセッション状態を `StateFlow` として公開する
- [ ] 2.2 開いている板タブ/スレッドタブ、読み込み状態、ページ状態、ページアニメーション、スレッド更新状態、更新進捗をセッションコンポーネントへ移す
- [ ] 2.3 タブ追加、タブ削除、固定切替、スレッドタブ更新、更新キャンセル、ページ切替、最終選択ページ保存の操作をセッションコンポーネントへ移す
- [ ] 2.4 `BoardTabsCoordinator`、`ThreadTabsCoordinator`、`TabViewModelRegistry` の利用境界をセッションコンポーネントへ接続する
- [ ] 2.5 セッションコンポーネントの Hilt スコープを Activity Retained 相当を第一候補として設定し、構成変更時にタブセッション状態が維持されるようにする

## 3. タブ一覧画面専用状態の追加

- [ ] 3.1 タブ一覧画面専用の `TabListUiState` 相当の型を追加し、検索、長押し選択、削除待ち、詳細 BottomSheet、URL入力ダイアログ状態を表現する
- [ ] 3.2 タブ一覧画面専用の `TabListViewModel` 相当を追加し、画面固有状態を `StateFlow` として公開する
- [ ] 3.3 `TabListViewModel` がタブセッション管理コンポーネントのタブ一覧・更新状態を入力として参照し、画面固有状態と合成できるようにする
- [ ] 3.4 検索モード、検索クエリ、板/スレッドタブの検索フィルタ処理を `TabListViewModel` 側へ移す

## 4. 画面固有イベントの移行

- [ ] 4.1 長押し選択の開始・解除・bounds 更新を `TabListViewModel` 側へ移す
- [ ] 4.2 削除待ちタブの設定・解除を `TabListViewModel` 側へ移し、削除確定時だけセッションコンポーネントのタブ削除操作へ委譲する
- [ ] 4.3 詳細 BottomSheet の表示対象コピー、表示、非表示を `TabListViewModel` 側へ移す
- [ ] 4.4 URL入力ダイアログの表示、検証中、エラー状態を `TabListViewModel` 側へ移し、URL解決後のタブ追加や遷移はセッションコンポーネント操作へ委譲する

## 5. Composable 連携の更新

- [ ] 5.1 タブ一覧画面の上位 Composable で画面スコープの `TabListViewModel` を取得し、タブセッション管理コンポーネントの状態と操作を利用できるようにする
- [ ] 5.2 タブ一覧画面の子 Composable へ、画面上位で収集した状態と必要な操作だけを引数として渡す
- [ ] 5.3 `TabsPagerContent`、`OpenBoardsList`、`OpenThreadsList`、`TabsBottomSheet` が不要に旧 `TabsViewModel` の画面固有状態を参照しないようにする
- [ ] 5.4 タブ追加、タブ削除、固定切替、スレッドタブ更新、ページ切替、子 ViewModel 取得はセッションコンポーネント操作を呼ぶように接続する

## 6. 旧 ViewModel の整理

- [ ] 6.1 `TabsViewModel` から移行済みのセッション状態とセッション操作を削除する
- [ ] 6.2 `TabsViewModel` から移行済みの検索、長押し選択、削除待ち、詳細 BottomSheet、URL入力ダイアログ状態を削除する
- [ ] 6.3 `TabsUiState` を必要最小限に整理し、不要になった場合は `TabListUiState` とセッション状態へ統合して削除する
- [ ] 6.4 `TabsViewModel` が不要になった場合は削除し、必要な場合でも画面または NavGraph に紐づく UI 状態所有者としての責務だけに縮退する
- [ ] 6.5 KDoc と公開 API 名を、ViewModel と非 ViewModel セッションコンポーネントの責務境界に合う説明へ更新する

## 7. 検証

- [ ] 7.1 タブセッション管理コンポーネントのタブ追加、削除、固定切替、更新進捗、キャンセル、ページ状態の単体テストを追加する
- [ ] 7.2 `TabListViewModel` の検索、画面離脱時の初期化、長押し選択、詳細 BottomSheet、削除待ち状態の単体テストを追加する
- [ ] 7.3 既存の `BoardTabsCoordinatorTest`、`ThreadTabsCoordinatorTest`、`TabSearchFiltersTest`、`ThreadTabCoordinatorTest` を責務分離後の構造に合わせて更新する
- [ ] 7.4 タブ一覧画面で検索、長押しメニュー、詳細 BottomSheet、固定切替、削除、スワイプ削除、スレッドタブ更新が従来通り動作することを確認する
- [ ] 7.5 タブ一覧画面から離れて戻ったとき、検索や長押し選択などの画面固有状態は初期化され、開いているタブと更新状態は維持されることを確認する
- [ ] 7.6 必須のビルドとユニットテストを実行し、成功を確認する
