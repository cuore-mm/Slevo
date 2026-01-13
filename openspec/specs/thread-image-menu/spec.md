# thread-image-menu Specification

## Purpose
TBD - created by archiving change add-batch-image-save. Update Purpose after archive.
## Requirements
### Requirement: レス内画像の一括保存
システムは画像メニューに「レス内の画像をすべて保存」を提供し、選択時に対象レス本文の画像URLをまとめて保存するSHALL。

#### Scenario: レス内画像をまとめて保存する
- **WHEN** ユーザーが画像サムネイルを長押しして「レス内の画像をすべて保存」を選択する
- **THEN** レス本文から抽出した画像URLを一括で保存する

#### Scenario: 同一URLは1回だけ保存する
- **WHEN** レス本文に同一の画像URLが複数回含まれる
- **THEN** 当該URLの保存処理は1回だけ実行される

#### Scenario: 画像が1件のみの場合はメニューを表示しない
- **WHEN** 対象レス本文に画像URLが1件だけ含まれる
- **THEN** 「レス内の画像をすべて保存」は表示されない

### Requirement: 一括保存結果の件数通知
システムはレス内画像の一括保存完了後に、成功件数と失敗件数を通知するSHALL。

#### Scenario: 成功件数と失敗件数を表示する
- **WHEN** 一括保存が完了する
- **THEN** 成功件数と失敗件数をユーザーへ通知する

