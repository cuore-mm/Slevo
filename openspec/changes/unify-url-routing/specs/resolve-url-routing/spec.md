## ADDED Requirements

### Requirement: 共通URLリゾルバの提供
システムはURL文字列を解析し、板/スレの種別と必要な識別子を返す共通リゾルバを提供することを SHALL 要求する。

#### Scenario: 解析結果を構造化して返す
- **WHEN** URLリゾルバがURLを解析する
- **THEN** 種別（Board/ItestBoard/Thread/Unknown）と `server` / `boardKey` / `threadKey` を含む結果を返す

### Requirement: 5ch入力URLパターンの解析
システムは `docs/external/5ch.md` に定義された入力URLパターン A〜D を解析できることを SHALL 要求する。

#### Scenario: PC版板URLを解析する
- **WHEN** `https://<server>.5ch.net/<board>/` を解析する
- **THEN** `Board` 種別として `server` と `boardKey` を返す

#### Scenario: PC版スレURLを解析する
- **WHEN** `https://<server>.5ch.net/test/read.cgi/<board>/<threadKey>/` を解析する
- **THEN** `Thread` 種別として `server` / `boardKey` / `threadKey` を返す

#### Scenario: itest版板URLを解析する
- **WHEN** `https://itest.5ch.net/subback/<board>` を解析する
- **THEN** `ItestBoard` 種別として `boardKey` を返し、`server` は未解決である

#### Scenario: itest版スレURLを解析する
- **WHEN** `https://itest.5ch.net/<server>/test/read.cgi/<board>/<threadKey>/` を解析する
- **THEN** `Thread` 種別として `server` / `boardKey` / `threadKey` を返す

### Requirement: 未対応URLの判定
システムは入力パターン A〜D に一致しないURLを `Unknown` として扱うことを SHALL 要求する。

#### Scenario: dat形式を解析する
- **WHEN** `https://<server>.5ch.net/<board>/dat/<threadKey>.dat` を解析する
- **THEN** `Unknown` として返す

#### Scenario: oyster形式を解析する
- **WHEN** `https://<server>.5ch.net/<board>/oyster/<prefix>/<threadKey>.dat` を解析する
- **THEN** `Unknown` として返す
