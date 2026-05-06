## ADDED Requirements

### Requirement: 5ch.io の板/スレを開く
システムは `5ch.io` ドメインのPC版およびitest版URLを、5ch系の板/スレURLとして開くことを SHALL 要求する。

#### Scenario: 5ch.io のPC版板URLを開く
- **WHEN** ユーザーが `https://agree.5ch.io/operate/` を開く
- **THEN** システムは `agree.5ch.io` の `operate` 板を表示する

#### Scenario: 5ch.io のPC版スレURLを開く
- **WHEN** ユーザーが `https://agree.5ch.io/test/read.cgi/operate/1234567890/` を開く
- **THEN** システムは `agree.5ch.io` の `operate` 板にある `1234567890` スレを表示する

#### Scenario: 5ch.io のitest版スレURLを開く
- **WHEN** ユーザーが `https://itest.5ch.io/agree/test/read.cgi/operate/1234567890/` を開く
- **THEN** システムは `agree.5ch.io` の `operate` 板にある `1234567890` スレを表示する

### Requirement: 5ch.net を 5ch.io として開く設定
システムは全般設定で `5ch.net` の板/スレURLを `5ch.io` として開く設定を提供することを SHALL 要求する。未設定時のデフォルトはオンであることを SHALL 要求する。設定オン時の変換は、保存済みデータを変更せず、板/スレを開くための一時的な遷移先 `boardUrl` にのみ適用することを SHALL 要求する。

#### Scenario: 設定オンで5ch.netの板URLを開く
- **WHEN** 設定がオンの状態でユーザーが `https://agree.5ch.net/operate/` を開く
- **THEN** システムは `https://agree.5ch.io/operate/` を板URLとして表示する

#### Scenario: 設定オンで5ch.netのスレURLを開く
- **WHEN** 設定がオンの状態でユーザーが `https://agree.5ch.net/test/read.cgi/operate/1234567890/` を開く
- **THEN** システムは `https://agree.5ch.io/operate/` を板URLとして `1234567890` スレを表示する

#### Scenario: 設定オフで5ch.netのスレURLを開く
- **WHEN** 設定がオフの状態でユーザーが `https://agree.5ch.net/test/read.cgi/operate/1234567890/` を開く
- **THEN** システムは `https://agree.5ch.net/operate/` を板URLとして `1234567890` スレを表示する

#### Scenario: 既存タブから5ch.netのスレを開く
- **WHEN** 設定がオンの状態で保存済みタブが持つ `https://agree.5ch.net/operate/` のスレを開く
- **THEN** システムは保存済みタブのURLを変更せず、`https://agree.5ch.io/operate/` を遷移先の板URLとしてスレを表示する

#### Scenario: ブックマークから5ch.netのスレを開く
- **WHEN** 設定がオンの状態でブックマークが持つ `https://agree.5ch.net/operate/` のスレを開く
- **THEN** システムはブックマークの保存URLを変更せず、`https://agree.5ch.io/operate/` を遷移先の板URLとしてスレを表示する

#### Scenario: 履歴から5ch.netのスレを開く
- **WHEN** 設定がオンの状態で履歴が持つ `https://agree.5ch.net/operate/` のスレを開く
- **THEN** システムは履歴の保存URLを変更せず、`https://agree.5ch.io/operate/` を遷移先の板URLとしてスレを表示する

#### Scenario: 画面内リンクから5ch.netのスレを開く
- **WHEN** 設定がオンの状態でレス本文や板画面などのアプリ内入口から `https://agree.5ch.net/operate/` のスレを開く
- **THEN** システムは `https://agree.5ch.io/operate/` を遷移先の板URLとしてスレを表示する

#### Scenario: 5ch.net以外のURLを開く
- **WHEN** ユーザーが `https://example.com/test/read.cgi/operate/1234567890/` または `https://example.bbspink.com/test/read.cgi/operate/1234567890/` を開く
- **THEN** システムは5ch.netから5ch.ioへの正規化を行わない

### Requirement: 投稿処理では追加変換しない
システムは投稿処理、スレ立て処理、OkHttpクライアント全体の通信処理で `5ch.net` から `5ch.io` への追加変換を行わないことを SHALL 要求する。投稿先hostは、板/スレを開いた結果として画面が保持している板URLから決定することを SHALL 要求する。

#### Scenario: 開いた板URLのhostを投稿先に使う
- **WHEN** ユーザーがスレへ投稿する
- **THEN** システムはスレを開いた時点の板URLに含まれるhostを投稿先として使用する
