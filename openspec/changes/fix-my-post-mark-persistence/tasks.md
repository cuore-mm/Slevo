## 1. Room schemaと永続化モデル

- [ ] 1.1 `data/datasource/local/entity` 配下へ `PendingOwnPostEntity` と `PENDING`、`MATCHED`、`EXPIRED` のdomain状態を追加し、design.md記載のscope、投稿入力、照合範囲、時刻、状態、`matchedResNum` を表現する。完了条件: 全fieldと複合scope/status indexがEntity定義に存在し、型KDocがリポジトリ規約を満たす。
- [ ] 1.2 `data/datasource/local/dao` 配下へ `PendingOwnPostDao` を追加し、insert、scope完全一致のPENDING取得、`lastCheckedResNum` 更新、期限切れ遷移、PENDINGからMATCHEDへの条件付き更新、30日超terminal削除を定義する。完了条件: 全更新queryが対象scopeまたはprimary keyと期待statusを条件に持ち、別スレッド全件走査APIがない。
- [ ] 1.3 `AppDatabase.kt` をversion 10へ更新し、Entity/DAO accessor、`MIGRATION_9_10`、`ALL_REGISTERED_MIGRATIONS` を追加する。完了条件: migrationが既存tableを変更せず `pending_own_posts` と必要indexだけを作成し、v9→v10の連続pathが登録される。
- [ ] 1.4 `DatabaseModule.kt` に `PendingOwnPostDao` providerを追加し、既存DAOと同じsingleton `AppDatabase` から取得する。完了条件: Hilt graphで新DAOを解決できる。
- [ ] 1.5 `app/schemas/com.websarva.wings.android.slevo.data.datasource.local.AppDatabase/10.json` をRoom schema exportで生成する。完了条件: schema JSONのtable、列、primary key、indexがEntityと `MIGRATION_9_10` に一致する。

## 2. scopeと照合ロジック

- [ ] 2.1 投稿対象を識別する `OwnPostThreadScope` を追加し、現行providerでは `parseServiceName(boardUrl)`、`parseBoardUrl(boardUrl)` のboard key、thread keyから生成する。完了条件: 正常URL、空provider、parse失敗、空thread keyの単体テストが通り、DB内部IDをscopeに使っていない。
- [ ] 2.2 純粋ロジックの `OwnPostMatcher` を追加し、本文の改行・行末空白正規化、名前/メールtrim、本文完全一致、空identity wildcard、非空identity完全一致を実装する。完了条件: `OwnPostMatcherTest` が一致、不一致、CRLF、行末空白、大小文字差、行内空白差、空/非空identityを検証する。
- [ ] 2.3 `PendingOwnPostRepository` を追加し、`DatabaseWriteGate` 経由のPENDING作成、scope限定取得、確認位置更新、EXPIRED遷移、terminal cleanupを実装する。完了条件: Repository/DAOテストが別provider・板・threadの非取得、24時間境界、30日cleanup境界を検証する。

## 3. 原子的な投稿履歴確定

- [ ] 3.1 `PostHistoryRepository.recordPost` のDAO書き込み本体を `internal recordPostUngated` へ抽出し、public APIは従来どおり1回だけgateを取得して委譲する。完了条件: 既存呼び出しの挙動を維持するRepositoryテストと、ungated helperがgateを再取得しないテストが通る。
- [ ] 3.2 `PendingOwnPostRepository.completeMatch` を `gate.withWritePermit { appDatabase.withTransaction { ... } }` で実装し、PENDING条件付きMATCHED更新が1件成功した場合だけ `recordPostUngated` を呼ぶ。完了条件: 成功時にpendingと投稿/identity履歴が全て保存され、投稿履歴失敗時に全てrollbackし、解決済みpendingの再実行で重複履歴が増えないinstrumented DAO/Repositoryテストが通る。
- [ ] 3.3 `OwnPostReconciliationUseCase` を追加し、scope限定PENDINGを `submittedAt` 順に、未確認範囲だけ0/1/複数候補判定し、確認位置更新、MATCHED確定、EXPIRED遷移を行う。完了条件: UseCase単体テストがレス増加なし、0候補、1候補、複数候補、期限境界、同一実行内候補再利用防止を検証する。

