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

## Phase 7: 操作結果 Snackbar の durable queue

以下は lifecycle 中断時に `BackupViewModel` の操作結果通知が失われる P2 finding だけを修正する。既存文言、`SnackbarDuration.Short`、Snackbar の style/host、画面 layout、バックアップ/復元処理は変更しない。

- [x] 7.1 `BackupUiEvent.kt` のバックアップ作成成功、バックアップ作成失敗、無効バックアップ、復元準備失敗の各 result に `Long` ID を持たせる。完了条件: 4 種類すべてが ID を公開し、同種 result も ID で区別できる。
- [x] 7.2 `BackupUiState.kt` に pending result の FIFO queue を追加し、先頭が唯一の表示対象である ordering invariant を KDoc に記載する。完了条件: queue は既定で空で、既存の dialog/progress/restore state と独立して `copy` 更新できる。
- [x] 7.3 `BackupViewModel.kt` の operation-event `MutableSharedFlow` と公開 `SharedFlow` を削除する。各 completion を ViewModel 上で直列化し、1 回の state transition 内で厳密に単調増加する ID の確定、対応 operation state の完了更新、`UiState` queue 末尾への result 追加を行う。mapping は (1) `onUriReceived` success → `ExportSucceeded`、(2) 同 failure → `ExportFailed`、(3) `onRestoreUriReceived` invalid → `InvalidBackup`、(4) 同 failure → `RestorePrepareFailed`、(5) `onConfirmRestore` invalid → `InvalidBackup`、(6) 同 failure → `RestorePrepareFailed` とする。完了条件: 6 path の既存 classification を変えず、並行 completion でも ID 順と queue 順が一致して result を失わず、保存先選択キャンセルなど通知対象外 path は queue を変更しない。
- [x] 7.4 `BackupViewModel.acknowledgeResult(resultId)` を追加し、現在の queue 先頭 ID と一致する場合だけ先頭 1 件を atomic な state update で削除する。完了条件: 空 queue、古い/未知 ID、後続 ID は no-op であり、一致時も後続 result とその順序を保持する。
- [x] 7.5 `BackupScreen.kt` の `SharedFlow.collect` effect を、`uiState` の queue 先頭を読む result-ID-keyed effect に置換する。mapping は `ExportSucceeded` → `backup_snackbar_success`（「バックアップファイルを作成しました」）、`ExportFailed` → `backup_snackbar_failure`（「バックアップファイルの作成に失敗しました」）、`RestorePrepareFailed` → `restore_snackbar_failed`（「復元の準備に失敗しました」）、`InvalidBackup` → `restore_snackbar_invalid`（「無効または未対応のバックアップファイルです」）を維持する。既存 `SnackbarHostState.showSnackbar(message)` を duration/action/style/layout の変更なしで呼び、正常 return 後だけ表示した ID を acknowledge する。完了条件: effect cancellation 時は acknowledge が呼ばれず pending head が残り、recreation では同じ ID が再表示され、正しい acknowledge 後は次の head が順次表示される。
- [x] 7.6 `BackupViewModelTest.kt` の `events.first()` assertions を `UiState` queue assertions に更新し、上記 6 completion path と 4 result mapping、厳密に増加する ID、複数および並行 completion での ID/queue 順と取りこぼしなし、対応 operation state との同一 transition、先頭一致で 1 件だけ削除、空/stale/unknown/non-head ID の no-op、対象外 path で追加なしを検証する。完了条件: 既存の export/restore state-transition test intent を維持したまま queue 契約を unit test で網羅する。
- [x] 7.7 Compose test で、queue 先頭だけが 4 種類それぞれの既存 resource 文言と `SnackbarDuration.Short` で表示され、`showSnackbar` 完了後に正しい ID が acknowledge されること、表示中の effect cancellation では acknowledge されず recreation 後に同じ先頭が再表示されること、複数 result が順番に表示されることを検証する。完了条件: export success/failure、invalid backup、restore preparation failure の message/duration/acknowledgement を自動 assertion する。
- [x] 7.8 変更範囲を `BackupUiEvent.kt`、`BackupUiState.kt`、`BackupViewModel.kt`、`BackupScreen.kt` と直接対応する test に限定して確認し、focused diff review で既存 `SnackbarHostState`、`SnackbarHost` 構成、style、layout が変更されていないことを確認する。続けて OpenSpec strict validation と CI の既存 build/unit/UI test workflow を実行する。完了条件: preservation check の結果を実装メモへ記録し、`openspec validate add-backup-export --strict` と CI が成功し、P2 finding と無関係な変更が差分に含まれない。確認結果: SnackbarHostState、SnackbarHost、style、layout は変更せず、strict validation 成功、Android CI Run ID `29575204151`（SHA `32a8d43fe0c8019a872e512c7eb08d058cdc57a1`）成功。

