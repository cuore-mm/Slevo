## Context

現行の `ThreadRouteViewModel.onThreadPostSuccess` は投稿成功情報を `ThreadSessionRuntimeState.pendingPost` に置き、直後に1回だけ `reloadThread` を実行する。`recordPendingPost` は `pending.resNum ?: uiPosts.size` を投稿番号とみなし、番号が取得範囲外でも最後に `pendingPost` を消去する。このため、投稿先で受付完了とスレッド本文への反映に時間差があると、自分の投稿履歴が作られず、投稿行、返信ポップアップ、ミニマップのマークが回復しない。また、`resNum` は `x-resnum` に由来するため、現行判定は特定サービスのレスポンス仕様に依存している。

自分の投稿マーク自体は `post_histories` の `threadHistoryId` ごとの `resNum` 集合を `PostHistoryRepository.observeMyPostNumbers` で監視して描画している。この既存の表示経路は維持し、投稿成功情報の保存と投稿レスの確定方法だけを変更する。

本変更は Room schema、バックアップ互換性、投稿成功イベント、スレッドロード後処理、複数DAOの書き込みを横断する。`DatabaseWriteGate` は非再入であるため、複数Repositoryをまたぐ確定処理では外側だけがpermitを取得し、内側はungated helperを使う必要がある。

## Goals / Non-Goals

**Goals:**

- 投稿先固有のレス番号応答を必要とせず、投稿内容と後続のスレッド取得結果から自分の投稿を確定する。
- 未確定投稿をRoomへ保存し、プロセス再生成や初回取得の反映遅延を越えて再照合できるようにする。
- `providerId + boardKey + threadKey` が一致するスレッドのロード時だけ照合し、未確認のレス範囲へ処理を限定する。
- 照合確定と既存投稿履歴の保存を、単一のwrite gate permitとRoom transactionで原子的に実行する。
- UIのマーク表示と既存の `myPostNumbers` 監視契約を維持する。

**Non-Goals:**

- 投稿行、返信ポップアップ、ミニマップの見た目、文言、アクセシビリティを変更しない。
- 新規スレッド作成投稿は対象に含めない。対象は既存スレッドへのレス投稿とする。
- 投稿先サービスへ追加APIを要求せず、`x-resnum` を照合の必須情報または優先情報として使わない。
- 本変更でサービスごとの投稿プラグイン基盤全体を導入しない。将来のproviderは同じスコープ値と正規化済み投稿モデルを供給する。
- 曖昧な候補を推測で確定したり、ユーザーへ手動選択UIを追加したりしない。

## Decisions

### 1. 未確定投稿を独立したRoomテーブルへ保存する

新規 `PendingOwnPostEntity` を `pending_own_posts` として追加し、少なくとも次を保持する。

- `id: Long`（auto-generated primary key）
- `providerId: String`
- `boardKey: String`
- `threadKey: String`
- `status: String`（`PENDING`、`MATCHED`、`EXPIRED`）
- `content: String`、`name: String`、`email: String`
- `baseResCount: Int`、`lastCheckedResNum: Int`
- `submittedAt: Long`、`expiresAt: Long`
- `matchedResNum: Int?`

`PostHistoryEntity` の `date` と `postId` は取得レスから確定する値であるため、未確定値や仮値を既存テーブルへ書かない。`PendingThreadPostState` によるメモリ上の所有判定は廃止し、Roomをsource of truthとする。

`status` はDB上では文字列として保存し、Repository境界でドメインenumへ変換する。新しいglobal `TypeConverter` は追加せず、未知の文字列は不正データとして処理を失敗させる。

代替案として `PostHistoryEntity` の列をnullableにする方法は、既存の投稿履歴利用箇所全体へ未確定状態を漏らすため採用しない。投稿成功時にレス番号だけを保存する方法も、provider固有応答への依存を残すため採用しない。

### 2. スレッドスコープは安定した文字列キーで保存する

