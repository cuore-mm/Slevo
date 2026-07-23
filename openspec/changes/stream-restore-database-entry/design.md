## Context

`BackupReader.readBackup(input)` は ZIP entry を `entries: MutableMap<String, ByteArray>` に読み込んでから検証している。現在は `database/slevo.db` も `zip.readBytes()` で heap に保持し、その `ByteArray` を `writeBytesToTempFile()` で一時ファイルへ書き出して `BackupDatabaseValidator.preValidate()` に渡す。その後、同じ `ByteArray` を `BackupPreview.dbBytes` として返し、`PendingRestoreManager.prepareRestore()` が `dbFile.writeBytes(preview.dbBytes)` で pending restore directory に保存している。

この流れでは大きい Room DB を復元 preview するだけで DB 全体が heap に載り、Android の通常 heap limit で `OutOfMemoryError` が発生し得る。JSON metadata は小さいため memory parse でよいが、`database/slevo.db` は ZIP stream から一時ファイルへ直接 copy し、以後も file として扱う必要がある。

現在の主要ファイル:

- `BackupReader.kt`
  - `readBackup(input: InputStream)`
  - `writeBytesToTempFile(bytes: ByteArray)`
  - `DB_PATH = "database/slevo.db"`
- `BackupPreview.kt`
  - `dbBytes: ByteArray`
  - `equals()` / `hashCode()` は `dbBytes.contentEquals/contentHashCode` を使う
- `PendingRestoreManager.kt`
  - `prepareRestore(preview)` 内で `dbFile.writeBytes(preview.dbBytes)`
- `BackupRepositoryImpl.kt`
  - `previewBackup()` は `BackupReaderResult.Success.preview.containsCookies` だけを使う
  - `restoreBackup()` は preview を `PendingRestoreManager.prepareRestore(effectivePreview)` へ渡す

## Goals / Non-Goals

**Goals:**

- `database/slevo.db` を `ByteArray` にせず、ZIP stream から一時ファイルへ直接書き込む。
- `BackupPreview` の DB payload を `File` として表現し、pending restore staging でも file copy/move を使う。
- manifest、settings、tabs、cookies JSON は引き続き memory 上で parse する。
- DB validation、manifest validation、DataStore JSON validation、commit 時再検証の順序と意味を変えない。
- preview/restore の処理完了時に一時 DB file を best-effort cleanup する。
- 既存 ZIP format、Room schema、ユーザー向け復元 UI は変更しない。

**Non-Goals:**

- エクスポート側の `BackupZipWriter.writeFileEntry()` の memory buffering 修正はこの change では扱わない（P2-3 として別 change 推奨）。
- preview 成功時の `BackupPreview` を ViewModel に cache し、commit 時の再読み込みを省略する変更は行わない。
- ZIP entry 全体を temp file 化しない。対象は大きくなり得る `database/slevo.db` のみ。
- DB size limit の仕様化はこの change の必須 scope にしない。必要なら streaming 実装後の防御的 validation として別途検討する。

## Decisions

### Decision 1: DB entry のみ一時ファイルへ stream copy する

`BackupReader.readBackup()` の ZIP entry loop で、`entry.name == DB_PATH` の場合だけ `File.createTempFile(...)` を作成し、`ZipInputStream.copyTo(FileOutputStream)` で一時ファイルへ直接書き込む。`manifest.json` と `datastore/*.json` は既存通り `ByteArray` として `entries` map に入れる。

一時 DB file の作成場所は、既存の復元 preview 用 DB 一時ファイルと同じ platform temp directory（`File.createTempFile(...)` の default directory）を使う。unit test で cleanup を検証しにくい場合のみ、`BackupReader` に `internal` な temp file factory/provider を追加し、production default は `File.createTempFile(...)` のままにする。Context や app `cacheDir` への新規依存はこの change では導入しない。

一時 DB file は履歴、ブックマーク、投稿履歴、タブ状態などユーザー由来データを含む可能性がある。実装では restore 処理以外の目的でこの file path/content を公開せず、ログにも path 以外の内容を出さない。handled failure path と preview/restore 完了 path では best-effort cleanup を行い、不要な永続化を避ける。

理由:

- OOM risk は DB entry が支配的で、JSON entry は小さい。
- 既存の JSON parse code を大きく変えずに DB heap allocation だけを取り除ける。
- `BackupDatabaseValidator.preValidate()` は既に `File` を受け取るため、validation path と相性がよい。

代替案:

- size limit だけ追加する案は採用しない。`readBytes()` 後に判定しても、判定前に OOM する可能性がある。
- すべての entry を temp file にする案は採用しない。JSON entry まで file I/O にする必要がなく、cleanup 対象も増える。

