## Context

現在の Slevo は、永続データを Room DB と Preference DataStore に分けて保存している。

この節の DB version、DB 名、DataStore API、設定 field、navigation 構造は計画作成時点のソース調査に基づく。実装前の task 1.1 / 1.2 で必ず再確認し、実ソースと差分がある場合は実装前に design/tasks/spec の該当箇所を更新する。

- Room DB:
  - `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/AppDatabase.kt`
  - `version = 9`, `exportSchema = true`
  - ブックマーク、履歴、NG、タブ、掲示板情報、キャッシュ、投稿履歴などを含む。
  - DB 名は `app/src/main/java/com/websarva/wings/android/slevo/di/DatabaseModule.kt` の `provideAppDatabase()` で、Debug は `slevo_dev_database`、Release は `slevo_database` として生成されている。
- DataStore:
  - `SettingsLocalDataSource` / `SettingsLocalDataSourceImpl`: テーマ、文字倍率、ジェスチャー、5ch.net → 5ch.io リダイレクト等。
  - `TabsLocalDataSource` / `TabsLocalDataSourceImpl`: タブ画面の最終選択ページ。
  - `CookieLocalDataSource` / `CookieLocalDataSourceImpl`: OkHttp Cookie の永続化。

既存 UI は `AppNavGraph.kt` の `AppRoute.Settings*` と設定系 navigation、`SettingsScreen.kt` の設定リストで構成されている。バックアップ機能は設定画面配下に「バックアップ作成」として追加するのが最も自然である。復元は非対応のため、初期実装では「バックアップと復元」という画面名・設定項目名を使わない。

外部ファイル出力は Android Storage Access Framework を使う。ユーザーが保存先を選ぶため、`WRITE_EXTERNAL_STORAGE`、`MANAGE_EXTERNAL_STORAGE`、`FileProvider` の追加は不要である。

## Goals / Non-Goals

**Goals:**

- 設定画面からバックアップ画面へ遷移できる。
- ユーザーが Android のファイル作成 UI で選択した保存先へ、`application/zip` と `.zip` 推奨ファイル名を使って単一 ZIP ファイルを出力できる。
- ZIP には Room DB の一貫性ある SQLite ファイル、通常設定 JSON、タブ選択 JSON、manifest JSON を含める。
- Room DB 書き込み停止は別変更 `add-database-write-gate` の `DatabaseWriteGate` を利用する。
- クッキー JSON は、バックアップ作成ボタン押下後の確認ダイアログでユーザーが明示的に「クッキーを含める」を有効にした場合のみ含める。
- バックアップ内部の DataStore データは `.preferences_pb` ではなく JSON として保存する。
- 確認ダイアログ、処理中ダイアログ、成功/失敗の Snackbar、クッキー含有有無を `UiState` で画面表示できる。
- バックアップ形式は、将来の復元実装で manifest を読んで互換性判定できる構造にする。

**Non-Goals:**

- この変更では復元処理を実装しない。
- DB テーブルごとの個別選択バックアップは実装しない。
- 暗号化、パスワード保護、自動バックアップ、クラウド同期は実装しない。
- Room schema version の変更や既存テーブル構造の変更は行わない。
- Android Auto Backup の `backup_rules.xml` / `data_extraction_rules.xml` は変更しない。

## Decisions

### 1. バックアップ単位は「フルバックアップ + クッキー任意」にする

バックアップ画面では、DB と通常設定は常に含め、クッキーのみ任意にする。クッキーの選択 UI は常時表示ではなく、ユーザーが「バックアップを作成」を押した後に表示する確認ダイアログ内へ配置する。

- 常に含める:
  - Room DB 全体
  - 通常設定 DataStore
  - タブ選択 DataStore
  - `manifest.json`
- 任意:
  - Cookie DataStore

DB テーブルごとの個別選択は、Room の外部キー、履歴、ブックマーク、タブ状態の依存関係を壊しやすい。初期実装では SQLite DB 全体を一貫性ある単位として扱う。

代替案として、履歴やブックマークを JSON へ個別変換する方式も考えたが、復元時の ID 衝突、外部キー再構築、重複判定が必要になるため採用しない。

### 2. ZIP 構造は固定パスにする

