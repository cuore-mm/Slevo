## ADDED Requirements

### Requirement: DataStore writes shall be snapshotted before pending restore commit
システムは pending restore の DataStore write phase を開始する前に、write 対象 DataStore の現在値を snapshot しなければならない（MUST）。snapshot は settings/tabs/cookies JSON parse と Cookie pre-validation が成功した後、最初の DataStore write より前に取得しなければならない（MUST）。

#### Scenario: snapshot is captured before first DataStore write
- **WHEN** pending restore の settings/tabs parse と Cookie pre-validation が成功する
- **THEN** システムは settings/tabs DataStore の snapshot を取得する
- **AND** staged cookies が restore 対象の場合のみ cookies DataStore の snapshot を取得する
- **AND** snapshot 取得後に settings DataStore write を開始する

#### Scenario: parse failure does not snapshot or write
- **WHEN** pending restore の settings/tabs/cookies JSON parse が失敗する
- **THEN** システムは DataStore snapshot を取得しない
- **AND** システムは settings/tabs/cookies DataStore を変更しない

#### Scenario: cookie pre-validation failure does not snapshot or write
- **WHEN** Cookie pre-validation が失敗する
- **THEN** システムは DataStore snapshot を取得しない
- **AND** システムは settings/tabs/cookies DataStore を変更しない

### Requirement: DataStore write failure shall restore written stores from snapshot
システムは pending restore の DataStore write phase で例外が発生した場合、例外発生前に write 成功した DataStore を snapshot へ best-effort で戻さなければならない（MUST）。write が成功していない DataStore は rollback write 対象にしてはならない（MUST NOT）。

#### Scenario: tabs write failure rolls back settings only
- **WHEN** settings DataStore write が成功し、tabs DataStore write が失敗する
- **THEN** システムは settings DataStore を snapshot へ戻す
- **AND** システムは tabs DataStore を rollback write しない
- **AND** システムは cookies DataStore を rollback write しない

#### Scenario: cookie write failure rolls back settings and tabs
- **WHEN** settings DataStore write と tabs DataStore write が成功し、cookies DataStore write が失敗する
- **THEN** システムは settings DataStore を snapshot へ戻す
- **AND** システムは tabs DataStore を snapshot へ戻す
- **AND** システムは cookies DataStore を rollback write しない

#### Scenario: settings write failure does not rollback untouched stores
- **WHEN** settings DataStore write が失敗する
- **THEN** システムは settings/tabs/cookies DataStore を rollback write しない
- **AND** システムは既存の pending restore failure flow に委譲する

#### Scenario: successful write phase does not rollback
- **WHEN** settings/tabs/cookies の対象 DataStore write がすべて成功する
- **THEN** システムは DataStore rollback を実行しない
- **AND** システムは従来どおり pending restore を success flow に進める

### Requirement: Cookie DataStore shall be excluded when cookies are not restored
システムは Cookie 復元が対象外の場合、cookies DataStore を snapshot / write / rollback 対象にしてはならない（MUST NOT）。Cookie 復元が対象外とは、restore marker の `includeCookies` が false、または staged `cookies.json` が存在しない状態を指す。

#### Scenario: includeCookies false excludes cookies snapshot and rollback
- **WHEN** restore marker の `includeCookies` が false で、settings write 成功後に tabs write が失敗する
- **THEN** システムは cookies DataStore の snapshot を取得しない
- **AND** システムは cookies DataStore を rollback write しない

#### Scenario: cookies file absent excludes cookies snapshot and rollback
- **WHEN** restore marker の `includeCookies` が true だが staged `cookies.json` が存在せず、settings write 成功後に tabs write が失敗する
- **THEN** システムは cookies DataStore の snapshot を取得しない
- **AND** システムは cookies DataStore を rollback write しない

### Requirement: DataStore rollback failure shall preserve original restore failure
システムは DataStore rollback が失敗した場合でも、元の DataStore write failure を restore failure として扱わなければならない（MUST）。rollback failure は元の failure を置き換えてはならず（MUST NOT）、DB rollback / marker/result file の既存 flow を継続しなければならない（MUST）。

#### Scenario: rollback failure keeps original write error
- **WHEN** tabs DataStore write が失敗し、その後の settings DataStore rollback も失敗する
- **THEN** DataStore reflector は元の tabs write failure に基づく error を返す
- **AND** `PendingRestoreApplier` は既存の DB rollback / failure result flow を実行する

#### Scenario: rollback failure is diagnostic only
- **WHEN** DataStore rollback 中に例外が発生する
- **THEN** システムは rollback failure を diagnostic/log として扱う
- **AND** ユーザー向け restore result は詳細 stack trace を表示しない既存方針を維持する

### Requirement: DataStore rollback shall use DataStore APIs only
システムは pending restore の DataStore rollback で AndroidX DataStore API を使用しなければならない（MUST）。DataStore の `.preferences_pb` file を直接 copy / rename / delete してはならない（MUST NOT）。

#### Scenario: rollback uses shared DataStore provider
- **WHEN** システムが settings/tabs/cookies DataStore の snapshot または rollback を行う
- **THEN** システムは `SlevoPreferenceDataStores.settings(context)`、`tabs(context)`、`cookies(context)` から DataStore instance を取得する
- **AND** システムは `PreferenceDataStoreFactory.create(...)` を pending restore rollback 用に直接呼ばない

#### Scenario: rollback restores snapshot as full overwrite
- **WHEN** システムが DataStore を snapshot へ rollback する
- **THEN** システムは現在の preferences を clear し、snapshot に含まれる key/value だけを保存する
- **AND** snapshot に存在しない key は rollback 後の DataStore に残らない
