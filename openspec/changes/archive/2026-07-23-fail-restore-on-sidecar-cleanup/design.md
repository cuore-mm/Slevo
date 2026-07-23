## Context

`RealPendingRestoreDbSwapper.cleanWalShm()` は、存在する live DB の `-wal` と `-shm` に `File.delete()` を実行するが、戻り値 `false` を無視し、例外も warning のみで呼び出し元へ返さない。したがって `replaceDbFile()` は古い sidecar を残したまま staged DB を live DB へ置換でき、`restoreRollbackBackup()` は古い sidecar を残したまま rollback main DB/WAL を復元できる。

`PendingRestoreApplier` には既に、DB 置換失敗から rollback を試行する経路と、rollback 失敗時に `ROLLBACK_REQUIRED` marker と pending/rollback artifact を残して次回起動で再試行する経路がある。本変更はその経路を再利用し、marker schema、snapshot manifest、backup format を変更しない。

DB file 操作中の quiescence は既存 startup 契約が提供する。`SlevoApplication.onCreate()` は `super.onCreate()` 直後、Hilt の `AppDatabase` が生成される前に `runBlocking` で `PendingRestoreApplier.runIfNeeded()` の完了を待つ。`PendingRestoreApplier` と `PendingRestoreDbSwapper` は Room/DAO/Repository に依存せず、Room が開いた後の `PendingRestoreCompletionChecker` は live DB を変更せず次回 cold start に rollback を延期する。この契約は初回 apply と全 cold-start recovery に共通であり、本変更は別の runtime gate を追加せず維持・検証する。

対象は `PendingRestoreDbSwapper.kt` の置換・rollback 前 sidecar cleanup と、その unit test に限定する。`cleanupCorruptFreshInstallDb()` の状態遷移、他の削除処理、キュー済みの別 finding は対象外とする。

## Goals / Non-Goals

**Goals:**

- 存在していた live `-wal` / `-shm` を一つでも安全に削除できない場合、メイン DB の置換・削除・上書き、rollback WAL 復元を開始しない。
- `File.delete()` の `false` と例外を同じ cleanup failure として呼び出し元へ伝える。
- rollback が未完了の cleanup failure では、既存 `ROLLBACK_REQUIRED` retry と pending/rollback artifact 保持を成立させる。
- WAL と SHM の各失敗を JVM unit test で決定的に再現する。
- cleanup 開始から置換・rollback 完了まで Room/SQLite writer が存在しない既存 cold-start ordering 契約を維持する。

**Non-Goals:**

- UI、通知文言、accessibility、画面遷移の変更。
- restore marker/state machine、Room schema、backup/rollback format の変更。
- `cleanupCorruptFreshInstallDb()` の契約変更。
- sidecar cleanup 以外のファイル削除失敗、またはキュー済み P2 finding の修正。

## Decisions

### 1. Sidecar 削除を失敗可能な前条件として扱う

`PendingRestoreDbSwapper.kt` の `cleanWalShm(dbFile)` を、成功時は `null`、失敗時は対象 sidecar を識別できる非 null error message を返す関数へ変更する。各 sidecar は、存在しない場合だけ成功扱いでスキップし、存在する場合は削除処理が `true` を返したときだけ成功扱いにする。`false` または例外では warning を記録して直ちに失敗を返す。

`replaceDbFile()` は cleanup error をそのまま非 null 戻り値として返し、temp copy、live main DB delete、rename を一切開始しない。`restoreRollbackBackup()` は cleanup error を記録して `false` を返し、live main DB delete、`mainDbRestore`、`walRestore` を一切開始しない。

この順序により、失敗不変条件は次の通りとなる。

1. cleanup が失敗した呼び出しでは、live main DB の内容と staged DB は変更されない。
2. rollback directory の main DB、任意 WAL、manifest は変更・削除されない。
3. 一方の sidecar が先に削除され、もう一方で失敗する部分 cleanup は許容するが、その後に別世代の main DB を配置しない。
4. 未削除 sidecar と異なる世代の main DB を意図的に共存させない。

代替案として warning 後の継続は、stale WAL replay を防げないため棄却する。sidecar の quarantine/rename は新しい recovery artifact と cleanup policy を必要とし、今回の最小修正を超えるため採用しない。

### 2. 既存 retry/state machine を変更せず利用する

置換 cleanup failure は `replaceDbFile()` の既存 error として `PendingRestoreApplier.applyRestore()` から `rollbackAndFail()` へ流す。rollback cleanup も失敗し続ける場合、`restoreRollbackBackup()` の `false` を既存処理が受け、`ROLLBACK_REQUIRED` marker、pending directory、rollback snapshot を保持する。次回起動の `recoverFromRollbackRequired()` が同じ rollback を再試行する。

一時的な cleanup failure の後、同じ失敗処理中の rollback cleanup が成功し安全な rollback が完了した場合は、既存どおり terminal failure と cleanup へ進める。安全な DB 世代へ回復済みであるため、不要な retry artifact は保持しない。

代替案として専用 marker を追加する方法は、永続 schema と recovery 分岐を増やす一方で既存 `ROLLBACK_REQUIRED` が同じ要件を満たすため棄却する。

### 3. Sidecar delete 専用の test seam を追加する

`RealPendingRestoreDbSwapper` に `internal var sidecarDelete: (File) -> Boolean = { it.delete() }` を追加し、`cleanWalShm()` だけが使用する。production behavior は `File.delete()` のまま維持し、unit test は file 名に応じて `false` または例外を返せるようにする。`PendingRestoreFileOperations` や `PendingRestoreDbSwapper` の公開契約は拡張しない。

