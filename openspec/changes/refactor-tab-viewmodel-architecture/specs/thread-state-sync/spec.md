## ADDED Requirements

### Requirement: ViewModel とスレッド状態正本の分離
システムはスレッドの客観状態、履歴紐づき既読状態、タブ固有状態を、タブ単位の `ThreadViewModel` インスタンスの長期保持状態として重複管理してはならないMUST NOT。タブ単位の `ThreadViewModel` は廃止し、`ThreadRouteViewModel` がこれらの正本を購読して表示用 `ThreadUiState` を合成しなければならないMUST。

#### Scenario: 新着情報を表示する
- **WHEN** スレッド画面が新着レス数、最初の新着レス番号、最終既読レス番号を表示する
- **THEN** システムは共通客観状態、履歴紐づき既読状態、タブ固有状態から値を合成し、タブ単位 `ThreadViewModel` 独自の正本値から導出しない

#### Scenario: スレッドタブを閉じる
- **WHEN** ユーザーがスレッドタブを閉じる
- **THEN** システムは対象タブのタブ固有状態を削除し、共通客観状態と履歴紐づき既読状態を route ViewModel の生存有無に依存せず維持する

#### Scenario: ThreadRouteViewModel が再作成される
- **WHEN** 構成変更または画面 route の再作成により `ThreadRouteViewModel` が再作成される
- **THEN** システムは永続化されたスレッド状態とタブセッション状態から `ThreadUiState` を再合成する
