## MODIFIED Requirements

### Requirement: URL入力の判定を共通化する
システムはURL入力時に共通URLリゾルバを使用し、`docs/external/5ch.md` の入力URLパターン A〜D に一致する場合のみ板/スレへ遷移することを SHALL 要求する。対象ドメインには `5ch.net` と `5ch.io` を含めることを SHALL 要求する。

#### Scenario: PC版板URLを入力する
- **WHEN** `https://<server>.5ch.net/<board>/` を入力する
- **THEN** システムは該当板を表示する

#### Scenario: PC版スレURLを入力する
- **WHEN** `https://<server>.5ch.net/test/read.cgi/<board>/<threadKey>/` を入力する
- **THEN** システムは該当スレを表示する

#### Scenario: itest版板URLを入力する
- **WHEN** `https://itest.5ch.net/subback/<board>` を入力する
- **THEN** システムは板ホストを解決し該当板を表示する

#### Scenario: itest版スレURLを入力する
- **WHEN** `https://itest.5ch.net/<server>/test/read.cgi/<board>/<threadKey>/` を入力する
- **THEN** システムは該当スレを表示する

#### Scenario: 5ch.io のPC版板URLを入力する
- **WHEN** `https://<server>.5ch.io/<board>/` を入力する
- **THEN** システムは該当板を表示する

#### Scenario: 5ch.io のPC版スレURLを入力する
- **WHEN** `https://<server>.5ch.io/test/read.cgi/<board>/<threadKey>/` を入力する
- **THEN** システムは該当スレを表示する

#### Scenario: 5ch.io のitest版板URLを入力する
- **WHEN** `https://itest.5ch.io/subback/<board>` を入力する
- **THEN** システムは板ホストを解決し該当板を表示する

#### Scenario: 5ch.io のitest版スレURLを入力する
- **WHEN** `https://itest.5ch.io/<server>/test/read.cgi/<board>/<threadKey>/` を入力する
- **THEN** システムは該当スレを表示する

## ADDED Requirements

### Requirement: URL入力で5ch.netを5ch.ioとして開く
システムは全般設定の `5ch.net` を `5ch.io` として開く設定がオンの場合、URL入力で開く `5ch.net` の板/スレの `boardUrl` を `5ch.io` に正規化することを SHALL 要求する。

#### Scenario: URL入力で5ch.net板URLを5ch.ioとして開く
- **WHEN** 設定がオンの状態で `https://agree.5ch.net/operate/` を入力する
- **THEN** システムは `https://agree.5ch.io/operate/` を板URLとして表示する

#### Scenario: URL入力で5ch.netスレURLを5ch.ioとして開く
- **WHEN** 設定がオンの状態で `https://agree.5ch.net/test/read.cgi/operate/1234567890/` を入力する
- **THEN** システムは `https://agree.5ch.io/operate/` を板URLとして `1234567890` スレを表示する