### Decision 2: `BackupPreview` は `dbFile: File` を持つ

`BackupPreview.dbBytes: ByteArray` を `dbFile: File` に置き換える。KDoc は「検証済みバックアップ DB を保持する一時ファイル。所有権は呼び出し側へ移る」ことを明記する。`equals()` / `hashCode()` は `dbFile.absolutePath` と metadata fields を比較対象にする、または `data class` default の `File.equals()` に委ねる。ただし `File` の内容比較は行わない。

理由:

- model が `ByteArray` を持つ限り、実装者が再び DB 全体を memory に戻す余地が残る。
- `PendingRestoreManager.prepareRestore()` は file を staging できるため、内部 model も file に寄せる方が自然。

注意:

- `BackupPreview` は UI state として長時間保持しない。`BackupRepositoryImpl.previewBackup()` は metadata を取り出したら `preview.dbFile.delete()` する。
- tests では `dbBytes.contentEquals` 前提の assertion を `dbFile.exists()` / `dbFile.length()` / 小さい fixture のみ `readBytes()` に置き換える。

### Decision 3: cleanup ownership は `BackupRepositoryImpl` が持つ

`BackupReader` は一時 DB file を作成して `BackupPreview` へ渡す。以後の cleanup は `BackupRepositoryImpl` が担当する。

- `previewBackup(uri)`:
  - `backupReader.readBackup(input)` が success を返したら `try/finally` で `preview.dbFile.delete()`。
  - 返却値は `BackupRestoreResult.Success(result.preview.containsCookies)` のみなので、DB file はその場で削除できる。
- `restoreBackup(uri, includeCookies)`:
  - success preview を `pendingRestoreManager.prepareRestore(effectivePreview)` に渡す。
  - `finally` で `preview.dbFile.delete()` を呼ぶ。`prepareRestore()` が move して既に存在しない場合は no-op。
- `BackupReader.readBackup()`:
  - validation/parsing failure で `BackupPreview` を返さない場合は、自身が作成した temp DB file を `finally` / error path で削除する。

`BackupReader.readBackup()` の実装では、success result を構築する直前に ownership transfer guard を明示する。具体的には、返却する `File` を local 変数（例: `val validatedDbFile = dbTempFile ?: ...`）へ取り出した後、cleanup 対象の `dbTempFile` を `null` にする、または `ownershipTransferred = true` を設定してから `BackupPreview(dbFile = validatedDbFile, ...)` を返す。これにより `finally` cleanup が success result の DB file を削除する事故を防ぐ。

cleanup 対象は validation/parsing failure だけではない。DB temp file 作成後に `zip.copyTo(...)`、`outputStream()`、`ZipInputStream.nextEntry`、malformed ZIP read など ZIP streaming 中の I/O 例外が発生した場合も、`BackupReader` は作成済み `dbTempFile` を best-effort で削除する。実装では ZIP 読み取り全体を cleanup scope の内側に置き、success transfer guard が設定されていない限り catch/finally で temp DB file を削除する。

理由:

- `BackupReader` だけでは success 後の file lifetime を判断できない。
- repository は preview/restore use case の boundary であり、temp resource cleanup の責務を持ちやすい。

### Decision 4: pending restore staging は move 優先、copy fallback にする

`PendingRestoreManager.prepareRestore()` の DB staging は `preview.dbFile` から pending DB path へ file として移す。

推奨手順:

1. `dbFile.parentFile?.mkdirs()` を済ませる。
2. 既存 staging file がある場合は削除または `REPLACE_EXISTING` で置換する。
3. Android API / desugaring の互換性を確認できる場合は `Files.move(preview.dbFile.toPath(), dbFile.toPath(), REPLACE_EXISTING)` を試す。
4. `Files.move` が project の supported API で使えない場合、または move が失敗した場合（cross-filesystem 等）は、API 互換な `File` / stream copy based の fallback を使う。
5. copy 成功後に `preview.dbFile.delete()` を best-effort 実行する。

move/copy のどちらかが最終的に失敗した場合は、`cleanupPendingDir()` を必ず実行し、途中まで書かれた `pending-restore/database/slevo.db` を残さない。copy fallback 中に例外が発生した場合も同じ failure path を通す。個別 file 削除ではなく pending directory cleanup を基本にし、marker 未作成の部分 staging 全体を破棄する。

copy-based staging path を使う場合は、それが `Files.move` failure 後の fallback であっても、Android API compatibility のための primary path であっても、copy 成功後に source である `preview.dbFile` を best-effort で削除する。