現行実装では `providerId` 型が存在しないため、`OwnPostThreadScope` 値オブジェクトを新設する。現行providerでは `providerId = parseServiceName(boardUrl)`、`boardKey = parseBoardUrl(boardUrl)` の板キー、`threadKey = ThreadTabInfo.threadKey` とする。DB内部IDである `BbsServiceEntity.id` はバックアップや再登録で変化し得るため使用しない。

`OwnPostThreadScope.from(boardUrl, threadKey)` が空のprovider、板キー、thread keyを検出した場合はpendingを作らず、詳細をログへ記録して通常のスレッド再読み込みは継続する。将来providerを追加するときは、この値オブジェクトへ安定したprovider IDとboard/thread keyを供給する。

DAOは `(providerId, boardKey, threadKey, status)` の複合indexを持ち、照合時はこの3キーと `PENDING` を完全一致させる。全pendingの走査や別スレッドの照合は禁止する。

### 3. 投稿成功を先に永続化してから再読み込みする

`ThreadRouteViewModel.onThreadPostSuccess` は、現在のタブ情報と `contentStates[tabKey].threadInfo.resCount`（なければ `ThreadTabInfo.resCount`）から `baseResCount` を取得する。`submittedAt` は注入可能な `nowMillis`、`expiresAt` は `submittedAt + 24時間`、`lastCheckedResNum` は `baseResCount` で初期化する。

`PostDialogSuccess.resNum` は互換性のため直ちに削除しなくてもよいが、pending作成と照合では参照しない。pendingのinsert完了後に `reloadThread(tabKey)` を呼ぶ。DB書き込みのcancellationは再throwし、それ以外の書き込み失敗はログへ記録して再読み込みを継続する。投稿自体は既に外部サービスで成功しているため、書き込み失敗を投稿失敗としてUIへ再通知しない。

### 4. スレッドロード成功後に新規UseCaseで対象pendingだけを照合する

`ThreadRouteViewModel.loadThreadContent` は `historyRepository.recordHistory` で `historyId` を得て `collectMyPostNumbers` を開始した後、現行 `recordPendingPost` の代わりに新規 `OwnPostReconciliationUseCase` を呼ぶ。UseCaseへ `OwnPostThreadScope`、`derived.uiPosts`、`historyId`、`boardInfo.boardId`、`nowMillis` を渡す。INITIAL、MANUAL、BOTTOM_PULL、AUTO_SCROLLのどのロード理由でも同じ成功経路を通るため、投稿直後の再読み込み、手動更新、自動更新、スレッド再表示がすべて照合契機になる。

`ThreadTabsCoordinator.refreshOpenThreads` はUI用content stateと `historyId` を持たない背景更新経路なので、本変更の照合契機に含めない。次に各タブの通常ロード成功経路を通った時点で照合する。

各pendingは `submittedAt` の昇順で処理する。`nowMillis >= expiresAt` なら `EXPIRED` にし、照合しない。候補範囲は次のinclusiveなレス番号範囲とする。

```text
start = max(baseResCount + 1, lastCheckedResNum + 1)
end   = uiPosts.size
```

- `start > end`: 状態を変更しない。
- 一致候補0件: `lastCheckedResNum = end` として、確認済みレスを次回対象から除外する。
- 一致候補1件: `MATCHED` とし、候補レス番号を `matchedResNum` に保存して既存投稿履歴へ確定保存する。
- 一致候補2件以上: 推測せず `PENDING` のままとし、`lastCheckedResNum` を進めない。期限まで同じ曖昧範囲を再検証する。

### 5. 照合は本文完全一致を必須にし、入力済みidentityを追加条件にする

新規 `OwnPostMatcher` はprovider非依存の純粋ロジックとする。投稿入力と取得済み `ThreadPostUiModel` の双方に次の正規化を適用する。

- 本文: CRLF/CRをLFへ統一し、各行末の空白と文字列末尾の改行を除去する。行の追加、空白の畳み込み、大小文字変換は行わない。
- 名前・メール: 前後空白を除去する。

