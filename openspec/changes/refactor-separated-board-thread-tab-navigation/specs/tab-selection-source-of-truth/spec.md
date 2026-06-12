## ADDED Requirements

### Requirement: 選択中タブを stable key で管理する
システムは選択中の板タブとスレッドタブを、page index ではなく stable key で管理しなければならないMUST。板タブの key は正規化済み boardUrl、スレッドタブの key は ThreadId 相当の一意識別子でなければならないMUST。

#### Scenario: 板タブを選択する
- **WHEN** ユーザーが板タブを選択する
- **THEN** システムは選択中板タブ key を対象板の正規化済み boardUrl に更新する

#### Scenario: スレッドタブを選択する
- **WHEN** ユーザーがスレッドタブを選択する
- **THEN** システムは選択中スレッドタブ key を対象スレッドの一意な ThreadId に更新する

### Requirement: Pager index を selected key から導出する
システムは Pager の表示 index を、タブ一覧と選択中 stable key から導出しなければならないMUST。永続的な表示状態の正本として page index を使用してはならないMUST NOT。

#### Scenario: selected key に一致するタブを表示する
- **WHEN** 選択中タブ key に一致するタブが開いているタブ一覧に存在する
- **THEN** システムはそのタブの index を Pager の表示対象として使用する

#### Scenario: selected key が存在しない
- **WHEN** 選択中タブ key に一致するタブが削除または未復元で存在しない
- **THEN** システムは coordinator の補正規則に基づき、隣接タブまたは先頭タブを選択中 key として設定する

### Requirement: Pager 操作は selected key に反映する
システムはユーザーが Pager を操作して表示ページを変更した場合、表示ページに対応するタブの stable key を選択中 key として反映しなければならないMUST。

#### Scenario: 板 Pager をスワイプする
- **WHEN** ユーザーが板画面の Pager をスワイプして別の板タブを表示する
- **THEN** システムは表示中板タブの boardUrl を選択中板タブ key に設定する

#### Scenario: スレッド Pager をスワイプする
- **WHEN** ユーザーがスレッド画面の Pager をスワイプして別のスレッドタブを表示する
- **THEN** システムは表示中スレッドタブの ThreadId を選択中スレッドタブ key に設定する