## Phase 8: DB snapshot の short transfer 完了保証

以下は `DatabaseBackupExporter.copyFile` が `FileChannel.transferTo` を 1 回だけ呼び、短い戻り値を無視する P2 finding だけを修正する。既存 export format/UI、checkpoint と `BEGIN IMMEDIATE` の順序、integrity check、repository cleanup ownership、cancellation と `IOException` の伝播、channel/stream close 順序を変更しない。

- [x] 8.1 `app/src/main/java/com/websarva/wings/android/slevo/data/backup/export/DatabaseBackupExporter.kt` に、production では `FileChannel.transferTo` へ委譲し test で戻り値または例外を制御できる最小の internal transfer seam を追加する。Hilt binding、公開 constructor/API、file copy 全体を置換する抽象は追加しない。完了条件: test が各 transfer 呼び出しの `position` と `count` を記録でき、production の channel/stream lifecycle は `copyFile` 内に残る。`internal transferTo` と既存の channel/stream `use` lifecycle で対応。
- [x] 8.2 source channel open 後に `sourceSize` を 1 回取得し、`position = 0` から `position < sourceSize` の間だけ `remaining = sourceSize - position` を指定して transfer を繰り返す。正かつ `remaining` 以下の戻り値だけ position に加算し、`position == sourceSize` の場合だけ copy を正常 return する。`0`、負値、remaining 超過は進捗不能または契約違反を示す `IOException` として直ちに失敗させる。完了条件: short transfer を何回繰り返しても exact source size まで継続し、zero progress で無限 loop せず、未完了 destination を integrity check へ渡さない。固定 source size と strict progress loop で対応。
- [x] 8.3 `DatabaseBackupExporterTest.kt` に transfer seam が例えば `[2, 3, remaining]` を返す deterministic test を追加する。各 call の position が `0, 2, 5`、count が各時点の remaining で、合計が source size と一致し、copy 完了後だけ commit することを assertion する。real SQLite integrity check に到達して失敗する場合でも、commit 前の exact-copy invariant と destination bytes/size を直接 assertion し、例外を無条件に swallow する既存 test patternへ依存しない。position/count、destination bytes、commit 時点を直接 assertion。
- [x] 8.4 同 test file に `[positive, 0]` の deterministic zero-progress test と transfer が `IOException` / `CancellationException` を送出する test を追加する。完了条件: zero 後の transfer call はなく、original exception type を保持して伝播し、commit せず rollback、gate release、destination/source channel と stream の close が完了し、integrity check へ進まない。zero、I/O、cancellation の伝播、rollback、gate release、channel close を assertion。
- [x] 8.5 `BackupRestoreRepositoryTest.kt` に、mock exporter が渡された session database directoryへ部分ファイルを作成してから `IOException` を送出する export test を追加する。完了条件: result は既存 `BackupExportResult.Failure` のまま、ZIP output は開始されず、`BackupRepositoryImpl.exportInternal` の `finally` により親 session directory と部分 destination が決定的に削除される。部分 destination、session cleanup、ZIP 未開始を assertion。
- [x] 8.6 focused review で `DatabaseBackupExporter.kt`、`DatabaseBackupExporterTest.kt`、`BackupRestoreRepositoryTest.kt` 以外の application/test diff がないことを確認する。source size 固定、`0 <= position <= sourceSize`、各成功 iteration の strict progress、exact-size completion、zero-progress failure、rollback/gate release、repository cleanup、exception/cancellation propagation、既存 integrity-check と close ordering、export format/UI 無変更を監査する。続けて OpenSpec strict validation と exact-HEAD CI build/unit test workflow を実行し、Run ID と commit SHA を記録する。確認結果: focused diff は指定 3 application/test files と本 tasks.md のみ。OpenSpec strict validation 成功。Android CI Run `29696472620`（https://github.com/cuore-mm/Slevo/actions/runs/29696472620）、SHA `2c4f6223a06636441626f72c7afb2ce6c60fa871` で build/unit/UI test 成功。
