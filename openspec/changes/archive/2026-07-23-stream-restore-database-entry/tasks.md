## 1. 事前確認

- [x] 1.1 `BackupReader.kt` の `readBackup()`、`writeBytesToTempFile()`、`DB_PATH`、`REQUIRED_ENTRIES` を確認し、DB entry が `entries[DB_PATH]` の `ByteArray` として扱われている箇所を列挙する。完了条件: 変更対象行と call site を実装メモまたはコメントで把握している。
 - [x] 1.2 `BackupPreview.kt` の `dbBytes`、`equals()`、`hashCode()` を確認する。完了条件: `dbBytes` を参照する production/test code を検索済みである。
 - [x] 1.3 `PendingRestoreManager.kt` の `prepareRestore()` を確認し、`preview.dbBytes` を `writeBytes()` している箇所と integrity check の順序を把握する。完了条件: DB staging 後に `checkIntegrity()` が走ることを確認済みである。
 - [x] 1.4 `BackupRepositoryImpl.kt` の `previewBackup()` / `restoreBackup()` を確認し、`BackupPreview.dbFile` cleanup を置く位置を決める。完了条件: success / error / early return の各 path が整理されている。

## 2. BackupPreview model 更新

 - [x] 2.1 `BackupPreview.kt` に `java.io.File` import を追加し、constructor の `dbBytes: ByteArray` を `dbFile: File` に置き換える。完了条件: production code で `BackupPreview(... dbBytes = ...)` が compile error になる状態まで置換できている。
 - [x] 2.2 `BackupPreview.kt` の KDoc を更新し、`dbFile` が検証済み DB の一時ファイルで、呼び出し側が cleanup responsibility を持つことを明記する。完了条件: KDoc が `dbBytes` を参照しない。
 - [x] 2.3 `BackupPreview.equals()` / `hashCode()` から `contentEquals()` / `contentHashCode()` を削除し、`dbFile` の path または `File.equals()` を使う形に更新する。完了条件: `dbBytes` symbol が `BackupPreview.kt` に残っていない。

## 3. BackupReader streaming 実装

 - [x] 3.1 `BackupReader.readBackup()` の entry 読み取りで `seenEntries: MutableSet<String>` を導入し、DB entry と JSON entry の duplicate 判定を共通化する。完了条件: `database/slevo.db` duplicate も invalid になる。
 - [x] 3.2 `BackupReader.readBackup()` に `var dbTempFile: File? = null` と cleanup helper を導入する。完了条件: success 前の error return で temp DB file を削除できる構造になっている。
 - [x] 3.3 DB temp file は `File.createTempFile(...)` の default directory に作成し、unit test で必要な場合のみ `internal` temp file factory/provider を追加する。完了条件: production code は Context/cacheDir 依存を追加せず、test は temp file cleanup を検証できる。
 - [x] 3.4 `entry.name == DB_PATH` の file entry では `zip.readBytes()` を使わず、`File.createTempFile(...)` と `outputStream().use { zip.copyTo(it) }` で一時ファイルへ stream copy する。完了条件: production code に DB payload 用の `readBytes()` が残っていない。
 - [x] 3.5 JSON file entry では既存通り `entries[name] = zip.readBytes()` を維持する。完了条件: `manifest.json` / `datastore/*.json` の parse logic が大きく変わっていない。
 - [x] 3.6 必須 entry 確認を `seenEntries` または `dbTempFile != null` を使う形に更新する。完了条件: `database/slevo.db` が missing の場合は従来通り `missing required entry: database/slevo.db` になる。
 - [x] 3.7 DB schema pre-validation では `dbTempFile` を `BackupDatabaseValidator.preValidate(dbFile, manifest.databaseVersion)` に渡し、既存の `writeBytesToTempFile()` call を削除する。完了条件: `writeBytesToTempFile()` が不要なら削除されている。
 - [x] 3.8 `BackupPreview` 生成時に `dbFile = validatedDbFile` を渡す直前に、`dbTempFile = null` または `ownershipTransferred = true` を設定して cleanup scope から所有権移譲する。完了条件: success result の `preview.dbFile.exists()` が true になり、`BackupReader` 内 cleanup によって削除されない。
 - [x] 3.9 manifest / DB / DataStore validation の failure path で `dbTempFile.delete()` が best-effort 実行されるようにする。完了条件: invalid ZIP で temp file が残らない test を追加できる構造になっている。
 - [x] 3.10 DB temp file 作成後、`zip.copyTo(...)`、`outputStream()`、`ZipInputStream.nextEntry`、malformed ZIP read など ZIP streaming 中の I/O 例外でも `dbTempFile.delete()` が best-effort 実行されるようにする。完了条件: stream failure 後に temp DB file が残らない。

