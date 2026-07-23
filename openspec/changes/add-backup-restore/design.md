## Context

現在の Slevo は `add-backup-export` により、設定画面から Room DB と DataStore を ZIP へ出力できる。バックアップ ZIP の version 1 形式は固定パスで定義済みである。

```text
backup.zip
├── manifest.json
├── database/
│   └── slevo.db
└── datastore/
    ├── settings.json
    ├── tabs.json
    └── cookies.json   # manifest.included.cookies = true の場合のみ
```

この節の DB version、DB 名、DataStore API、UI/navigation 構造は計画作成時点のソース調査に基づく。実装前の Phase 0 で必ず再確認し、差分がある場合は実装前に design/tasks/spec を更新する。

- Room DB:
  - `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/AppDatabase.kt`
  - `version = 9`, `exportSchema = true`
  - DB 名は `app/src/main/java/com/websarva/wings/android/slevo/di/DatabaseModule.kt` の `provideAppDatabase()` で Debug は `slevo_dev_database`、Release は `slevo_database`。
  - DB 書き込み経路は `add-database-write-gate` により `DatabaseWriteGate.withWritePermit` 経由へ移行済みであることを前提にする。
- DataStore:
  - `SettingsLocalDataSource` / `SettingsLocalDataSourceImpl`: テーマ、文字倍率、ジェスチャー、5ch.net → 5ch.io リダイレクト等。
  - `TabsLocalDataSource` / `TabsLocalDataSourceImpl`: 最終選択タブページ。
  - `CookieLocalDataSource` / `CookieLocalDataSourceImpl`: OkHttp Cookie の永続化。
  - startup restore で直接扱う Preferences DataStore file path は `<filesDir>/datastore/settings.preferences_pb`、`<filesDir>/datastore/tabs.preferences_pb`、`<filesDir>/datastore/cookies.preferences_pb`。
- バックアップ実装:
  - `data/backup/model/BackupManifest.kt`
  - `data/backup/model/BackupSettingsJson.kt`
  - `data/backup/model/BackupTabsJson.kt`
  - `data/backup/model/BackupCookiesJson.kt`
  - `data/backup/BackupDataMapper.kt`
  - `data/backup/BackupRepository.kt` / `BackupRepositoryImpl.kt`
  - `data/backup/DatabaseBackupExporter.kt`
  - `data/backup/BackupZipWriter.kt` / `BackupOutputWriter.kt`
- UI:
  - `ui/settings/backup/BackupScreen.kt` は `BackupScreen` route wrapper と stateless `BackupScreenContent` に分離済み。
  - `ui/settings/backup/BackupViewModel.kt` は `StateFlow<BackupUiState>` と `SharedFlow<BackupUiEvent>` を公開する。
  - `ui/navigation/AppNavGraph.kt` と `ui/navigation/SettingsRoute.kt` に設定系 route がある。

復元は作成済みバックアップを戻す destructive operation である。UI は新規の独立画面を作らず、既存の `ui/settings/backup/BackupScreen.kt` を「バックアップと復元」画面へ拡張し、同じ画面からバックアップ作成と復元を実行できるようにする。DB ファイル置換、DataStore 反映、Cookie 復元を含むため、UI だけでなく data/repository/DI/navigation へまたがる変更になる。

## Goals / Non-Goals

**Goals:**

- 設定画面から既存のバックアップ画面へ遷移し、その画面上でバックアップ作成と復元を選択できる。
- `ActivityResultContracts.OpenDocument` により、ユーザーがバックアップ ZIP を選択できる。
- 選択した ZIP から `manifest.json` を読み取り、復元前に作成日時、アプリ version、DB version、Cookie 含有有無を確認ダイアログへ表示できる。
- `backupFormatVersion = 1`、`backupMode = "full"`、`databaseVersion = 現在の Room DB version` のバックアップだけを復元可能にする。
- ZIP 内部パス、manifest の `included.*`、必須 JSON/DB の整合性を検証する。
- 復元は全上書きとし、Room DB、通常設定 DataStore、タブ選択 DataStore をバックアップ内容へ置き換える。
- Cookie はバックアップに含まれていて、かつユーザーが確認ダイアログで「クッキーを復元する」を有効にした場合のみ復元する。
- Room DB 置換時は次回アプリ起動時に `AppDatabase` 生成前へ適用し、実行中の Hilt singleton `AppDatabase` を close または再利用しない。
- 復元対象 DB は置換前に読み取り専用 open と `PRAGMA integrity_check` で検証する。
- 復元準備中は重複実行を防ぎ、進捗ダイアログ、準備完了/失敗/無効なバックアップの Snackbar を表示する。
- 外部ストレージ権限、`MANAGE_EXTERNAL_STORAGE`、FileProvider を追加しない。

**Non-Goals:**

- テーブル単位、設定項目単位、Cookie 単位の選択復元は実装しない。
- 既存データとのマージ復元は実装しない。
- `databaseVersion` が現在 version と異なるバックアップの migration/downgrade 復元は実装しない。
- 暗号化、パスワード保護、クラウド同期、自動復元は実装しない。
- バックアップ ZIP format version 2 以降の読み込みは実装しない。
- Room schema version や既存 entity 構造は変更しない。
- Android Auto Backup の `backup_rules.xml` / `data_extraction_rules.xml` は変更しない。

## Decisions

### 1. 復元は「全上書き」にする

Room DB はブックマーク、履歴、NG、タブ、投稿履歴など複数 entity と外部キーを含む。ID 衝突、参照整合性、重複判定を避けるため、初期実装ではマージを行わず、バックアップの DB ファイルで現在 DB を置き換える。

DataStore もバックアップ JSON の値を現在値へ反映する。settings/tabs/cookies をまたいだ原子的 transaction は DataStore には存在しないため、DB 置換と DataStore 反映を 1 つの完全 atomic operation として扱わない。失敗時は詳細ログへ記録し、ユーザー向けには共通失敗または無効なバックアップ通知へ変換する。

代替案として JSON から各 DB entity を merge/upsert する方式を検討できるが、外部キーと履歴系データの競合処理が大きくなるため採用しない。

### 2. v1 では同一 DB version のみ復元する

`manifest.databaseVersion` は現在の `AppDatabase.version` と一致する場合だけ許可する。計画時点では `9` である。古い DB version は Room migration chain で理論上移行可能な場合があるが、復元パスで Hilt singleton の live DB、DataStore、seed callback を含めた互換性検証が必要になるため、v1 ではスコープ外にする。新しい DB version は downgrade になるため必ず拒否する。

拒否時は `BackupRestoreResult.Invalid` 相当を返し、UI は「このバックアップは現在のアプリでは復元できません」のような Snackbar を表示する。詳細な version はログへ記録し、必要なら確認ダイアログの preview 情報に表示する。

### 3. ZIP 読み込みは固定パスと manifest で検証する

