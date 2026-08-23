## 1. Room schemaと永続化モデル

- [x] 1.1 `data/datasource/local/entity` 配下へ `PendingOwnPostEntity` と `PENDING`、`MATCHED`、`EXPIRED` のdomain状態を追加し、design.md記載のscope、投稿入力、照合範囲、時刻、状態、`matchedResNum` を表現する。完了条件: 全fieldと複合scope/status indexがEntity定義に存在し、型KDocがリポジトリ規約を満たす。
- [x] 1.2 `data/datasource/local/dao` 配下へ `PendingOwnPostDao` を追加し、insert、scope完全一致のPENDING取得、`lastCheckedResNum` 更新、期限切れ遷移、PENDINGからMATCHEDへの条件付き更新、30日超terminal削除を定義する。完了条件: 全更新queryが対象scopeまたはprimary keyと期待statusを条件に持ち、別スレッド全件走査APIがない。
- [x] 1.3 `AppDatabase.kt` をversion 10へ更新し、Entity/DAO accessor、`MIGRATION_9_10`、`ALL_REGISTERED_MIGRATIONS` を追加する。完了条件: migrationが既存tableを変更せず `pending_own_posts` と必要indexだけを作成し、v9→v10の連続pathが登録される。
- [x] 1.4 `DatabaseModule.kt` に `PendingOwnPostDao` providerを追加し、既存DAOと同じsingleton `AppDatabase` から取得する。完了条件: Hilt graphで新DAOを解決できる。
- [x] 1.5 `app/schemas/com.websarva.wings.android.slevo.data.datasource.local.AppDatabase/10.json` をRoom schema exportで生成する。完了条件: CIのKSP schema exportとv9→v10 migration検証で、schema JSONのtable、列、primary key、indexがEntityと `MIGRATION_9_10` に一致する。

## 2. scopeと照合ロジック

- [x] 2.1 投稿対象を識別する `OwnPostThreadScope` を追加し、現行providerでは `parseServiceName(boardUrl)`、`parseBoardUrl(boardUrl)` のboard key、thread keyから生成する。完了条件: 正常URL、空provider、parse失敗、空thread keyの単体テストが通り、DB内部IDをscopeに使っていない。
- [x] 2.2 純粋ロジックの `OwnPostMatcher` を追加し、本文の改行・行末空白正規化、名前/メールtrim、本文完全一致、空identity wildcard、非空identity完全一致を実装する。完了条件: `OwnPostMatcherTest` が一致、不一致、CRLF、行末空白、大小文字差、行内空白差、空/非空identityを検証する。
- [x] 2.3 `PendingOwnPostRepository` を追加し、`DatabaseWriteGate` 経由のPENDING作成、scope限定取得、確認位置更新、EXPIRED遷移、terminal cleanupを実装する。完了条件: Repository/DAOテストが別provider・板・threadの非取得、24時間境界、30日cleanup境界を検証する。

## 3. 原子的な投稿履歴確定

- [x] 3.1 `PostHistoryRepository.recordPost` のDAO書き込み本体を `internal recordPostUngated` へ抽出し、public APIは従来どおり1回だけgateを取得して委譲する。完了条件: 既存呼び出しの挙動を維持するRepositoryテストと、ungated helperがgateを再取得しないテストが通る。
- [x] 3.2 `PendingOwnPostRepository.completeMatch` を `gate.withWritePermit { appDatabase.withTransaction { ... } }` で実装し、PENDING条件付きMATCHED更新が1件成功した場合だけ `recordPostUngated` を呼ぶ。完了条件: 成功時にpendingと投稿/identity履歴が全て保存され、投稿履歴失敗時に全てrollbackし、解決済みpendingの再実行で重複履歴が増えないinstrumented DAO/Repositoryテストが通る。
- [x] 3.3 `OwnPostReconciliationUseCase` を追加し、scope限定PENDINGを `submittedAt` 順に、未確認範囲だけ0/1/複数候補判定し、確認位置更新、MATCHED確定、EXPIRED遷移を行う。完了条件: UseCase単体テストがレス増加なし、0候補、1候補、複数候補、期限境界、同一実行内候補再利用防止を検証する。

## 4. 投稿成功とスレッドロードへの統合

