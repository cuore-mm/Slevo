## ADDED Requirements

### Requirement: スクロール位置保存ロジックの検証可能性
システムはタブ固有スクロール位置の保存処理を、画面全体のレイアウト構築に依存せず検証できる単位として構成しなければならないMUST。保存処理は、重複保存抑制、連続更新中の周期保存、非アクティブ化時保存、破棄時保存を個別に検証できなければならないMUST。

#### Scenario: 重複保存抑制を検証する
- **WHEN** 同じ `firstVisibleItemIndex` と `firstVisibleItemScrollOffset` の組み合わせが連続して保存対象になる
- **THEN** システムは2回目以降の同一位置保存を抑制する判定を単体テストで確認できる

#### Scenario: 連続更新中の周期保存を検証する
- **WHEN** スクロール位置が停止せず連続して更新される
- **THEN** システムは一定間隔ごとに最新のスクロール位置が保存対象になることを Flow テストで確認できる

#### Scenario: 非アクティブ化時保存を検証する
- **WHEN** アクティブだったタブが非アクティブへ遷移する
- **THEN** システムは遷移時点のスクロール位置が保存されることを Compose test で確認できる

#### Scenario: 破棄時保存を検証する
- **WHEN** スクロール位置保存を担う Composable が composition から外れる
- **THEN** システムは破棄時点のスクロール位置が保存されることを Compose test で確認できる

### Requirement: スクロール位置保存の既存挙動維持
システムはスクロール位置保存ロジックを分離した後も、既存の保存データ形式、保存タイミング、保存コールバックの意味を維持しなければならないMUST。

#### Scenario: BbsRouteScaffold から保存処理を呼び出す
- **WHEN** `BbsRouteScaffold` がタブページを表示する
- **THEN** システムは分離したスクロール位置保存 Composable を通じて、従来と同じ index / offset を保存コールバックへ渡す

#### Scenario: 保存データ形式を変更しない
- **WHEN** スクロール位置保存ロジックを分離する
- **THEN** システムは保存先へ渡す `firstVisibleItemIndex` と `firstVisibleItemScrollOffset` の値の意味を変更しない
