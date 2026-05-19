## MODIFIED Requirements

### Requirement: Deep Link の板/スレ遷移
システムは共通URLリゾルバを用いて Deep Link を解析し、板またはスレに解決できる場合のみ遷移することを SHALL 要求する。板/スレへ遷移する場合、システムは永続化済みの `5ch.net` を `5ch.io` として開く設定値を取得し、その値に基づいてrouteを正規化してからタブ保証と画面遷移を行うことを SHALL 要求する。

#### Scenario: PC版のスレURLを開く
- **WHEN** `https://{host}/test/read.cgi/{board}/{thread}/` の Deep Link を開く
- **THEN** システムは永続化済み設定値に基づきrouteを正規化してから該当スレを表示する

#### Scenario: itestの板URLを開く
- **WHEN** `https://itest.{domain}/subback/{board}` の Deep Link を開く
- **THEN** システムは板ホストを解決し、永続化済み設定値に基づきrouteを正規化してから該当板を表示する

#### Scenario: itestのスレURLを開く
- **WHEN** `https://itest.{domain}/{server}/test/read.cgi/{board}/{thread}/` の Deep Link を開く
- **THEN** システムは永続化済み設定値に基づきrouteを正規化してから該当スレを表示する

#### Scenario: 起動直後にDeep Linkで5ch.netスレを開く
- **WHEN** アプリ未起動状態から `https://agree.5ch.net/test/read.cgi/operate/1234567890/` の Deep Link を開く
- **THEN** システムは設定Flowの初期キャッシュではなく永続化済み設定値を使ってrouteを正規化する