DB staging が成功した後でも、pending restore が完全に準備される前に失敗した場合は `cleanupPendingDir()` を実行する。対象には `checkIntegrity(dbFile, preview.databaseVersion)` の失敗、DataStore JSON staging の失敗、marker 書き込み失敗を含む。marker 作成前の pending directory は常に「部分 staging」として扱い、復元候補として残してはならない。

data-safety 上、`prepareRestore()` は既存 pending state の確認・拒否・cleanup を終えた後に新しい staging を開始する前提である。新規 staging 開始後の `cleanupPendingDir()` は「現在の試行で作成した marker 未作成の部分 staging」を破棄する目的で使う。既存の completed/prepared pending restore を誤って削除しないことは実装時に source inspection で確認し、必要なら current-attempt 用 staging directory を分けるか、既存 pending を拒否してから cleanup する順序を維持する。

理由:

- `File.renameTo()` は失敗理由が取得しにくい。
- `Files.move()` は例外で failure を扱え、同一 filesystem なら DB file の二重書き込みを避けられる。
- fallback により temp dir と filesDir が異なる filesystem の場合も対応できる。

### Decision 5: validation 順序は維持する

DB file は ZIP 読み取り中に temp file へ作成するが、validation の論理順序は既存と同じにする。

1. ZIP entry path validation / duplicate validation
2. 必須 entry 存在確認
3. manifest parse / field validation
4. Cookie entry 整合性確認
5. DB schema pre-validation
6. DataStore JSON parse / value validation
7. `BackupPreview` 作成

DB temp file は entry 読み取り時点で作成されるが、manifest validation に失敗した場合は DB validation せず cleanup して invalid を返す。

## Implementation Contract

実装 agent は以下を守ること。

1. `BackupReader.readBackup()` から `entries[DB_PATH] = zip.readBytes()` と `val dbBytes = entries[DB_PATH]!!` を削除する。
2. DB file entry の存在判定は `entries` map ではなく、DB temp file 変数または `seenEntries` set で行う。
   - directory entry `database/` は DB file として扱わない。
   - duplicate 判定は JSON entry と DB entry の両方に効くように `seenEntries: MutableSet<String>` を使う。
3. DB temp file は `File.createTempFile(...)` の default directory に作成する。testability が不足する場合だけ `internal` temp file factory/provider を追加し、production default は変えない。
4. ZIP 読み取り中に DB temp file を作った後、後続 validation/parsing が失敗した場合は必ず temp file を削除する。
5. success result を返す直前に ownership transfer guard（`dbTempFile = null` または `ownershipTransferred = true`）を設定し、cleanup scope が返却済み file を削除しないようにする。
6. `BackupPreview` の constructor / KDoc / `equals()` / `hashCode()` / call sites を `dbFile: File` に更新する。
7. `PendingRestoreManager.prepareRestore()` は `preview.dbFile` から pending DB path へ move/copy し、`writeBytes(preview.dbBytes)` を使わない。
8. `PendingRestoreManager.prepareRestore()` の move/copy failure path は `cleanupPendingDir()` を通し、partial pending DB file を残さない。
9. `PendingRestoreManager.prepareRestore()` は DB staging 成功後の integrity check failure、DataStore JSON staging failure、marker write failure でも `cleanupPendingDir()` を通し、marker 作成前の部分 staging を残さない。
10. `Files.move` の Android compatibility を実装時に確認する。使えない場合は API-compatible fallback を使い、同じ move/copy/cleanup semantics と tests を満たす。
11. `BackupRepositoryImpl.previewBackup()` と `restoreBackup()` は success preview の `dbFile` を `finally` で削除する。
12. 小さい unit test fixture 以外で DB file 全体を `readBytes()` しない。production code に DB-sized `readBytes()` を残さない。

## Risks / Trade-offs

- [Risk] `BackupReader` success 後の temp DB file が cleanup されず cache/temp に残る。
  → Mitigation: `BackupRepositoryImpl` で `try/finally` cleanup を実装し、failure path は `BackupReader` 内で cleanup する unit test を追加する。
- [Risk] `Files.move()` が filesystem 差異や provider/cache path の差異で失敗する。
  → Mitigation: `copyTo(overwrite = true)` fallback を実装し、move failure を fake/helper で unit test 可能にする。
- [Risk] `BackupPreview` の equality が DB content 比較ではなく file identity/path 比較になる。
  → Mitigation: `BackupPreview` は UI 表示や staging の一時 model として使い、content equality に依存しない。tests は content equality ではなく file existence/length/staged file content を検証する。
- [Risk] ZIP 読み取り中に DB temp file を作った後、manifest validation で invalid になると temp file が残る。
  → Mitigation: `readBackup()` 内に `var dbTempFile: File?` を置き、success transfer 前の error return では cleanup helper を必ず通す。