新規 `BackupReader` を `data/backup/` 配下へ追加する。役割は `InputStream` または staging file から ZIP entry を読み取り、以下の構造化データを返すことである。

```kotlin
BackupContent(
    manifest = BackupManifest,
    databaseFile = File,
    settings = BackupSettingsJson,
    tabs = BackupTabsJson,
    cookies = BackupCookiesJson?,
)
```

実装時の型名は変更してよいが、責務は分離する。ZIP entry は以下のみを有効にする。

- `manifest.json`
- `database/slevo.db`
- `datastore/settings.json`
- `datastore/tabs.json`
- `datastore/cookies.json`

`../`、絶対パス、空 entry 名、同一 entry の重複、directory traversal になる path は拒否する。未知 entry は初期実装では拒否する。将来 format version を拡張して任意 metadata を許可する場合は spec を更新する。

ZIP directory entry は `database/` と `datastore/` のみ許容し、内容を持たない directory marker として無視する。それ以外の directory entry は未知 entry として拒否する。restore reader の entry validation は file entry と directory entry を区別し、`database/` / `datastore/` directory entry が存在しても必須 file entry の存在判定には使わない。

`manifest.included.cookies = true` の場合は `datastore/cookies.json` が存在しなければ無効なバックアップとして扱う。`manifest.included.cookies = false` の場合に `datastore/cookies.json` が存在する場合も、manifest と ZIP が不一致なので無効なバックアップとして扱う。

### 4. 復元前に preview を取得して確認ダイアログへ表示する

Repository API は preview と commit を分ける。

```kotlin
suspend fun previewBackup(uri: Uri): BackupPreviewResult
suspend fun restoreBackup(uri: Uri, includeCookies: Boolean): BackupRestoreResult
```

`previewBackup` は ZIP を読み込み、manifest と構造を検証するが、DB/DataStore へ書き込まない。確認ダイアログへ表示する情報は以下とする。

- `createdAt`
- `appVersionName` / `appVersionCode`
- `databaseVersion`
- Cookie がバックアップに含まれているか
- 現在のデータが上書きされる警告

`restoreBackup` は preview 済みであっても再度 ZIP を読み込み検証する。SAF URI の内容が preview 後に変わる可能性があるため、commit 時の再検証を省略しない。

### 5. SAF input は repository/data 層で開く

UI は `ActivityResultContracts.OpenDocument` で `Uri` を取得し、既存 `BackupViewModel` へ渡す。`BackupViewModel` は `BackupRepository` のみに依存し、`ContentResolver` を直接扱わない。

`BackupRepositoryImpl` は `ContentResolver.openInputStream(uri)` を呼び出すための `BackupInputReader` 相当を持つ。`openInputStream(uri)` が `null` を返す、または読み込み中に失敗する場合は `BackupRestoreResult.Failure` に変換する。

OpenDocument の MIME type は `arrayOf("application/zip", "application/octet-stream", "*/*")` を候補にする。provider 側の MIME 判定が不安定な場合に備え、表示名や拡張子だけで成功/失敗を決めない。

### 6. 復元は pending restore として保存し、次回起動時に DB 置換する

初期実装では、復元確定時に live `AppDatabase` を close して DB ファイルを即時差し替えない。Hilt singleton が close 済み `AppDatabase` instance を保持し続ける可能性を避けるため、復元確定時は検証済みバックアップ内容を内部一時領域へ pending restore として保存し、ユーザーにアプリ再起動が必要であることを通知する。

次回アプリ起動時に、Hilt による `AppDatabase` 生成より前のタイミングで pending restore を適用する。適用処理は `AppDatabase`、DAO、Repository、Hilt-provided DB instance に依存してはならない。`Context`、file operation、Moshi DTO decode、DataStore 反映に必要な最小依存だけで動作させる。

#### 6.1 起動 hook 位置

調査結果として、現在の起動順序では `SlevoApplication.onCreate()` の `super.onCreate()` 直後の時点では `AppDatabase` は未生成である。Hilt の `SingletonComponent` は初期化済みだが、`@Singleton` binding は lazy に解決されるため、この時点では `DatabaseModule.provideAppDatabase()` は呼ばれていない。

`AppDatabase` が最初に生成され得るのは、`MainActivity.onCreate()` の `super.onCreate()` で Hilt field injection が実行され、`@Inject lateinit var tabSessionStore: TabSessionStore` が解決される時点である。`TabSessionStore` は repository/DAO 経由で `AppDatabase` に到達するため、`MainActivity.onCreate()` の body に hook を置くのは遅い。

採用する hook は以下の 1 箇所に限定する。

```kotlin
// app/src/main/java/com/websarva/wings/android/slevo/SlevoApplication.kt
override fun onCreate() {
    super.onCreate()

    PendingRestoreApplier(this).runIfNeeded()

    // 既存の Coil / Logger / crash handler 初期化
}
```

`PendingRestoreApplier` は Hilt から取得しない。手動 `new` で生成し、constructor は原則 `Context` のみを必須引数にする。constructor または推移依存に `AppDatabase`、DAO、Repository、Hilt EntryPoint、Hilt-injected DataSource を含めてはならない。DataStore 反映が必要な場合は、Hilt 経由の `SettingsLocalDataSource` / `TabsLocalDataSource` / `CookieLocalDataSource` ではなく、`PreferenceDataStoreFactory.create(...)` で対象 file path を直接指定して生成する。

`runIfNeeded()` は同期的に完了する API とする。pending restore が存在する場合、DB 置換判断、必要な rollback、DataStore 反映、result file 記録まで完了してから return する。未完了の非同期 job を起動したまま `SlevoApplication.onCreate()` の後続初期化へ進んではならない。

DataStore 書き込みは suspend/asynchronous API であっても、`runIfNeeded()` 内で完了を待つ。実装は `runBlocking` 相当の startup 専用 blocking bridge、または同等に完了を保証できる同期 wrapper を使い、DataStore 反映 job を未完了のまま return してはならない。

`androidx.startup.Initializer` は採用しない。現状 `AndroidManifest.xml` に `InitializationProvider` はなく、`WorkManager` も未使用である。将来 `androidx.startup` や `WorkManager` を追加する場合は、pending restore が `AppDatabase` 生成前に実行される保証を再検証する。

新規 component 例:

- `data/backup/PendingRestoreManager.kt`: ZIP 検証後の復元対象 DB/JSON を内部領域へ保存し、pending marker を管理する。
- `data/backup/PendingRestoreApplier.kt`: アプリ起動時に pending marker を確認し、`AppDatabase` 生成前に DB ファイル差し替えと DataStore 反映を実行する。
- `data/backup/DatabaseBackupImporter.kt`: live `AppDatabase` instance を close せず、pending restore の staging DB を live DB path へ差し替える file operation 専用 component とする。

pending restore の内部配置例:

```text
filesDir/pending-restore/
├── restore.json              # status/includeCookies/databaseVersion/createdAt などの marker
├── database/
│   └── slevo.db              # integrity check 済み staging DB
└── datastore/
    ├── settings.json
    ├── tabs.json
    └── cookies.json          # backup に含まれ、かつ復元対象の場合のみ
```

`restore.json` は DataStore ではなく通常 file として保存する。DataStore 自体が復元対象であり、pending 状態の記録を復元対象 DataStore に依存させないためである。

処理順序:

1. 復元確定時、`BackupRepository.restoreBackup(uri, includeCookies)` は ZIP を再読み込み・再検証する。
2. ZIP 内の `database/slevo.db` を pending restore directory へ staging file として保存する。
3. staging DB を読み取り専用で開き、`PRAGMA integrity_check` が `ok` を返すことを確認する。
4. `settings.json`、`tabs.json`、復元対象の場合のみ `cookies.json` を pending restore directory へ保存する。
5. `restore.json` を `status = "prepared"` として最後に書き込む。marker は最後に作ることで、不完全な staging を pending と誤認しない。
6. UI は「復元の準備が完了しました。アプリを再起動すると復元が適用されます。」相当を通知する。
7. 次回アプリ起動時、`PendingRestoreApplier` が `AppDatabase` 生成前に `restore.json` を確認する。
8. `status = "prepared"` の場合、live DB path の `-wal` / `-shm` sibling を best-effort で削除し、staging DB を live DB path へ置換する。
9. 置換後 DB を読み取り専用で開き、`PRAGMA integrity_check` が `ok` を返すことを確認する。
10. pending の settings/tabs/cookies JSON を DB 非依存の `PendingRestoreDataStoreWriter` 相当で反映する。startup restore では Hilt 経由 DataSource や Repository を使ってはならない。
11. すべて成功したら `restore.json` と pending directory を削除する。
12. pending 適用完了後、通常の Hilt 初期化で `AppDatabase` を生成する。

pending marker の状態例:

- `prepared`: staging 完了、次回起動時に適用可能。
- `applying`: 起動時適用中。クラッシュ復旧時に再試行または failure 扱いを判断する。
- `db-swapped`: DB 差し替え完了、DataStore 反映前。
- `failed`: 適用失敗。詳細は marker とログへ記録し、通常起動を可能にするか停止するかを実装時に判断する。

pending marker の状態遷移は以下に固定する。

| current | event | next | behavior |
|---|---|---|---|
| none | 復元準備完了 | `prepared` | DB/JSON staging 完了後、marker を最後に作成する。 |
| `prepared` | 起動時適用開始 | `applying` | live DB rollback backup を作成してから DB 置換へ進む。 |
| `applying` | DB 置換完了 + 置換後 DB 検証成功 | `db-swapped` | DataStore 反映へ進む。 |
| `applying` | DB 置換前または置換後検証失敗 | `failed` | rollback backup から live DB を復旧し、通常起動を継続できる状態に戻す。 |
| `db-swapped` | DataStore 反映成功 | none | pending directory と rollback backup を削除する。 |
| `db-swapped` | DataStore 反映失敗 | `failed` | rollback backup から live DB を復旧し、DataStore は best-effort rollback せず、失敗 status を残す。 |
| `failed` | 次回起動 | `failed` | 自動再試行しない。UI 通知用 status を残し、通常起動を優先する。 |

起動時に stale marker が残っている場合の recovery は以下に固定する。

- stale `applying`: 前回起動で DB 置換前または置換中に中断された可能性があるため、rollback backup が存在する場合は live DB を rollback backup へ戻し、marker を `failed` にする。rollback backup がない場合でも自動再試行はせず、marker を `failed` にして通常起動を優先する。
- stale `db-swapped`: 前回起動で DB 置換後、DataStore 反映前または反映中に中断された可能性があるため、rollback backup が存在する場合は live DB を rollback backup へ戻し、marker を `failed` にする。rollback backup がない場合は marker を `failed` にし、result file で手動確認が必要な失敗として通知する。
- stale `failed`: 自動再試行しない。result file がなければ失敗 result を作成し、通常起動を優先する。

起動時適用では、live DB を置換する前に live DB main file と `-wal` / `-shm` を `filesDir/pending-restore/rollback/` へ best-effort でコピーする。fresh install などで live DB main file が存在しない場合は rollback source なしとして扱い、rollback backup 作成を必須にしない。`-wal` / `-shm` が存在しないことも正常として扱う。DB 置換後の検証失敗、または `db-swapped` 後の DataStore 反映失敗では、rollback backup が存在する場合は live DB を rollback backup へ戻す。rollback backup が作成できない、または rollback 自体に失敗した場合は、marker を `failed` にし、詳細ログと起動後 UI 通知用 status に「復元に失敗し、アプリデータの確認が必要」相当を記録する。

rollback 実行時は、置換後に生成された可能性がある live DB path の `-wal` / `-shm` を先に best-effort で削除する。その後、rollback backup に main DB が存在する場合は main DB を戻し、rollback backup に `-wal` / `-shm` が存在する場合はそれらも戻す。rollback backup に `-wal` / `-shm` が存在しない場合は、元々存在しなかったものとして扱い、live DB path 側にも残さない。

rollback backup の失敗判定は以下に固定する。

- live DB main file が存在し、main file を rollback directory へコピーできない場合は、live DB 置換前に `failed` として停止する。
- rollback directory を作成できない場合は、live DB 置換前に `failed` として停止する。
- `-wal` / `-shm` が存在しない場合は正常として扱う。
- `-wal` / `-shm` が存在するがコピーに失敗した場合は詳細ログへ記録する。main DB rollback が存在する限り復元適用は続行してよいが、置換後失敗時の rollback では main DB rollback を優先する。

`failed` は自動再試行しない。復元適用失敗時に同じ pending を起動ごとに再適用すると、破壊的操作のループになるためである。ユーザーが再度復元を実行する場合は、既存 pending/rollback を削除してから新しい pending restore を作成する。

新しい復元準備を開始する時点で既存の pending/rollback/result が存在する場合の扱いは以下に固定する。

- `prepared`: まだ起動時適用されていない復元準備があるため、新しい復元準備は開始せず「既に復元準備済みです。再起動してください」相当を返す。
- `applying` または `db-swapped`: 前回の起動時適用が中断された可能性があるため、新しい復元準備は開始せず、次回起動時の recovery に任せる。
- `failed`: 新しい復元準備を許可する前に、既存 pending directory、rollback backup、result file を cleanup する。cleanup に失敗した場合は新しい復元準備を開始しない。
- marker がない不完全 staging: pending と扱わず cleanup してから新しい復元準備を開始できる。
- result file のみ存在: UI 通知対象として残し、新しい復元準備前に通知済み扱いで削除するか、ユーザー通知後に削除する。削除できない場合は新しい復元準備を開始しない。

