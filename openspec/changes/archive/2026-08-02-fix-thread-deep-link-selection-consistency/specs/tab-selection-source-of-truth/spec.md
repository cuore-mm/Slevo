## MODIFIED Requirements

### Requirement: Pager index を selected key から導出する
システムは Pager の表示 index を、タブ一覧と選択中 stable key から導出しなければならないMUST。永続的な表示状態の正本として page index を使用してはならないMUST NOT。`currentPage` は永続化してはならず、復元時の fallback としても使用してはならないMUST NOT。スレッド Deep Link target の pending または一時的不在により selected key を一覧から解決できない場合、Pager は page 0 へ fallback せず現在の表示 page を維持しなければならないMUST。

#### Scenario: selected key に一致するタブを表示する
- **WHEN** 選択中タブ key に一致するタブが開いているタブ一覧に存在する
- **THEN** システムはそのタブの index を Pager の表示対象として使用する

#### Scenario: pending target が canonical 一覧に存在しない
- **WHEN** スレッド Deep Link target の登録または canonical confirmation が未完了で、target が開いているタブ一覧にまだ存在しない
- **THEN** システムは現在の selected key と Pager page を維持し、target または page 0 を表示対象として選択しない

#### Scenario: selected key が一時的に解決できない
- **WHEN** pending operation または Flow reconciliation の途中で selected key に一致するスレッドタブを一時的に一覧から解決できない
- **THEN** システムは programmatic page scroll を行わず現在 page を維持し、Pager 自身で page 0 へ fallback しない

#### Scenario: タブ削除が確定する
- **WHEN** 選択中タブの削除が canonical state で確定する
- **THEN** システムは coordinator の補正規則に基づき隣接タブ、先頭タブ、またはタブなしを selected key として明示し、Pager はその補正済み key から表示対象を導出する

#### Scenario: 復元時に currentPage を使用しない
- **WHEN** アプリ復元時に過去の page index 情報が存在する
- **THEN** システムは page index を表示タブの正本や fallback として使用せず、selected key と coordinator の補正規則だけで表示タブを決定する
