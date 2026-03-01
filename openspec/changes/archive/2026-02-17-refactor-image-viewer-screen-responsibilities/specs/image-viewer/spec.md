## ADDED Requirements

### Requirement: 画像ビューア画面責務の分離構成
システムは `ImageViewerScreen` の実装において、画面オーケストレーション、表示構築、副作用処理の責務を分離した構成を維持しなければならないMUST。画面表示に関わる state は `UiState` として `ViewModel` が所有しなければならないMUST。Compose ランタイム依存オブジェクトは UI ローカルに限定して保持してよいMAY。責務分離後も画像ビューアの既存機能契約を変更してはならないMUST。

#### Scenario: 画面組み立てと副作用処理が分離されている
- **WHEN** 開発者が画像ビューア画面の実装を確認する
- **THEN** `ImageViewerScreen` は画面の組み立てを主責務とし、副作用処理は専用の補助Composableまたは補助関数へ分離されている

#### Scenario: 責務分離後も既存の操作契約が維持される
- **WHEN** ユーザーが画像ビューアで画像切替、バー表示切替、保存操作、メニュー操作を行う
- **THEN** システムは分割前と同一の結果を返し、機能上の振る舞いを変更しない

#### Scenario: 画面表示 state が UiState で管理される
- **WHEN** 開発者が画像ビューア画面の状態管理実装を確認する
- **THEN** 画面表示を決める state は `UiState` で定義され `ViewModel` が管理し、`PagerState` など Compose ランタイム依存オブジェクトのみ UI ローカルで保持される