起動時適用結果は `filesDir/pending-restore-result/restore-result.json` 相当の通常 file に記録する。この result はアプリ起動後に root-level の UI state owner（例: `MainActivity` 配下の app-level snackbar host、または全画面共通の起動通知 ViewModel）が読み取り、現在表示中の画面に関係なく成功/失敗を 1 回だけ通知する。`BackupViewModel` だけに通知処理を閉じ込めてはならない。UI が通知済みにした後、result file を削除する。起動時適用処理自体は UI/ViewModel に依存しない。

初期実装では `AppDatabaseHolder` による動的 DB 再生成は行わない。既存の Hilt singleton `AppDatabase` は復元処理内で close しない。DB 置換は「次回起動時、AppDatabase 未生成」を前提にする。

### 7. DataStore 復元は startup 専用 writer で反映する

DataStore の物理ファイルをコピーせず、バックアップ JSON DTO を DataStore へ反映する。ただし起動時の `PendingRestoreApplier` は Hilt 経由の `SettingsLocalDataSource` / `TabsLocalDataSource` / `CookieLocalDataSource` を使わない。これらの DataSource が将来 DB 依存を持つ、または Hilt graph 解決により `AppDatabase` を生成するリスクを避けるためである。

通常の repository-time validation や mapper は共有してよいが、startup restore の保存処理は DB 非依存の `PendingRestoreDataStoreWriter` 相当を使う。

#### 7.1 DataStore instance は必ず一元管理する

`PendingRestoreDataStoreWriter` が `PreferenceDataStoreFactory.create(...)` を直接呼び出して、通常実行時の `preferencesDataStore(name = ...)` とは別の DataStore instance を作ってはならない。同一 process 内で同じ `.preferences_pb` file に対して複数の DataStore instance が存在すると、DataStore の single-instance contract に反し、lock/error/更新競合を起こし得るためである。

実装者は次の順序で修正すること:

1. 新規 file `data/datasource/local/impl/SlevoPreferenceDataStores.kt` を作成する。
2. `object SlevoPreferenceDataStores` を定義する。
3. この object が settings/tabs/cookies の DataStore を返す唯一の provider になる。
4. `SettingsLocalDataSourceImpl`、`TabsLocalDataSourceImpl`、`CookieLocalDataSourceImpl` の `preferencesDataStore(...)` delegate を削除し、`SlevoPreferenceDataStores.settings(context)` / `tabs(context)` / `cookies(context)` を使う。
5. `PendingRestoreDataStoreWriter` も同じ `SlevoPreferenceDataStores` provider を使う。
6. `PendingRestoreDataStoreWriter` 内で `PreferenceDataStoreFactory.create(...)` を直接呼ぶコードを残してはならない。

推奨 provider 形:

```kotlin
object SlevoPreferenceDataStores {
    fun settings(context: Context): DataStore<Preferences>
    fun tabs(context: Context): DataStore<Preferences>
    fun cookies(context: Context): DataStore<Preferences>
}
```

実装は `preferencesDataStore(name = "settings")` 相当の lazy singleton でも、`PreferenceDataStoreFactory` を内部で一元管理する形でもよい。ただし public entry point は上記 provider に集約し、同じ process で同じ file 用 DataStore が複数生成されないことを test で確認する。

`PreferenceDataStoreFactory` を内部で使う場合、初回取得 race で同一 file 用 DataStore が複数生成されないよう、`@Volatile` だけに依存してはならない。double-checked locking、`lazy`、または同等の同期機構で初期化を保護する。`Context` は `applicationContext` 相当を使い、Activity context を保持しない。

#### 7.2 startup restore writer は DB 非依存だが shared DataStore provider を使う

`PendingRestoreDataStoreWriter` は DB/Hilt 非依存を維持する。ただし「独自 DataStore instance を作る」という意味ではない。writer は以下だけに依存してよい:

- `Context`
- `Moshi`
- `SlevoPreferenceDataStores` の static/object function
- `BackupRestoreMapper`

writer は以下に依存してはならない:

- `AppDatabase`
- DAO
- Repository
- Hilt `EntryPoint`
- `SettingsLocalDataSource` / `TabsLocalDataSource` / `CookieLocalDataSource`

#### 7.3 起動時復元の top-level 例外は通常起動を妨げない

`SlevoApplication.onCreate()` の `runBlocking { PendingRestoreApplier(...).runIfNeeded() }` は `super.onCreate()` 直後に置く。位置は正しい。ただし、`runIfNeeded()` から想定外例外が漏れて app 起動を crash させてはならない。

実装者は次のどちらかを必ず行う:

1. 推奨: `PendingRestoreApplier.runIfNeeded()` の最外周で `try/catch` し、例外時に marker を `failed` へ更新し、result file に失敗を記録して return する。
2. 補助: `SlevoApplication.onCreate()` 側でも `try/catch` し、少なくとも `android.util.Log.e(...)` へ記録して通常初期化を継続する。

低性能実装者向けの明確な条件:

- `PendingRestoreApplier.runIfNeeded()` は外へ例外を投げない。
- pending marker がある状態で失敗した場合は `filesDir/pending-restore-result/restore-result.json` に失敗 result を書く。
- result file 書き込みにも失敗した場合だけ log に残して通常起動を継続する。
- `SlevoApplication.onCreate()` の crash handler 初期化前に restore 例外で process を落としてはならない。

#### 7.4 重い I/O は `Dispatchers.IO` で実行する

`SlevoApplication.onCreate()` で `runBlocking` するのは、`AppDatabase` 生成前に pending restore を同期完了させるために必要である。ただし、`runBlocking` の中で main thread 上に重い I/O を直接置いてはならない。

`PendingRestoreApplier.runIfNeeded()` は以下を `withContext(Dispatchers.IO)` 内で行う:

- marker/result file read/write
- DB file copy/delete/rename
- WAL/SHM copy/delete
- SQLite open と `PRAGMA integrity_check`
- DataStore JSON file read
- DataStore `edit` の呼び出し

実装者向けの形:

```kotlin
suspend fun runIfNeeded() {
    withContext(Dispatchers.IO) {
        try {
            runIfNeededOnIo()
        } catch (e: Exception) {
            recordStartupRestoreFailureOnIo(e)
        }
    }
}
```

`runIfNeededOnIo()` は private にし、DB/file/DataStore の実処理をここへ集約する。例外時に marker/result file を読む・書く処理も main thread で直接行わず、`withContext(Dispatchers.IO)` 内の `recordStartupRestoreFailureOnIo(...)` 相当に集約する。

#### 7.5 rollback 失敗時に復旧材料を消さない

rollback は「live DB を復元できたこと」を確認してから pending directory と rollback backup を cleanup する。rollback copy 自体が失敗した場合、`cleanup()` によって rollback backup を削除してはならない。ユーザー通知用 result には失敗を記録しつつ、可能な限り rollback directory と marker を残し、後続の起動や手動調査で復旧材料を参照できる状態にする。

低性能実装者向けの明確な条件:

- rollback backup から live DB へ main DB を戻す copy が失敗した場合、rollback backup directory は削除しない。
- rollback copy に失敗した後に `failed` result file を書けない場合でも、通常起動は継続する。
- rollback 成功時のみ、置換後に生成された可能性のある live DB の `-wal` / `-shm` を削除し、rollback backup 側の `-wal` / `-shm` を復元してから cleanup する。

#### 7.6 fresh install で置換後検証に失敗した DB は残さない

fresh install など live DB main file が存在しない状態では rollback source がない。この状態で pending DB を live DB path へ copy した後、置換後 DB validation または DataStore 反映に失敗した場合、rollback できないことを理由に壊れた live DB を残してはならない。

fresh install 失敗時は、置換済み live DB main file と対応する `-wal` / `-shm` を best-effort で削除し、次回起動時に Room が空 DB を作成できる状態へ戻す。削除できない場合は failed result に記録し、通常起動は継続する。

#### 7.7 PendingRestoreApplier は実動作 test で state machine を検証する

`PendingRestoreApplier` は DB file 置換、rollback、DataStore 反映、marker/result file 更新を扱うため、reflection や source-level assertion だけで完了扱いにしてはならない。JVM unit test で Android framework SQLite を直接使えない分岐は fake validator / temporary directory / source-level 補助 test を組み合わせてよいが、少なくとも marker state transition と rollback 判断は実行して検証する。

最低限検証する分岐:

- marker なしは何もしない。
- `prepared` は `applying`、DB 置換、`db-swapped`、DataStore 反映、成功 result、cleanup の順に進む。
- `applying` / `db-swapped` は stale として自動再試行せず、rollback 可能なら rollback して failed result を残す。
- `failed` は自動再試行しない。
- rollback backup 作成失敗時は live DB を置換しない。
- rollback copy 失敗時は rollback backup を cleanup しない。
- fresh install で置換後 validation に失敗した場合は壊れた live DB を削除する。
- `runIfNeeded()` は例外を外へ投げず、失敗 result file を可能な範囲で作成する。
- DataStore 書き込み完了前に `runIfNeeded()` から戻らない。

#### 7.8 PendingRestoreApplier は orchestration に集中させる

Phase 3S 時点の実装は安全性を優先して `PendingRestoreApplier` に marker/result file、DB file 操作、rollback、fresh install cleanup、DataStore 反映、例外処理、test hook が集まりやすい。Phase 4 で repository/UI 層を重ねる前に、起動時 restore の責務を分割して、`PendingRestoreApplier` は state machine orchestration のみに近づける。

責務分割の目標構造:

```text
PendingRestoreApplier
├─ PendingRestoreFileStore
│  ├─ marker read/write
│  ├─ result write/read helper
│  └─ pending/result cleanup
├─ PendingRestoreDbSwapper
│  ├─ live DB path resolution
│  ├─ rollback backup creation
│  ├─ DB replace / temp cleanup
│  ├─ rollback restore
│  └─ fresh install failure cleanup
├─ BackupDatabaseValidator
└─ PendingRestoreDataStoreReflector
   └─ PendingRestoreDataStoreWriter
```

`PendingRestoreApplier` が保持してよい責務:

- `runIfNeeded()` の top-level 例外制御と `Dispatchers.IO` 切り替え
- marker status に基づく state machine 分岐
- `PREPARED`、stale `APPLYING` / `DB_SWAPPED`、`FAILED` の orchestration
- 各 collaborator の戻り値を `success` / `failed` result へ mapping する制御

`PendingRestoreApplier` から切り出す責務:

- marker/result file の JSON I/O
- pending/result directory cleanup
- live DB file path 判定
- DB temp file 作成、copy、rename、rename 失敗時 temp cleanup
- WAL/SHM cleanup と rollback backup restore
- fresh install 失敗時の壊れた DB cleanup
- JVM unit test のためだけの `rollbackRestorerOverride` のような個別 hook

実装者向けの明確な条件:

- `PendingRestoreFileStore` は `Context` または `filesDir` と `Moshi` だけで marker/result/pending cleanup を扱う。`AppDatabase`、DAO、Repository、Hilt EntryPoint に依存しない。
- `PendingRestoreDbSwapper` は DB file path と file operation を扱う。`BackupDatabaseValidator` は引数として受け取り、Room singleton `AppDatabase` は生成・close しない。
- `PendingRestoreDataStoreReflector` は DataStore JSON 反映だけを扱い、`PendingRestoreDataStoreWriter` と `SlevoPreferenceDataStores` を使う。
- `PendingRestoreApplier` の public 生成 API は `Context` のみを必須にし、testability は file store / DB swapper / DataStore reflector の fake 差し替えで確保する。
- 責務分割後も `SlevoApplication.onCreate()` の呼び出し位置、同期完了、通常起動を妨げない例外処理、`Dispatchers.IO` 上での I/O 実行を維持する。
- Phase 3S で追加した rollback backup 保持、fresh install 失敗 cleanup、unknown gesture action 非永続化の振る舞いを維持する。

責務分割にあわせて test も整理する。source-level 文字列検査だけで完了扱いにせず、以下を fake collaborator で検証する:

- `PendingRestoreApplier` は marker state に応じて collaborator を正しい順序で呼ぶ。
- `PendingRestoreFileStore` は marker/result JSON I/O と cleanup を検証する。
- `PendingRestoreDbSwapper` は rollback backup 作成、DB replace、rename 失敗時 temp cleanup、rollback copy 失敗時 backup 保持、fresh install 失敗 cleanup を検証する。
- `PendingRestoreDataStoreWriter.applySettingsToPreferences(...)` は `MutablePreferences` へ full overwrite、未知 gesture action 削除、`gesture_assignments_initialized = true` を検証する。
- `SlevoPreferenceDataStores` は settings/tabs/cookies すべての concurrent first access で単一 instance を返すことを検証する。

推奨追加 API:

- `BackupRestoreMapper`: `BackupSettingsJson` / `BackupTabsJson` / `BackupCookiesJson` の validation と domain value 変換を担当する。DB/Hilt に依存しない。
- `SlevoPreferenceDataStores`: settings/tabs/cookies DataStore instance を一元提供する。通常 DataSource と startup restore writer が必ず共有する。
- `PendingRestoreFileStore`: pending marker/result file と cleanup を DB/Hilt 非依存で扱う。
- `PendingRestoreDbSwapper`: live DB 置換、rollback backup、fresh install cleanup を DB/Hilt 非依存で扱う。
- `PendingRestoreDataStoreReflector`: pending DataStore JSON を [PendingRestoreDataStoreWriter] へ委譲する薄い adapter。
- `PendingRestoreDataStoreWriter`: DB/Hilt 非依存の経路で settings/tabs/cookies DataStore へ mapper 済み値を保存する。ただし DataStore instance は `SlevoPreferenceDataStores` から取得する。
- 既存 `SettingsLocalDataSource.applyBackupSettings(...)` などを追加する場合でも、それは通常実行時 API とし、起動時 pending restore 適用では使用しない。

