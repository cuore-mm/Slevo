# handle-deep-link Specification

## Purpose
TBD - created by archiving change add-bbs-deep-link. Update Purpose after archive.
## Requirements
### Requirement: Deep Link の受付と正規化
システムはアプリ内部で対応想定しているURLパターンに一致する http/https の Deep Link のみ受け付け、スキームの正規化を行わずに解析することを SHALL 要求する。対象は PC 版/itest 版の板・スレ 4 種とし、`2ch.sc` ドメインでは itest パターンを許可しない。

- PC版・板: `https://{server}.{domain}/{board}/`
- PC版・スレ: `https://{server}.{domain}/test/read.cgi/{board}/{threadKey}/[option]`
- itest版・板: `https://itest.{domain}/subback/{board}`
- itest版・スレ: `https://itest.{domain}/{server}/test/read.cgi/{board}/{threadKey}/[option]`
- `{domain}` は `5ch.net` / `bbspink.com` / `2ch.sc` を想定するが、itest は `5ch.net` / `bbspink.com` のみを許可する

#### Scenario: http のDeep Linkを受け付ける
- **WHEN** ユーザーが対象パターンに一致する http の Deep Link を開く
- **THEN** システムはスキームを変更せずに解析を継続する

### Requirement: Deep Link の板/スレ遷移
システムは共通URLリゾルバを用いて Deep Link を解析し、板またはスレに解決できる場合のみ遷移することを SHALL 要求する。

#### Scenario: PC版のスレURLを開く
- **WHEN** `https://{host}/test/read.cgi/{board}/{thread}/` の Deep Link を開く
- **THEN** システムは該当スレを表示する

#### Scenario: itestの板URLを開く
- **WHEN** `https://itest.{domain}/subback/{board}` の Deep Link を開く
- **THEN** システムは板ホストを解決し該当板を表示する

#### Scenario: itestのスレURLを開く
- **WHEN** `https://itest.{domain}/{server}/test/read.cgi/{board}/{thread}/` の Deep Link を開く
- **THEN** システムは該当スレを表示する

### Requirement: 未対応URLの通知
システムは許可ドメイン外、対象パターン外、または itest 非対応ドメインの Deep Link を受け取った場合、遷移を行わずエラートーストで通知することを SHALL 要求する。

#### Scenario: dat形式のURLを開く
- **WHEN** `https://{host}/{board}/dat/{thread}.dat` の Deep Link を開く
- **THEN** システムはエラートーストを表示する

#### Scenario: 2ch.sc の itest URL を開く
- **WHEN** `https://itest.2ch.sc/subback/{board}` の Deep Link を開く
- **THEN** システムはエラートーストを表示する

