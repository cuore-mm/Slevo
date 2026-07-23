## 1. 事前調査と配置確認

- [ ] 1.1 `AppDatabase.kt`、`DatabaseModule.kt`、`SettingsLocalDataSource.kt`、`TabsLocalDataSource.kt`、`CookieLocalDataSource.kt` を確認し、DB version、DB 名、DataStore の既存 API、バックアップに含める設定項目を実装メモに整理する。完了条件: 実装で参照する既存メソッドと不足 API が明確になり、計画書と差分がある場合は実装前に design/tasks/spec が更新されている。
- [ ] 1.2 `AppNavGraph.kt` と `SettingsScreen.kt` の設定 navigation 構造を確認し、追加する route 名、画面 package、文字列 resource 名を決める。完了条件: 新規 `AppRoute.SettingsBackup` と設定画面項目の追加位置が決まっている。
- [ ] 1.3 別変更 `add-database-write-gate` が実装済みであることを確認する。完了条件: `DatabaseWriteGate` が利用可能で、既存 Room DB 書き込み経路の gate 移行がこの変更のスコープ外として完了済みである。

## 2. バックアップモデルと JSON 変換

- [ ] 2.1 `data/backup/model/BackupManifest.kt` を追加し、`backupFormatVersion`、`backupMode`、`createdAt`、`appVersionCode`、`appVersionName`、`databaseVersion`、`included` を保持する JSON モデルを定義する。完了条件: Moshi codegen adapter で encode/decode でき、`@JsonClass(generateAdapter = true)` が付いている。
- [ ] 2.2 `data/backup/model/BackupSettingsJson.kt` を追加し、`SettingsLocalDataSource` が扱うテーマ、ツリー順、ミニマップスクロールバー、文字倍率、行間、5ch.io リダイレクト、ジェスチャー設定を保持できるモデルを定義する。完了条件: 既存設定項目が漏れなくモデル化されている。
- [ ] 2.3 `data/backup/model/BackupTabsJson.kt` を追加し、`TabsLocalDataSource.observeLastSelectedTabsPage()` の値を保持できるモデルを定義する。完了条件: 最終選択ページを JSON 化できる。
- [ ] 2.4 `data/backup/model/BackupCookiesJson.kt` を追加し、`CookieLocalDataSource.getCookies()` から取得した OkHttp Cookie を JSON として保存できるバックアップ専用モデルを定義する。完了条件: `name`、`value`、`domain`、`path`、`expiresAt`、`secure`、`httpOnly`、`hostOnly`、`persistent` の 9 field が保持できる。
- [ ] 2.5 JSON 変換クラスまたは mapper を追加し、DataStore/Cookie の既存モデルからバックアップ DTO へ変換できるようにする。完了条件: settings/tabs/cookies の field 名、型、nullable ルール、cookie 配列の `domain`、`path`、`name` 昇順、gesture action key の昇順を JVM unit test で検証できる。

## 3. DB エクスポートと ZIP 書き込み

- [ ] 3.1 `data/backup/DatabaseBackupExporter.kt` を追加し、`DatabaseWriteGate.withWritesSuspended { ... }` の内側で `PRAGMA wal_checkpoint(TRUNCATE)`、checkpoint 結果確認、`BEGIN IMMEDIATE`、main DB ファイルコピー、`COMMIT`/`ROLLBACK` の順で SQLite DB を出力する。完了条件: 一時 `slevo.db` ファイルが生成され、失敗時に例外または Result で呼び出し元へ通知される。
- [ ] 3.2 checkpoint 結果の `busy`、`log`、`checkpointed` を読み取り、`busy == 0` かつ `log == checkpointed` でない場合に最大 3 回、各 100ms 待機でリトライしてから失敗扱いにする。完了条件: checkpoint 未完了のまま main DB ファイルをコピーせず、リトライ回数と待機処理をテストで決定的に検証できる。
- [ ] 3.3 `BEGIN IMMEDIATE` は checkpoint 完了後、main DB ファイルコピー直前に開始する。完了条件: checkpoint 前に `BEGIN IMMEDIATE` を実行するコードがない。
- [ ] 3.4 コピー済み DB を読み取り専用で開き、`PRAGMA integrity_check` を実行する検証処理を追加する。完了条件: `ok` の場合のみ ZIP 作成へ進み、失敗時は詳細ログを残してバックアップを失敗扱いにする。
- [ ] 3.5 `data/backup/BackupZipWriter.kt` を追加し、`manifest.json`、`database/slevo.db`、`datastore/settings.json`、`datastore/tabs.json`、任意の `datastore/cookies.json` を ZIP entry として書き込む。完了条件: `includeCookies=false` では cookies entry が存在せず、`includeCookies=true` では存在することをテストで確認できる。
- [ ] 3.6 `ContentResolver.openOutputStream(uri)` を使う出力処理を repository/data 層に実装し、SAF の URI へ直接 ZIP を書き込む。完了条件: `FileProvider` と外部ストレージ権限を追加せずに書き込み処理が完結している。
- [ ] 3.7 `cacheDir/backups/<session>` の一時ディレクトリ管理を実装し、成功・失敗どちらでも `finally` で削除する。完了条件: 例外時にも一時ファイルが残らない構造になっている。

