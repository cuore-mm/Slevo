## MODIFIED Requirements

### Requirement: Deep Link の受付と正規化
システムはアプリ内部で対応想定しているURLパターンに一致する http/https の Deep Link のみ受け付け、スキームの正規化を行わずに解析することを SHALL 要求する。対象は PC 版/itest 版の板・スレ 4 種とし、`2ch.sc` ドメインでは itest パターンを許可しない。

- PC版・板: `https://{server}.{domain}/{board}/`
- PC版・スレ: `https://{server}.{domain}/test/read.cgi/{board}/{threadKey}/[option]`
- itest版・板: `https://itest.{domain}/subback/{board}`
- itest版・スレ: `https://itest.{domain}/{server}/test/read.cgi/{board}/{threadKey}/[option]`
- `{domain}` は `5ch.net` / `5ch.io` / `bbspink.com` / `2ch.sc` を想定するが、itest は `5ch.net` / `5ch.io` / `bbspink.com` のみを許可する

#### Scenario: http のDeep Linkを受け付ける
- **WHEN** ユーザーが対象パターンに一致する http の Deep Link を開く
- **THEN** システムはスキームを変更せずに解析を継続する

#### Scenario: 5ch.io のDeep Linkを受け付ける
- **WHEN** ユーザーが `https://agree.5ch.io/test/read.cgi/operate/1234567890/` の Deep Link を開く
- **THEN** システムはDeep Linkを解析対象として扱う

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

#### Scenario: 5ch.io のPC版スレDeep Linkを開く
- **WHEN** `https://agree.5ch.io/test/read.cgi/operate/1234567890/` の Deep Link を開く
- **THEN** システムは `agree.5ch.io` の `operate` 板にある `1234567890` スレを表示する

#### Scenario: 5ch.io のitest版スレDeep Linkを開く
- **WHEN** `https://itest.5ch.io/agree/test/read.cgi/operate/1234567890/` の Deep Link を開く
- **THEN** システムは `agree.5ch.io` の `operate` 板にある `1234567890` スレを表示する

## ADDED Requirements

### Requirement: Deep Linkで5ch.netを5ch.ioとして開く
システムは全般設定の `5ch.net` を `5ch.io` として開く設定がオンの場合、Deep Linkで開く `5ch.net` の板/スレの `boardUrl` を `5ch.io` に正規化することを SHALL 要求する。

#### Scenario: Deep Linkで5ch.net板URLを5ch.ioとして開く
- **WHEN** 設定がオンの状態で `https://agree.5ch.net/operate/` の Deep Link を開く
- **THEN** システムは `https://agree.5ch.io/operate/` を板URLとして表示する

#### Scenario: Deep Linkで5ch.netスレURLを5ch.ioとして開く
- **WHEN** 設定がオンの状態で `https://agree.5ch.net/test/read.cgi/operate/1234567890/` の Deep Link を開く
- **THEN** システムは `https://agree.5ch.io/operate/` を板URLとして `1234567890` スレを表示する