バックアップファイルの MIME type は `application/zip` とし、ユーザーに提示する推奨ファイル名の拡張子は `.zip` とする。SAF の provider またはユーザー操作により最終表示名が変わる可能性があるため、アプリは返却された `Uri` の表示名から拡張子を強制判定しない。内部パスは固定する。

```text
slevo-backup-YYYYMMDD-HHmmss.zip
├── manifest.json
├── database/
│   └── slevo.db
└── datastore/
    ├── settings.json
    ├── tabs.json
    └── cookies.json   # includeCookies = true の場合のみ
```

`manifest.json` 例:

```json
{
  "backupFormatVersion": 1,
  "backupMode": "full",
  "createdAt": "2026-06-22T12:00:00Z",
  "appVersionCode": 1,
  "appVersionName": "1.0.0",
  "databaseVersion": 9,
  "included": {
    "database": true,
    "settings": true,
    "tabs": true,
    "cookies": false
  }
}
```

推奨ファイル名は `slevo-backup-YYYYMMDD-HHmmss.zip` とする。日時は端末ローカル時刻で生成し、月日・時分秒は 0 埋めする。この形式は SAF に渡す suggested display name の UI 契約であり、provider が実際に保存する表示名は保証しない。復元時の判定はファイル名ではなく `manifest.json` で行う。

`manifest.json` の schema は以下を version 1 の互換性契約とする。

| field | type | required | value / default |
|---|---|---:|---|
| `backupFormatVersion` | integer | yes | `1` 固定 |
| `backupMode` | string | yes | `"full"` 固定 |
| `createdAt` | string | yes | ISO-8601 UTC timestamp |
| `appVersionCode` | integer | yes | 作成元アプリの versionCode |
| `appVersionName` | string | yes | 作成元アプリの versionName。取得不能時は空文字 |
| `databaseVersion` | integer | yes | Room schema version。初期実装では `9` |
| `included.database` | boolean | yes | `true` 固定 |
| `included.settings` | boolean | yes | `true` 固定 |
| `included.tabs` | boolean | yes | `true` 固定 |
| `included.cookies` | boolean | yes | 確認ダイアログの選択値 |

将来の復元実装では `backupFormatVersion`、`databaseVersion`、`included.cookies` を確認して復元可否と確認文言を決定する。

### 3. DataStore は JSON としてエクスポートする

DataStore の物理ファイル `.preferences_pb` はバックアップに含めない。理由は以下の通り。

- DataStore の内部ファイル形式やキー構造に強く依存しない。
- 復元実装時に現在の DataStore API へ明示的に再投入できる。
- クッキーだけを安全に除外しやすい。
- ZIP を展開して調査しやすい。

実装では DataStore の既存インターフェースに、バックアップ用途の一括取得/一括反映を追加するか、新規 `BackupSettingsDataSource` 相当を作る。既存 interface に追加する場合は以下を検討する。

- `SettingsLocalDataSource`:
  - `suspend fun getBackupSettings(): BackupSettingsJson`
  - 復元を見据えるなら `suspend fun applyBackupSettings(settings: BackupSettingsJson)` も設計しておくが、初期実装では未使用でもよい。
- `TabsLocalDataSource`:
  - `suspend fun getBackupTabs(): BackupTabsJson`
- `CookieLocalDataSource`:
  - 既存 `getCookies(): Flow<List<Cookie>>` から `first()` で取得できる。

JSON モデルは Entity や UI State と分け、`data/backup/model/` などにバックアップ専用 DTO として置く。

DataStore JSON は各 DataStore から順に取得した最新値を保存する。初期実装では `settings`、`tabs`、`cookies` を横断した原子的スナップショット整合性は保証しない。各 JSON は取得時点の値として扱い、Room DB の checkpoint/copy の整合性制御とは独立させる。

JSON serialization は既存方針に合わせて Moshi の codegen adapter を使う。バックアップ DTO は `@JsonClass(generateAdapter = true)` を付け、field 名は DTO property 名をそのまま使う。出力の安定性は、配列の並び順と object key の生成元をテストで固定する。Moshi 以外の serializer を使う場合は、この design と tasks を更新してから実装する。

`datastore/settings.json` は以下の schema を version 1 の互換性契約とする。enum は Kotlin enum 名ではなく小文字 kebab-case 文字列として保存する。未知の enum 値は将来の復元で既定値へフォールバックできるよう、初期実装では出力しない。