代替案として filesystem permission を操作する test は実行環境依存で不安定なため棄却する。既存の `mainDbRestore` / `walRestore` seam と同じ internal lambda pattern を使う。

### 4. Quiescence は既存 cold-start ordering の契約として固定する

`SlevoApplication.onCreate()` の `runBlocking`、`PendingRestoreApplier` の Room 非依存、Room open 後の rollback 延期を、sidecar cleanup の前提として明記する。cleanup から `replaceDbFile()` / `restoreRollbackBackup()` 完了までこの同期呼び出しを抜けないため、同じ main-thread startup flow から DB が再 open されない。

新しい `DatabaseWriteGate` 適用は採用しない。同 gate は既に開かれた repository write の調停用であり、物理 DB file 切替の前提である「Room 未生成」を置き換えない。新しい process lock や Room close/reopen も今回の false-return defect より広い lifecycle 変更になるため対象外とする。既存 ordering を破る ContentProvider/Worker 等が将来追加される場合は、別 change で startup architecture を再評価する。

## Implementation Contract

1. `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreDbSwapper.kt` に、既存 seam と同じ形式・KDoc 付きで `sidecarDelete` を追加する。
2. `cleanWalShm()` は sidecar が存在するとき `sidecarDelete` を呼び、`false` と例外を失敗として返す。失敗 message には少なくとも `-wal` または `-shm` の file 名を含める。
3. `replaceDbFile()` は cleanup 成功を temp file 作成/copy より前の必須条件にする。
4. `restoreRollbackBackup()` は manifest 検証後、live main DB または rollback source を変更する前に cleanup を完了させる。
5. `cleanupCorruptFreshInstallDb()` は本変更で return type、呼び出し元、marker 遷移を変更しない。共通 helper の新しい戻り値を利用する場合も、既存の best-effort 契約を維持する。
6. sidecar cleanup 以外の delete 判定や restore state machine を変更しない。
7. `app/src/main/java/com/websarva/wings/android/slevo/SlevoApplication.kt` の synchronous startup ordering、`PendingRestoreApplier` / `PendingRestoreDbSwapper` の Room 非依存、`PendingRestoreCompletionChecker` の cold-start rollback 延期を維持する。sidecar cleanup 実装から asynchronous work を起動しない。

## Error Cases and Compatibility

- WAL 削除が `false` または例外: WAL を識別する error を返し、SHM 処理および DB 置換/rollback restore を開始しない。
- WAL が存在せず SHM 削除が `false` または例外: SHM を識別する error を返し、DB 置換/rollback restore を開始しない。
- WAL 削除成功後に SHM 削除失敗: WAL は消えていてよいが、main DB と recovery artifact は保持し、異なる DB 世代への切替を禁止する。
- sidecar が存在しない、または両方削除成功: 現行の置換・rollback behavior を維持する。
- 永続データ形式と API は変えないため migration は不要で、既存 pending restore は次回処理から強化された前条件を受ける。

## Testing Strategy

`PendingRestoreDbSwapperTest.kt` で `sidecarDelete` を注入し、`WAL/SHM × false/exception × replace/rollback` の 8 組を明示的に網羅する。共通 assertion helper は使用できるが、両 call site の gate を直接呼び出す。置換失敗では error、live DB 内容不変、staged DB 不変、temp replacement 不在を確認する。rollback 失敗では `false`、live DB 内容不変、rollback main/WAL/manifest 不変、`mainDbRestore` と `walRestore` が未呼び出しであることを確認する。SHM failure では先行 WAL 削除後も main DB 切替がないことを確認する。既存の成功 test で sidecar 削除後の置換・rollback 成功を維持する。

`PendingRestoreApplierTest.kt` では fake の `replaceDbFileResult` を非 null にする未検証 branch を追加し、置換 error が `rollbackAndFail()` へ伝播することを確認する。rollback cleanup failure は `restoreRollbackBackupResult = false` で `ROLLBACK_REQUIRED`、`cleanupPending` 未実行、再試行に必要な artifact/state 保持を確認し、次回成功時のみ terminal cleanup へ進むことを確認する。real swapper の filesystem contract は上記 8 組、applier の orchestration contract は fake による branch test と分離し、Android filesystem setup を重複させる end-to-end unit test は追加しない。

`PendingRestoreApplierDependencyTest.kt` と `PendingRestoreApplierExceptionTest.kt` の既存 structural test を実行し、Room 非依存と `SlevoApplication` の synchronous invocation を維持する。必要なら assertion を補強し、`PendingRestoreCompletionChecker` が Room open 後に file rollback を行わない既存 test/コード契約も audit する。実装後は repository の必須 build/unit test を CI で実行する。

## Risks / Trade-offs

- [永続的な filesystem failure では起動ごとに rollback を再試行する] → 破壊的続行より安全性を優先し、既存 `ROLLBACK_REQUIRED` と artifact を手動復旧可能な形で保持する。
- [WAL 削除後の SHM 失敗で sidecar cleanup が部分完了する] → main DB 切替を禁止し、次回 retry では存在する sidecar のみ再処理する。
- [test seam が production class の可変 surface を増やす] → `internal` に限定し、既存 seam pattern に揃え、production default を `File.delete()` に固定する。
- [将来の startup component が Room を先行または並行 open すると quiescence が崩れる] → 現行 synchronous ordering と dependency isolation を structural test で固定し、その architecture を変える変更では restore safety review を必須にする。

## Migration Plan

永続 migration はない。通常リリースで適用し、既存 pending restore は marker を変換せず既存 recovery handler から再開する。rollback が必要な場合はこのコード変更を戻せるが、安全性を失うため stale sidecar failure が観測された端末では pending/rollback artifact を先に保全する。

## Open Questions

なし。
