## ADDED Requirements

### Requirement: migration 試行開始証跡を復元世代と一体で永続化する

システムは pending restore の Room migration の最初の実処理を呼び出す前に、その復元 marker と同じ原子的な永続化単位へ試行開始証跡を記録しなければならない（SHALL）。証跡を永続化できない場合、システムは migration の実処理を開始してはならない（MUST NOT）。

#### Scenario: pending restore の最初の migration を開始する
- **WHEN** marker が `MIGRATION_PENDING`、試行開始証跡なし、`marker.databaseVersion` が最初の migration の開始 version と一致する
- **THEN** システムは試行開始証跡を原子的に永続化してから migration delegate を一度だけ呼ぶ

#### Scenario: 証跡の書き込みに失敗する
- **WHEN** migration delegate の呼び出し前に試行開始証跡を永続化できない
- **THEN** システムは migration delegate を呼ばず、marker、DB rollback snapshot、DataStore rollback snapshot を次回復旧用に保持する

#### Scenario: 同じ開始 migration が同一プロセスで再び呼ばれる
- **WHEN** 同じ `MIGRATION_PENDING` marker と開始 version に対する試行開始証跡が既に true で、Room open または migration wrapper が再び呼ばれる
- **THEN** システムは migration delegate を呼ばずに停止し、marker と rollback artifact を次回コールドスタート用に保持する

#### Scenario: pending restore ではない通常 migration
- **WHEN** pending restore marker が存在しない、または marker status/version が開始する migration と一致しない
- **THEN** システムは pending-restore 証跡を書き換えず、登録済み migration delegate を従来と同じ順序と version で実行する

### Requirement: 初回と反復 migration 失敗をクラッシュセーフに区別する

システムはメモリ内カウンター、時刻、またはプロセス生存状態ではなく、永続 marker の試行開始証跡と live DB の `user_version` の組み合わせで初回試行と反復失敗を判定しなければならない（SHALL）。

#### Scenario: 旧形式 marker による初回試行
- **WHEN** `migrationAttemptStarted` を含まない旧形式の `MIGRATION_PENDING` marker を読み、live DB の `user_version` が `marker.databaseVersion` と等しく pre-validation が成功する
- **THEN** システムは試行開始証跡なしとして Room に最初の migration 試行を委ねる

#### Scenario: 初回の新形式 marker による試行
- **WHEN** `migrationAttemptStarted=false` の `MIGRATION_PENDING` marker があり、live DB の `user_version` が `marker.databaseVersion` と等しく pre-validation が成功する
- **THEN** システムは marker と rollback artifact を保持したまま Room migration の開始を許可する

#### Scenario: migration transaction の失敗後に再起動する
- **WHEN** `migrationAttemptStarted=true` の `MIGRATION_PENDING` marker があり、live DB の `user_version` が `marker.databaseVersion` と等しく pre-validation が成功する
- **THEN** システムは同じ migration を再実行せず、既存の migration failure rollback 経路へ遷移する

#### Scenario: 証跡 commit 後かつ SQL 実行前に終了する
- **WHEN** 試行開始証跡の commit 後、migration delegate の実処理または DB transaction commit 前にプロセスが終了する
- **THEN** 次回起動は永続証跡を反復失敗として保守的に扱い、同じ migration を再実行せず rollback 経路へ遷移する

### Requirement: marker の条件判定と更新を直列化する

システムは試行開始証跡の read-check-write と他の pending-restore marker 操作を同じプロセス内排他境界で直列化し、stale な marker copy で新しい status または復元内容を上書きしてはならない（MUST NOT）。完全な marker のディスク公開は既存の atomic file 契約を使用しなければならない（SHALL）。

#### Scenario: recorder の判定中に marker status の更新が競合する
- **WHEN** recorder の conditional mutation と別の marker status 更新が競合する
- **THEN** システムは両操作を直列化し、recorder は lock 内で最新 marker を再評価して不一致なら証跡を書かない

#### Scenario: conditional mutation の公開中にプロセスが終了する
- **WHEN** 試行開始証跡を含む marker の atomic publication 中にプロセスが終了する
- **THEN** 次回起動は以前 commit 済み marker または新しく commit 済み marker の一方だけを読み、部分 JSON を読まない

### Requirement: DB commit 済み状態を試行証跡より優先する

システムは live DB の `user_version` が現在の DB version 以上である場合、試行開始証跡の値にかかわらず migration commit 済みとして strict validation と既存の完了処理を行わなければならない（SHALL）。

#### Scenario: DB commit 後かつ completion 前にプロセスが終了する
- **WHEN** `migrationAttemptStarted=true` の marker が残る一方、live DB の `user_version` が現在の DB version 以上である
- **THEN** システムは DB を rollback せず strict validation を行い、成功時は `COMPLETED`、result 永続化、marker-last cleanup の既存順序で完了する

#### Scenario: commit 済み DB の strict validation が失敗する
- **WHEN** live DB の `user_version` が現在の DB version 以上で、strict validation が失敗する
- **THEN** システムは既存の migration failure rollback または quarantine 経路へ遷移する

### Requirement: 反復失敗の rollback は DB と DataStore の世代整合を維持する

システムは反復 migration 失敗を既存の rollback state machine へ渡し、復元前 DB snapshot と DataStore snapshot の両方が復旧するまで terminal cleanup を行ってはならない（MUST NOT）。

#### Scenario: DB と DataStore の rollback が成功する
- **WHEN** 完全な rollback snapshot を持つ反復 migration 失敗で DB と DataStore の復旧が両方成功する
- **THEN** システムは両者を同じ復元前世代へ戻して失敗を確定し、既存の durable result/marker/cleanup 順序を適用する

#### Scenario: rollback の片側が失敗する
- **WHEN** DB または DataStore の rollback の少なくとも一方が完了しない
- **THEN** システムは `ROLLBACK_REQUIRED` marker と必要な全 artifact を保持し、次回コールドスタートで同じ snapshot 世代の rollback を再試行する

#### Scenario: rollback snapshot が存在しない
- **WHEN** 反復 migration 失敗を検出したが安全に利用できる復元前 DB snapshot が存在しない
- **THEN** システムは現行の quarantine/failure 経路を使用し、同じ migration を無期限に再試行しない

#### Scenario: DB rollback manifest が欠落または破損している
- **WHEN** marker は rollback が必要な既存 DB を示すが、DB rollback manifest または必須 DB artifact が欠落・破損している
- **THEN** システムは不完全な DB snapshot を復元せず、現行の安全な failure/quarantine 状態と利用可能 artifact を保持し、同じ migration を再実行しない

#### Scenario: DataStore rollback snapshot が欠落または破損している
- **WHEN** DB rollback 後に必要な DataStore rollback snapshot が欠落・破損している、または DataStore rollback が完了しない
- **THEN** システムは片側復旧を terminal cleanup せず `ROLLBACK_REQUIRED` と必要 artifact を保持し、同じ snapshot 集合で安全に再試行または手動復旧できる状態を維持する

### Requirement: ユーザー向け UI 契約を変更しない

システムは反復 migration 失敗の復旧に新しい画面、操作、通知種別、またはユーザー向け文言を追加してはならず（MUST NOT）、既存の pending restore result 通知契約を再利用しなければならない（SHALL）。

#### Scenario: 反復 migration 失敗を rollback する
- **WHEN** 反復 migration 失敗が既存 rollback/failure 経路で処理される
- **THEN** システムは既存 result consumer が扱える結果だけを生成し、Compose UI と string resource を変更しない