## 4. PendingRestoreManager staging 更新

 - [x] 4.1 `PendingRestoreManager.prepareRestore()` の DB staging を `preview.dbBytes` から `preview.dbFile` ベースに変更する。完了条件: `writeBytes(preview.dbBytes)` が存在しない。
 - [x] 4.2 `java.nio.file.Files.move` が project の Android minSdk / desugaring 設定で利用可能か source inspection で確認する。完了条件: 利用可能なら `Files.move(..., REPLACE_EXISTING)` を使い、利用不可なら API-compatible な `File` / stream copy strategy を primary path にする判断が記録されている。
 - [x] 4.3 move が使える場合は `Files.move(preview.dbFile.toPath(), dbFile.toPath(), REPLACE_EXISTING)` を優先し、失敗時は `preview.dbFile.copyTo(dbFile, overwrite = true)` または equivalent stream copy に fallback する helper を作成する。完了条件: move failure でも copy 成功なら staging が完了する。
 - [x] 4.4 copy-based staging path が fallback の場合も primary の場合も、copy 成功後に `preview.dbFile.delete()` を best-effort 実行する。完了条件: repository 側 cleanup と二重になっても no-op として安全である。
 - [x] 4.5 copy fallback が途中で失敗した場合、partial `pending-restore/database/slevo.db` を含めて `cleanupPendingDir()` で破棄する。完了条件: DB staging failure 後に pending directory が残らない。
 - [x] 4.6 DB staging 成功後に `checkIntegrity(dbFile, preview.databaseVersion)` が失敗した場合も `cleanupPendingDir()` を実行する。完了条件: integrity failure 後に pending directory が残らない。
 - [x] 4.7 DataStore JSON staging または marker write が失敗した場合も、DB staging 済みの pending directory 全体を `cleanupPendingDir()` で破棄する。完了条件: marker 作成前の部分 staging が残らない。
 - [x] 4.8 `cleanupPendingDir()` が既存の completed/prepared pending restore を誤って削除しない順序で呼ばれることを source inspection で確認する。完了条件: 既存 pending を拒否/整理してから current attempt staging を開始する順序、または current-attempt のみを cleanup する構造が確認されている。
 - [x] 4.9 DB staging failure 時は既存通り `failed to stage DB: ...` を返す。完了条件: staging failure の user-facing behavior が維持される。
 - [x] 4.10 `checkIntegrity(dbFile, preview.databaseVersion)` の呼び出し順序を維持する。完了条件: pending DB file 作成後に integrity check が実行される。
 - [x] 4.11 `BackupDatabaseValidator.preValidate()` が temp DB file を読み取り専用で検証しているか source inspection で確認する。完了条件: 読み取り専用でない場合は既存 requirement に合わせて validator 側または呼び出し方の修正 task を追加する。

## 5. BackupRepositoryImpl cleanup 更新

 - [x] 5.1 `BackupRepositoryImpl.previewBackup()` で `BackupReaderResult.Success` の `preview` を受け取った場合、`try/finally` または local helper で `preview.dbFile.delete()` を必ず実行する。完了条件: preview success 後に temp DB file が残らない。
 - [x] 5.2 `BackupRepositoryImpl.restoreBackup()` で `pendingRestoreManager.prepareRestore(effectivePreview)` 完了後、success/failure にかかわらず `preview.dbFile.delete()` を best-effort 実行する。完了条件: pending manager が move 済みの場合でも cleanup が安全に no-op になる。
 - [x] 5.3 `includeCookies == false` で `preview.copy(cookiesJson = null, containsCookies = false)` する箇所が `dbFile` を維持することを確認する。完了条件: copy 後も同じ temp DB file を staging に渡す。
 - [x] 5.4 `Dispatchers.IO` wrapping と `backupMutex.withLock` の既存構造を維持する。完了条件: repository public methods の dispatcher behavior が変わっていない。