startup restore writer が扱う DataStore path と key は以下に固定する。

| DataStore | file path | keys |
|---|---|---|
| settings | `<filesDir>/datastore/settings.preferences_pb` | `theme_mode`, `tree_sort`, `thread_minimap_scrollbar`, `text_scale`, `individual_text_scale`, `header_text_scale`, `body_text_scale`, `line_height`, `redirect_5ch_net_to_io`, `gesture_enabled`, `gesture_show_action_hint`, `gesture_assignments_initialized`, `gesture_action_<direction_name_lowercase>` |
| tabs | `<filesDir>/datastore/tabs.preferences_pb` | `last_selected_page` |
| cookies | `<filesDir>/datastore/cookies.preferences_pb` | `app_cookies` |

`gesture_action_<direction_name_lowercase>` は既存 `GestureDirection.name.lowercase()` に合わせる。既知 direction は `RIGHT`, `RIGHT_UP`, `RIGHT_LEFT`, `RIGHT_DOWN`, `LEFT`, `LEFT_UP`, `LEFT_RIGHT`, `LEFT_DOWN` の 8 種である。JSON に存在しない既知 direction は未割当として扱い、既存 DataStore の値を残さない。未知 direction key は `Invalid` とする。

v1 の DataStore restore は full overwrite として扱う。backup format が表す既知 settings/tabs/cookies field は JSON からすべて validation して反映し、必須 field が欠落した場合は `Invalid` とする。backup format に含まれる既知 key について、JSON にないことを理由に既存 DataStore 値を保持してはならない。例外は gesture actions map の「既知 direction が欠落した場合」で、この場合は未割当として既存値を削除する。

`BackupDataMapper` または新規 `BackupRestoreMapper` へ逆変換を追加する。

- `themeMode` は `light` / `dark` / `system` を既存 enum へ戻す。未知値は validation で無効扱いにするか、spec に従い `system` へ fallback する。初期実装は復元の予測可能性を優先し、未知値は無効なバックアップとして扱う。
- gesture direction/action は kebab-case 文字列から既存 enum へ戻す。startup restore writer は action 文字列を単純な PascalCase 変換だけで DataStore に保存せず、`GestureAction` に存在する値だけを保存対象にする。未知 action は validation 済み backup reader 経由では到達しない想定だが、防御的に invalid または未割当として扱い、未知文字列を DataStore へ永続化してはならない。
- `gestureSettings.actions` に存在しない方向は未割当として扱い、既存 setter が null/削除を表現できる場合は削除する。
- Cookie は `BackupCookieItem` 9 field から `okhttp3.Cookie.Builder` で復元する。

復元 JSON は厳格に validation する。malformed JSON、必須 field 不足、未知 enum、null 不許可 field の null、scale/lineHeight の非有限値または 0 以下、tab page の負数、Cookie の空 name/domain/path、Cookie builder が拒否する値は `Invalid` として扱い、pending restore を作成しない。v1 では scale/lineHeight の上限値は定義せず、正の有限値であれば受け付ける。将来、実ソース側で UI 許容範囲に基づく上限 validation を追加する場合は別 change で spec を更新する。

### 8. DB schema compatibility validation

`PRAGMA integrity_check` は SQLite ファイルとしての物理整合性だけを確認する。復元対象 DB が Slevo の期待する Room schema であることを保証するため、pending restore 作成前に schema compatibility validation を実行する。

以下を確認する。

- SQLite `PRAGMA user_version` が `9` である。
- `manifest.databaseVersion` が `9` である。
- `room_master_table` が存在し、`id = 42` の `identity_hash` が `f87f9edff16faf278567dbb60497a466` である。
- 以下 20 個の application table が存在する。

必須 application table:

```text
services
categories
boards
board_category_cross_ref
groups
bookmark_boards
bookmark_threads
thread_bookmark_groups
open_board_tabs
open_thread_tabs
thread_histories
thread_history_accesses
ng_entries
thread_summaries
board_visits
board_fetch_meta
post_histories
post_identity_histories
post_last_identities
thread_states
```

Room schema file `app/schemas/com.websarva.wings.android.slevo.data.datasource.local.AppDatabase/9.json` の `database.identityHash` は `f87f9edff16faf278567dbb60497a466` である。`room_master_table` の作成 query と identity hash 登録 query は Room schema JSON の `setupQueries` と一致することを前提にする。

### 9. UI は既存 BackupScreen を「バックアップと復元」画面へ拡張する

既存 package `app/src/main/java/com/websarva/wings/android/slevo/ui/settings/backup/` を拡張する。

```text
app/src/main/java/com/websarva/wings/android/slevo/ui/settings/backup/
├── BackupScreen.kt        # BackupScreenContent を「バックアップと復元」用に拡張
├── BackupViewModel.kt     # export と restore の UI event/state を管理
├── BackupUiState.kt       # export state + restore preview/restoring state
├── BackupUiEvent.kt       # export event + restore event
└── BackupPreviewUiModel.kt # 必要に応じて preview 表示用 model を追加
```

独立した `RestoreScreen` / `RestoreViewModel` / `SettingsRestore` route は作らない。既存 `AppRoute.SettingsBackup` route と `SettingsRoute.kt` の `BackupScreen` 表示を維持し、画面タイトルと設定項目名を「バックアップと復元」へ変更する。

`BackupUiState` に追加する restore state 例:

- `restoreIncludeCookies: Boolean`
- `showRestoreConfirmDialog: Boolean`
- `isPreviewLoading: Boolean`
- `isRestoring: Boolean`
- `restorePreview: BackupPreviewUiModel?`
- `selectedRestoreUri: Uri?` または ViewModel 内部保持値

`BackupUiEvent` に追加する restore event 例:

- `RestorePrepared`
- `RestorePrepareFailed`
- `InvalidBackup`

`BackupUiEvent` には backup screen 内で発生する復元準備 event のみを置く。推奨は `RestorePrepared`、`RestorePrepareFailed`、`InvalidBackup` である。起動時適用結果の `StartupRestoreSucceeded` / `StartupRestoreFailed` は backup screen 専用 event ではなく、app-level startup notification owner の event/state として扱う。

画面フロー:

1. ユーザーが設定画面の「バックアップと復元」を押す。
2. 既存 `BackupScreen` を表示する。
3. 画面内に「バックアップ作成」と「バックアップから復元」の 2 つの action row/card を表示する。
4. ユーザーが「バックアップから復元」を押す。
5. `OpenDocument` を起動する。
6. URI が返ったら `BackupViewModel.onRestoreFileSelected(uri)` を呼ぶ。
7. ViewModel が `previewBackup(uri)` を実行する。
8. preview 成功時は復元確認ダイアログを表示する。
9. ユーザーが「復元する」を押すと `restoreBackup(uri, restoreIncludeCookies)` を実行し、pending restore を作成する。
10. 準備完了/失敗/無効を Snackbar またはダイアログで表示する。準備完了時はアプリ再起動後に復元が適用されることを明示する。

