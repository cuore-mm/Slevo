# legacy-backup-restore Specification

## Purpose
TBD - created by archiving change support-legacy-backup-restore. Update Purpose after archive.
## Requirements
### Requirement: 古い DB version のバックアップ許可
システムはバックアップ ZIP の `manifest.databaseVersion` が現在 Room DB version より古い場合でも、対応範囲内で現在 version までの migration path が存在するなら復元候補として受け付けなければならない（MUST）。

#### Scenario: current DB version のバックアップを受け付ける
- **WHEN** `manifest.databaseVersion` が現在 Room DB version と一致するバックアップをユーザーが選択する
- **THEN** システムは従来通り復元候補として preview を表示できる

#### Scenario: migration path がある古い DB version のバックアップを受け付ける
- **WHEN** `manifest.databaseVersion` が対応最小 DB version 以上かつ現在 Room DB version 未満で、現在 version までの migration path が存在するバックアップをユーザーが選択する
- **THEN** システムは復元候補として preview を表示できる

#### Scenario: 対応最小 DB version より古いバックアップを拒否する
- **WHEN** `manifest.databaseVersion` が対応最小 DB version より小さいバックアップをユーザーが選択する
- **THEN** システムは復元を開始せず、現在のアプリでは復元できないバックアップとして通知する

#### Scenario: migration path がない古いバックアップを拒否する
- **WHEN** `manifest.databaseVersion` が現在 Room DB version 未満だが現在 version までの migration path が存在しないバックアップをユーザーが選択する
- **THEN** システムは復元を開始せず、現在のアプリでは復元できないバックアップとして通知する

#### Scenario: 未来 DB version のバックアップを拒否する
- **WHEN** `manifest.databaseVersion` が現在 Room DB version より大きいバックアップをユーザーが選択する
- **THEN** システムは downgrade 復元を行わず、現在のアプリでは復元できないバックアップとして通知する

### Requirement: pre-migration DB validation
システムは古い DB version のバックアップを pending restore として保存または適用する前に、pre-migration validation として SQLite integrity、DB file の user_version、manifest の databaseVersion、migration path を検証しなければならない（MUST）。

#### Scenario: SQLite integrity が正常な古い DB を受け付ける
- **WHEN** バックアップ内の `database/slevo.db` が読み取り専用で開け、`PRAGMA integrity_check` が `ok` を返し、`PRAGMA user_version` が manifest の `databaseVersion` と一致し、現在 version までの migration path が存在する
- **THEN** システムはその DB を pre-migration validation 成功として扱う

#### Scenario: SQLite integrity が壊れた DB を拒否する
- **WHEN** バックアップ内の `database/slevo.db` を開けない、または `PRAGMA integrity_check` が `ok` 以外を返す
- **THEN** システムは pending restore を作成せず、無効または破損したバックアップとして通知する

#### Scenario: manifest と DB file の version 不一致を拒否する
- **WHEN** `manifest.databaseVersion` と `database/slevo.db` の `PRAGMA user_version` が一致しない
- **THEN** システムは pending restore を作成せず、無効なバックアップとして通知する

#### Scenario: DB file の user_version が対応最小より古い場合を拒否する
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が対応最小 DB version より小さい
- **THEN** システムは pending restore を作成せず、現在のアプリでは復元できないバックアップとして通知する

#### Scenario: DB file の user_version が未来 version の場合を拒否する
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が現在 Room DB version より大きい
- **THEN** システムは pending restore を作成せず、現在のアプリでは復元できないバックアップとして通知する

#### Scenario: DB file の user_version から migration path がない場合を拒否する
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が対応範囲内だが現在 Room DB version までの migration path が存在しない
- **THEN** システムは pending restore を作成せず、現在のアプリでは復元できないバックアップとして通知する

#### Scenario: version validation 失敗理由を内部記録する
- **WHEN** manifest または DB file の version が future、too-old、mismatch、または migration path missing により拒否される
- **THEN** システムは user-facing 文言を既存方針に保ち、preview-only の拒否では詳細ログ、ユーザー復元確定後に result writer または pending result area を確保できた後の拒否では result file に詳細 reason を記録する

#### Scenario: 古い DB の現在 identity hash 不一致を pre-migration では拒否しない
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が現在 Room DB version より古く、SQLite integrity と migration path validation が成功する
- **THEN** システムは現在 Room schema の identity hash と一致しないことだけを理由に pending restore を拒否しない

