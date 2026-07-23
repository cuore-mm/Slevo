## Context

`SlevoApplication.onCreate()` は Hilt/Room の DB 生成より先に `PendingRestoreApplier.runIfNeeded()` を同期実行する。復元処理は DB と DataStore のロールバックスナップショットを確定してから DB を差し替え、`PendingRestoreMarker.status` を `MIGRATION_PENDING` にして Room に migration を委ねる。

現行の `PendingRestoreApplier.recoverFromMigrationPending()` は live DB の `user_version` が `marker.databaseVersion` と等しく、`BackupDatabaseValidator.preValidate()` が成功すると常に「まだ migration を試していない」と判断して return する。Room/SQLite の migration と `user_version` 更新は同一トランザクションであるため、migration が例外終了すると両方がロールバックされる。次回起動でも同じ分岐に入り、ロールバックスナップショットが存在しても利用されない。

一方、`user_version >= AppDatabase.DATABASE_VERSION` の場合は DB コミット後と判断でき、現行の strict validation、`COMPLETED`、result、marker-last cleanup の順序を維持する必要がある。`ROLLBACK_REQUIRED` では DB と `datastore-rollback.json` を同じ復元世代として再試行する既存契約も維持する。

## Goals / Non-Goals

**Goals:**

- migration の最初の実処理へ入る直前に、現在の pending-restore マーカーへ試行開始証跡を原子的に永続化する。
- 次回コールドスタートで migration 前の `user_version` と試行開始証跡が同時に残っていれば、同じ migration を再試行せず既存の安全な rollback/quarantine 経路へ遷移する。
- marker 書き込み、Room migration transaction、DB コミット、completion checker の各境界でプロセスが終了しても、DB と DataStore の世代を混在させない。
- 証跡を持たない既存 JSON marker を初回試行として読み込み、バックアップファイル形式と Room schema を変更しない。

**Non-Goals:**

- migration 自体の修正、任意回数の retry、時間ベースまたはメモリ内カウンターの導入は行わない。
- 既存の rollback/quarantine アルゴリズム、結果 Snackbar の文言・表示条件、画面、操作を変更しない。
- キューに残る別の Codex finding や `prevent-partial-datastore-restore` の範囲を取り込まない。

## Decisions

### 1. 試行証跡は既存 marker の後方互換フィールドとする

`PendingRestoreMarker` に `migrationAttemptStarted: Boolean = false` を追加する。`false` は「この復元世代について Room migration の実処理開始を永続確認していない」、`true` は「開始を永続確認したが completion まで到達していない」を表す。

証跡を `restore.json` と別ファイルにすると marker と証跡の世代を原子的に対応付けられない。既存 marker の `copy()` と `AtomicPendingRestoreMarkerFile` に同居させれば、`databaseVersion`、`hadExistingLiveDb`、rollback artifact と同じ pending restore の source of truth になる。Moshi の既定値によりフィールドを持たない旧 marker は `false` として読む。`DB_SWAPPED -> MIGRATION_PENDING` の公開時には明示的に `false` を設定し、新しい復元世代が古い証跡を継承しないようにする。

新しい `RestoreStatus` は追加しない。enum 値追加は旧アプリが marker を deserialize できないため、既定値付き追加フィールドより互換性が低い。別の DataStore カウンターも、DB/DataStore rollback 世代とは独立した source of truth を作るため採用しない。

### 2. 証跡は各 Room migration の delegate 実行直前に記録する

新規の pending-restore migration ラッパーを `data/backup/pending` に置き、`Migration(startVersion, endVersion)` を委譲する。`DatabaseModule.provideAppDatabase()` は `AppDatabase.ALL_REGISTERED_MIGRATIONS` を直接登録せず、このラッパーで包んだリストを `.addMigrations(...)` へ渡す。

ラッパーは delegate の `migrate()` を呼ぶ直前に recorder を呼ぶ。recorder は `PendingRestoreFileStore` が提供するプロセス内直列化済みの conditional mutation で `restore.json` を読み、次の全条件を満たす場合だけ marker を `copy(migrationAttemptStarted = true)` で原子的に置換する。

1. marker が存在する。
2. `status == MIGRATION_PENDING` である。
3. `migrationAttemptStarted == false` である。
4. `marker.databaseVersion == delegate.startVersion` である。

recorder は `Recorded`、`NotApplicable`、`AlreadyStarted` の 3 結果を返す。`Recorded` は一致した初回 migration の証跡を今回 commit した状態、`NotApplicable` は marker 不在・status/version 不一致（通常 migration または複数段 chain の後段）、`AlreadyStarted` は同じ `MIGRATION_PENDING` かつ同じ開始 version の証跡が既に true の状態である。wrapper は `Recorded` と `NotApplicable` のときだけ delegate を呼び、`AlreadyStarted` では delegate より前に例外を投げる。これにより同一プロセスで DB provider/open が再試行されても同じ開始 migration を二度実行しない。最初の migration だけが条件 4 を満たし、複数段 migration の途中で marker を重ね書きしない。pending restore がない通常のアプリ更新では recorder は no-op であり、migration の SQL と順序は変えない。