本文の正規化後完全一致を必須とする。投稿時の名前またはメールが空でなければ、その項目も正規化後完全一致を必須とする。空の名前・メールは投稿先が既定値を補う可能性があるためwildcardとして扱う。取得レスの日時とIDは、投稿側に同等情報がないため一致条件に使わず、MATCHED後の `PostHistoryEntity` 作成にだけ使う。

この方式は同一本文の同時投稿を誤確定しないことを優先する。複数候補が残る場合は期限までPENDINGとし、24時間後にEXPIREDとする。

### 6. MATCHED確定と投稿履歴保存を原子的にする

`PostHistoryRepository.recordPost` のDAO書き込み本体を `internal suspend fun recordPostUngated(...)` へ抽出し、既存public `recordPost` は `DatabaseWriteGate.withWritePermit { recordPostUngated(...) }` に委譲する。

`PendingOwnPostRepository.completeMatch(...)` は、外側で1回だけ `DatabaseWriteGate.withWritePermit` を取得し、その内側で `AppDatabase.withTransaction` を開始する。transaction内で対象行がまだ `PENDING` であることを条件付きUPDATEで確認し、更新件数が1件の場合だけ `PostHistoryRepository.recordPostUngated(...)` を呼ぶ。更新件数が0件なら既に他の処理が解決済みとして投稿履歴を追加しない。投稿履歴insertまたはidentity履歴更新が失敗した場合はtransaction全体をrollbackし、pendingをPENDINGへ戻す。

照合済みpendingのレス番号と同じ候補は、同じUseCase実行内の後続pending候補から除外する。`PostHistoryDao` の既存schemaへ新しいunique制約は追加せず、条件付き状態更新、単一write gate、transactionで同一pendingの重複記録を防ぐ。

### 7. terminal状態を低頻度で削除する

`MATCHED` と `EXPIRED` は通常照合クエリから除外する。pending作成または対象スレッド照合時に、`submittedAt` が現在時刻から30日より古いterminal行を削除する。これはテーブルの無制限増加を防ぐ保守処理であり、PENDING行は期限判定より先に削除しない。

### 8. Room v10とバックアップ検証を同時更新する

`AppDatabase` をv10へ上げ、`MIGRATION_9_10` で `pending_own_posts` と複合indexを作成する。既存行からのbackfillは不要である。`ALL_REGISTERED_MIGRATIONS`、`DatabaseModule` のDAO provider、exported schema `10.json` を更新する。

`BackupDatabaseValidator` のcurrent identity hash、必須table、`EXPECTED_TABLES_BY_VERSION[10]` をexported schema v10と一致させる。v2-v9のexpected table setは変更しないため、古いバックアップは既存migration pathでv10へ移行できる。

## Data Flow

```text
PostDialogSuccess
  -> ThreadRouteViewModel.onThreadPostSuccess
  -> PendingOwnPostRepository.create(PENDING, scope, normalized input, range)
  -> reloadThread
  -> ThreadRouteViewModel.loadThreadContent
  -> recordHistory / collectMyPostNumbers
  -> OwnPostReconciliationUseCase(scope, uiPosts, historyId, boardId)
       -> scope限定PENDING query
       -> OwnPostMatcher
       -> completeMatch: gate -> Room transaction
            -> pending MATCHED
            -> PostHistoryRepository.recordPostUngated
  -> Room Flow invalidation
  -> existing myPostNumbers
  -> existing row / popup / minimap marks
```

## Implementation Contract

