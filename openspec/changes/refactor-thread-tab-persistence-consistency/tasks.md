## 1. 決定的テスト基盤と回帰テスト

- [x] 1.1 `app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/ThreadTabsCoordinatorTest.kt` と同 package の test fixture に、`StandardTestDispatcher`、制御可能な tab Flow、repository completion 用 `CompletableDeferred` を追加する。初回 emission、古い emission、新しい emission、write completion をテストから個別に進められることを fixture test で確認する。
- [x] 1.2 `ThreadTabsCoordinatorTest.kt` に「DB は 1,252 件、初回 Flow は blocked、add intent は初回 snapshot 前に repository write を開始しない」失敗テストを追加し、件数と全 `threadId` が保持される assertion を入れる。
- [x] 1.3 `ThreadTabsCoordinatorTest.kt` に「1,252 件初回 snapshot → add pending → 古い 1,252 件 emission → 新しい 1,253 件 emission」を追加し、全段階で削除、重複、表示巻き戻りがなく、completion は 1,253 件 canonical confirmation 後だけ完了する assertion を入れる。
- [x] 1.4 `ThreadTabsCoordinatorTest.kt` に loaded-empty と rapid add/delete/pin のテストを追加する。intent の repository call/completion が FIFO で、削除済みタブの resurrection、追加済みタブの消失、pin 巻き戻りがなく、最終 ID 集合と値が期待どおりであることを確認する。
- [x] 1.5 `ThreadTabsCoordinatorTest.kt` に repository failure と cancellation のテストを追加し、pending projection が除去され、既存 canonical state が維持され、次の intent が処理されることを確認する。

## 2. DAO と repository の対象行 mutation

- [x] 2.1 `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/dao/OpenThreadTabDao.kt` に、対象 `threadId` の取得/追加、削除、pin 更新、最大 `sortOrder` 取得に必要な single-row query を追加する。既存 entity/schema column だけを使用し、DB version と migration を変更しないことを `AppDatabaseMigrationTest` の既存前提で確認する。
- [x] 2.2 `app/src/main/java/com/websarva/wings/android/slevo/data/repository/TabsRepository.kt` に ensure/add mutation を実装する。`DatabaseWriteGate.withWritePermit` と一つの `db.withTransaction` で未登録時だけ `MAX(sortOrder) + 1` の行と必要な ThreadState を保存し、既存行の sort/pin/scroll を維持して結果を返すことを確認する。
- [x] 2.3 `TabsRepository.kt` に対象行 delete と pin mutation を実装する。delete は他行を触らず、pin は対象列だけを更新して確定値/no-op を返し、どちらも既存 gate と transaction を一度だけ通ることを確認する。
- [x] 2.4 `app/src/androidTest/java/com/websarva/wings/android/slevo/data/repository/TabsRepositoryThreadStateTest.kt` に 1,252 件を保存した Room in-memory DB の targeted add/delete/pin test を追加し、対象外 1,252/1,251 件の ID、sort、pin、scroll と ThreadState が不変で `deleteNotIn` 相当の消失がないことを確認する。
- [x] 2.5 `TabsRepository.kt` の全件 API を通常操作向け `saveOpenThreadTabs` から bulk 専用の名前/可視性へ整理し、初回 load 後かつ exclusive orchestration からだけ使う契約を KDoc と API 境界に記述する。production 検索で通常 add/delete/pin/info/scroll call site が full replacement を参照しないことを確認する。

## 3. coordinator の canonical state と intent queue