#### Scenario: 古い DB の現在 table 不足を pre-migration では拒否しない
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が現在 Room DB version より古く、SQLite integrity と migration path validation が成功する
- **THEN** システムは現在 Room schema の全必須 table が存在しないことだけを理由に pending restore を拒否しない

#### Scenario: historical schema sanity check を pre-migration では要求しない
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が対応範囲内で、SQLite integrity、manifest version 一致、migration path validation が成功する
- **THEN** システムは historical Room identity hash または historical table list の照合を pre-migration validation の必須条件にしない

### Requirement: 起動時 migration 待ち状態
システムは pending restore を live DB path へ差し替えた後、current DB version のバックアップを含めて Room open 後の completion checker が成功を確認するまで rollback backup と pending marker を保持しなければならない（MUST）。

#### Scenario: DB swap 後に migration pending として保持する
- **WHEN** 起動時 pending restore 適用で DB swap と DataStore 反映が成功し、差し替えた DB の `PRAGMA user_version` が現在 Room DB version 以下である
- **THEN** システムは rollback backup を削除せず、pending marker を migration 成功確認待ちの状態として保持する

#### Scenario: current DB version でも migration pending として保持する
- **WHEN** 起動時 pending restore 適用で差し替えた DB の `PRAGMA user_version` が現在 Room DB version と一致する
- **THEN** システムは即時 cleanup せず、Room open 後の post-migration validation 成功確認を経て pending restore を完了扱いにする

#### Scenario: migration pending 中は新しい復元準備を拒否する
- **WHEN** migration 成功確認待ちの pending marker が存在する状態でユーザーが別のバックアップ復元を確定する
- **THEN** システムは新しい pending restore を作成せず、前回の復元状態の完了または失敗 recovery を優先する

#### Scenario: rollback-required または completed cleanup 中は新しい復元準備を拒否する
- **WHEN** rollback-required marker または completed marker が存在する状態でユーザーが別のバックアップ復元を確定する
- **THEN** システムは新しい pending restore を作成せず、既存 marker、rollback backup、staging file の recovery または cleanup を優先する

#### Scenario: failed marker は cleanup 後に新しい復元準備を許可する
- **WHEN** terminal failed marker が存在する状態でユーザーが別のバックアップ復元を確定する
- **THEN** システムは failed result を保持し、active failed marker、staging file、rollback backup を cleanup できた場合のみ新しい pending restore を作成する

### Requirement: Room migration 後の完了確認
システムは Room が restored DB を open して migration を実行した後、`DatabaseCallback.onOpen()` から起動される completion checker により post-migration validation を行い、成功した場合だけ pending restore を完了扱いにしなければならない（MUST）。

#### Scenario: DatabaseCallback.onOpen から completion checker を起動する
- **WHEN** Room が restored DB を open し、必要な migration が完了して `DatabaseCallback.onOpen()` が呼ばれる
- **THEN** システムは post-DB-open startup task として completion checker を I/O dispatcher 上で起動する

#### Scenario: DatabaseCallback は checker の operational exception を局所的に隔離する
- **WHEN** `DatabaseCallback.onOpen()` が起動した coroutine で provider 取得または completion checker から `CancellationException` 以外の `Exception` が送出される
- **THEN** システムはその例外をログへ記録して swallow し、marker に基づく次回 cold start recovery を妨げない

#### Scenario: DatabaseCallback は checker coroutine の cancellation を伝播する
- **WHEN** `DatabaseCallback.onOpen()` が起動した completion checker coroutine で `CancellationException` が発生する
- **THEN** システムは `CancellationException` を swallow せず再 throw する

#### Scenario: Application や Activity 本体から completion checker を起動しない
- **WHEN** システムが migration 成功確認の呼び出し位置を構成する
- **THEN** システムは `SlevoApplication.onCreate()` または `MainActivity.onCreate()` 本体ではなく、Room migration 後に呼ばれる `DatabaseCallback.onOpen()` を使用する

#### Scenario: migration 成功後に cleanup する
- **WHEN** Room が restored DB を open し、post-migration validation で `PRAGMA user_version`、Room identity hash、現在 schema の必須 table、SQLite integrity がすべて現在 schema と一致する
- **THEN** システムは completed marker を記録した後に復元成功 result、staging file、rollback backup、pending marker を cleanup し、pending marker の削除は最後に行う

