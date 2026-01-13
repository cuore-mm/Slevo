## ADDED Requirements
### Requirement: Deep Link の受付と正規化
システムは以下のホストに一致するhttp/httpsのDeep Linkを受け付け、httpはhttpsに正規化して処理することを SHALL 要求する。

- *.bbspink.com
- *.5ch.net
- *.2ch.sc

#### Scenario: http のDeep Linkを受け付ける
- **WHEN** ユーザーがhttpのDeep Linkを開く
- **THEN** システムはhttpsに正規化したURLで処理を継続する

### Requirement: Deep Link の板/スレ遷移
システムはアプリ内のURL入力およびスレ内リンク遷移と同等の解析規則に従い、Deep Linkを板またはスレに解決して該当画面へ遷移することを SHALL 要求する。

#### Scenario: スレURLを開く
- **WHEN** https://{host}/test/read.cgi/{board}/{thread}/ のDeep Linkを開く
- **THEN** システムは該当スレを表示する

#### Scenario: itestの板URLを開く
- **WHEN** https://itest.5ch.net/{board}/ のDeep Linkを開く
- **THEN** システムは該当板を表示する

### Requirement: 未対応URLの通知
システムはdat形式や解析不能なDeep Linkを受け取った場合、エラートーストで通知することを SHALL 要求する。

#### Scenario: dat形式のURLを開く
- **WHEN** https://{host}/{board}/dat/{thread}.dat のDeep Linkを開く
- **THEN** システムはエラートーストを表示する
