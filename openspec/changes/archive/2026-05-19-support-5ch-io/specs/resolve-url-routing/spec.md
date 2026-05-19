## MODIFIED Requirements

### Requirement: 5ch入力URLパターンの解析
システムは `docs/external/5ch.md` に定義された入力URLパターン A〜D を解析できることを SHALL 要求する。対象ドメインには `5ch.net` と `5ch.io` を含めることを SHALL 要求する。

#### Scenario: PC版板URLを解析する
- **WHEN** `https://<server>.5ch.net/<board>/` を解析する
- **THEN** `Board` 種別として `host`（`<server>.5ch.net`）と `boardKey` を返す

#### Scenario: PC版スレURLを解析する
- **WHEN** `https://<server>.5ch.net/test/read.cgi/<board>/<threadKey>/` を解析する
- **THEN** `Thread` 種別として `host`（`<server>.5ch.net`）/ `boardKey` / `threadKey` を返す

#### Scenario: itest版板URLを解析する
- **WHEN** `https://itest.5ch.net/subback/<board>` を解析する
- **THEN** `ItestBoard` 種別として `boardKey` を返し、`host` は未解決である

#### Scenario: itest版スレURLを解析する
- **WHEN** `https://itest.5ch.net/<server>/test/read.cgi/<board>/<threadKey>/` を解析する
- **THEN** `Thread` 種別として `host`（`<server>.<domain>`）/ `boardKey` / `threadKey` を返す

#### Scenario: 5ch.io のPC版板URLを解析する
- **WHEN** `https://<server>.5ch.io/<board>/` を解析する
- **THEN** `Board` 種別として `host`（`<server>.5ch.io`）と `boardKey` を返す

#### Scenario: 5ch.io のPC版スレURLを解析する
- **WHEN** `https://<server>.5ch.io/test/read.cgi/<board>/<threadKey>/` を解析する
- **THEN** `Thread` 種別として `host`（`<server>.5ch.io`）/ `boardKey` / `threadKey` を返す

#### Scenario: 5ch.io のitest版板URLを解析する
- **WHEN** `https://itest.5ch.io/subback/<board>` を解析する
- **THEN** `ItestBoard` 種別として `boardKey` を返し、`host` は未解決である

#### Scenario: 5ch.io のitest版スレURLを解析する
- **WHEN** `https://itest.5ch.io/<server>/test/read.cgi/<board>/<threadKey>/` を解析する
- **THEN** `Thread` 種別として `host`（`<server>.5ch.io`）/ `boardKey` / `threadKey` を返す