## 6. Tests 更新

 - [x] 6.1 `BackupReaderTest` の success assertion を `preview.dbBytes` から `preview.dbFile.exists()` / `preview.dbFile.length()` に更新する。完了条件: `BackupReaderTest` に `dbBytes` 参照が残っていない。
 - [x] 6.2 `BackupReaderTest` に `database/slevo.db` duplicate entry を invalid とする test を追加または既存 test を更新する。完了条件: DB entry も duplicate validation の対象であることを検証する。
 - [x] 6.3 `BackupReaderTest` に「DB temp file 作成後、DataStore JSON invalid で temp DB file cleanup が行われる」追加テストを実装する。完了条件: `database/slevo.db` が temp file として作成された後に `datastore/settings.json` または `datastore/tabs.json` の validation が失敗し、処理後に temp DB file が残らないことを検証する。
 - [x] 6.4 `BackupReaderTest` に DB stream copy / ZIP read failure 後の temp DB cleanup test を追加する。完了条件: failure 後に作成途中の temp DB file が存在しない。
 - [x] 6.5 `BackupReaderTest` または repository-level test で、通常 JSON metadata より大きい DB entry を含む ZIP の preview が成功することを確認する。完了条件: 大容量 fixture が CI に重すぎる場合は、code-search acceptance を採用する理由を test/comment に残す。
 - [x] 6.6 `PendingRestoreManagerTest` の `BackupPreview` fixture を `dbFile` ベースに更新する。完了条件: pending restore の `database/slevo.db` が fixture DB file と同じ内容で staging される。
 - [x] 6.7 `PendingRestoreManagerTest` に copy fallback が途中失敗した場合の partial pending DB cleanup test を追加する。完了条件: failure 後に `pending-restore/database/slevo.db` が存在しない。
 - [x] 6.8 `PendingRestoreManagerTest` に「DB staging 成功後、integrity check failure で pending directory cleanup が行われる」追加テストを実装する。完了条件: `pending-restore/database/slevo.db` 作成後に `checkIntegrity()` が失敗し、処理後に pending directory が残らないことを検証する。
 - [x] 6.9 `PendingRestoreManagerTest` に「DB staging 成功後、marker write failure で pending directory cleanup が行われる」追加テストを実装する。完了条件: DB と DataStore JSON staging 後に marker 作成が失敗し、marker 未作成の pending directory が残らないことを検証する。
 - [x] 6.10 repository / ViewModel tests の `BackupPreview(dbBytes = ...)` 作成箇所を `dbFile = tempFile` に更新する。完了条件: test source 全体で `dbBytes` symbol が存在しない。
 - [x] 6.11 cleanup behavior の unit test が難しい場合は、testable helper を internal 化して JVM unit test で確認する。完了条件: helper の可視性変更には KDoc を追加する。

## 7. ドキュメント・コメント確認

- [x] 7.1 新規 helper / data model の KDoc が AGENTS.md の規則に合っていることを確認する。完了条件: class/interface/data class と非自明 function に KDoc がある。
- [x] 7.2 `BackupReader.readBackup()` が長くなる場合、`// --- ZIP entry read ---`、`// --- DB validation ---` など既存 section comment を維持・更新する。完了条件: 30行超の処理が section comment で区切られている。
- [x] 7.3 DB temp file cleanup の fallback / early return には brief comment を付ける。完了条件: non-obvious cleanup path がコメントで説明されている。
- [x] 7.4 一時 DB file はユーザーデータを含み得るため、DB content をログ出力しないこと、restore 処理以外へ露出しないこと、handled path で best-effort cleanup されることをコメントまたは実装で確認する。完了条件: temp DB content/path の扱いが data-safety 方針に合っている。

## 8. 検証

- [x] 8.1 `openspec validate stream-restore-database-entry --strict` を実行し、change artifact が valid であることを確認する。
- [x] 8.2 GitHub Actions workflow `Android CI` を現在 branch に対して実行する（例: `gh workflow run "Android CI" --ref <current-branch> --repo cuore-mm/Slevo`）。workflow が利用できない場合のみ、同等の repository-standard unit test command を実行し、`BackupReaderTest`、`PendingRestoreManagerTest`、関連 repository/ViewModel tests が含まれることを確認する。
- [x] 8.3 production restore code が `database/slevo.db` payload を DB-sized `ByteArray` として読み込まないことを検索で確認する。JSON metadata の `readBytes()` は許容する。
- [x] 8.4 production restore-related files（`data/backup/restore/**`、`data/backup/pending/**`、`data/backup/BackupRepositoryImpl.kt`）で `database/slevo.db` / `DB_PATH` / `slevo.db` handling と `readBytes(` / `writeBytes(` usage を確認し、DB payload の DB-sized `ByteArray` 化が残っていないことを確認する。完了条件: JSON metadata の `readBytes()` だけが許容対象として残る。
- [x] 8.5 一時 DB file の content がログ出力されず、restore 処理以外へ露出せず、handled success/failure path で best-effort cleanup されることを code review または test で確認する。
- [x] 8.6 実装 commit 前に `git diff` を確認し、P2-3（export 側 `BackupZipWriter.writeFileEntry()`）をこの change に混ぜていないことを確認する。
