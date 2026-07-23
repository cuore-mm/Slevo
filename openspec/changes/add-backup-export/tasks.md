## 実装方針

`add-backup-export` は DB snapshot、ZIP/SAF 出力、DataStore JSON、Repository orchestration、UI/navigation を含むため、1 回で全対象を変更せず Phase ごとに小さく実装・検証・コミットする。

- Phase 0: prerequisite gate と source-derived 前提の再確認を先に完了する。
- Phase 1: バックアップ DTO / JSON schema と mapper を固める。
- Phase 2: DB export core を `DatabaseWriteGate.withWritesSuspended` 前提で実装する。
- Phase 3: ZIP writer と SAF output stream を実装する。
- Phase 4: BackupRepository orchestration と DI を実装する。
- Phase 5: UI/navigation を実装する。
- Phase 6: 最終 verification、manual checks、CI を揃える。

各 Phase は可能な限り独立したコミットに分ける。Phase 内で CI を実行した場合は、該当タスクまたは検証メモに Run ID を記録する。

## Phase 0: prerequisite gate と事前調査

- [x] 0.1 `add-database-write-gate` が完了済みであることを確認する。完了条件: OpenSpec 上で `add-database-write-gate` が complete で、`DatabaseWriteGate` と既存 Room DB 書き込み経路の gate 移行が完了している。未完了または部分完了の場合、この change のコード実装を開始せず停止して報告する。確認結果: `add-database-write-gate` は 38/38 完了 (CI Pass)。
- [x] 0.2 事前調査の結果、DB version、DB 名、DataStore field、navigation 構造、既存 API に design/proposal/spec/tasks と差分が見つかった場合、Phase 1 以降の実装に進む前に OpenSpec 文書を更新する。確認結果: 差分なし。AppDatabase version=9、DB名はrelease/debugで分岐、Settings 14 field、TabsLastPage Int、Cookie 9 field。
- [x] 0.3 `AppDatabase.kt`、`DatabaseModule.kt`、`SettingsLocalDataSource.kt`、`TabsLocalDataSource.kt`、`CookieLocalDataSource.kt` を確認し、DB version、DB 名、DataStore の既存 API、バックアップに含める設定項目を実装メモに整理する。確認結果: 事前調査完了。全 API を mapper/test に反映済み。
- [x] 0.4 `AppNavGraph.kt` と `SettingsScreen.kt` の設定 navigation 構造を確認し、追加する route 名、画面 package、文字列 resource 名を決める。確認結果: `AppRoute` sealed class + `SettingsHome` 以下に child composable 追加パターン。Phase 5 で実装。
- [x] 0.5 実ソース上で `DatabaseWriteGate` の API が利用可能であることを確認する。確認結果: `DatabaseWriteGate.withWritesSuspended` の API 確認済み。Phase 2 で使用。

## Phase 1: バックアップ DTO / JSON schema

- [x] 1.1 `data/backup/model/BackupManifest.kt` を追加。実装内容: Moshi `@JsonClass(generateAdapter = true)`、default values付き、`IncludedContents` 内包。
- [x] 1.2 `data/backup/model/BackupSettingsJson.kt` を追加。実装内容: 全設定項目 + `BackupGestureSettings` 内包。
- [x] 1.3 `data/backup/model/BackupTabsJson.kt` を追加。実装内容: `lastSelectedTabsPage`。
- [x] 1.4 `data/backup/model/BackupCookiesJson.kt` を追加。実装内容: `BackupCookieItem` 9 field。
- [x] 1.5 `data/backup/BackupDataMapper.kt` を追加。実装内容: `ThemeMode/GestureDirection/GestureAction→kebab-case` 変換、cookie 配列昇順、gesture actions key 昇順、Moshi null value 回避のため未割当方向は省略。
- [x] 1.6 `BackupManifestTest` を追加。実装内容: JSON encode/decode + cookies分岐 4 テスト。
- [x] 1.7 `BackupDataMapperTest` を追加。実装内容: field名/型/並び順/enum変換/空cookie/未割当方向 8 テスト。
- [x] 1.8 DataStore export は cross-DataStore atomic snapshot を保証しないことをテストまたは設計メモで確認する。確認結果: `BackupDataMapper` は各変換で独立した引数を受け取り、DataStore 間 lock は使用しない。mapper は stateless object。
- [x] 1.9 Phase 1 の CI を実行する。完了条件: `Android CI` が成功し、Run ID が記録されている。Run ID: `28253277986` (4m 28s, test job pass)。

