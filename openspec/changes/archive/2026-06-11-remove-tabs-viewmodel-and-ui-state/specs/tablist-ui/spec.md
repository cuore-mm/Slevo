## ADDED Requirements

### Requirement: タブセッション状態の非 ViewModel 正本化
システムは開いている板タブ、開いているスレッドタブ、読み込み状態、ページ状態、スレッド更新状態、新着レス数、子 ViewModel キャッシュを `TabsViewModel` ではなく非 ViewModel のタブセッション管理コンポーネントで管理しなければならないMUST。タブ一覧画面固有状態をタブセッション管理コンポーネントへ保存してはならないMUST NOT。

#### Scenario: 画面固有状態をセッション状態へ混在させない
- **WHEN** ユーザーがタブ一覧画面で検索、長押し選択、詳細 BottomSheet、URL入力ダイアログを操作する
- **THEN** システムはそれらの一時 UI 状態をタブ一覧画面専用状態として扱い、タブセッション状態へ保存しない

#### Scenario: タブセッション状態を画面遷移後も維持する
- **WHEN** ユーザーがタブ一覧画面から板画面またはスレッド画面へ遷移する
- **THEN** システムは開いているタブ、ページ状態、更新状態をタブセッション管理コンポーネントで維持する

### Requirement: `TabsViewModel` と `TabsUiState` への依存除去
システムはタブ一覧、板画面、スレッド画面、BBSルート画面、navigation helper で `TabsViewModel` または `TabsUiState` を状態取得・操作実行の契約として使用してはならないMUST NOT。各画面は必要なタブセッション状態と操作を `TabSessionStore` から取得しなければならないMUST。

#### Scenario: 板画面がタブセッション状態を参照する
- **WHEN** 板画面が開いている板タブ一覧または板タブ読み込み状態を必要とする
- **THEN** システムは `TabsUiState` ではなくタブセッション管理コンポーネントの状態から必要な値を取得する

#### Scenario: スレッド画面がタブセッション状態を参照する
- **WHEN** スレッド画面が開いているスレッドタブ一覧またはスレッドタブ読み込み状態を必要とする
- **THEN** システムは `TabsUiState` ではなくタブセッション管理コンポーネントの状態から必要な値を取得する

#### Scenario: navigation helper が ViewModel 型へ依存しない
- **WHEN** navigation helper が板またはスレッドへの遷移前にルート正規化やタブ確保を必要とする
- **THEN** システムは `TabsViewModel` 型ではなくタブセッション操作または必要な操作関数を使用する

### Requirement: URL検証状態の画面所有
システムは URL入力ダイアログの検証中状態を `TabsUiState` ではなく、そのダイアログを表示する画面または画面専用 ViewModel の状態として管理しなければならないMUST。BBSルート画面の URL入力ダイアログ検証中状態は、同画面の `rememberSaveable` ローカル状態として管理しなければならないMUST。URL検証中状態はタブセッション状態として扱ってはならないMUST NOT。

#### Scenario: BBSルート画面のURL入力で検証中表示を維持する
- **WHEN** ユーザーがBBSルート画面のURL入力ダイアログでURLを開く
- **THEN** システムはタブセッション状態に依存せず、`BbsRouteScaffold` の `rememberSaveable` 状態に基づいて検証中表示を行う

#### Scenario: タブ一覧画面のURL入力で検証中表示を維持する
- **WHEN** ユーザーがタブ一覧画面のURL入力ダイアログでURLを開く
- **THEN** システムは `TabListViewModel` の状態に基づいて検証中表示を行う

## MODIFIED Requirements

### Requirement: タブ一覧画面状態の単一収集
システムはタブ一覧画面で、タブセッション状態とタブ一覧画面固有状態を画面上位の Composable で収集しなければならないMUST。同じ画面ツリー内で同じタブセッション状態または同じ `TabListUiState` を不要に複数回収集してはならないMUST NOT。タブ一覧画面は `TabsViewModel` または `TabsUiState` を状態収集の契約として使用してはならないMUST NOT。子 Composable へは既存の受け渡し構造を保ったまま `TabSessionStore` を渡してよく、必要な値と操作だけへの分解は必須としない。

#### Scenario: 子 Composable がタブセッション操作を利用する
- **WHEN** タブ一覧画面を描画する
- **THEN** システムは `TabsPagerContent`、`OpenBoardsList`、`OpenThreadsList` が `TabsViewModel` に依存せず、必要に応じて上位から渡された `TabSessionStore` または状態値・操作を利用する

#### Scenario: lifecycle aware に状態を収集する
- **WHEN** タブ一覧画面が composition に入る
- **THEN** システムは画面上位で lifecycle aware な方法によりタブセッション状態と `TabListUiState` を収集する

#### Scenario: タブ一覧画面固有状態が画面ライフサイクルで初期化される
- **WHEN** ユーザーがタブ一覧画面を離れてから再度表示する
- **THEN** システムは検索、長押し選択、削除待ち、詳細 BottomSheet、URL入力ダイアログ状態をタブ一覧画面専用状態として初期化し、開いているタブと更新状態は維持する