| field | type | required | nullable | default when absent in future restore |
|---|---|---:|---:|---|
| `themeMode` | string | yes | no | `"system"` |
| `isTreeSort` | boolean | yes | no | `false` |
| `isThreadMinimapScrollbarEnabled` | boolean | yes | no | `true` |
| `textScale` | number | yes | no | `1.0` |
| `isIndividualTextScale` | boolean | yes | no | `false` |
| `headerTextScale` | number | yes | no | `1.0` |
| `bodyTextScale` | number | yes | no | `1.0` |
| `lineHeight` | number | yes | no | `1.0` |
| `isRedirect5chNetToIoEnabled` | boolean | yes | no | `false` |
| `gestureSettings` | object | yes | no | default gesture settings |
| `gestureSettings.enabled` | boolean | yes | no | `true` |
| `gestureSettings.showActionHints` | boolean | yes | no | `true` |
| `gestureSettings.actions` | object | yes | no | `{}` |

`gestureSettings.actions` は gesture direction の小文字 kebab-case 文字列を key、gesture action の小文字 kebab-case 文字列または `null` を value とする object として保存する。object の key は昇順で出力し、差分比較しやすくする。

`datastore/tabs.json` は以下の schema とする。

| field | type | required | nullable | default when absent in future restore |
|---|---|---:|---:|---|
| `lastSelectedTabsPage` | integer | yes | no | `0` |

`datastore/cookies.json` は以下の schema とする。

```json
{
  "cookies": [
    {
      "name": "",
      "value": "",
      "domain": "",
      "path": "/",
      "expiresAt": 0,
      "secure": false,
      "httpOnly": false,
      "hostOnly": false,
      "persistent": false
    }
  ]
}
```

Cookie item の必須 field は `name`、`value`、`domain`、`path`、`expiresAt`、`secure`、`httpOnly`、`hostOnly`、`persistent` の 9 個とする。`cookies` 配列は `domain`、`path`、`name` の昇順で出力する。Cookie に復元不能な属性がある場合は、初期実装では属性を追加せず、上記 9 field の範囲で lossless に扱える値だけを保存する。

### 4. Room DB は SDK 24 互換の checkpoint + DB ファイルコピーで出力する

`VACUUM INTO` は SQLite 3.27.0 以降の機能で、SDK 24 では利用できない。そのため、DB エクスポートの主経路として `VACUUM INTO` を使わない。

`context.getDatabasePath(databaseName)` を無条件に単純コピーする方式も、WAL の `-wal` / `-shm` と書き込み中状態を考慮できないため避ける。実装では WAL checkpoint の完了を確認し、その後に書き込みロックを取って main DB ファイルだけを一時ファイルへコピーする。

処理イメージ:

1. `BackupRepository` が `backupMutex` でバックアップ処理同士の多重実行を防ぐ。
2. `DatabaseWriteGate` でアプリ内の新規 DB 書き込みを待機させる。
3. `DatabaseWriteGate` 導入済みの進行中 DB 書き込みが完了するまで待つ。
4. `AppDatabase.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)")` を実行する。
5. checkpoint 結果の `busy`、`log`、`checkpointed` を必ず読み取る。
6. `busy == 0` かつ `log == checkpointed` の場合のみ次へ進む。
7. checkpoint 未完了の場合は最大 3 回までリトライし、各リトライ前に 100ms 待機する。回数と待機時間は `DatabaseBackupExporter` 内の定数として定義し、テストで差し替え可能にする。
8. リトライ後も `busy != 0` または `log != checkpointed` の場合は DB エクスポート失敗として詳細ログを残し、バックアップ全体を失敗させる。
9. checkpoint 完了後に `BEGIN IMMEDIATE` を実行して、コピー中に新規 writer が入らない状態にする。
10. `context.getDatabasePath(databaseName)` の main DB ファイルを `cacheDir/backups/<session>/database/slevo.db` へコピーする。
11. コピーが成功したら `COMMIT`、失敗したら `ROLLBACK` を実行する。
12. `DatabaseWriteGate` を解除する。
13. コピーした `slevo.db` を読み取り専用で開き、`PRAGMA integrity_check` を実行する。
14. `integrity_check` が `ok` を返した場合のみ、生成した `slevo.db` を ZIP の `database/slevo.db` に追加する。失敗または検証不能の場合は DB エクスポート失敗として詳細ログを残し、バックアップ全体を失敗させる。

