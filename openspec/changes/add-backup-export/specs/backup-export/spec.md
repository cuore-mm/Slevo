## ADDED Requirements

### Requirement: バックアップ画面への導線
システムは設定画面から「バックアップ作成」画面へ遷移できる導線を提供しなければならない（MUST）。

#### Scenario: 設定画面からバックアップ画面を開く
- **WHEN** ユーザーが設定画面の「バックアップ作成」項目を選択する
- **THEN** システムはバックアップ作成画面を表示する

#### Scenario: バックアップ作成画面を後続変更で拡張できる
- **WHEN** 後続の復元機能が追加される
- **THEN** システムは既存のバックアップ作成画面をバックアップと復元画面へ拡張できる

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

#### Scenario: 確認ダイアログで標準バックアップの注意を表示する
- **WHEN** システムがバックアップ作成確認ダイアログを表示する
- **THEN** システムは標準バックアップに閲覧履歴、ブックマーク、投稿履歴、タブ状態、設定など個人に紐づく利用データが含まれることを表示する

#### Scenario: 確認ダイアログで未暗号化であることを表示する
- **WHEN** システムがバックアップ作成確認ダイアログを表示する
- **THEN** システムはバックアップ ZIP が暗号化またはパスワード保護されないため安全に保管する必要があることを表示する

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

#### Scenario: DB コピー中断時に source transaction を rollback する
- **WHEN** `BEGIN IMMEDIATE` 後かつ `COMMIT` 完了前に DB コピー失敗または coroutine cancellation により DB エクスポートが中断される
- **THEN** システムは source DB transaction の `ROLLBACK` を試行し、DB 書き込み停止を解除し、一時ファイルを削除する

#### Scenario: commit 後の integrity check 失敗では rollback しない
- **WHEN** main DB ファイルコピーが成功して `COMMIT` が完了した後、コピー済み DB の integrity check が失敗する
- **THEN** システムは source DB transaction の rollback を試みず、バックアップ作成を失敗扱いにして一時ファイルを削除する

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

#### Scenario: DataStore 間の原子的 snapshot を要求しない
- **WHEN** システムが settings、tabs、cookies の DataStore JSON を作成する
- **THEN** システムは各 DataStore を取得時点の値として保存し、複数 DataStore を横断する原子的 snapshot を保証しない

#### Scenario: JSON schema の安定性を保つ
- **WHEN** システムが `datastore/settings.json`、`datastore/tabs.json`、または `datastore/cookies.json` を作成する
- **THEN** システムは backup format version 1 の必須 field、enum 文字列表現、配列/キーの安定した並び順を保つ

### Requirement: manifest の記録
システムはバックアップ ZIP に、バックアップ形式と含有データを判定できる manifest を含めなければならない（MUST）。

#### Scenario: manifest に形式情報を記録する
- **WHEN** システムがバックアップファイルを作成する
- **THEN** システムは `backupFormatVersion`、`backupMode`、`createdAt`、`appVersionCode`、`appVersionName`、`databaseVersion`、`included` を含む `manifest.json` を ZIP に含める

#### Scenario: manifest の version 1 固定値を記録する
- **WHEN** システムが `manifest.json` を作成する
- **THEN** システムは `backupFormatVersion = 1`、`backupMode = "full"`、`included.database/settings/tabs = true`、`included.cookies` にユーザー選択値を記録する

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

#### Scenario: ZIP close または output close で失敗する
- **WHEN** ZIP stream close、flush、または underlying output stream close が失敗する
- **THEN** システムはバックアップ作成を成功扱いせず、失敗 Snackbar を表示し、出力先ファイルが不完全な可能性をログへ記録する

#### Scenario: 詳細エラーをログに記録する
- **WHEN** DB エクスポート、JSON 変換、保存先 open、または ZIP 書き込みに失敗する
- **THEN** システムは詳細エラーをログへ記録し、詳細エラー文言を画面に表示しない

### Requirement: 操作結果 Snackbar の durable queue
システムはバックアップ作成成功、バックアップ作成失敗、無効または未対応のバックアップ、復元準備失敗の各操作結果を、`UiState` 上の識別可能な FIFO queue に完了順で保持し、Snackbar の表示完了が確認されるまで失ってはならない（MUST）。各結果 ID は ViewModel instance 内で厳密に単調増加しなければならない（MUST）。

#### Scenario: 操作結果を完了順に保持する
- **WHEN** Snackbar が未表示または表示中の間に複数の対象操作が完了する
- **THEN** システムは各 completion を直列化された単一 state transition として処理し、その transition 内で operation state を完了状態へ更新して各結果へ厳密に単調増加する ID を付け、transition 順を保って queue 末尾へ追加する

#### Scenario: 並行する操作完了を取りこぼさない
- **WHEN** 複数の対象 operation completion が並行して ViewModel へ到着する
- **THEN** システムは completion transition を直列化し、ID 順と queue 順を一致させ、いずれの結果も競合する state update で失わない

#### Scenario: queue の先頭だけを表示する
- **WHEN** pending result queue に 1 件以上の結果がある
- **THEN** システムは queue 先頭の結果だけを、その result ID を key とする effect で Snackbar に表示する

#### Scenario: Snackbar 表示完了後に先頭を acknowledge する
- **WHEN** queue 先頭に対応する Snackbar が timeout または dismiss により表示を完了する
- **THEN** システムは表示した result ID を acknowledge し、ID が現在の先頭と一致する場合だけ先頭 1 件を削除する

#### Scenario: Snackbar effect が中断される
- **WHEN** Snackbar 表示中に lifecycle change または画面 recreation により表示 effect が cancellation される
- **THEN** システムはその result を acknowledge または削除せず、recreation 後に同じ queue 先頭を再表示する

#### Scenario: stale または wrong ID を acknowledge する
- **WHEN** システムが空 queue、古い result ID、未知の result ID、または queue の後続 result ID を acknowledge する
- **THEN** システムは queue のどの result も削除せず、順序と内容を維持する

#### Scenario: queued results を順次表示する
- **WHEN** queue 先頭の正しい result ID が acknowledge され、後続 result が残っている
- **THEN** システムは削除した先頭だけを除き、次の先頭を次の Snackbar として表示する

#### Scenario: 既存の Snackbar 表示契約を維持する
- **WHEN** システムが queue 内の対象操作結果を Snackbar に表示する
- **THEN** システムは `ExportSucceeded` に `backup_snackbar_success`、`ExportFailed` に `backup_snackbar_failure`、`RestorePrepareFailed` に `restore_snackbar_failed`、`InvalidBackup` に `restore_snackbar_invalid` を対応させ、既存の各文言、`SnackbarDuration.Short`、style、host、layout を変更しない

### Requirement: 権限不要のファイル出力
システムはバックアップ出力に Storage Access Framework を使用し、追加の外部ストレージ権限を要求してはならない（MUST）。

#### Scenario: バックアップ作成で権限ダイアログを出さない
- **WHEN** ユーザーがバックアップファイルの保存先を選択する
- **THEN** システムは Android の保存先選択 UI を使用し、外部ストレージ権限要求を表示しない

#### Scenario: 外部ストレージ権限と FileProvider を追加しない
- **WHEN** システムがバックアップファイルを作成する
- **THEN** システムは `WRITE_EXTERNAL_STORAGE`、`MANAGE_EXTERNAL_STORAGE`、または `FileProvider` をバックアップ出力のために追加または使用しない
