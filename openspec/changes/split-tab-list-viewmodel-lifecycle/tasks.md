## 1. 状態責務の整理

- [ ] 1.1 `TabsViewModel` と `TabsUiState` の各フィールド・イベントを、タブセッション状態とタブ一覧画面固有状態に分類する
- [ ] 1.2 タブ一覧画面以外から参照される `TabsViewModel` の公開 API を洗い出し、セッション側に残す操作を確定する
- [ ] 1.3 タブ一覧画面専用 ViewModel の取得位置を決め、Activity スコープ ViewModel との取得方法を分離する

## 2. タブ一覧画面専用状態の追加

- [ ] 2.1 タブ一覧画面専用の `TabListUiState` 相当の型を追加し、検索、長押し選択、削除待ち、詳細 BottomSheet、URL入力ダイアログ状態を表現する
- [ ] 2.2 タブ一覧画面専用の `TabListViewModel` 相当を追加し、画面固有状態を `StateFlow` として公開する
- [ ] 2.3 `TabListViewModel` が Activity スコープのタブセッション ViewModel のタブ一覧・更新状態を入力として参照し、画面固有状態と合成できるようにする
- [ ] 2.4 検索モード、検索クエリ、板/スレッドタブの検索フィルタ処理を `TabListViewModel` 側へ移す

## 3. 画面固有イベントの移行

- [ ] 3.1 長押し選択の開始・解除・bounds 更新を `TabListViewModel` 側へ移す
- [ ] 3.2 削除待ちタブの設定・解除を `TabListViewModel` 側へ移し、削除確定時だけセッション側のタブ削除操作へ委譲する
- [ ] 3.3 詳細 BottomSheet の表示対象コピー、表示、非表示を `TabListViewModel` 側へ移す
- [ ] 3.4 URL入力ダイアログの表示、検証中、エラー状態を `TabListViewModel` 側へ移し、URL解決後のタブ追加や遷移はセッション側操作へ委譲する

## 4. Composable 連携の更新

- [ ] 4.1 タブ一覧画面の上位 Composable で Activity スコープのタブセッション ViewModel と画面スコープの `TabListViewModel` を取得する
- [ ] 4.2 タブ一覧画面の子 Composable へ、画面上位で収集した状態と必要な操作だけを引数として渡す
- [ ] 4.3 `TabsPagerContent`、`OpenBoardsList`、`OpenThreadsList`、`TabsBottomSheet` が不要にセッション ViewModel の画面固有状態を参照しないようにする
- [ ] 4.4 タブ追加、タブ削除、固定切替、スレッドタブ更新、ページ切替、子 ViewModel 取得はセッション側操作を呼ぶように接続する

## 5. 既存 ViewModel の整理

- [ ] 5.1 `TabsViewModel` から移行済みの検索、長押し選択、削除待ち、詳細 BottomSheet、URL入力ダイアログ状態を削除する
- [ ] 5.2 `TabsUiState` をセッション状態中心に整理し、画面固有状態を含めない構造にする
- [ ] 5.3 `TabsViewModel` の KDoc と公開 API 名を、タブセッション管理の責務に合う説明へ更新する
- [ ] 5.4 必要に応じて `TabsViewModel` から `TabSessionViewModel` へのリネーム可否を判断し、実施する場合は参照箇所をすべて更新する

## 6. 検証

- [ ] 6.1 `TabListViewModel` の検索、画面離脱時の初期化、長押し選択、詳細 BottomSheet、削除待ち状態の単体テストを追加する
- [ ] 6.2 既存の `BoardTabsCoordinatorTest`、`ThreadTabsCoordinatorTest`、`TabSearchFiltersTest`、`ThreadTabCoordinatorTest` を責務分離後の構造に合わせて更新する
- [ ] 6.3 タブ一覧画面で検索、長押しメニュー、詳細 BottomSheet、固定切替、削除、スワイプ削除、スレッドタブ更新が従来通り動作することを確認する
- [ ] 6.4 タブ一覧画面から離れて戻ったとき、検索や長押し選択などの画面固有状態は初期化され、開いているタブと更新状態は維持されることを確認する
- [ ] 6.5 必須のビルドとユニットテストを実行し、成功を確認する