`backupMutex` はバックアップ同士の多重実行防止に限定し、アプリ内 DB 書き込み抑制には使わない。アプリ内 DB 書き込み抑制には、別変更 `add-database-write-gate` で導入済みの `DatabaseWriteGate` を利用する。バックアップ側は `DatabaseWriteGate.withWritesSuspended { ... }` の block 内で checkpoint と main DB コピーを実行する。

`BackupRepository` は repository/data 層でもバックアップ作成の多重実行を防ぐ。UI の disabled 状態だけに依存せず、`exportBackup(uri, includeCookies)` 相当の API は内部の `backupMutex` で 1 件ずつ実行する。2 件目以降の同時要求は、先行要求の完了まで待機してから開始する。将来の要件で即時失敗に変える場合は spec を更新する。

`add-backup-export` の実装前提として、Room DB への既存書き込み経路は `add-database-write-gate` により `DatabaseWriteGate.withWritePermit { ... }` 経由へ移行済みであること。未実装の場合はこの変更の実装を開始しない。

### 5. UI は確認ダイアログを挟んでから SAF launcher を起動する

`BackupScreen` で `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"))` を使う。保存ボタン押下時はすぐに launcher を起動せず、確認ダイアログを表示する。確認ダイアログには、バックアップに含まれる通常データの説明、クッキーがセンシティブ情報を含む可能性の説明、「クッキーを含める」チェックボックス、キャンセルボタン、作成ボタンを配置する。

確認ダイアログの作成ボタン押下時に、現在の `includeCookies` を ViewModel に保持した上で推奨ファイル名を生成して launcher を起動する。launcher から `Uri` が返ったら、その `Uri` と保持済みの `includeCookies` を `BackupViewModel` に渡す。ユーザーがダイアログをキャンセルした場合、SAF launcher は起動しない。

ViewModel は `StateFlow<BackupUiState>` を公開する。状態例:

- `includeCookies: Boolean`
- `showConfirmDialog: Boolean`
- `isExporting: Boolean`
- `lastResult: BackupResultMessage?`
- `errorMessage: String?`

Composable は状態を描画し、ビジネスロジック、ZIP 書き込み、DB 操作を持たない。

処理中表示は `isExporting == true` の間、モーダルの進捗ダイアログとして表示する。ダイアログにはタイトル「バックアップを作成中」、本文「データを書き出しています。しばらくお待ちください。」、`CircularProgressIndicator` を表示する。処理中はユーザーが重複実行できないように、バックアップ作成ボタン、確認ダイアログの作成ボタン、クッキー checkbox を無効化する。

成功/失敗は Snackbar で短く通知する。成功時は「バックアップファイルを作成しました」、失敗時は「バックアップファイルの作成に失敗しました」を表示する。詳細エラーはユーザー向け UI には表示せず、既存の logging 方針に合わせてログへ出力する。保存先選択キャンセルはエラー扱いにせず、Snackbar も表示しない。

### 6. 依存注入と配置

推奨配置:

```text
app/src/main/java/com/websarva/wings/android/slevo/data/backup/
├── BackupRepository.kt
├── BackupRepositoryImpl.kt
├── BackupZipWriter.kt
├── DatabaseBackupExporter.kt
├── DataStoreBackupExporter.kt
├── model/BackupManifest.kt
├── model/BackupSettingsJson.kt
├── model/BackupTabsJson.kt
└── model/BackupCookiesJson.kt

app/src/main/java/com/websarva/wings/android/slevo/ui/settings/backup/
├── BackupScreen.kt
├── BackupViewModel.kt
└── BackupUiState.kt
```

Hilt binding は既存の `DataSourceModule.kt` に詰め込みすぎず、必要に応じて `BackupModule.kt` を新規作成する。

### 7. エラー処理

最低限、以下を区別して ViewModel へ返す。ユーザー向け UI では失敗 Snackbar を共通文言にし、詳細はログへ出力する。