- [x] 3.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/ThreadTabsCoordinator.kt`（必要なら同 package の新規 model file）に `Loading` と `Loaded(tabs)` を区別する単一 state を導入する。既存 `openThreadTabs` / `threadLoaded` の互換値を残す場合はこの state からのみ導出し、初回 empty emission が loaded-empty になる unit test を通す。
- [x] 3.2 `ThreadTabsCoordinator.bind` を、Room collector だけが canonical snapshot を更新する構造へ変更する。collector が optimistic mutation の値を canonical に代入せず、各 emission 後に pending projection を再計算することを code review と 1,252/1,253 test で確認する。
- [x] 3.3 `ThreadTabsCoordinator.kt` に add/delete/pin/info intent、FIFO `Channel` worker、caller completion を実装する。worker が初回 `Loaded` を待って一件ずつ repository mutation を await し、受付順を rapid mutation test で確認する。
- [x] 3.4 canonical tabs と pending operations から表示 tabs を作る pure projection と operation 固有 confirmation predicate を実装する。threadId 一意性、順序、既存 pin/scroll 保持、stale add/delete/pin emission を pure/unit test で確認する。
- [x] 3.5 worker の成功経路を「pending 登録 → targeted DB write → matching Room snapshot 待機 → pending 除去 → completion」に統一し、DB success 直後かつ Flow confirmation 前には completion が未完了である assertion を通す。
- [x] 3.6 worker の failure/cancellation/teardown 経路で pending operation と `CompletableDeferred` を必ず完了/破棄し、後続 intent を停止させない cleanup を実装する。Task 1.5 の全 assertion を通す。

## 4. production call site の completion API 移行

- [x] 4.1 `ThreadTabsCoordinator.ensureThreadTab`、`closeThreadTab`、`togglePinThreadTab` を suspend completion API へ移行し、`scope.launch { saveOpenThreadTabs(...) }` とメモリ先行 full-save helper を削除する。production 検索で coordinator 内に fire-and-forget tab-list save が残っていないことを確認する。
- [x] 4.2 `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadTabCoordinator.kt` の `updateThreadTabInfo` を対象 ThreadState update へ変更し、`observeOpenThreadTabs().first()` と一覧 full save を削除する。`ThreadTabCoordinatorTest.kt` で他タブ不変と full replacement 未呼出しを確認する。
- [x] 4.3 `ThreadTabsCoordinator.updateThreadResolvedBoardInfo` を対象 ThreadState completion API へ移行し、tab-list full save を削除する。既存 field preservation test を新しい Flow confirmation 契約へ更新して通す。
- [x] 4.4 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/store/TabSessionStore.kt` に thread readiness await と canonical confirmation まで待つ register/ensure result を公開し、coordinator completion を捨てない。`TabSessionStoreTest.kt` で success、failure、blocked readiness の delegation を確認する。
- [x] 4.5 `TabSessionStore`、tab list ViewModel、thread screen などの compile error で列挙された全 mutation call site を coroutine owner が明示された suspend 呼出しへ変更する。失敗時に現在の canonical state を維持し、未承認の UI/text を追加していないことを差分確認する。

## 5. 互換性、回帰、完了確認

- [x] 5.1 repository 全体を検索し、通常 thread add/delete/pin/info/scroll 経路から `deleteNotIn`、`upsertAll`、bulk replacement が到達不能で、`PendingRestoreApplier` の cold-start DB swap と `DatabaseBackupExporter` の gate 契約が未変更であることを確認する。
- [ ] 5.2 `ThreadTabsCoordinatorTest.kt`、`ThreadTabCoordinatorTest.kt`、`TabSessionStoreTest.kt`、`TabsRepositoryThreadStateTest.kt` を実行し、1,252/1,253、blocked Flow、loaded-empty、rapid operations、failure/cancellation の全テストが安定して通ることを確認する。
- [ ] 5.3 `./gradlew testDebugUnitTest` を実行して unit test 全件成功を確認し、接続済み emulator/device で `TabsRepositoryThreadStateTest` を実行して Room transaction/Flow の回帰がないことを確認する。
- [x] 5.4 CI の APK build で build 成功を確認し、production diff に DB schema/version 変更、board-tab behavior 変更、UI 文言/アイコン/theme/accessibility 変更がないことを確認する。
- [x] 5.5 `fix-thread-deep-link-selection-consistency` を開始する前に、本変更の readiness、targeted mutation completion、canonical confirmation API が production と tests で利用可能であることを確認する。
