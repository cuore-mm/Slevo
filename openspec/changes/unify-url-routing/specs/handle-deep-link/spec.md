## MODIFIED Requirements

### Requirement: Deep Link の板/スレ遷移
システムは共通URLリゾルバを用いてDeep Linkを解析し、板またはスレに解決できる場合のみ遷移することを SHALL 要求する。

#### Scenario: スレURLを開く
- **WHEN** `https://{host}/test/read.cgi/{board}/{thread}/` のDeep Linkを開く
- **THEN** システムは該当スレを表示する

#### Scenario: itestの板URLを開く
- **WHEN** `https://itest.5ch.net/subback/{board}` のDeep Linkを開く
- **THEN** システムは板ホストを解決し該当板を表示する

### Requirement: Deep Link の対象外判定
システムは許可ドメイン外、または入力パターンに一致しないDeep Linkを受け取った場合、遷移を行わずエラートーストで通知することを SHALL 要求する。

#### Scenario: dat形式のURLを開く
- **WHEN** `https://{host}/{board}/dat/{thread}.dat` のDeep Linkを開く
- **THEN** システムはエラートーストを表示する