1. `PendingOwnPostEntity`、DAO、Repository、`OwnPostThreadScope`、`OwnPostMatcher`、`OwnPostReconciliationUseCase` を責務別ファイルに分け、全class/interfaceと非自明関数へリポジトリ規約どおりKDocを付ける。
2. `ThreadRouteViewModel` から `PendingThreadPostState`、`recordPendingPost`、`resNum ?: uiPosts.size` による自レス判定を除去する。`x-resnum` は新照合経路へ渡さない。
3. 投稿成功時はpending insertの完了後にreloadし、ロード成功時は `recordHistory` と `collectMyPostNumbers` の後に照合UseCaseを呼ぶ。
4. pending検索は必ず `providerId + boardKey + threadKey + PENDING` を条件にし、全件取得APIをRepositoryへ公開しない。
5. `PostHistoryRepository.recordPostUngated` は `internal` とし、自身でgateを取得しない。`completeMatch` は `gate -> db.withTransaction -> conditional pending update -> recordPostUngated` の順序を守る。
6. 時刻はテストから固定値を渡せる引数または注入関数にし、24時間expiryと30日terminal retentionを定数化する。
7. `MIGRATION_9_10`、schema JSON、backup validatorのidentity hash/table setを同一変更で更新する。
8. 新しい画面要素、Snackbar、Toast、content description、文字列resourceは追加しない。

## Error Cases and Compatibility

- scope生成失敗: pendingを作らずログへ記録し、スレッドreloadは継続する。
- pending insert失敗: cancellationは再throwし、それ以外はログへ記録してreloadを継続する。
- dat取得失敗: pendingはPENDINGのまま維持し、次回成功ロードで再照合する。
- match確定transaction失敗: transactionをrollbackしてPENDINGを維持し、ロード失敗の既存処理へ例外を伝える。
- プロセス終了: RoomのPENDINGを次回の対象スレッドロードで再利用する。
- v9 DB: `MIGRATION_9_10` で空のpending tableを追加し、既存投稿履歴を維持する。
- v2-v9バックアップ: current v10までmigration pathがある場合は従来どおり復元候補にできる。

## Testing Strategy

- `OwnPostMatcher` 単体テスト: 改行・行末空白の正規化、本文一致/不一致、空identityのwildcard、非空identity一致、複数候補。
- `OwnPostReconciliationUseCase` 単体テスト: scope限定、範囲限定、レス増加なし、0/1/複数候補、24時間expiry、`lastCheckedResNum` 更新、同一実行内の候補再利用防止。
- Repository/DAOテスト: PENDING作成、条件付きMATCHED、EXPIRED、terminal cleanup、別scope非取得、transaction失敗時rollback、二重確定防止。
- `ThreadRouteViewModelTest`: 投稿成功時にbase count付きpendingを保存してからreloadすること、各ロード理由の共通成功経路で照合すること、別スレッドpendingを渡さないこと。
- migration instrumentation test: v9からv10へ移行し、新table・列・indexと既存データ保持を検証する。
- backup validatorテスト: v10 identity hash/table set、v2-v9 historical set不変、v9 backupからv10 migration pathを検証する。
- 既存UIテストまたはCompose test: MATCHED後の `myPostNumbers` により投稿行、返信ポップアップ、ミニマップの既存マークが維持されることを回帰確認する。

実装完了時はリポジトリ規約に従い、少なくとも `./gradlew assembleDebug` と `./gradlew testDebugUnitTest` を実行し、migration instrumentation testは実行可能なAndroid test環境で検証する。

## Migration Plan

1. 新Entity/DAOと `MIGRATION_9_10` を追加し、schema v10を生成する。
2. Repository、matcher、reconciliation UseCaseを追加し、単体/DAO/migrationテストを先に成立させる。
3. `PostHistoryRepository` のungated helperを抽出し、transactionalなMATCHED確定へ接続する。
4. `ThreadRouteViewModel` をRoom pending作成・照合へ切り替え、メモリpending経路を削除する。
5. backup validatorと関連spec固定値をv10へ更新し、全build/testを実行する。

rollbackでアプリコードをv9へ戻すとv10 DBを開けないため、リリース後の単純downgradeはサポートしない。問題が生じた場合はv10 schemaを維持した修正版を配布する。

## Open Questions

なし。expiryは24時間、terminal retentionは30日、曖昧候補は推測せず期限切れとする方針で実装する。