## 4. Repository と DI

- [ ] 4.1 `data/backup/BackupRepository.kt` と `BackupRepositoryImpl.kt` を追加し、`exportBackup(uri, includeCookies)` 相当の suspend API を定義する。完了条件: ViewModel が単一 API 呼び出しでバックアップ作成を依頼でき、repository/data 層の `backupMutex` で同時実行が 1 件ずつ直列化される。
- [ ] 4.2 `BackupRepositoryImpl` で manifest 作成、DB エクスポート、DataStore JSON 作成、ZIP 書き込みを順序通りに orchestrate する。完了条件: クッキー有無が manifest と ZIP 内容の両方に反映される。
- [ ] 4.3 `di/BackupModule.kt` または既存 DI module に Hilt binding/provider を追加する。完了条件: `BackupViewModel` に `BackupRepository` を注入できる。
- [ ] 4.4 エラー種別を sealed class または Result 型で定義し、DB 失敗、JSON 失敗、ZIP 失敗、保存先 open 失敗を区別する。完了条件: ViewModel がユーザー向けメッセージへ変換できる。

## 5. UI と navigation

- [ ] 5.1 `AppNavGraph.kt` の `AppRoute` に `SettingsBackup` と `RouteName.SETTINGS_BACKUP` を追加し、設定 navigation にバックアップ画面 route を追加する。完了条件: 設定画面から「バックアップ作成」画面へ遷移でき、復元 UI が表示されない。
- [ ] 5.2 `SettingsScreen.kt` に「バックアップ作成」項目と callback を追加する。完了条件: Preview を含めて既存 callback 呼び出しがコンパイルでき、設定項目名に「復元」が含まれない。
- [ ] 5.3 `ui/settings/backup/BackupUiState.kt` を追加し、`includeCookies`、`showConfirmDialog`、`isExporting`、成功/失敗 Snackbar 用イベントを表現する。完了条件: 画面本体、確認ダイアログ、処理中ダイアログ、結果 Snackbar の表示状態が ViewModel から購読できる。
- [ ] 5.4 `ui/settings/backup/BackupViewModel.kt` を追加し、バックアップ作成ボタン押下、確認ダイアログのクッキー checkbox 変更、確認、キャンセル、保存先選択キャンセル、成功、失敗の状態遷移を実装する。完了条件: `isExporting` 中に重複実行されず、ダイアログキャンセル時に保存処理が開始されない。
- [ ] 5.5 `ui/settings/backup/BackupScreen.kt` を追加し、バックアップ作成ボタン押下で確認ダイアログを表示し、ダイアログの作成ボタン押下後に `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"))` で保存先選択を起動する。完了条件: `slevo-backup-YYYYMMDD-HHmmss.zip` 形式の推奨ファイル名を提示し、返却 URI の provider 側表示名には依存せず、Composable は launcher、確認ダイアログ、描画のみを担当し、ZIP/DB 処理を持たない。
- [ ] 5.6 `BackupScreen` に確認ダイアログ、ダイアログ内のクッキーを含める checkbox、センシティブ情報の説明、保存ボタンを追加する。完了条件: `includeCookies` の初期値が false で、確認ダイアログのキャンセル時に SAF launcher が起動しない。
- [ ] 5.7 `BackupScreen` に `isExporting` 中のモーダル進捗ダイアログを追加し、タイトル、説明文、`CircularProgressIndicator` を表示する。完了条件: 処理中は保存ボタン・確認ダイアログの作成ボタン・checkbox が無効になる。
- [ ] 5.8 `BackupScreen` に成功/失敗 Snackbar を追加し、成功時は「バックアップファイルを作成しました」、失敗時は「バックアップファイルの作成に失敗しました」を表示する。完了条件: 詳細エラーや例外 stack trace が画面に表示されない。
- [ ] 5.9 repository/data 層または ViewModel で詳細エラーを既存 logging 方針に合わせてログ出力する。完了条件: 保存先 open、DB エクスポート、JSON 変換、ZIP 書き込みの失敗詳細がログに残る。
- [ ] 5.10 `BackupScreen` の `@Preview` を追加する。完了条件: Preview 関数に KDoc を付けず、通常状態、確認ダイアログ表示状態、処理中状態の表示を確認できる。
- [ ] 5.11 `strings.xml` に画面タイトル、説明文、ボタン文言、進捗ダイアログ文言、成功/失敗 Snackbar 文言を追加する。完了条件: UI にハードコード文字列が残っていない。