- [Risk] P2-3（export 側の DB readBytes）は残る。
  → Mitigation: この change の scope を restore 側に限定し、別 change で `BackupZipWriter.writeFileEntry()` の streaming write を計画する。

## Migration Plan

- Runtime data migration は不要。
- ZIP format version は変更しない。
- Room schema は変更しない。
- 実装後は `openspec validate stream-restore-database-entry --strict`、Android CI の unit test を実行する。
- Android verification は repository standard の GitHub Actions workflow `Android CI` を現在 branch に対して実行する（例: `gh workflow run "Android CI" --ref <current-branch> --repo cuore-mm/Slevo`）。workflow が利用できない場合のみ、同等の repository-standard unit test command を実行し、少なくとも `BackupReaderTest`、`PendingRestoreManagerTest`、関連 repository/ViewModel tests が含まれることを確認する。
- 問題があれば、この change のコード差分を revert しても既存 backup ZIP 互換性には影響しない。

## Testing Strategy

- `BackupReaderTest`
  - valid ZIP success 時に `preview.dbFile.exists()` と `preview.dbFile.length() > 0` を確認する。
  - `database/slevo.db` entry が duplicate の場合に invalid になることを確認する。
  - manifest invalid / required entry missing などの failure path で temp DB file が削除されることを確認する（helper で temp dir を注入できない場合は実装を testable にする）。
  - DB temp file 作成後、`BackupPreview` 返却前に失敗する case（例: DataStore JSON invalid）で cleanup が行われることを確認する。
  - DB temp file 作成後、ZIP stream copy または ZIP read が I/O 例外で失敗する case で cleanup が行われることを確認する。既存 API で stream failure を注入しにくい場合は、`internal` helper/provider を追加して JVM unit test 可能にする。
  - 実用的な範囲で、通常の JSON metadata より大きい DB entry（例: 数 MB〜数十 MB の生成 fixture）を含む ZIP を読み込み、heap に DB-sized `ByteArray` を保持しなくても preview が成功することを確認する。CI 時間や memory 制約で大容量 fixture が現実的でない場合は、production restore path の DB payload `readBytes()` 不在を code-search acceptance check として採用する理由を test/comment に残す。
- `PendingRestoreManagerTest`
  - `BackupPreview(dbFile = tempDbFile, ...)` を渡すと `pending-restore/database/slevo.db` が作成され、内容が fixture DB と一致することを確認する。
  - move/copy failure 時に pending dir cleanup と error message が返ることを確認する。
  - move failure 後の copy fallback が途中失敗した場合、partial `pending-restore/database/slevo.db` が残らないことを確認する。
  - DB staging 成功後に `checkIntegrity()` が failure を返した場合、pending directory が cleanup されることを確認する。
  - DB staging 成功後に DataStore JSON staging または marker write が failure になった場合、pending directory が cleanup されることを確認する。
- Repository level tests
  - `previewBackup()` success 後に temp DB file cleanup が呼ばれることを、fake `BackupReader` または temp file assertion で確認する。
  - `restoreBackup()` success/failure 後も temp DB file cleanup が行われることを確認する。
- Verification search
  - production restore-related files（`data/backup/restore/**`、`data/backup/pending/**`、`data/backup/BackupRepositoryImpl.kt`）で `database/slevo.db` / `DB_PATH` / `slevo.db` handling と `readBytes(` / `writeBytes(` usage を確認し、DB payload を DB-sized `ByteArray` として読み込まないことを確認する。JSON metadata の `readBytes()` は許容する。正確な production restore path の網羅は実装時 source inspection で確認する。
  - temporary DB file について、DB content をログ出力していないこと、restore 処理以外へ露出していないこと、handled path で best-effort cleanup されることを確認する。

## Implementation-Time Source Inspection Items

- `java.nio.file.Files.move` が project の Android minSdk / desugaring 設定で利用可能か確認する。利用不可なら `File` / stream copy fallback を primary path にする。
- `BackupDatabaseValidator.preValidate()` が temp DB file を読み取り専用で検証しているか確認する。読み取り専用でない場合は、既存 requirement と整合するよう validator 側または呼び出し方を調整する。
- `cleanupPendingDir()` が既存の completed/prepared pending restore を削除しない順序で呼ばれることを確認する。必要なら current attempt staging の作成前に既存 pending を拒否・整理する順序を維持する。
- ZIP stream copy failure と pending staging failure を unit test で再現するための既存 helper があるか確認し、なければ `internal` helper/provider を追加する。
- production restore-related files の正確な search scope を source inspection で確認し、`database/slevo.db` payload の DB-sized `ByteArray` 化が残っていないことを確認する。

## Open Questions

なし。DB size limit はこの change の必須要件に含めない。