- ユーザーがファイル作成 UI をキャンセルした場合: エラーにしない。
- `ContentResolver.openOutputStream(uri)` が null を返した場合: 詳細ログへ「保存先を開けませんでした」を記録する。
- DB エクスポート失敗: 詳細ログへ「データベースのバックアップに失敗しました」を記録する。
- コピー済み DB の integrity check 失敗: 詳細ログへ「バックアップ DB の整合性検証に失敗しました」を記録する。
- JSON 変換失敗: 詳細ログへ「設定データの変換に失敗しました」を記録する。
- ZIP 書き込み失敗: 詳細ログへ「バックアップファイルの作成に失敗しました」を記録する。
- SAF `Uri` への ZIP 書き込み途中で失敗した場合: success Snackbar を表示せず、出力先ファイルが不完全な可能性を詳細ログへ記録する。可能であれば stream を close し、provider が削除または truncate をサポートする場合だけ best-effort cleanup を行う。cleanup 不能時もユーザー向け表示は共通失敗 Snackbar に統一する。

例外を握りつぶさずログへ出力し、ViewModel では失敗種別を共通のユーザー向け失敗メッセージへ変換する。

## Risks / Trade-offs

- [Risk] `add-database-write-gate` が未実装、または移行漏れがある状態でバックアップを実装すると、コピー中に WAL が再生成される。 → この変更は `add-database-write-gate` 完了後に実装し、バックアップ側では `withWritesSuspended` を必ず使う。
- [Risk] checkpoint が busy または未完了のまま main DB をコピーすると、WAL 未反映分が欠落する。 → checkpoint 結果の `busy`、`log`、`checkpointed` を確認し、未完了ならリトライ後に失敗扱いにする。
- [Risk] コピー処理自体は成功しても DB ファイルが読み取り不能な可能性がある。 → コピー後に読み取り専用 open と `PRAGMA integrity_check` を実行し、`ok` 以外なら失敗扱いにする。
- [Risk] DB エクスポート中に UI が再押下される。 → `isExporting` 中は保存ボタン、確認ダイアログの作成ボタン、クッキー checkbox を無効化する。
- [Risk] クッキーを含むバックアップはセンシティブ情報を含む。 → 確認ダイアログ内の checkbox はデフォルト OFF にし、同じダイアログ内で認証情報が含まれる可能性を明示する。
- [Risk] DataStore の JSON モデルと既存設定キーがずれる。 → `BackupSettingsJson` 生成テストで既存設定を網羅し、設定追加時にモデル更新が必要なことを KDoc に記載する。
- [Risk] ZIP 作成途中に失敗した場合、一時ファイルが残る。 → `cacheDir/backups/<session>` は `try/finally` で削除する。
- [Risk] 復元未実装のためユーザーが期待を誤解する。 → 画面文言は「バックアップを作成」に限定し、復元ボタンは初期実装では表示しない。

## Migration Plan

1. 新規画面とバックアップ作成機能を追加する。
2. 既存データ構造は変更しないため、Room migration は追加しない。
3. リリース後に問題があれば、設定画面の導線を非表示にするだけで既存データへ影響なくロールバックできる。

## Implementation Contract