#### Scenario: migration 成功後は completed marker を cleanup より先に記録する
- **WHEN** Room open 後の post-migration validation が成功する
- **THEN** システムは completed marker を記録してから success result、staging file、rollback backup を処理し、pending marker の削除は cleanup の最後に行う

#### Scenario: completed marker 書き込み失敗時は成功処理を停止する
- **WHEN** post-migration validation が成功したが completed marker の書き込みで `Exception` が発生する
- **THEN** completion checker は例外を外へ投げず、success result 書き込みと cleanup を実行せず、migration-pending marker と rollback backup を次回 cold start recovery 用に保持する

#### Scenario: success result 書き込み失敗でも rollback しない
- **WHEN** post-migration validation が成功して completed marker が記録された後に success result の書き込みが失敗する
- **THEN** completion checker は例外を外へ投げず、後続 cleanup を実行せず、rollback backup と staging file を削除せず、completed marker を保持して次回 cold start で success result 書き込みと cleanup を再試行する

#### Scenario: completed marker が残る場合は rollback しない
- **WHEN** completed marker が残っている状態でアプリが cold start する
- **THEN** システムは live DB を rollback せず、未完了の success result 書き込みまたは cleanup を再試行し、完了後に marker を削除する

#### Scenario: stale migration-pending は rollback 前に live DB を再検証する
- **WHEN** migration 成功確認待ちの marker が cold start 時に残っている
- **THEN** システムは rollback 前に live DB の post-migration validation と同等の strict validation を実行し、成功する場合は rollback せず completed cleanup へ進む

#### Scenario: stale migration-pending は rollback backup 不在より strict validation を優先する
- **WHEN** migration 成功確認待ちの marker が cold start 時に残っており rollback backup が存在しないが、live DB の strict validation は成功する
- **THEN** システムは pending restore を failed にせず、completed cleanup へ進む

#### Scenario: migration 後 validation 失敗は rollback-required として記録する
- **WHEN** Room open 後の post-migration validation が失敗する
- **THEN** システムは live DB file を即時置換せず、pending marker を rollback-required として記録し、rollback backup を保持する

#### Scenario: rollback-required は success result を記録しない
- **WHEN** Room open 後の post-migration validation が失敗して rollback-required を記録する
- **THEN** システムは復元成功 result を記録せず、rollback-required と診断情報を result file に記録し、追加 UI 文言は表示しない

#### Scenario: rollback-required は result 後に marker を更新する
- **WHEN** Room open 後の post-migration validation が失敗して rollback-required を記録する
- **THEN** システムは rollback-required result を先に書き、その後で marker を rollback-required へ atomic replace し、recovery 判定では marker を source of truth として扱う

#### Scenario: rollback-required 記録後の現在 session では DB recovery を行わない
- **WHEN** completion checker が rollback-required marker と result file の記録に成功する
- **THEN** システムは同じ session では live DB file の置換、rollback、quarantine、process restart を行わず、次回 cold start の recovery に委ねる

#### Scenario: rollback-required result 書き込み失敗時は marker 更新を停止する
- **WHEN** Room open 後の post-migration validation が失敗し、rollback-required result の書き込みで `Exception` が発生する
- **THEN** completion checker は例外を外へ投げず、rollback-required marker を書き込まず、migration-pending marker と rollback backup を recovery authority として保持する

#### Scenario: rollback-required marker 書き込み失敗時は migration-pending を保持する
- **WHEN** rollback-required result の書き込みは成功したが、marker の rollback-required への atomic replace で `Exception` が発生する
- **THEN** completion checker は例外を外へ投げず、live DB file と rollback backup を変更せず、既存の migration-pending marker を recovery authority として保持する

#### Scenario: completion checker の operational exception は recovery state を保持する
- **WHEN** marker 読み取りまたは post-migration validation を含む completion checker の operational 処理で `CancellationException` 以外の `Exception` が発生する
- **THEN** completion checker は例外を外へ投げず、失敗した操作より後の write/cleanup を実行せず、直近の durable marker と rollback backup を次回 cold start recovery 用に保持する

#### Scenario: rollback-required は次回 cold start で rollback する
- **WHEN** アプリ起動時に rollback-required marker が存在する
- **THEN** システムは Room が live DB を開く前に rollback backup から DB を復旧し、pending restore を failed として記録する

