## 1. Marker 互換性と試行証跡

- [x] 1.1 `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreMarker.kt` に `migrationAttemptStarted: Boolean = false` を追加し、型 KDoc にフィールドの意味を記載する。既存 constructor 呼び出しがコンパイルでき、フィールド欠落 JSON が既定値を使えることを確認する。
- [x] 1.2 `app/src/test/.../PendingRestoreMarkerTest.kt` と `PendingRestoreManagerTest.kt` に、旧 JSON の decode が `false`、新 JSON の true/false round-trip が値を保持するテストを追加する。
- [x] 1.3 `PendingRestoreApplier.applyRestore()` の `DB_SWAPPED -> MIGRATION_PENDING` marker 公開で `migrationAttemptStarted = false` を明示し、新しい復元世代が既存 marker copy の証跡を継承しないことを `PendingRestoreApplierTest.kt` で確認する。

## 2. Room migration 開始境界の永続 recorder

- [x] 2.1 `PendingRestoreFileStore` に companion-level の process-wide lock で marker の read/write/replace と同じ排他境界を使う conditional mutation API を追加し、lock 内の最新 marker にだけ変換を適用する。既存 AtomicFile publication と KDoc を維持する。
- [x] 2.2 `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/` に migration 試行 recorder を追加し、marker が `MIGRATION_PENDING`、証跡 false、`databaseVersion == startVersion` の全条件を満たす場合だけ conditional mutation で true にする。結果を `Recorded` / `NotApplicable` / `AlreadyStarted` で返し、新規 class と非自明関数へ規約準拠 KDoc を追加する。
- [x] 2.3 同 package に AndroidX `Migration` の wrapper を追加し、`Recorded` / `NotApplicable` の場合だけ delegate `migrate()` を一度呼び、同じ version の `AlreadyStarted` と永続化例外では delegate 前に停止する。delegate の start/end version と例外を保持する。
- [x] 2.4 recorder/wrapper の unit test を追加し、対象 marker の原子的更新、marker 不在・status 不一致・version 不一致の `NotApplicable`、複数段 chain で初段だけ更新、delegate 一回実行を検証する。
- [x] 2.5 同じ開始 version で証跡が既に true の wrapper を同一プロセス内で再呼び出す unit test を追加し、`AlreadyStarted`、delegate 実行 0 回、marker/artifact 保持を確認する。
- [x] 2.6 conditional mutation の stale read/marker status 競合テストを追加し、別 writer の新 status または marker 内容を recorder が上書きしないことを確認する。
- [x] 2.7 marker read/write 例外を注入する unit test を追加し、証跡永続化失敗時に delegate 実行回数が 0、marker と rollback artifact が保持されることを検証する。
- [x] 2.8 `app/src/main/java/com/websarva/wings/android/slevo/di/DatabaseModule.kt` の `provideAppDatabase()` に recorder を注入し、`AppDatabase.ALL_REGISTERED_MIGRATIONS` を wrapper 化して `.addMigrations(...)` へ登録する。callback と debug downgrade fallback の登録順序を維持する。
- [x] 2.9 `AppDatabaseMigrationTest.kt` の migration chain/path 検証を wrapper 登録後も通し、pending marker がない通常 upgrade で全 delegate が従来順に一度ずつ実行されるテストを追加する。

## 3. 次回起動の状態分類と安全な rollback

- [x] 3.1 `PendingRestoreApplier.recoverFromMigrationPending()` の `userVersion == marker.databaseVersion` かつ pre-validation 成功分岐に証跡判定を追加し、false は現行どおり return、true は `rollbackMigrationFailure()` へ渡す。current/unreadable/intermediate version の先行分類を変更しない。
- [x] 3.2 `PendingRestoreApplierTest.kt` に、証跡 false なら `MIGRATION_PENDING` と artifact を保持して Room に委ね、証跡 true なら同じ migration を再試行せず DB/DataStore を元世代へ rollback して失敗確定するテストを追加する。
- [x] 3.3 `PendingRestoreApplierTest.kt` に、証跡 true でも current `user_version` なら rollback せず strict validation、`COMPLETED`、result、cleanup の順で完了するテストを追加する。
- [x] 3.4 `PendingRestoreApplierTest.kt` に、反復失敗時の DB rollback 成功/DataStore rollback 失敗および逆方向の失敗を追加し、`ROLLBACK_REQUIRED` と両 snapshot が残り、再生成した applier の次回起動で同じ snapshot 世代へ収束することを generation 値で検証する。
- [x] 3.5 rollback snapshot がない反復失敗のテストを追加し、現行 quarantine/failure 経路で terminal 化して migration retry に戻らないことを確認する。
- [x] 3.6 DB rollback manifest の欠落・破損と DataStore rollback snapshot の欠落・破損をそれぞれ再構築するテストを追加し、不完全 snapshot を成功扱いせず、片側復旧を cleanup せず、安全な `ROLLBACK_REQUIRED` または quarantine/failure 状態と artifact を保持することを確認する。

## 4. transaction・process death 境界テスト

- [x] 4.1 `app/src/androidTest/.../AppDatabaseMigrationTest.kt` または専用 migration instrumented test に、一時 DB と例外を投げる wrapped `Migration` を用意し、例外後の `PRAGMA user_version` が開始 version、marker の `migrationAttemptStarted` が true であることを検証する。
- [x] 4.2 証跡 commit 後かつ delegate 呼び出し前の終了状態を unit test で再構築し、新しい applier インスタンスが保守的 rollback へ進むことを確認する。
- [x] 4.3 migration/DB commit 成功後かつ completion checker 前の状態を test fixture で再構築し、新しい applier インスタンスが成功 DB を rollback せず finalization することを確認する。
- [x] 4.4 marker atomic write の中断テストに新フィールドを含め、更新中断時は以前 commit 済みの false/true のどちらか一方だけが読め、部分 JSON や世代不一致を公開しないことを確認する。
- [x] 4.5 startup ordering の regression test を追加し、`SlevoApplication.onCreate()` の `PendingRestoreApplier.runIfNeeded()` が完了して反復失敗 rollback を確定するまで、`DatabaseModule` の Room 生成と wrapped migration が開始されないことを test seam の呼び出し順で確認する。

## 5. UI 非変更・回帰・監査

- [x] 5.1 `PendingRestoreCompletionCheckerTest.kt` と `PendingRestoreResultConsumer` 関連テストを実行し、marker copy が証跡を失わず、既存 result 種別・表示条件・acknowledge 契約が変わらないことを確認する。Compose と `res/values/strings.xml` に差分がないことも diff で確認する。
- [x] 5.2 GitHub Actions で build、unit test、instrumented migration test の対象 workflow を実行し、正確な実装 HEAD の run ID と成功結果を記録する。CI run `29646989225`（HEAD `1c426aeb0671cda5d2a5309905cd5b4f0cb5aee1`）が成功。
- [x] 5.3 実装差分を対象に Codex review を実行し、本 P1 の crash-loop 解消、startup/DI ordering、AtomicFile 証跡、DB/DataStore generation、process-death 境界だけを監査する。別途キュー済みの P2 finding はこの変更へ取り込まず、発見した回帰だけを独立に記録する。P1 範囲の回帰なし、キュー済み P2 は未変更。