既存の backup export flow は同じ画面内に残す。`BackupScreenContent` は stateless を維持し、export 用 callback と restore 用 callback をどちらも parameters として受け取る。

確認ダイアログは destructive operation であることを明確にする。文言には「現在のデータはバックアップ内容で上書きされます」「復元準備後にアプリを再起動すると適用されます」「クッキーには認証情報が含まれる可能性があります」を含める。

### 10. Repository と mutex

`BackupRepository` に復元 API を追加するか、`BackupRestoreRepository` を新設する。既存 `BackupRepositoryImpl` が export orchestration を持つため、初期実装では同じ `data/backup/` に復元 API を追加してよい。ただし class が肥大化する場合は `BackupRestoreRepositoryImpl` へ分ける。

export と restore は同時実行させない。既存 `backupMutex` を `backupOperationMutex` 相当にリネームするか、同じ mutex を export/restore の両方で共有する。UI disabled だけに依存せず、repository/data 層で直列化する。

### 11. エラー処理

最低限、以下を区別する。

- ユーザーがファイル選択 UI をキャンセルした場合: エラーにしない。
- `ContentResolver.openInputStream(uri)` が null を返す: `Failure`。
- ZIP として読めない: `Invalid`。
- 必須 entry がない、重複 entry がある、path traversal がある: `Invalid`。
- `backupFormatVersion != 1`: `Invalid`。
- `backupMode != "full"`: `Invalid`。
- `databaseVersion != current`: `Invalid`。
- `manifest.included.*` と entry 存在が矛盾する: `Invalid`。
- DB integrity check 失敗: `Invalid` または `Failure`。入力ファイルの不正として扱える場合は `Invalid`、I/O 例外の場合は `Failure`。
- DB schema compatibility validation 失敗: `Invalid`。
- pending restore 作成失敗: `Failure`。
- 次回起動時の DB 置換失敗: pending marker に `failed` を記録し、詳細ログへ出力する。
- 起動時の DataStore 反映失敗: pending marker に `failed` を記録し、詳細ログへ出力する。

UI は詳細な stack trace を表示しない。詳細は既存 logging 方針に合わせてログへ出力する。

## Risks / Trade-offs

- [Risk] live Room DB を close して差し替えると Hilt singleton が close 済み `AppDatabase` を保持し、復元後アクセスで失敗する可能性がある。 → 初期実装では live `AppDatabase` を close せず、pending restore を次回起動時に `AppDatabase` 生成前へ適用する。
- [Risk] pending restore 適用中にプロセス終了すると中間状態が残る。 → marker に `prepared` / `applying` / `db-swapped` / `failed` を記録し、次回起動時に再試行または failure 処理できるようにする。
- [Risk] DB 置換後に DataStore 反映が失敗すると DB と DataStore が混在状態になる。 → live DB の rollback backup を作成し、DataStore 反映失敗時は DB を rollback して `failed` result を残す。DataStore の完全 rollback は保証しないため、失敗通知で再復元を促す。
- [Risk] SQLite integrity check に通るが Slevo の Room schema ではない DB を復元する可能性がある。 → `user_version` と Room schema identity/必須 table を検証し、schema 不一致は `Invalid` とする。
- [Risk] pending restore marker を DataStore に置くと、復元対象 DataStore 自体の状態に依存する。 → marker は `filesDir/pending-restore/restore.json` の通常 file として保存する。
- [Risk] `-wal` / `-shm` が残ると置換後 DB と不整合になる。 → DB 置換前後で sibling file を best-effort 削除する。
- [Risk] `databaseVersion` が異なるバックアップを許可すると migration/downgrade の失敗でデータを失う。 → v1 は同一 version のみ許可する。
- [Risk] DataStore は複数 store を横断する atomic transaction を提供しない。 → 復元順序を design/tasks に固定し、失敗時は詳細ログへ記録する。完全 atomic を要件にしない。
- [Risk] Cookie 復元は認証状態を戻す可能性があり、センシティブである。 → Cookie checkbox は backup に含まれる場合のみ表示または有効化し、初期状態は OFF にする。
- [Risk] 無効な ZIP や zip-slip による不正 path がある。 → `BackupReader` で固定 path 以外を拒否し、entry name を正規化して検証する。
- [Risk] UI が回転・再生成されると選択済み URI や preview 状態が失われる可能性がある。 → ViewModel state に URI と preview を保持し、復元準備中は再実行を guard する。

## Migration Plan

1. 新規 `backup-restore` capability として実装する。
2. 既存 Room schema version、既存 DB entity、既存 backup export ZIP 形式は変更しない。
3. リリース後に問題があれば、既存バックアップ画面内の「バックアップから復元」action だけを非表示にしても既存データやバックアップ作成機能へ影響しない。
4. 将来 `databaseVersion` migration 復元を追加する場合は、新しい OpenSpec change で `backup-restore` requirement を変更する。

## Implementation Contract

