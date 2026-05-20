# handle-thread-link Specification

## Purpose
TBD - created by archiving change unify-url-routing. Update Purpose after archive.
## Requirements
### Requirement: スレ内リンクの判定を共通化する
システムはスレ内リンクをタップした際、共通URLリゾルバで判定しスレに該当する場合のみアプリ内遷移することを SHALL 要求する。スレに該当する場合、システムは永続化済みの `5ch.net` を `5ch.io` として開く設定値を取得し、その値に基づいてrouteを正規化してからタブ保証と画面遷移を行うことを SHALL 要求する。

#### Scenario: スレURLのリンクをタップする
- **WHEN** `https://{host}/test/read.cgi/{board}/{thread}/` のリンクをタップする
- **THEN** システムは永続化済み設定値に基づきrouteを正規化してから該当スレを表示する

#### Scenario: スレ内リンクを設定オフでタップする
- **WHEN** ユーザーが設定をオフにしており、`https://agree.5ch.net/test/read.cgi/operate/1234567890/` のリンクをタップする
- **THEN** システムは `https://agree.5ch.net/operate/` を保持したrouteで該当スレを表示する

#### Scenario: スレ内リンクを設定オンでタップする
- **WHEN** ユーザーが設定をオンにしており、`https://agree.5ch.net/test/read.cgi/operate/1234567890/` のリンクをタップする
- **THEN** システムは `https://agree.5ch.io/operate/` に正規化したrouteで該当スレを表示する

### Requirement: スレ内リンクの対象外処理
システムはスレ判定に一致しないリンクを外部ブラウザに委譲することを SHALL 要求する。

#### Scenario: スレ判定に一致しないリンクをタップする
- **WHEN** スレ判定に一致しないURLをタップする
- **THEN** システムは外部ブラウザを開く

#### Scenario: dat形式のリンクをタップする
- **WHEN** `https://{host}/{board}/dat/{thread}.dat` のリンクをタップする
- **THEN** システムは外部ブラウザを開く
