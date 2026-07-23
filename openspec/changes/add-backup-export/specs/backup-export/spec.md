## ADDED Requirements

### Requirement: バックアップ画面への導線
システムは設定画面から「バックアップ作成」画面へ遷移できる導線を提供しなければならない（MUST）。

#### Scenario: 設定画面からバックアップ画面を開く
- **WHEN** ユーザーが設定画面の「バックアップ作成」項目を選択する
- **THEN** システムはバックアップ作成画面を表示する

#### Scenario: 復元 UI を表示しない
- **WHEN** ユーザーがバックアップ作成画面を表示する
- **THEN** システムは復元ボタンまたは復元が利用可能であることを示す UI を表示しない

### Requirement: バックアップファイルの作成
システムはバックアップ作成ボタン押下後に確認ダイアログを表示し、ユーザーが確認した後、Android のファイル作成 UI で選択した保存先へ単一の ZIP バックアップファイルを書き込まなければならない（MUST）。

#### Scenario: 確認後に保存先を選択してバックアップを作成する
- **WHEN** ユーザーがバックアップ作成ボタンを押し、確認ダイアログで作成を確定し、ファイル作成 UI で保存先を選択する
- **THEN** システムは選択された URI に ZIP 形式のバックアップファイルを書き込む

#### Scenario: 確認ダイアログをキャンセルする
- **WHEN** ユーザーがバックアップ作成ボタン押下後の確認ダイアログをキャンセルする
- **THEN** システムはファイル作成 UI を表示せず、バックアップ処理を開始しない

#### Scenario: 保存先選択をキャンセルする
- **WHEN** ユーザーがファイル作成 UI をキャンセルする
- **THEN** システムはバックアップ処理を開始せず、エラー表示を行わない

#### Scenario: zip 拡張子のファイル名を提示する
- **WHEN** システムが Android のファイル作成 UI を起動する
- **THEN** システムは MIME type `application/zip` と `slevo-backup-YYYYMMDD-HHmmss.zip` 形式の推奨ファイル名を提示する

#### Scenario: provider 側表示名に依存しない
- **WHEN** Android のファイル作成 UI が保存先 URI を返す
- **THEN** システムは返却された URI の provider 側表示名が `.zip` で終わることをバックアップ成功条件にしない

#### Scenario: repository 層で同時バックアップを直列化する
- **WHEN** システムが `BackupRepository` に対して複数のバックアップ作成要求を同時に受け取る
- **THEN** システムはバックアップ作成処理を 1 件ずつ直列に実行する

### Requirement: バックアップ内容
システムはバックアップ ZIP に Room DB、通常設定、タブ選択状態、バックアップメタデータを含めなければならない（MUST）。

#### Scenario: 標準バックアップを作成する
- **WHEN** ユーザーがクッキーを含めずにバックアップを作成する
- **THEN** システムは `manifest.json`、`database/slevo.db`、`datastore/settings.json`、`datastore/tabs.json` を ZIP に含める

#### Scenario: 標準バックアップにクッキーを含めない
- **WHEN** ユーザーがクッキーを含めずにバックアップを作成する
- **THEN** システムは `datastore/cookies.json` を ZIP に含めない

### Requirement: SDK 24 互換の DB エクスポート
システムは SDK 24 で利用できない `VACUUM INTO` を使わず、WAL checkpoint と DB ファイルコピーで Room DB をエクスポートしなければならない（MUST）。

#### Scenario: checkpoint 完了後に DB ファイルをコピーする
- **WHEN** システムが Room DB をバックアップ用一時ファイルへ出力する
- **THEN** システムは `PRAGMA wal_checkpoint(TRUNCATE)` の結果を確認し、完了後に `BEGIN IMMEDIATE` を開始して main DB ファイルをコピーする

#### Scenario: コピー済み DB の整合性を検証する
- **WHEN** システムが main DB ファイルをバックアップ用一時ファイルへコピーする
- **THEN** システムはコピー済み DB を読み取り専用で開き、`PRAGMA integrity_check` が `ok` を返すことを確認してから ZIP に含める

#### Scenario: コピー済み DB の整合性検証に失敗する
- **WHEN** コピー済み DB を開けない、または `PRAGMA integrity_check` が `ok` 以外を返す
- **THEN** システムはコピー済み DB を ZIP に含めず、バックアップ作成を失敗扱いにする

#### Scenario: checkpoint 未完了時に失敗する
- **WHEN** `PRAGMA wal_checkpoint(TRUNCATE)` の結果がリトライ後も `busy != 0` または `log != checkpointed` である
- **THEN** システムは main DB ファイルをコピーせず、バックアップ作成を失敗扱いにする

#### Scenario: バックアップ中の DB 書き込みを待機させる
- **WHEN** バックアップ処理が Room DB を checkpoint してコピーしている
- **THEN** システムはアプリ内の新規 Room DB 書き込みを待機させる

#### Scenario: VACUUM INTO を使わない
- **WHEN** システムが Room DB をエクスポートする
- **THEN** システムは `VACUUM INTO` を実行しない