## 6. テスト

- [ ] 6.1 `BackupManifestTest` を追加し、manifest の JSON encode/decode と `included.cookies` の true/false を検証する。完了条件: JVM unit test が通る。
- [ ] 6.2 `BackupZipWriterTest` を追加し、クッキーを含む場合/含まない場合の ZIP entry 一覧を検証する。完了条件: `datastore/cookies.json` の有無が仕様通りである。
- [ ] 6.3 `BackupViewModelTest` を追加し、確認ダイアログ表示、ダイアログキャンセル、保存先選択キャンセル、成功 Snackbar イベント、共通失敗 Snackbar イベント、重複実行抑制、クッキー checkbox の状態遷移を検証する。完了条件: `MainDispatcherRule` と fake repository で決定的にテストできる。
- [ ] 6.4 `DatabaseBackupExporter` の fake/抽象化テストを追加し、checkpoint 結果確認、最大 3 回・各 100ms のリトライ方針、待機処理の差し替え、checkpoint 未完了時に main DB コピーへ進まないこと、`BEGIN IMMEDIATE` 後の main DB ファイルコピー、コピー済み DB の `PRAGMA integrity_check`、integrity check 成功後にだけ ZIP へ進む順序を検証する。完了条件: core safety logic が一時ファイル SQLite の可否に依存せず自動テストされる。
- [ ] 6.4a 可能であれば追加で一時ファイル DB を使い、実 SQLite に対する checkpoint と main DB ファイルコピーを検証する。完了条件: 困難な場合は対象 API の制約と手動確認手順を設計メモに残す。
- [ ] 6.5 `DatabaseBackupExporter` の fake test を追加し、`DatabaseWriteGate.withWritesSuspended` が呼ばれることを検証する。完了条件: バックアップ処理が gate 停止区間内で checkpoint/copy を実行することを確認できる。
- [ ] 6.5a `BackupRepositoryTest` で `exportBackup` を concurrent call した場合に 1 件ずつ直列実行されることを fake writer/exporter で検証する。完了条件: UI 以外の呼び出しでも duplicate backup が同時実行されない。
- [ ] 6.6 Compose UI または instrumented test を追加し、設定画面の「バックアップ作成」項目からバックアップ作成画面へ遷移できることを検証する。完了条件: navigation route の追加漏れを自動テストで検出できる。
- [ ] 6.7 Compose UI または ViewModel + UI state test で、確認ダイアログ、クッキー checkbox の初期未選択かつ処理中でなければ選択可能な状態、進捗ダイアログ、成功/失敗 Snackbar を検証する。完了条件: 主要 UI 状態が自動テストまたは明示的な UI state test で確認できる。
- [ ] 6.8 ZIP 書き込み途中の失敗を fake output stream で検証し、success Snackbar が出ず、共通失敗 Snackbar と「出力先ファイルが不完全な可能性」の詳細ログが発生することを確認する。完了条件: partial output を成功扱いしない。

## 7. 検証と仕上げ

- [ ] 7.1 新規 class/interface/object/data class に KDoc があること、Preview 関数に KDoc がないこと、長い関数にセクションコメントがあることを確認する。完了条件: リポジトリのコメント規約に違反しない。
- [ ] 7.2 `./gradlew testDebugUnitTest` 相当の単体テストを CI workflow で実行する。完了条件: 既存テストと新規テストが成功する。
- [ ] 7.3 GitHub Actions の build workflow を実行する。完了条件: Android build と unit test が成功し、失敗時はログから原因を特定して修正する。
- [ ] 7.4 実機またはエミュレータでバックアップ作成を手動確認し、ZIP を展開して `manifest.json`、`database/slevo.db`、`datastore/settings.json`、`datastore/tabs.json`、クッキー有無を確認する。完了条件: クッキー OFF/ON の両方で仕様通りの ZIP 構造になる。
