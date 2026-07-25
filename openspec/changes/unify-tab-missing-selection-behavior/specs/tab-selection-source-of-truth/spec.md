## ADDED Requirements

### Requirement: 選択欠落の原因を共有状態で表す
システムは板/スレッドの loaded tab 一覧と選択解決結果を一つの整合した状態として公開しなければならないMUST。選択解決結果は初回読込中、有効な selected key、既知の pending cause による一時的不在、または 0 tab を区別しなければならないMUST。画面種別だけを根拠に欠落時の fallback を選んではならないMUST NOT。

#### Scenario: 有効な selected key を公開する
- **WHEN** selected key に一致する tab が同じ loaded 一覧に存在する
- **THEN** システムはその key を有効な選択として一覧と同じ状態で公開する

#### Scenario: 既知の pending cause により selected key が一時的に存在しない
- **WHEN** pending operation、Deep Link 登録、または canonical Flow reconciliation の未完了が selected key の不在を説明している
- **THEN** システムは selected key を書き換えず、一時的不在として公開する
- **AND** pending cause の存在しない欠落を一時的不在として推測しない

#### Scenario: 初回 canonical 読込が完了していない
- **WHEN** coordinator が最初の canonical tab 一覧をまだ確定していない
- **THEN** システムは選択を初回読込中として公開し、null key を確定無効または 0 tab とみなさない

#### Scenario: tab が 0 件である
- **WHEN** canonical 読込が完了し、開いている tab が 0 件である
- **THEN** システムは選択 key を null にし、0 tab 状態を公開する

### Requirement: 確定無効 selection を coordinator が補正する
システムは loaded な非空 tab 一覧で selected key が存在せず、かつその不在を説明する pending cause がない場合、共有 UI に状態を公開する前に coordinator が selected key を有効な key へ補正しなければならないMUST。UI は確定無効 selection を page 0 表示だけで補ってはならないMUST NOT。

#### Scenario: 復元した selected key が確定無効である
- **WHEN** 初回 loaded 一覧が非空で、復元した selected key が一覧に存在せず pending cause もない
- **THEN** coordinator は一覧の先頭 key を selected key に設定する
- **AND** UI は補正済み key に対応する tab content を表示する

#### Scenario: selected key が null のまま非空一覧を読み込む
- **WHEN** 初回 loaded 一覧が非空で selected key が null である
- **THEN** coordinator は一覧の先頭 key を selected key に設定してから loaded selection を公開する
- **AND** UI は blank content を表示しない

#### Scenario: 選択中 tab の close が確定する
- **WHEN** 選択中 tab が削除され、削除後も tab が残る
- **THEN** coordinator は削除前 index と同じ位置の tab を選択し、その位置が範囲外なら末尾 tab を選択する

#### Scenario: 選択中でない tab の close が確定する
- **WHEN** 選択中でない tab が削除され、現在の selected key が削除後一覧にも存在する
- **THEN** coordinator は現在の selected key を維持する

#### Scenario: 最後の tab の close が確定する
- **WHEN** 選択中の最後の tab が削除される
- **THEN** coordinator は selected key を null にし、0 tab 状態を公開する

## MODIFIED Requirements

### Requirement: Pager index を selected key から導出する
システムは Pager の表示 index を、整合した tab 一覧と選択解決結果から導出しなければならないMUST。永続的な表示状態の正本として page index を使用してはならないMUST NOT。`currentPage` は永続化してはならず、復元時の fallback としても使用してはならないMUST NOT。板/スレッドのどちらでも、有効な selected key は対応 tab を表示し、既知の pending cause による一時的不在は programmatic page 移動と選択 key の書換えを行わず現在の表示 tab を維持し、確定無効 selection は coordinator の補正済み key から表示対象を導出しなければならないMUST。

#### Scenario: selected key に一致する tab を表示する
- **WHEN** 有効な selected key に一致する tab が loaded 一覧に存在する
- **THEN** システムはその tab の index を Pager の表示対象として使用する
- **AND** その tab の content、bottom bar、および既存 sheet content を構成する

#### Scenario: selected key が一時的に解決できない
- **WHEN** selected key の不在を説明する pending cause が存在する
- **THEN** Pager は programmatic page scroll を行わず現在の表示 tab を維持する
- **AND** 現在 page の tab content を継続表示し、blank content または暗黙の page 0 fallback を表示しない
- **AND** Pager 同期は selected key を現在 page の key へ書き換えない

#### Scenario: pending target が確認される
- **WHEN** pending cause が完了し、selected key に一致する tab が loaded 一覧に現れる
- **THEN** システムはその key を有効な選択として公開し、Pager を対応 index へ同期する

#### Scenario: pending target が失敗または cancel される
- **WHEN** pending cause が失敗または cancel され、selected key が loaded 一覧に存在しない
- **THEN** coordinator は pending 状態を解除し、隣接 tab、先頭 tab、または 0 tab の規則で選択を確定する
- **AND** FIFO worker、DB-canonical state、および既存 cancellation 境界を維持する

#### Scenario: 板の初期読込または復元を表示する
- **WHEN** 板 tab の初回 canonical 読込が非空一覧として完了する
- **THEN** システムは有効な selected key に対応する tab を最初の content として表示する
- **AND** selected key が未確定のまま page 0 content または blank content を表示しない

#### Scenario: 板 Deep Link target を表示する
- **WHEN** 板 Deep Link target の登録と選択が確認される
- **THEN** navigation 後の Pager は target key に対応する tab を表示する
- **AND** 登録または選択が失敗した場合は既存の有効な selection と表示 tab を維持する

#### Scenario: tab が 0 件である
- **WHEN** loaded tab 一覧が 0 件で選択解決結果も 0 tab である
- **THEN** システムは Pager の tab content を構成せず、既存の empty navigation 処理を行う

#### Scenario: 復元時に currentPage を使用しない
- **WHEN** アプリ復元時に過去の page index 情報が存在する
- **THEN** システムは page index を表示 tab の正本や fallback として使用せず、selected key、pending cause、および coordinator の補正規則だけで表示 tab を決定する