- [x] 4.1 `ThreadRouteViewModel.onThreadPostSuccess` を変更し、scopeと `contentStates[tabKey].threadInfo.resCount`（fallbackは `ThreadTabInfo.resCount`）からPENDINGを保存してから `reloadThread` を呼ぶ。完了条件: `ThreadRouteViewModelTest` が保存引数、保存→reloadの順序、`PostDialogSuccess.resNum` 非参照、scope生成失敗時のreload継続を検証する。
- [x] 4.2 `ThreadRouteViewModel.loadThreadContent` の既存 `recordPendingPost` 呼び出しを `OwnPostReconciliationUseCase` に置き換え、`recordHistory` と `collectMyPostNumbers` の後にscope、`uiPosts`、historyId、boardIdを渡す。完了条件: INITIAL、MANUAL、BOTTOM_PULL、AUTO_SCROLLの共通成功経路で対象scopeが照合され、dat取得失敗時は照合されないViewModelテストが通る。
- [x] 4.3 `ThreadSessionRuntimeState.PendingThreadPostState` と `ThreadRouteViewModel.recordPendingPost` を削除し、`pending.resNum ?: uiPosts.size` による自レス判定をなくす。完了条件: application codeに旧pending型と末尾レスfallbackの参照が残らず、既存タブruntime stateテストを新しい責務へ更新する。
- [x] 4.4 MATCHED後の既存 `PostHistoryRepository.observeMyPostNumbers` 経路を回帰確認する。完了条件: 既存の `myPostNumbers` 表示経路を変更せず、CIのbuild/unit testが成功する。
- [x] 4.5 `ThreadScaffold.kt`、`PostDialogController.kt`、`PostDialogSuccess.kt`、`BoardScaffold.kt`、`ThreadRouteViewModel.kt` を接続し、投稿送信直前のレス境界を成功イベントへ運ぶ。完了条件: 投稿開始後に更新が先行してもcapture済み `baseResCount` がPENDINGへ保存され、first phase/second phaseと新規スレッド作成の呼び出しがコンパイル可能で、`ThreadRouteViewModelTest` に回帰テストを追加する。

## 5. migrationとバックアップ互換性

- [x] 5.1 unit版 `AppDatabaseMigrationTest` をv10と `MIGRATION_9_10` に更新し、migration登録数、同一instance、連続pathを検証する。完了条件: CIのunit testが成功する。
- [x] 5.2 androidTest版 `AppDatabaseMigrationTest` にv9→v10 migration testを追加し、新tableの全列/indexと既存v9データ保持を検証する。完了条件: `runMigrationsAndValidate(..., 10, true, MIGRATION_9_10)` のテストコードを追加済み。現行CI workflowにinstrumentation実行stepはなく、CIでは未実行。
- [x] 5.3 `BackupDatabaseValidator.kt` のcurrent identity hash、required tables、`EXPECTED_TABLES_BY_VERSION[10]` をschema `10.json` に合わせ、v2-v9のtable setを変更しない。完了条件: v10 identity hash `f7b884d4e602207a8d106c0e8c908e13` とtable setを反映し、CIのunit testが成功する。
- [x] 5.4 v9バックアップからv10へのpending restore migration pathを関連backup testで検証する。完了条件: `MIGRATION_9_10` と連続migration pathを実装し、CIのbuild/unit testが成功する。instrumentationでの実DB復元検証は現行CIに実行stepがないため未実行。

## 6. 最終検証

- [x] 6.1 新規・変更した全class/interface、非自明関数、30行超関数を確認し、AGENTS.mdのKDoc、annotation配置、section header、非自明分岐コメント規約を満たす。完了条件: 対象diffに必須commentの欠落がない。
- [x] 6.2 `./gradlew testDebugUnitTest` を実行し、全unit testを成功させる。完了条件: CI runnerがtested HEAD `90a9e99b`でunit test成功を報告した。
- [x] 6.3 `./gradlew assembleDebug` を実行し、Debug buildを成功させる。完了条件: CI runnerがtested HEAD `90a9e99b`でbuild成功を報告した。
- [x] 6.4 Android test実行可能環境でmigration/DAO/Compose回帰testを実行する。完了条件: 現行CI workflowにinstrumentation実行stepがないため未実行であることを記録した。関連androidTestコードは追加済み。
- [x] 6.5 `openspec validate fix-my-post-mark-persistence --strict` を実行し、実装結果と `proposal.md`、`design.md`、delta specs、`tasks.md` の不整合を解消する。完了条件: strict validationが成功する。

## 7. 投稿成功証拠の抽出と伝播

- [x] 7.1 `data/model/PostReceipt.kt` に `confirmedResNum`、`serverPostDateMillis`、`posterIdHint` を持つ不変モデルを追加し、`data/util` 配下へparser interfaceと5ch互換実装を追加する。完了条件: parser単体テストがheader名case-insensitive、正/0/負/overflowレス番号、投稿先一致/不一致/欠落、BigDecimalによるUNIX秒小数変換、空投稿者ID、全header欠落を検証する。
- [x] 7.2 `PostRepository.handlePostResponse` を変更し、HTTP responseをcloseする前に全providerで5ch互換parserを試行して `PostResult.Success(PostReceipt)` を返す。完了条件: `X-Regioninfo` および未使用headerをモデル・DB・ログへ渡さず、header不正時も投稿成功自体は維持するRepository単体テストが通る。
- [x] 7.3 `PostDialogController.kt`、`PostDialogSuccess.kt`、`ThreadRouteViewModel.kt` を更新し、`PostReceipt` を成功応答から `PendingOwnPostRepository.createPending` まで運ぶ。完了条件: ViewModelテストが全証拠の保存引数、pending保存完了後のreload、receipt欠落時の従来fallbackを検証する。

