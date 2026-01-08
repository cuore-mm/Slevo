# handle-deep-link Specification

## Purpose
Deep Link、URL入力、スレ内リンクのURL解析と遷移の現行挙動を定義する。
## Requirements
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

### Requirement: Deep Link の対象外判定
システムは許可ドメイン外、または解析不能なDeep Linkを受け取った場合、遷移を行わずエラートーストで通知することを SHALL 要求する。

#### Scenario: 許可ドメイン外のDeep Linkを開く
- **WHEN** 許可されていないドメインのDeep Linkを開く
- **THEN** システムはエラートーストを表示する

#### Scenario: itest以外のURLが解析不能な場合
- **WHEN** 許可ドメインのDeep Linkが板/スレ判定に一致しない
- **THEN** システムはエラートーストを表示する

### Requirement: 未対応URLの通知
システムはdat形式や解析不能なDeep Linkを受け取った場合、エラートーストで通知することを SHALL 要求する。

#### Scenario: dat形式のURLを開く
- **WHEN** https://{host}/{board}/dat/{thread}.dat のDeep Linkを開く
- **THEN** システムはエラートーストを表示する

### Requirement: URL入力の判定順序
システムはURL入力時に、itest判定 → スレ判定 → 板判定の順で処理することを SHALL 要求する。

#### Scenario: itest URLを入力する
- **WHEN** https://itest.5ch.net/{board}/ のURLを入力する
- **THEN** システムはitest解析を優先して板を開く

#### Scenario: スレURLを入力する
- **WHEN** https://{host}/test/read.cgi/{board}/{thread}/ のURLを入力する
- **THEN** システムは該当スレを表示する

#### Scenario: 板URLを入力する
- **WHEN** https://{host}/{board}/ のURLを入力する
- **THEN** システムは該当板を表示する

#### Scenario: 解析不能なURLを入力する
- **WHEN** itest/スレ/板の判定がすべて失敗するURLを入力する
- **THEN** システムはURL入力ダイアログ内にエラーを表示する

### Requirement: itest URLのホスト解決
システムはitest URLを受け付けた場合、板キーからホスト解決を行い、解決に失敗した場合はエラーとして扱うことを SHALL 要求する。

#### Scenario: URL入力でitestのホスト解決に失敗する
- **WHEN** itest URLを入力しホスト解決に失敗する
- **THEN** システムはURL入力ダイアログ内にエラーを表示する

#### Scenario: Deep Linkでitestのホスト解決に失敗する
- **WHEN** itest URLのDeep Linkを開きホスト解決に失敗する
- **THEN** システムはエラートーストを表示する

### Requirement: URL入力のスキーム取り扱い
システムはURL入力の解析後、板/スレ遷移に使用するboardUrlをhttpsで構築することを SHALL 要求する。

#### Scenario: httpのスレURLを入力する
- **WHEN** http://{host}/test/read.cgi/{board}/{thread}/ のURLを入力する
- **THEN** システムは https://{host}/{board}/ をboardUrlとして遷移する

#### Scenario: httpの板URLを入力する
- **WHEN** http://{host}/{board}/ のURLを入力する
- **THEN** システムは https://{host}/{board}/ をboardUrlとして遷移する

### Requirement: URL入力のdat/oyster判定
システムはURL入力のdat形式をスレとして扱い、oyster形式は板として扱うことを SHALL 要求する。

#### Scenario: dat形式のURLを入力する
- **WHEN** https://{host}/{board}/dat/{thread}.dat を入力する
- **THEN** システムは該当スレを表示する

#### Scenario: oyster形式のURLを入力する
- **WHEN** https://{host}/{board}/oyster/{prefix}/{thread}.dat を入力する
- **THEN** システムは板URLとして扱い該当板を表示する

#### Scenario: itestのdat形式URLを入力する
- **WHEN** https://itest.5ch.net/{board}/dat/{thread}.dat を入力する
- **THEN** システムはitest判定の結果として板を表示する

### Requirement: スレ内URLの判定
システムはスレ内リンクをスレURL判定に一致した場合のみアプリ内遷移し、それ以外は外部URLとして扱うことを SHALL 要求する。

#### Scenario: スレ内リンクがスレURLに一致する
- **WHEN** https://{host}/test/read.cgi/{board}/{thread}/ のリンクをタップする
- **THEN** システムは該当スレを表示する

#### Scenario: スレ内リンクがdat形式である
- **WHEN** https://{host}/{board}/dat/{thread}.dat のリンクをタップする
- **THEN** システムは該当スレを表示する

#### Scenario: スレ内リンクがoyster形式である
- **WHEN** https://{host}/{board}/oyster/{prefix}/{thread}.dat のリンクをタップする
- **THEN** システムは外部URLとして扱う
