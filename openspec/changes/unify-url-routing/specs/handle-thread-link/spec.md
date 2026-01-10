## ADDED Requirements

### Requirement: スレ内リンクの判定を共通化する
システムはスレ内リンクをタップした際、共通URLリゾルバで判定しスレに該当する場合のみアプリ内遷移することを SHALL 要求する。

#### Scenario: スレURLのリンクをタップする
- **WHEN** `https://{host}/test/read.cgi/{board}/{thread}/` のリンクをタップする
- **THEN** システムは該当スレを表示する

### Requirement: スレ内リンクの対象外処理
システムはスレ判定に一致しないリンクを外部ブラウザに委譲することを SHALL 要求する。

#### Scenario: スレ判定に一致しないリンクをタップする
- **WHEN** スレ判定に一致しないURLをタップする
- **THEN** システムは外部ブラウザを開く

#### Scenario: dat形式のリンクをタップする
- **WHEN** `https://{host}/{board}/dat/{thread}.dat` のリンクをタップする
- **THEN** システムは外部ブラウザを開く