#### Scenario: migration 成功確認前のクラッシュを次回起動で rollback する
- **WHEN** アプリ起動時に migration 成功確認待ちの marker が残っている
- **THEN** システムは live DB の strict validation が失敗し、rollback backup が存在する場合だけ live DB を置換前の DB へ戻し、pending restore を failed として記録する

#### Scenario: rollback backup がない migration pending を failed として記録する
- **WHEN** アプリ起動時に migration 成功確認待ちの marker が残っており、live DB の strict validation が失敗し、rollback backup も存在しない
- **THEN** システムは invalid live DB file-set を quarantine して fresh DB 起動を優先し、pending restore を failed として記録する

#### Scenario: quarantine 成功後に fresh DB 起動可能にする
- **WHEN** rollback backup が存在せず、invalid live DB file-set の quarantine が成功する
- **THEN** システムは live DB path に invalid main DB、`-wal`、`-shm` を残さず、Room が fresh DB を作成できる状態で通常起動を優先する

#### Scenario: quarantine は次回復元準備をブロックしない
- **WHEN** quarantine directory が診断 artifact として残っている状態でユーザーが別のバックアップ復元を確定する
- **THEN** システムは quarantine directory の存在だけを理由に新しい pending restore 作成を拒否せず、quarantine directory を暗黙に削除しない

#### Scenario: rollback は WAL と SHM を整合させる
- **WHEN** システムが cold start 時に rollback backup から live DB を復旧する
- **THEN** システムは live DB path の `-wal` と `-shm` を先に削除し、rollback backup の main DB を戻し、rollback backup に `-wal` または `-shm` が存在する場合のみそれらを戻す

#### Scenario: rollback backup がない rollback-required を failed として記録する
- **WHEN** アプリ起動時に rollback-required marker が残っているが rollback backup が存在しない
- **THEN** システムは invalid live DB file-set を quarantine して fresh DB 起動を優先し、pending restore を failed として記録する

#### Scenario: quarantine できない場合は手動確認が必要な failed として記録する
- **WHEN** rollback backup が存在せず、invalid live DB file-set の quarantine または削除にも失敗する
- **THEN** システムは pending restore を failed として記録し、result file に手動確認が必要な状態を記録する

### Requirement: migration path 定義の一貫性
システムは復元許可判定に使う migration path 定義と Room に登録される migration chain を一貫させなければならない（MUST）。

#### Scenario: migration path helper と Room 登録が一致する
- **WHEN** アプリが `DatabaseModule.provideAppDatabase()` で Room migration を登録する
- **THEN** システムはバックアップ復元の migration path 判定で使う migration edge と同じ migration chain を Room に登録する

#### Scenario: migration chain の欠けをテストで検出する
- **WHEN** 対応最小 DB version から現在 DB version までの migration chain に欠けがある
- **THEN** 自動テストは migration path 定義または Room migration 登録の不整合として失敗する

### Requirement: 古い DB migration の内部診断情報
システムは古い DB version のバックアップを復元する場合、UI 文言を変更せず、migration の有無と完了状態を result file の診断情報として永続化しなければならない（MUST）。

#### Scenario: 古い DB migration の詳細を内部記録する
- **WHEN** 現在 Room DB version より古い `databaseVersion` のバックアップ復元が migration 完了確認まで成功する
- **THEN** システムは `backupDatabaseVersion`、`currentDatabaseVersion`、`migrationRequired`、`migrationCompleted` を result file に記録する

#### Scenario: rollback-required から final failed へ result を更新する
- **WHEN** rollback-required result が記録された後、次回 cold start で rollback、quarantine、または quarantine 失敗のいずれかが確定する
- **THEN** システムは result file を latest `failed` status として上書きし、`previousStatus = "rollback-required"`、`rollbackRequiredAt`、final failure reason 相当を保持する

#### Scenario: 診断 field の failure 値を記録する
- **WHEN** rollback-required、failed after rollback、または rollback backup missing の final failed result を記録する
- **THEN** システムは `migrationRequired` を backup DB version が current DB version より古いかどうかで設定し、`migrationCompleted = false` を記録する

#### Scenario: 古い DB migration でも UI 文言を変更しない
- **WHEN** 現在 Room DB version より古い `databaseVersion` のバックアップを復元する
- **THEN** システムは復元前確認ダイアログ、Snackbar、成功通知に古い DB migration 専用の追加文言を表示しない

