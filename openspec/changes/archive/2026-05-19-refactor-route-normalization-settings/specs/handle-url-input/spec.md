## MODIFIED Requirements

### Requirement: URL入力の判定を共通化する
システムはURL入力時に共通URLリゾルバを使用し、`docs/external/5ch.md` の入力URLパターン A〜D に一致する場合のみ板/スレへ遷移することを SHALL 要求する。板/スレへ遷移する場合、システムは永続化済みの `5ch.net` を `5ch.io` として開く設定値を取得し、その値に基づいてrouteを正規化してからタブ保証と画面遷移を行うことを SHALL 要求する。

#### Scenario: PC版板URLを入力する
- **WHEN** `https://<server>.5ch.net/<board>/` を入力する
- **THEN** システムは永続化済み設定値に基づきrouteを正規化してから該当板を表示する

#### Scenario: PC版スレURLを入力する
- **WHEN** `https://<server>.5ch.net/test/read.cgi/<board>/<threadKey>/` を入力する
- **THEN** システムは永続化済み設定値に基づきrouteを正規化してから該当スレを表示する

#### Scenario: itest版板URLを入力する
- **WHEN** `https://itest.5ch.net/subback/<board>` を入力する
- **THEN** システムは板ホストを解決し、永続化済み設定値に基づきrouteを正規化してから該当板を表示する

#### Scenario: itest版スレURLを入力する
- **WHEN** `https://itest.5ch.net/<server>/test/read.cgi/<board>/<threadKey>/` を入力する
- **THEN** システムは永続化済み設定値に基づきrouteを正規化してから該当スレを表示する

#### Scenario: 起動直後にURL入力から5ch.netスレを開く
- **WHEN** アプリ起動直後にURL入力で `https://agree.5ch.net/test/read.cgi/operate/1234567890/` を入力する
- **THEN** システムは設定Flowの未読込状態ではなく永続化済み設定値を使ってrouteを正規化する