marker 更新に失敗した場合、ラッパーは delegate を実行せず例外を伝播する。「DB を変更したが証跡がない」状態を禁止するためである。次回起動では未試行 marker と完全な rollback artifact が残り、永続化が回復した場合だけ再度開始できる。書き込み不能時に安全性を犠牲にして migration を続ける fallback は設けない。

`PendingRestoreFileStore` の同じ AtomicFile 経路を recorder から利用し、独自 JSON 書き込みを実装しない。`AtomicFile` 単体は read-check-write 全体の排他を保証しないため、`PendingRestoreFileStore` に companion-level の process-wide lock を使う conditional mutation API を追加する。marker の通常 read/write/replace と conditional mutation は同じ lock を通し、recorder が読んだ後に別 writer が公開した status や marker 内容を stale copy で上書きできないようにする。アプリは単一 Android process でこの marker を扱い、process death に対する publication atomicity は既存 AtomicFile が担う。Hilt から recorder を `DatabaseModule` に渡すが、`SlevoApplication.onCreate()` の pre-Hilt applier 順序は変更しない。

### 3. 起動時判定は DB の確定状態を先に、試行証跡を次に評価する

`PendingRestoreApplier.recoverFromMigrationPending()` の分類順序を次のまま明示する。

1. DB が読めない: 現行 `rollbackMigrationFailure()`。
2. `user_version >= currentDbVersion`: migration は commit 済みなので、`migrationAttemptStarted` に関係なく strict validation と既存 completion/finalization を行う。
3. `user_version == marker.databaseVersion`: pre-validation を実行する。
   - pre-validation 失敗: 現行 rollback/quarantine。
   - 成功かつ `migrationAttemptStarted == false`: 初回として Room に委ねる。
   - 成功かつ `migrationAttemptStarted == true`: 前回の migration transaction が完了しなかったと確定し、`rollbackMigrationFailure()` を呼ぶ。
4. その他の中間 version: 現行 rollback/quarantine。

これにより、DB commit 後から completion checker 前のプロセス終了では成功 DB を rollback しない。migration transaction 内または commit 前の終了では `user_version` が旧値へ戻り、外部 AtomicFile の証跡だけが残るため、次回起動で rollback できる。

### 4. rollback と失敗確定は既存の世代整合契約を再利用する

反復失敗は新しい削除・コピー処理を持たず、`rollbackMigrationFailure()` から既存 `rollbackAndFail()` / `quarantineAndFail()` へ渡す。

- `hadExistingLiveDb == true` かつ完全な rollback manifest がある場合、DB/WAL と `datastore-rollback.json` の両方が成功するまで terminal cleanup しない。
- 一部 rollback が失敗した場合は `ROLLBACK_REQUIRED` を保持し、次のコールドスタートで同じ snapshot 世代を再試行する。
- 置換前 DB がなかった場合または安全な DB rollback snapshot がない場合は、現行 quarantine 経路で失敗を確定する。
- DB manifest または DataStore snapshot が欠落・破損している場合は artifact を完全な snapshot とみなさず、片側だけを terminal success として cleanup しない。既存の `ROLLBACK_REQUIRED` 保持または quarantine/failure の安全側分岐を使う。
- rollback 完了時のみ `FAILED` result/marker と marker-last cleanup を行う。新しい UI 文言は追加しない。

### 5. テストは境界ごとの永続状態と実 migration failure を検証する

unit test では marker JSON の旧形式、recorder 条件、書き込み失敗時の delegate 非実行、`PendingRestoreApplier` を再生成した二回目起動、および DB/DataStore fake generation を検証する。instrumented migration test では一時 DB と意図的に例外を投げる wrapped `Migration` を使い、(a) 証跡がディスクに残る、(b) transaction rollback 後の `user_version` が開始 version のまま、(c) 次回 applier が rollback へ進むことを検証する。実プロセス kill に依存せず、各 durable commit 境界から新しいオブジェクトを生成することで process death 後の観測可能状態を決定論的に再現する。

## State Machine

```text
MIGRATION_PENDING(attempt=false), old user_version
  ├─ preValidate NG ───────────────> rollback/quarantine
  └─ preValidate OK ───────────────> Room migration wrapper
                                        │
                                        ├─ marker write NG -> delegate 未実行、artifact 保持
                                        └─ marker attempt=true を atomic commit
                                             │
                                             ├─ migration/commit 前失敗
                                             │    次回: old user_version + attempt=true
                                             │          -> rollback/quarantine
                                             └─ DB commit 成功
                                                  次回/同一起動:
                                                  current user_version
                                                  -> strict validation
                                                  -> COMPLETED -> result -> cleanup

rollback 一部失敗 -> ROLLBACK_REQUIRED -> 次回同じ DB/DataStore snapshot を再試行
```