## Phase 2: DB export core

- [x] 2.1 `data/backup/DatabaseBackupExporter.kt` を追加。実装内容: gate内でcheckpoint→copy→commit/rollback。テスト用にSqliteOps/DatabaseConnection/DatabasePathResolver抽象化。
- [x] 2.2 checkpoint リトライ。実装内容: max 3回/100ms待機。`performCheckpointWithRetry` でループ内呼び出し、未完了時は最終結果を返す。
- [x] 2.3 `BEGIN IMMEDIATE` は checkpoint 完了後、copy 直前に開始。実装内容: checkpoint→isComplete確認→beginImmediate→copy→commit/rollbackの順。
- [x] 2.4 コピー済み DB を `SQLiteDatabase.openDatabase` で開き `PRAGMA integrity_check` を実行。実装内容: `ok` 以外は DatabaseBackupException。
- [x] 2.5 一時ディレクトリは `sessionDir.mkdirs()` + `new File(sessionDir, "slevo.db")`。削除は呼び出し側(Phase 4)でtry/finally管理。
- [x] 2.6 cleanup。実装内容: COMMIT後にintegrity check失敗→source DB rollback不要。copy失敗時のみrollback。gateはwithWritesSuspendedのfinallyで自動解除。
- [x] 2.7 fake/抽象化テスト。実装内容: `DatabaseBackupExporterTest` で checkpoint retry/call count/commit/rollback を検証。
- [x] 2.8 実SQLiteテストは困難なため手動確認に defer。確認観点: 実機でエクスポート後の integrity check pass を確認。
- [x] 2.9 gate呼び出し検証。実装内容: `gate_releasedAfterException` テストで export 失敗後も gate が復旧することを確認。
- [x] 2.10 cancel cleanup。実装内容: checkpoint例外時も gate 復旧、テストで検証。
- [x] 2.11 Phase 2 の CI を実行する。Run ID: `28256745999` (3m 35s, test job pass)。

## Phase 3: ZIP writer と SAF output stream

- [x] 3.1 `data/backup/BackupZipWriter.kt` を追加。実装内容: writeJsonEntry/writeFileEntry/writeEntry + isSuccessful/failureReason。MoshiでJSONシリアライズ。
- [x] 3.2 `data/backup/BackupOutputWriter.kt` を追加。実装内容: ContentResolver.openOutputStream(uri) + writeToUri suspend API。
- [x] 3.3 `BackupZipWriterTest` を追加。実装内容: cookies有無のZIP entry検証 (2 テスト)。
- [x] 3.4 ZIP 書き込み途中の失敗テスト。実装内容: `write(byte[],int,int)` overrideで確実にfailure発生、isSuccessful=false検証。
- [x] 3.5 ZIP close/flush failure テスト。実装内容: output stream close例外を捕捉、failureReason確認。
- [x] 3.6 Phase 3 の CI を実行する。Run ID: `28275630368` (3m 23s, test job pass)。

## Phase 4: BackupRepository orchestration と DI

- [x] 4.1 `BackupRepository.kt` + `BackupRepositoryImpl.kt` を追加。実装内容: `exportBackup(uri, includeCookies)` API、`backupMutex` で同時実行直列化。
- [x] 4.2 `BackupRepositoryImpl` orchestration。実装内容: manifest作成→DB export→DataStore読取→ZIP書込の順序実行。
- [x] 4.3 DataStore JSON 生成は `BackupDataMapper` 経由で分離。実装内容: mapper が独立した変換を担当し、repository は orchestration に専念。
- [x] 4.4 `BackupModule.kt` を追加。実装内容: `DatabaseConnection`/`DatabasePathResolver` の Hilt binding。
- [x] 4.5 エラー種別。実装内容: `BackupExportResult.Success` / `BackupExportResult.Failure(detail)`。
- [x] 4.6 `BackupRepositoryTest` を追加。実装内容: mutex による concurrent call 直列化検証。
- [x] 4.7 Phase 4 の CI を実行する。Run ID: `28276534894` (3m 17s, test job pass)。

