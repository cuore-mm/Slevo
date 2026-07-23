## ADDED Requirements

### Requirement: Cookie restore pre-validation
システムは pending restore の DataStore 反映前に、復元対象 Cookie を全件変換・serialize できることを検証しなければならない（MUST）。

#### Scenario: valid cookies are prepared before write
- **WHEN** staged `cookies.json` に valid な Cookie item のみが含まれる
- **THEN** システムは DataStore 書き込み前に全 Cookie を DataStore 保存用文字列 set へ変換する

#### Scenario: one invalid cookie prevents pre-validation
- **WHEN** staged `cookies.json` に `BackupRestoreMapper.toCookie()` が `null` を返す Cookie item が 1 件以上含まれる
- **THEN** システムは Cookie pre-validation を失敗として扱い、DataStore 書き込みを開始しない

#### Scenario: serialization failure prevents pre-validation
- **WHEN** Cookie item から OkHttp `Cookie` への変換後、DataStore 保存文字列への serialize が 1 件以上失敗する
- **THEN** システムは Cookie pre-validation を失敗として扱い、DataStore 書き込みを開始しない

#### Scenario: empty cookie list is valid
- **WHEN** staged `cookies.json` の Cookie list が空である
- **THEN** システムは Cookie pre-validation を成功として扱い、空の cookie set を prepared result とする

### Requirement: DataStore writes start only after parse and pre-validation
システムは pending restore の DataStore 反映で、settings/tabs/cookies の parse と Cookie pre-validation が成功するまで DataStore を変更してはならない（MUST）。

#### Scenario: cookie parse failure leaves DataStore untouched
- **WHEN** settings/tabs parse は成功するが staged `cookies.json` の parse が失敗する
- **THEN** システムは settings/tabs/cookies DataStore を変更せず error を返す

#### Scenario: cookie pre-validation failure leaves DataStore untouched
- **WHEN** settings/tabs/cookies parse は成功するが Cookie pre-validation が失敗する
- **THEN** システムは settings/tabs/cookies DataStore を変更せず error を返す

#### Scenario: no cookies requested writes settings and tabs
- **WHEN** restore marker の `includeCookies` が false で settings/tabs parse が成功する
- **THEN** システムは Cookie pre-validation を行わず、settings と tabs DataStore を書き込む

#### Scenario: cookies requested but file absent writes settings and tabs only
- **WHEN** restore marker の `includeCookies` が true だが staged `cookies.json` が存在せず、settings/tabs parse が成功する
- **THEN** システムは Cookie pre-validation を行わず、settings と tabs DataStore を書き込む

### Requirement: Prepared cookie commit
システムは Cookie pre-validation 成功後、検証済み cookie set のみを cookies DataStore に書き込まなければならない（MUST）。

#### Scenario: prepared cookies are committed after settings and tabs
- **WHEN** settings/tabs/cookies parse と Cookie pre-validation がすべて成功する
- **THEN** システムは settings、tabs、prepared cookies の順で DataStore を書き込む

#### Scenario: prepared cookie write propagates I/O failure
- **WHEN** prepared cookies の DataStore write 中に I/O exception が発生する
- **THEN** システムは従来どおり error を返し、pending restore の failure/rollback flow に委譲する

### Requirement: Restore state machine compatibility
システムは Cookie pre-validation failure を既存の pending restore failure path として扱わなければならない（MUST）。

#### Scenario: pre-validation failure returns reflector error
- **WHEN** Cookie pre-validation が失敗する
- **THEN** DataStore reflector は error string を返し、`PendingRestoreApplier` は既存の rollback/failure result flow を実行する

#### Scenario: successful pre-validation preserves existing success flow
- **WHEN** DB swap、DataStore parse、Cookie pre-validation、DataStore write がすべて成功する
- **THEN** システムは従来どおり marker を `MIGRATION_PENDING` に進め、post-migration validation に委譲する