### Requirement: クッキーを含むバックアップ
システムはバックアップ作成ボタン押下後の確認ダイアログでユーザーが明示的に選択した場合のみ、クッキーをバックアップに含めなければならない（MUST）。

#### Scenario: クッキーを含めてバックアップを作成する
- **WHEN** ユーザーが確認ダイアログで「クッキーを含める」を有効にしてバックアップを作成する
- **THEN** システムは `datastore/cookies.json` を ZIP に含め、`manifest.json` にクッキーが含まれることを記録する

#### Scenario: 確認ダイアログのクッキー選択初期状態
- **WHEN** ユーザーがバックアップ作成ボタンを押して確認ダイアログを表示する
- **THEN** システムは確認ダイアログ内の「クッキーを含める」を未選択状態として表示し、処理中でなければ選択可能にする

#### Scenario: 確認ダイアログでクッキー説明を表示する
- **WHEN** システムがバックアップ作成確認ダイアログを表示する
- **THEN** システムはクッキーに認証情報が含まれる可能性があることを表示する

### Requirement: DataStore の JSON 化
システムはバックアップ内部の DataStore データを DataStore 物理ファイルではなく JSON として保存しなければならない（MUST）。

#### Scenario: 設定 DataStore を JSON として保存する
- **WHEN** システムがバックアップファイルを作成する
- **THEN** システムは通常設定 DataStore の内容を `datastore/settings.json` として保存する

#### Scenario: タブ選択 DataStore を JSON として保存する
- **WHEN** システムがバックアップファイルを作成する
- **THEN** システムはタブ選択 DataStore の内容を `datastore/tabs.json` として保存する

#### Scenario: クッキー DataStore を JSON として保存する
- **WHEN** ユーザーが確認ダイアログで「クッキーを含める」を有効にしてバックアップを作成する
- **THEN** システムはクッキー DataStore の内容を `datastore/cookies.json` として保存する

#### Scenario: クッキー JSON の必須フィールドを保存する
- **WHEN** システムが `datastore/cookies.json` を保存する
- **THEN** システムは各 Cookie に `name`、`value`、`domain`、`path`、`expiresAt`、`secure`、`httpOnly`、`hostOnly`、`persistent` を含める

### Requirement: manifest の記録
システムはバックアップ ZIP に、バックアップ形式と含有データを判定できる manifest を含めなければならない（MUST）。

#### Scenario: manifest に形式情報を記録する
- **WHEN** システムがバックアップファイルを作成する
- **THEN** システムは `backupFormatVersion`、`backupMode`、`createdAt`、`appVersionCode`、`appVersionName`、`databaseVersion`、`included` を含む `manifest.json` を ZIP に含める

#### Scenario: manifest にクッキー未含有を記録する
- **WHEN** ユーザーが確認ダイアログでクッキーを含めずにバックアップを作成する
- **THEN** システムは `manifest.json` の `included.cookies` を `false` として記録する

#### Scenario: manifest にクッキー含有を記録する
- **WHEN** ユーザーが確認ダイアログでクッキーを含めてバックアップを作成する
- **THEN** システムは `manifest.json` の `included.cookies` を `true` として記録する

### Requirement: エクスポート中の UI 状態
システムはバックアップ作成中、重複実行を防ぎ、処理状態をユーザーに示さなければならない（MUST）。

#### Scenario: バックアップ作成中の操作抑制
- **WHEN** バックアップ作成処理が実行中である
- **THEN** システムはバックアップ作成ボタン、確認ダイアログの作成ボタン、確認ダイアログ内のクッキー選択を無効化する

#### Scenario: バックアップ作成中の進捗ダイアログ表示
- **WHEN** バックアップ作成処理が実行中である
- **THEN** システムはバックアップ作成中であることを示すモーダルの進捗ダイアログを表示する

#### Scenario: バックアップ作成成功を表示する
- **WHEN** バックアップファイルの書き込みが完了する
- **THEN** システムはバックアップ作成が完了したことを Snackbar で表示する

#### Scenario: バックアップ作成失敗を表示する
- **WHEN** 保存先 open、DB エクスポート、コピー済み DB の整合性検証、JSON 変換、または ZIP 書き込みに失敗する
- **THEN** システムはバックアップ作成に失敗したことを Snackbar で表示する

#### Scenario: ZIP 書き込み途中で失敗する
- **WHEN** システムが選択された URI へ ZIP を書き込んでいる途中で失敗する
- **THEN** システムは成功 Snackbar を表示せず、出力先ファイルが不完全な可能性をログへ記録する

#### Scenario: 詳細エラーをログに記録する
- **WHEN** DB エクスポート、JSON 変換、保存先 open、または ZIP 書き込みに失敗する
- **THEN** システムは詳細エラーをログへ記録し、詳細エラー文言を画面に表示しない

### Requirement: 権限不要のファイル出力
システムはバックアップ出力に Storage Access Framework を使用し、追加の外部ストレージ権限を要求してはならない（MUST）。

#### Scenario: バックアップ作成で権限ダイアログを出さない
- **WHEN** ユーザーがバックアップファイルの保存先を選択する
- **THEN** システムは Android の保存先選択 UI を使用し、外部ストレージ権限要求を表示しない