- アプリコード実装時は、すべての新規 class/interface/object/data class に KDoc を追加する。
- Compose Preview 関数には KDoc を追加しない。
- 新規 Composable には意味のある `@Preview` を追加する。
- `BackupScreen` は描画、確認ダイアログ表示、launcher 起動のみを担当し、ZIP/DB/DataStore 処理を持たない。
- `BackupScreen` は `isExporting == true` の間、閉じる操作を持たないモーダル進捗ダイアログを表示する。
- `BackupScreen` は成功/失敗を Snackbar で表示し、詳細エラー文言や例外 stack trace を画面に表示しない。
- `BackupViewModel` は `BackupRepository` のみに依存し、`ContentResolver` 直接操作を持たない。
- `BackupRepository` は `Uri` への出力を repository/data 層へ委譲し、処理結果を sealed class または Result 型で返す。
- この変更は `add-database-write-gate` の実装完了を前提にし、`DatabaseWriteGate` 自体や既存 Repository の gate 移行を含めない。
- `BackupRepository.exportBackup` は repository/data 層の `backupMutex` で多重実行を防ぎ、直接 concurrent call されても 1 件ずつ実行する。
- バックアップ ZIP は `CreateDocument("application/zip")` で作成し、推奨ファイル名として `slevo-backup-YYYYMMDD-HHmmss.zip` を渡す。返却後の provider 側表示名が `.zip` で終わることは前提にしない。
- ZIP 書き込みの成功判定は、全 entry の書き込み、ZIP stream の close、output stream の close が完了した後にだけ行う。
- ZIP 書き込み途中で失敗した場合は success Snackbar を表示せず、出力先ファイルが不完全な可能性をログへ記録する。削除または truncate は provider が安全に実行可能な場合だけ best-effort で行う。
- 設定項目名と画面タイトルは「バックアップ作成」とし、復元 UI を表示してはならない。
- DB エクスポートで `VACUUM INTO` を使ってはならない。
- `PRAGMA wal_checkpoint(TRUNCATE)` は `BEGIN IMMEDIATE` の前に実行し、checkpoint 結果を確認してから DB ファイルコピーへ進む。
- `BEGIN IMMEDIATE` は checkpoint 完了後、main DB ファイルコピー直前に開始する。
- コピー済み DB は ZIP へ追加する前に読み取り専用 open と `PRAGMA integrity_check` で検証する。
- バックアップ中は `DatabaseWriteGate.withWritesSuspended` を使い、新規 DB 書き込みを待機させる。
- DataStore のバックアップ内容は `.preferences_pb` コピーではなく JSON DTO から生成する。
- Cookie は `includeCookies == true` の場合のみ `datastore/cookies.json` と manifest の `included.cookies = true` を出力する。
- `includeCookies == false` の場合は `cookies.json` を ZIP に含めてはならない。
- 「クッキーを含める」選択はバックアップ画面本体ではなく、バックアップ作成ボタン押下後の確認ダイアログ内に配置する。
- 確認ダイアログのキャンセル時は SAF launcher を起動してはならない。
- 外部ストレージ権限、`MANAGE_EXTERNAL_STORAGE`、FileProvider の追加は禁止する。

## Testing Strategy

- JVM unit test:
  - `BackupManifest` の JSON encode/decode。
  - `BackupSettingsJson` / `BackupTabsJson` / `BackupCookiesJson` の field 名、型、並び順、cookie 必須 field の変換。
  - ZIP writer が期待する entry 名を作ること。
  - `includeCookies = false` のとき `datastore/cookies.json` が存在しないこと。
  - `includeCookies = true` のとき `datastore/cookies.json` と manifest が一致すること。
- Robolectric または AndroidX test:
  - `ContentResolver.openOutputStream(uri)` 相当を使った保存処理の成功/失敗。
  - `BackupViewModel` の確認ダイアログ表示/非表示、`includeCookies`、`isExporting`、成功、失敗状態遷移。
  - 成功時に成功 Snackbar 用イベント、失敗時に共通失敗 Snackbar 用イベント、保存先選択キャンセル時にイベントなしとなること。
- Room 関連:
  - `DatabaseBackupExporter` の checkpoint 結果処理、最大 3 回リトライ、待機処理の差し替え、checkpoint 未完了時に main DB コピーへ進まないこと、integrity check 成功/失敗分岐は fake/抽象化を使って必ず自動テストする。
  - 可能であれば追加で in-memory ではなく一時ファイル DB を使い、`PRAGMA wal_checkpoint(TRUNCATE)` の結果確認と main DB ファイルコピーを検証する。
  - checkpoint リトライが最大 3 回、各 100ms 待機の方針で実行され、テストでは待機処理を差し替えて決定的に検証できること。
  - `DatabaseWriteGate.withWritesSuspended` が呼ばれることを fake gate で検証する。
  - `BackupRepository.exportBackup` を concurrent call した場合でも 1 件ずつ実行されることを fake writer で検証する。
  - コピー済み DB の読み取り専用 open と `PRAGMA integrity_check` が成功した場合だけ ZIP へ進むことを検証する。
- UI/navigation:
  - Compose UI または instrumented test で、設定画面の「バックアップ作成」項目からバックアップ作成画面へ遷移できることを検証する。
  - Compose UI または ViewModel + UI state test で、確認ダイアログ、クッキー checkbox の初期未選択かつ処理中でなければ選択可能な状態、進捗ダイアログ、成功/失敗 Snackbar を検証する。
- 実装後の確認コマンド:
  - CI で既存の build/test workflow を実行する。ローカル Gradle 実行は明示指示がある場合のみ行う。

## Open Questions

- なし。Room DB 書き込み制御は別変更 `add-database-write-gate` の完了を前提にする。
