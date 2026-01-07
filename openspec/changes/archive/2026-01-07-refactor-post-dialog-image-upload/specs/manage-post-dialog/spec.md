## ADDED Requirements
### Requirement: 画像URL挿入のController集約
システムは画像アップロード後のURL追記をPostDialogControllerが管理するSHALL。

#### Scenario: 画像アップロード成功
- **WHEN** 画像アップロードが成功しURLが取得できる
- **THEN** PostDialogControllerが本文末尾にURLを追記する

### Requirement: 画像アップロード処理の共通化
システムはBoard/Threadの画像アップロード処理を共通のUploaderで実行するSHALL。

#### Scenario: 画面間で同一のアップロードを利用
- **WHEN** BoardまたはThreadで画像アップロードが行われる
- **THEN** 共通Uploaderが実行される