- アプリコード実装時は、すべての新規 class/interface/object/data class/sealed interface/sealed class/enum に KDoc を追加する。
- Compose Preview 関数には KDoc を追加しない。
- 新規 Composable には意味のある `@Preview` を追加する。
- 復元 UI は独立した `RestoreScreen` を作らず、既存 `BackupScreen` / `BackupScreenContent` を「バックアップと復元」画面へ拡張する。
- `BackupViewModel` は `BackupRepository` または復元専用 repository のみに依存し、`ContentResolver` を直接扱わない。
- SAF は `ActivityResultContracts.OpenDocument` を使い、外部ストレージ権限、`MANAGE_EXTERNAL_STORAGE`、FileProvider を追加してはならない。
- 復元可否はファイル名や拡張子ではなく、ZIP 内の `manifest.json` と固定 entry で判定する。
- `backupFormatVersion != 1`、`backupMode != "full"`、`databaseVersion != current` は復元を開始せず `Invalid` とする。
- `manifest.included.cookies` と `datastore/cookies.json` の有無が一致しない場合は `Invalid` とする。
- Cookie 復元 checkbox は初期 OFF とし、backup に Cookie が含まれない場合は表示しないか無効化する。
- `restoreBackup` は `previewBackup` 済み URI でも commit 時に ZIP と manifest を再検証する。
- `restoreBackup` は live DB を即時置換せず、検証済み DB/JSON を `filesDir/pending-restore/` 相当へ保存し、最後に marker を作成する。
- 既存 Hilt singleton `AppDatabase` を復元処理中に close して再利用してはならない。
- `AppDatabaseHolder` による動的 DB 再生成は初期実装では導入しない。
- `PendingRestoreApplier.runIfNeeded()` は `SlevoApplication.onCreate()` の `super.onCreate()` 直後の 1 箇所からのみ呼び出す。
- `PendingRestoreApplier.runIfNeeded()` は同期的に完了し、pending restore 適用中の非同期 job を残したまま return してはならない。
- DataStore 反映は `runIfNeeded()` が return する前に durable に完了していなければならない。
- `PendingRestoreApplier` は Hilt binding を持たず、Hilt EntryPoint から取得してはならない。
- `PendingRestoreApplier` の constructor および推移依存は `AppDatabase`、DAO、Repository、DB 依存の DataSource を含んではならない。
- pending restore の DB 置換は次回アプリ起動時、Hilt が `AppDatabase` を生成する前に実行する。
- DB 置換前に staging DB の読み取り専用 open と `PRAGMA integrity_check` を実行し、`ok` 以外では pending restore を作成してはならない。
- pending restore 作成前に `PRAGMA user_version` と Room schema compatibility を検証し、不一致なら `Invalid` とする。
- 起動時 DB 置換前に live DB main file と `-wal` / `-shm` の rollback backup を作成する。
- fresh install などで live DB main file が存在しない場合は rollback source なしとして扱い、復元適用を失敗にしてはならない。`-wal` / `-shm` が存在しないことも正常として扱う。
- live DB main file が存在するのに main file rollback backup を作成できない場合、または rollback directory を作成できない場合は、live DB 置換前に `failed` として停止する。
- `-wal` / `-shm` のコピー失敗は詳細ログへ記録し、main DB rollback が存在する場合は復元適用を続行してよい。
- rollback 実行時は replacement-era の `-wal` / `-shm` を削除し、rollback backup に存在する main DB / `-wal` / `-shm` を戻す。rollback backup に存在しない `-wal` / `-shm` は live DB path に残してはならない。
- DB 置換後検証または DataStore 反映が失敗した場合、rollback backup が存在する限り live DB を rollback する。
- stale `applying` / `db-swapped` marker 検出時は自動再試行せず、rollback backup があれば DB rollback して `failed` result を残す。
- pending restore の `failed` 状態は自動再試行しない。
- 既存 pending/rollback/result がある状態で新しい復元準備を開始する場合は、state ごとの cleanup/blocking rules に従う。
- 起動時 pending restore の成功/失敗は result file に記録し、UI は起動後に 1 回だけ通知して result file を削除する。
- pending restore 適用時は live DB path の `-wal` / `-shm` を best-effort cleanup してから DB を置換する。
- 復元準備中は同一画面内の「バックアップ作成」「バックアップから復元」「復元する」「クッキーを復元する」を無効化し、モーダル進捗ダイアログを表示する。
- 復元準備完了/失敗/無効なバックアップは Snackbar で短く通知し、詳細エラーや stack trace は UI に表示しない。
- 復元準備完了時は、アプリ再起動後に復元が適用されることをユーザーへ表示する。
- export と restore は repository/data 層の同一 mutex で直列化する。
- DataStore は `.preferences_pb` をコピーせず、startup restore ではバックアップ JSON DTO を DB 非依存の `PendingRestoreDataStoreWriter` 相当で反映する。Hilt 経由 DataSource setter は startup restore path で使わない。

## Testing Strategy

- JVM unit test:
  - `BackupReader` が固定 entry を読み取り、必須 entry 不足、重複 entry、未知 entry、zip-slip、manifest 不整合を拒否する。
  - `BackupManifest` / `BackupSettingsJson` / `BackupTabsJson` / `BackupCookiesJson` の decode と逆変換を検証する。
  - malformed JSON、必須 field 不足、未知 enum、範囲外 scale/tab/cookie 値が `Invalid` になることを検証する。
  - `BackupRestoreMapper` または `BackupDataMapper` 逆方向変換で theme/gesture/cookie が復元 DTO へ変換される。
  - Repository preview は DB/DataStore へ書き込まない。
  - Repository restore は pending restore directory に DB/settings/tabs/cookies と marker を作成し、live DB を変更しない。
  - includeCookies=false の場合、backup に Cookie が含まれていても Cookie importer を呼ばない。
  - export/restore concurrent call が mutex で直列化される。
  - `BackupViewModelTest` を拡張し、restore preview loading、confirm dialog、includeCookies toggle、URI null、success/failure/invalid event、isRestoring guard を検証する。
- pending restore / DB file replacement test:
  - `PendingRestoreApplier` の constructor parameter と推移的に使う dependencies に `AppDatabase`、DAO、Repository が含まれないことを reflection または静的検査で確認する。
  - `SlevoApplication.onCreate()` 内の `PendingRestoreApplier.runIfNeeded()` 呼び出しが `super.onCreate()` 直後に 1 箇所だけ存在することを source inspection test または Robolectric test で確認する。
  - Robolectric test または Hilt test で、`SlevoApplication.onCreate()` 実行中に `AppDatabase` provider が解決されないことを確認する。
  - pending marker state transition、rollback backup 作成、DB 置換後検証失敗時の rollback、DataStore 反映失敗時の DB rollback、`failed` 自動再試行なし、result file の 1 回通知を検証する。
  - 既存 `prepared` / `applying` / `db-swapped` / `failed` / marker なし不完全 staging / result file のみ存在時に、新しい復元準備が block または cleanup されることを検証する。
  - startup DataStore writer の書き込みが `runIfNeeded()` return 前に完了していることを検証する。
  - DB schema compatibility validation と `PRAGMA user_version` 不一致を検証する。
  - JVM では `PendingRestoreManager` と `DatabaseBackupImporter` の file operation / sqlite operation を fake/抽象化して、integrity check 成功/失敗、marker 最後書き、wal/shm cleanup、copy/rename 失敗を検証する。
  - 可能であれば instrumented test で、アプリ起動時相当の `PendingRestoreApplier` が `AppDatabase` 生成前に実 SQLite file を置換し、その後 Room で読めることを検証する。
- UI/navigation:
  - 設定画面の「バックアップと復元」項目から既存 `BackupScreen` へ遷移し、同一画面にバックアップ作成と復元 action が表示されることを compile または instrumented test で確認する。
  - `BackupScreenContent` Preview でバックアップ作成 action、復元 action、preview 確認ダイアログ、復元準備中ダイアログを確認できるようにする。
- Manual:
  - 実機/エミュレータでバックアップ作成 → アプリデータ変更 → 復元準備 → アプリ再起動 → ブックマーク/履歴/タブ/設定/Cookie の戻りを確認する。
  - 壊れた ZIP、manifest なし、DB version 不一致、Cookie manifest 不一致で無効なバックアップ通知になることを確認する。
  - 外部ストレージ権限なしで OpenDocument のみ動作することを確認する。

## Open Questions

- なし。`PendingRestoreApplier` は `SlevoApplication.onCreate()` の `super.onCreate()` 直後に手動生成して実行する。テストでは constructor dependency の静的検査、`runIfNeeded()` 呼び出し位置検査、Application 起動中に `AppDatabase` が解決されないことを検証する。