## 4. 投稿成功とスレッドロードへの統合

- [ ] 4.1 `ThreadRouteViewModel.onThreadPostSuccess` を変更し、scopeと `contentStates[tabKey].threadInfo.resCount`（fallbackは `ThreadTabInfo.resCount`）からPENDINGを保存してから `reloadThread` を呼ぶ。完了条件: `ThreadRouteViewModelTest` が保存引数、保存→reloadの順序、`PostDialogSuccess.resNum` 非参照、scope生成失敗時のreload継続を検証する。
- [ ] 4.2 `ThreadRouteViewModel.loadThreadContent` の既存 `recordPendingPost` 呼び出しを `OwnPostReconciliationUseCase` に置き換え、`recordHistory` と `collectMyPostNumbers` の後にscope、`uiPosts`、historyId、boardIdを渡す。完了条件: INITIAL、MANUAL、BOTTOM_PULL、AUTO_SCROLLの共通成功経路で対象scopeが照合され、dat取得失敗時は照合されないViewModelテストが通る。
- [ ] 4.3 `ThreadSessionRuntimeState.PendingThreadPostState` と `ThreadRouteViewModel.recordPendingPost` を削除し、`pending.resNum ?: uiPosts.size` による自レス判定をなくす。完了条件: application codeに旧pending型と末尾レスfallbackの参照が残らず、既存タブruntime stateテストを新しい責務へ更新する。
- [ ] 4.4 MATCHED後の既存 `PostHistoryRepository.observeMyPostNumbers` 経路を回帰確認する。完了条件: 同じhistoryIdのRoom Flowが確定resNumを発行し、投稿行、返信ポップアップ、ミニマップへ渡す `isMyPost` 判定が既存どおりtrueになるテストが通る。

## 5. migrationとバックアップ互換性

- [ ] 5.1 unit版 `AppDatabaseMigrationTest` をv10と `MIGRATION_9_10` に更新し、migration登録数、同一instance、連続pathを検証する。完了条件: v2→v10までの登録migration検証が通る。
- [ ] 5.2 androidTest版 `AppDatabaseMigrationTest` にv9→v10 migration testを追加し、新tableの全列/indexと既存v9データ保持を検証する。完了条件: `runMigrationsAndValidate(..., 10, true, MIGRATION_9_10)` が成功する。
- [ ] 5.3 `BackupDatabaseValidator.kt` のcurrent identity hash、required tables、`EXPECTED_TABLES_BY_VERSION[10]` をschema `10.json` に合わせ、v2-v9のtable setを変更しない。完了条件: validatorテストがv10 current schemaを受理し、`pending_own_posts` 欠落を拒否し、v2-v9 historical schemaへv10 tableを要求しない。
- [ ] 5.4 v9バックアップからv10へのpending restore migration pathを関連backup testで検証する。完了条件: v9の整合したバックアップDBがpre-validation後に `MIGRATION_9_10` を通過し、v10 strict validationに成功する。

## 6. 最終検証

- [ ] 6.1 新規・変更した全class/interface、非自明関数、30行超関数を確認し、AGENTS.mdのKDoc、annotation配置、section header、非自明分岐コメント規約を満たす。完了条件: 対象diffに必須commentの欠落がない。
- [ ] 6.2 `./gradlew testDebugUnitTest` を実行し、全unit testを成功させる。完了条件: commandがexit code 0で終了する。
- [ ] 6.3 `./gradlew assembleDebug` を実行し、Debug buildを成功させる。完了条件: commandがexit code 0で終了する。
- [ ] 6.4 Android test実行可能環境でmigration/DAO/Compose回帰testを実行する。完了条件: 対象androidTestが成功するか、環境不足の場合は未実行commandと理由を実装handoffへ明記する。
- [ ] 6.5 `openspec validate fix-my-post-mark-persistence --strict` を実行し、実装結果と `proposal.md`、`design.md`、delta specs、`tasks.md` の不整合を解消する。完了条件: strict validationが成功する。
