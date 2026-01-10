## ADDED Requirements

### Requirement: URL入力の判定を共通化する
システムはURL入力時に共通URLリゾルバを使用し、`docs/external/5ch.md` の入力URLパターン A〜D に一致する場合のみ板/スレへ遷移することを SHALL 要求する。

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

### Requirement: URL入力の対象外判定
システムは入力パターン A〜D に一致しないURLを入力された場合、ダイアログ内でエラーを表示することを SHALL 要求する。

#### Scenario: dat形式のURLを入力する
- **WHEN** `https://<server>.5ch.net/<board>/dat/<threadKey>.dat` を入力する
- **THEN** システムはダイアログ内にエラーを表示する

#### Scenario: oyster形式のURLを入力する
- **WHEN** `https://<server>.5ch.net/<board>/oyster/<prefix>/<threadKey>.dat` を入力する
- **THEN** システムはダイアログ内にエラーを表示する