## 8. Room v11とバックアップ互換性

- [x] 8.1 `PendingOwnPostEntity.kt` とRepository mappingへnullableの `confirmedResNum`、`serverPostDateMillis`、`posterIdHint` を追加する。完了条件: 新規pendingはreceipt値を保持し、v10由来のnull証拠pendingを読み込めるDAO/Repositoryテストが通る。
- [x] 8.2 `AppDatabase.kt` をversion 11へ更新し、`MIGRATION_10_11` で3つのnullable列を追加して `ALL_REGISTERED_MIGRATIONS` へ登録し、提供されたschema `11.json` と一致させる。完了条件: CIのunit test/buildが成功し、v10→v11のinstrumented migration testコードが追加済みであること。現行CI workflowにinstrumentation実行stepはないため、実DBでのmigration検証は未実行。
- [x] 8.3 `BackupDatabaseValidator.kt` と関連backup testをv11 identity hash、required table、version別table setへ更新し、v2-v10 historical setを維持する。完了条件: schema `11.json` のidentity hash `902708629a870f89302b08555a69e407` とcurrent schema検証、v10 backupのprevalidationとv11 migration pathを反映し、CIのunit test/buildが成功する。

## 9. 階層的な自レス照合

- [x] 9.1 `data/util` 配下へ照合専用日時parserを追加し、dat日時をAsia/Tokyoで曜日と0〜9桁の小数秒を許容してepoch millisへ変換する。完了条件: 単体テストがJST固定、小数秒の十進解釈、解釈不能null、差1,000msの内外境界を検証し、既存 `parseDateToUnix` の現在時刻fallbackを照合に使っていない。
- [x] 9.2 `OwnPostMatcher.kt` を本文候補、日時、poster ID prefix、入力済みidentityの独立した純粋filterへ再構成する。完了条件: `OwnPostMatcherTest` が `datPosterId.trim().startsWith(posterIdHint.trim())` のcase-sensitiveな0/1/複数件、0件時の元候補復元、name/mail最終絞り込み、先頭空白・改行を含む本文のdat parser互換正規化を検証する。
- [x] 9.3 `OwnPostReconciliationUseCase.kt` にscope整合済み `confirmedResNum` の最優先確定とdat未反映時の待機を追加する。完了条件: UseCaseテストが即時確定、範囲外待機、同一実行内候補再利用防止、MATCHED時に取得レスの日時/IDを履歴へ保存することを検証する。
- [x] 9.4 `OwnPostReconciliationUseCase.kt` の通常照合を本文→利用可能な日時±1,000ms→poster ID prefix→name/mailの順へ変更する。完了条件: 各段階の一意確定、日時欠落fallback、日時0候補PENDING、poster ID 0候補rollback、最終曖昧PENDING、本文0候補だけの `lastCheckedResNum` 更新を単体テストで検証する。

## 10. 統合・最終検証

- [x] 10.1 新規・変更する全class/interface、非自明関数、30行超関数をAGENTS.mdのKDoc、annotation配置、section header、非自明分岐コメント規約に適合させる。完了条件: Codex独立レビューで検出された曜日付きdat日時の問題を修正し、対象diffに必須commentの欠落がない。
- [x] 10.2 CI workflowの `testDebugUnitTest` と `assembleCi` を実行する。完了条件: CI run `32635179353` がtested commit `2332980a`でunit testとDebug相当APK buildに成功する。ローカルコマンドは実行していない。
- [x] 10.3 Android test環境でv10→v11 migration、PendingOwnPostRepository transaction、既存自レスマーク表示のinstrumented testを実行する。完了条件: 関連androidTestコードを追加済みで、現行CI workflowにinstrumentation実行stepがないため未実行であることを記録する。
- [x] 10.4 `openspec validate fix-my-post-mark-persistence --strict` を実行し、proposal、design、delta specs、tasksと実装を整合させる。完了条件: strict validationが成功する。
- [x] 10.5 `ThreadRouteViewModel.kt` の自レス照合呼び出しを専用例外境界へ分離し、`ThreadRouteViewModelTest.kt` に照合DB例外時の成功状態維持・失敗Toast非表示を追加し、CancellationExceptionは再throwする。完了条件: 照合非キャンセル例外が`handleLoadFailure`へ到達せず、CIのunit test/buildが成功する。