## Implementation Contract

1. `PendingRestoreMarker.kt` に既定値 `false` の `migrationAttemptStarted` を追加し、既存 constructor/copy/JSON test を更新する。既存 marker fixture を書き換えずに decode 成功と `false` を確認する。
2. `PendingRestoreFileStore` の marker read/atomic replace を利用する recorder と、既存 `Migration` を委譲する wrapper を `data/backup/pending` に追加する。新規 class と非自明関数にはリポジトリ規約どおり KDoc を付ける。
3. `PendingRestoreFileStore` に全 marker 操作と同じ process-wide lock を使う conditional mutation を追加する。recorder は上記 4 条件だけで `true` を書き、`Recorded` / `NotApplicable` の場合だけ delegate を一度呼ぶ。同じ開始 version が既に true なら `AlreadyStarted` として delegate 前に停止する。delegate の `startVersion` / `endVersion`、実行順序、例外をそのまま保つ。
4. `DatabaseModule.provideAppDatabase()` に recorder を注入し、全 `ALL_REGISTERED_MIGRATIONS` を wrapper 化して登録する。debug downgrade fallback と `DatabaseCallback` の順序は変更しない。
5. `PendingRestoreApplier.applyRestore()` の `MIGRATION_PENDING` 公開で `migrationAttemptStarted = false` を明示し、`recoverFromMigrationPending()` の old-version/preValidate-success 分岐だけに証跡判定を追加する。current-version、unreadable、intermediate-version の既存分岐順序を崩さない。
6. completion checker/result consumer は新 status を追加せず、必要な marker copy が新フィールドを保持することだけを test で確認する。UI resource と Composable は変更しない。
7. unit test と instrumented migration test を追加し、GitHub Actions の build/unit/instrumented 対象範囲に沿って検証する。ローカル専用のタイミング依存テストは追加しない。

## Error Cases and Invariants

- **証跡永続化失敗**: migration delegate は 0 回、marker/rollback artifact は削除しない。
- **同じ開始 migration の同一プロセス再呼び出し**: `AlreadyStarted` により delegate は 0 回で停止し、次のコールドスタートの applier が rollback を担当する。
- **recorder の stale read と marker status 更新の競合**: 同じ process-wide lock 内で条件判定と置換を行い、新 status/世代を上書きしない。
- **証跡成功後の migration 例外/プロセス終了**: old `user_version` と `attempt=true` が残り、次回は migration を 0 回再実行して rollback へ進む。
- **DB commit 後のプロセス終了**: current `user_version` を優先し、`attempt=true` でも strict validation/finalization する。
- **rollback の DB または DataStore 片側失敗・snapshot 欠落/破損**: `ROLLBACK_REQUIRED` または既存 quarantine/failure の安全側状態と利用可能 artifact を保持し、世代混在を terminal success/failure として cleanup しない。
- **旧 marker**: 欠落フィールドは `false`。最初の migration を一度許可する。
- **通常のアプリ DB migration**: pending marker がなければ recorder は no-op。既存 migration SQL、chain、schema validation の意味を変えない。

## Risks / Trade-offs

- [AtomicFile の証跡 commit 直後、delegate の最初の SQL より前にプロセスが終了すると、次回は実 SQL 未実行でも反復失敗として rollback する] → データ安全性を優先する保守的判定とする。復元前 snapshot へ戻るだけで、破損 DB を再試行し続けるより安全である。
- [process-wide lock は複数 Android process 間の排他を提供しない] → 現行アプリと manifest の単一 process 契約を維持し、別 process をこの変更で導入しない。複数 process 対応を将来行う場合は OS file lock を別変更で設計する。
- [全 migration の wrapper 化で通常アップグレード経路へコードが入る] → marker/status/version の厳密な gate と no-op unit test、既存 migration chain test、instrumented migration test で退行を防ぐ。
- [marker 更新と Room transaction は同一トランザクションではない] → この分離が failure 証跡を Room rollback 後も残すために必要であり、判定は DB の commit 済み version を常に優先する。
- [旧アプリへバイナリ downgrade すると新フィールドを認識せず従来の retry 挙動になり得る] → schema/backup 互換を壊さない追加フィールドとするが、古い実装自体の crash-loop 修正までは保証しない。新バージョンへ戻れば marker と snapshot から復旧できる。

## Migration Plan

1. 追加フィールドは既定値付きで配布し、既存 marker と pending restore artifact をそのまま受け入れる。DB migration や一括データ変換は行わない。
2. 実装後は unit test、instrumented migration test、build を CI で実行し、pending marker がない通常 migration も確認する。
3. 問題があればアプリコードを revert できる。marker の追加 JSON フィールドは Moshi の未知フィールドとして旧形式 reader から無視でき、DB/backup schema は変わらない。ただし修正版で `attempt=true` が残る状態を旧バイナリが安全に処理する保証はしないため、rollback 中のバイナリ downgrade は避ける。

## Open Questions

なし。