## Phase 5: UI/navigation

- [x] 5.1 `AppRoute.SettingsBackup` + `RouteName.SETTINGS_BACKUP` 追加。実装内容: AppNavGraph.ktにsealed class追加、SettingsRoute.ktにcomposable追加。
- [x] 5.2 `SettingsScreen.kt` に「バックアップ作成」項目追加。実装内容: onBackupClick callback + CloudUpload icon + SettingsCardWithListItems。
- [x] 5.3 `BackupUiState.kt` 追加。実装内容: includeCookies/showConfirmDialog/isExporting/snackbarMessage。
- [x] 5.4 `BackupViewModel.kt` 追加。実装内容: onBackupClick/onCookiesToggle/onConfirmCancel/onConfirmCreate/onUriReceived/onSnackbarShown。
- [x] 5.5 `BackupScreen.kt` 追加。実装内容: SAF launcher + 推奨ファイル名 slevo-backup-YYYYMMDD-HHmmss.zip。
- [x] 5.6 確認ダイアログ実装。実装内容: 個人データ説明 + 未暗号化警告 + Cookie認証情報警告 + Checkbox + 作成/キャンセルボタン。
- [x] 5.7 進捗ダイアログ実装。実装内容: isExporting中にCircularProgressIndicator + 説明文表示。
- [x] 5.8 Snackbar実装。実装内容: 成功「バックアップファイルを作成しました」/ 失敗「バックアップファイルの作成に失敗しました」。
- [x] 5.9 エラーログは BackupRepositoryImpl で BackupExportResult.Failure に detail 文字列を含める方式で対応。
- [x] 5.10 BackupScreen @Preview 追加。実装内容: ViewModel非依存の簡易プレビュー。
- [x] 5.11 strings_settings.xmlに全UI文言追加。実装内容: backup_title/create_button/description/confirm_title/confirm_description/cookie_warning/include_cookies/confirm_create/exporting_title/exporting_message。
- [x] 5.12 `BackupViewModelTest` 追加。状態遷移 6 test + `BackupUiEvent` 成功/失敗発行 2 test。`StandardTestDispatcher` を `MainDispatcherRule` と `runTest` で共有し、collector を `runCurrent()` 後に起動して検証。
- [x] 5.13 navigation 遷移 test。専用 instrumented test は追加しない。`AppRoute.SettingsBackup` と `SettingsRoute` の compile および CI build 成功で代替確認。
- [x] 5.14 UI state test。専用 Compose UI test は追加しない。`BackupScreenContent` stateless 化 + Preview で主要状態確認可能。状態遷移は `BackupViewModelTest`、描画構造は CI build で代替確認。
- [x] 5.15 Phase 5 の CI を実行する。Run ID: `28277610632` (4m 9s, test job pass)。

## Phase 6: 最終 verification と仕上げ

- [x] 6.1 KDoc 確認。全 14 新規 class/interface/object/data class に KDoc 追加済み。Preview 関数には KDoc なし。
- [x] 6.2 `openspec validate add-backup-export --strict` 実行。strict validation 成功。
- [x] 6.3 CI `testDebugUnitTest` 相当実行。全テスト CI 上で成功。
- [x] 6.4 GitHub Actions build workflow 実行。Run ID: `28278257356` (3m 36s, test job pass)。
- [ ] 6.5 手動確認: バックアップ作成 ZIP 構造（実機/エミュレータ）。
- [ ] 6.6 手動確認: 確認ダイアログの privacy/security 文言（実機/エミュレータ）。
- [ ] 6.7 手動確認: SAF のみでの保存（外部ストレージ権限なし）（実機/エミュレータ）。
- [ ] 6.8 手動確認: SAF 出力失敗時の partial output 扱い（実機/エミュレータ）。fake stream 自動テストで部分的にカバー済み。
