## Context

現行の `ThreadRouteViewModel.onThreadPostSuccess` は投稿成功情報を `ThreadSessionRuntimeState.pendingPost` に置き、直後に1回だけ `reloadThread` を実行する。`recordPendingPost` は `pending.resNum ?: uiPosts.size` を投稿番号とみなし、番号が取得範囲外でも最後に `pendingPost` を消去する。このため、投稿先で受付完了とスレッド本文への反映に時間差があると、自分の投稿履歴が作られず、投稿行、返信ポップアップ、ミニマップのマークが回復しない。また、`resNum` は `x-resnum` に由来するため、現行判定は特定サービスのレスポンス仕様に依存している。

自分の投稿マーク自体は `post_histories` の `threadHistoryId` ごとの `resNum` 集合を `PostHistoryRepository.observeMyPostNumbers` で監視して描画している。この既存の表示経路は維持し、投稿成功情報の保存と投稿レスの確定方法だけを変更する。

本変更は Room schema、バックアップ互換性、投稿成功イベント、スレッドロード後処理、複数DAOの書き込みを横断する。`DatabaseWriteGate` は非再入であるため、複数Repositoryをまたぐ確定処理では外側だけがpermitを取得し、内側はungated helperを使う必要がある。

## Goals / Non-Goals

**Goals:**

- 投稿成功応答の証拠を利用できる場合は誤確定を減らし、利用できない場合も投稿内容と後続のスレッド取得結果から自分の投稿を確定する。
- 未確定投稿をRoomへ保存し、プロセス再生成や初回取得の反映遅延を越えて再照合できるようにする。
- `providerId + boardKey + threadKey` が一致するスレッドのロード時だけ照合し、未確認のレス範囲へ処理を限定する。
- 照合確定と既存投稿履歴の保存を、単一のwrite gate permitとRoom transactionで原子的に実行する。
- UIのマーク表示と既存の `myPostNumbers` 監視契約を維持する。

**Non-Goals:**

- 投稿行、返信ポップアップ、ミニマップの見た目、文言、アクセシビリティを変更しない。
- 新規スレッド作成投稿は対象に含めない。対象は既存スレッドへのレス投稿とする。
- 投稿先サービスへ追加APIを要求せず、投稿成功ヘッダーの存在を確定の必須条件にしない。
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
- `confirmedResNum: Int?`、`serverPostDateMillis: Long?`、`posterIdHint: String?`

`PostHistoryEntity` の `date` と `postId` は取得レスから確定する値であるため、未確定値や仮値を既存テーブルへ書かない。`PendingThreadPostState` によるメモリ上の所有判定は廃止し、Roomをsource of truthとする。

`status` はDB上では文字列として保存し、Repository境界でドメインenumへ変換する。新しいglobal `TypeConverter` は追加せず、未知の文字列は不正データとして処理を失敗させる。

`confirmedResNum` は投稿成功応答が示した、dat未反映でも保持するレス番号であり、`matchedResNum` は取得済みdatレスを確認して投稿履歴へ保存したレス番号である。この2つを混同しない。代替案として `PostHistoryEntity` の列をnullableにする方法は、既存の投稿履歴利用箇所全体へ未確定状態を漏らすため採用しない。

### 2. スレッドスコープは安定した文字列キーで保存する

現行実装では `providerId` 型が存在しないため、`OwnPostThreadScope` 値オブジェクトを新設する。現行providerでは `providerId = parseServiceName(boardUrl)`、`boardKey = parseBoardUrl(boardUrl)` の板キー、`threadKey = ThreadTabInfo.threadKey` とする。DB内部IDである `BbsServiceEntity.id` はバックアップや再登録で変化し得るため使用しない。

`OwnPostThreadScope.from(boardUrl, threadKey)` が空のprovider、板キー、thread keyを検出した場合はpendingを作らず、詳細をログへ記録して通常のスレッド再読み込みは継続する。将来providerを追加するときは、この値オブジェクトへ安定したprovider IDとboard/thread keyを供給する。

DAOは `(providerId, boardKey, threadKey, status)` の複合indexを持ち、照合時はこの3キーと `PENDING` を完全一致させる。全pendingの走査や別スレッドの照合は禁止する。

### 3. 投稿成功を先に永続化してから再読み込みする

投稿送信を開始する直前に、`ThreadScaffold` が現在表示している投稿数を `baseResCount` としてcaptureする。確認画面を挟む投稿では、実際にネットワーク書き込みを開始する `postSecondPhase` の直前にcaptureし、captureした値を `PostDialogController` から `PostDialogSuccess.baseResCount` へ運ぶ。これにより、投稿成功callbackより先に自動更新・手動更新が完了して投稿レスを含んだとしても、投稿前の境界が変化しない。新規スレッド作成などスレッド返信でない呼び出し元は `null` を渡す。

`ThreadRouteViewModel.onThreadPostSuccess` は、成功イベントの `baseResCount` を優先し、互換性のため値がない場合だけ現在のタブ情報と `contentStates[tabKey].posts?.size`（なければ `ThreadTabInfo.resCount`）へfallbackする。`submittedAt` は注入可能な `nowMillis`、`expiresAt` は `submittedAt + 24時間`、`lastCheckedResNum` は確定した `baseResCount` で初期化する。

`PostResult.Success` と `PostDialogSuccess` は単独の `resNum` ではなく `PostReceipt` を運ぶ。pendingのinsert完了後に `reloadThread(tabKey)` を呼ぶ。DB書き込みのcancellationは再throwし、それ以外の書き込み失敗はログへ記録して再読み込みを継続する。投稿自体は既に外部サービスで成功しているため、書き込み失敗を投稿失敗としてUIへ再通知しない。

### 4. スレッドロード成功後に新規UseCaseで対象pendingだけを照合する

`ThreadRouteViewModel.loadThreadContent` は `historyRepository.recordHistory` で `historyId` を得て `collectMyPostNumbers` を開始した後、現行 `recordPendingPost` の代わりに新規 `OwnPostReconciliationUseCase` を呼ぶ。UseCaseへ `OwnPostThreadScope`、`derived.uiPosts`、`historyId`、`boardInfo.boardId`、`nowMillis` を渡す。INITIAL、MANUAL、BOTTOM_PULL、AUTO_SCROLLのどのロード理由でも同じ成功経路を通るため、投稿直後の再読み込み、手動更新、自動更新、スレッド再表示がすべて照合契機になる。

`ThreadTabsCoordinator.refreshOpenThreads` はUI用content stateと `historyId` を持たない背景更新経路なので、本変更の照合契機に含めない。次に各タブの通常ロード成功経路を通った時点で照合する。

各pendingは `submittedAt` の昇順で処理する。`nowMillis >= expiresAt` なら `EXPIRED` にし、照合しない。候補範囲は次のinclusiveなレス番号範囲とする。

```text
start = max(baseResCount + 1, lastCheckedResNum + 1)
end   = uiPosts.size
```

- `start > end`: 状態を変更しない。
- `confirmedResNum` がある: 詳細はDecision 6の最優先経路に従い、dat未反映なら確認位置を進めない。
- 本文一致候補0件: `lastCheckedResNum = end` として、確認済みレスを次回対象から除外する。
- 本文一致候補1件以上: Decision 6の日時、投稿者ID、identityの順で絞り、一意なら `MATCHED` として `matchedResNum` と既存投稿履歴を保存する。証拠矛盾または複数候補では確認位置を進めない。

### 5. `PostReceipt` を境界モデルとして5ch互換ヘッダーを安全に解釈する

`data/model/PostReceipt.kt` に `confirmedResNum: Int?`、`serverPostDateMillis: Long?`、`posterIdHint: String?` を持つ不変モデルを追加する。`PostRepository.handlePostResponse` はresponseをcloseする前に、ヘッダー名の大文字小文字を区別せず `X-Resnum`、`X-Postplace`、`X-Postdate`、`X-Posterid` を5ch互換parserへ渡す。当面はproviderで分岐せず全providerで同じparserを試行し、欠落・不正値はnullとして通常照合へfallbackする。

`X-Resnum` は正のIntだけを採用する。`X-Postplace` がある場合はリクエストのboard/thread scopeと整合するときだけ `confirmedResNum` を採用し、欠落時は互換性のため `X-Resnum` を採用する。不整合時はレス番号を破棄する。`X-Postdate` はDoubleを介さず `BigDecimal` のUNIX秒として厳密にmillisへ変換し、範囲外・非数値・負値はnullとする。`X-Posterid` はtrim後の非空値だけを採用する。`X-Regioninfo` は個人情報であるため、保存もログ出力もしない。

parser interfaceをprovider境界として定義し、5ch実装をdefaultで注入する。provider別の選択機構全体は導入せず、将来providerが異なる仕様を持つ場合に実装差し替え可能な最小境界だけを設ける。

### 6. 照合は強い証拠から非破壊的なtie-breakerへ段階化する

新規または再構成した `OwnPostMatcher` はprovider非依存の純粋ロジックとする。投稿入力と取得済み `ThreadPostUiModel` の双方に次の正規化を適用する。

- 本文: CRLF/CRをLFへ統一し、各行末の空白と文字列の先頭・末尾の空白を除去する。これは取得側 `DatParser.cleanContent` の `trim()` と同じ境界を採用するためである。行の追加、空白の畳み込み、大小文字変換は行わない。
- 名前・メール: 前後空白を除去する。

照合順序は次で固定する。

1. `confirmedResNum` が現在の取得範囲にあれば、本文や他のhintを確認せず即時確定する。まだ取得されていなければPENDINGを維持する。
2. `confirmedResNum` がない場合、本文完全一致候補を作る。`serverPostDateMillis` があればdat日時をAsia/Tokyoでepoch millisへ変換し、差の絶対値が1,000ms以下の候補だけを採用する。日時変換失敗または0候補は誤確定を避けてPENDINGとする。時刻hintがなければ本文候補をそのまま使う。
3. 候補が1件なら確定する。複数かつ `posterIdHint` が非空なら、`datPosterId.trim().startsWith(posterIdHint.trim())` のcase-sensitive条件で絞る。0件なら投稿者ID適用前の候補へ戻し、1件なら確定し、複数なら次へ進む。
4. 残った複数候補を投稿時に入力済みの非空name/mailのtrim後完全一致で絞る。空項目はwildcardとする。1件なら確定し、0件または複数ならPENDINGを維持する。

dat日時には曜日表記を許容し、小数秒1〜9桁を十進の秒小数として保持する専用parserを使う。既存 `parseDateToUnix` の「小数秒除去・失敗時現在時刻fallback」は照合に使わない。1,000ms許容は、datが小数秒を表示しない板でも同一秒の `X-Postdate` を比較できる境界とする。

本文候補が0件の場合だけ `lastCheckedResNum = end` へ進める。本文候補が存在するが日時またはidentity証拠と矛盾した場合は、証拠の形式差や後続修正を考慮して確認位置を進めずPENDINGを維持する。この方式は同一本文の同時投稿を誤確定しないことを優先し、24時間後にEXPIREDとする。

### 7. MATCHED確定と投稿履歴保存を原子的にする

`PostHistoryRepository.recordPost` のDAO書き込み本体を `internal suspend fun recordPostUngated(...)` へ抽出し、既存public `recordPost` は `DatabaseWriteGate.withWritePermit { recordPostUngated(...) }` に委譲する。

`PendingOwnPostRepository.completeMatch(...)` は、外側で1回だけ `DatabaseWriteGate.withWritePermit` を取得し、その内側で `AppDatabase.withTransaction` を開始する。transaction内で対象行がまだ `PENDING` であることを条件付きUPDATEで確認し、更新件数が1件の場合だけ `PostHistoryRepository.recordPostUngated(...)` を呼ぶ。更新件数が0件なら既に他の処理が解決済みとして投稿履歴を追加しない。投稿履歴insertまたはidentity履歴更新が失敗した場合はtransaction全体をrollbackし、pendingをPENDINGへ戻す。

照合済みpendingのレス番号と同じ候補は、同じUseCase実行内の後続pending候補から除外する。`PostHistoryDao` の既存schemaへ新しいunique制約は追加せず、条件付き状態更新、単一write gate、transactionで同一pendingの重複記録を防ぐ。

### 8. terminal状態を低頻度で削除する

`MATCHED` と `EXPIRED` は通常照合クエリから除外する。pending作成または対象スレッド照合時に、`submittedAt` が現在時刻から30日より古いterminal行を削除する。これはテーブルの無制限増加を防ぐ保守処理であり、PENDING行は期限判定より先に削除しない。

### 9. Room v11とバックアップ検証を同時更新する

既存の `MIGRATION_9_10` は維持し、`AppDatabase` をv11へ上げる。`MIGRATION_10_11` で `pending_own_posts` にnullableの `confirmedResNum`、`serverPostDateMillis`、`posterIdHint` を追加する。既存pendingはnull証拠として従来の本文照合へfallbackする。`ALL_REGISTERED_MIGRATIONS` とexported schema `11.json` を更新する。

`BackupDatabaseValidator` のcurrent identity hash、必須table、`EXPECTED_TABLES_BY_VERSION[11]` をexported schema v11と一致させる。証拠列はRoom identity hashによるcurrent schema整合性で検証する。v2-v10のhistorical table setは変更せず、古いバックアップは既存migration pathでv11へ移行できる。

## Risks / Trade-offs

- [未公開ヘッダーの欠落・形式変更] → 全fieldをnullableにし、解析不能時は投稿成功を失敗へ変えず本文照合へfallbackする。
- [全providerで5ch互換parserを試行すると同名ヘッダーを誤解釈する可能性] → 正数・UNIX時刻・scopeを厳格に検証し、parser interfaceで将来のprovider別実装へ分離可能にする。
- [`X-Postplace` 欠落時の `X-Resnum` が誤っている可能性] → 確定後も取得済みdatの実在レスだけを履歴へ保存し、番号が未取得なら推測fallbackせずPENDINGを維持する。
- [同一本文が1秒以内に複数投稿される] → 投稿者IDとidentityで追加絞り込みし、最後まで複数なら確定しない。
- [Room v11への更新後はv10アプリへdowngradeできない] → nullable列だけを追加し、問題発生時もv11 schemaを維持した修正版を配布する。

## Data Flow

```text
HTTP response headers
  -> FiveChPostReceiptParser
  -> PostResult.Success(PostReceipt)
  -> PostDialogSuccess
  -> ThreadRouteViewModel.onThreadPostSuccess
  -> PendingOwnPostRepository.create(PENDING, scope, normalized input, range)
  -> reloadThread
  -> ThreadRouteViewModel.loadThreadContent
  -> recordHistory / collectMyPostNumbers
  -> OwnPostReconciliationUseCase(scope, uiPosts, historyId, boardId)
       -> scope限定PENDING query
       -> confirmedResNum または階層的OwnPostMatcher
       -> completeMatch: gate -> Room transaction
            -> pending MATCHED
            -> PostHistoryRepository.recordPostUngated
  -> Room Flow invalidation
  -> existing myPostNumbers
  -> existing row / popup / minimap marks
```

## Implementation Contract

1. `PendingOwnPostEntity`、DAO、Repository、`OwnPostThreadScope`、`OwnPostMatcher`、`OwnPostReconciliationUseCase` を責務別ファイルに分け、全class/interfaceと非自明関数へリポジトリ規約どおりKDocを付ける。
2. `PostRepository.handlePostResponse` でresponse close前に `PostReceipt` を作り、`PostResult.Success`、`PostDialogController`、`PostDialogSuccess`、`ThreadRouteViewModel`、`PendingOwnPostRepository.createPending` の順に全証拠を欠落なく運ぶ。
3. 投稿成功時はpending insertの完了後にreloadし、ロード成功時は `recordHistory` と `collectMyPostNumbers` の後に照合UseCaseを呼ぶ。
4. pending検索は必ず `providerId + boardKey + threadKey + PENDING` を条件にし、全件取得APIをRepositoryへ公開しない。
5. `PostHistoryRepository.recordPostUngated` は `internal` とし、自身でgateを取得しない。`completeMatch` は `gate -> db.withTransaction -> conditional pending update -> recordPostUngated` の順序を守る。
6. 時刻はテストから固定値を渡せる引数または注入関数にし、24時間expiryと30日terminal retentionを定数化する。
7. `MIGRATION_10_11`、schema `11.json`、backup validatorのidentity hash/table setを同一変更で更新し、既存 `MIGRATION_9_10` を連続pathに残す。
8. 新しい画面要素、Snackbar、Toast、content description、文字列resourceは追加しない。
9. `X-Regioninfo` と未使用のresponse header/bodyを永続化またはログ出力しない。

## Error Cases and Compatibility

- scope生成失敗: pendingを作らずログへ記録し、スレッドreloadは継続する。
- pending insert失敗: cancellationは再throwし、それ以外はログへ記録してreloadを継続する。
- dat取得失敗: pendingはPENDINGのまま維持し、次回成功ロードで再照合する。
- ヘッダー欠落・不正: 対応証拠をnullにし、本文を起点とする既存の汎用照合を継続する。
- `X-Postplace` 不整合: `confirmedResNum` だけを採用せず、日時・投稿者IDおよび本文照合へfallbackする。
- 確定レス番号がdat未反映: PENDINGを維持し、範囲終端や別レスへ置換せず次回取得を待つ。
- 妥当な投稿時刻と本文の一致が0件: 確認位置を進めずPENDINGを維持する。
- 投稿開始後に更新が先行: 成功イベントに運んだ投稿開始時の `baseResCount` を使い、更新後のレス数で自分の投稿を候補範囲から除外しない。
- match確定transaction失敗: transactionをrollbackしてPENDINGを維持し、照合処理の非キャンセル例外はログへ記録する。dat取得とUI反映は既に成功しているため、ロード失敗Toastへ変換せず、次回ロードで再照合する。
- 照合DB処理失敗: `OwnPostReconciliationUseCase` の非キャンセル例外をスレッドロード処理から分離し、取得済み投稿一覧、履歴、ロード成功状態を維持する。キャンセルは再throwする。
- プロセス終了: RoomのPENDINGを次回の対象スレッドロードで再利用する。
- v10 DB: `MIGRATION_10_11` でnullable証拠列を追加し、既存pendingと投稿履歴を維持する。
- v2-v10バックアップ: current v11までmigration pathがある場合は従来どおり復元候補にできる。

## Testing Strategy

- `PostReceipt` parser単体テスト: header名case-insensitive、正/0/負/overflow `X-Resnum`、`X-Postplace` の一致/不一致/欠落、`X-Postdate` の秒・小数・不正・overflow、trim済み/空 `X-Posterid`、`X-Regioninfo` 非保持。
- 日時parser単体テスト: JST固定、曜日あり/なし、小数秒0/1/2/3/9桁、±1,000ms境界、解釈不能時null。
- `OwnPostMatcher` 単体テスト: 本文正規化、日時+本文、poster ID startsWithの0/1/複数と0件rollback、空identity wildcard、非空identity最終絞り込み。
- `OwnPostReconciliationUseCase` 単体テスト: confirmed resNum即時確定/未反映待機、scope限定、範囲限定、各証拠の欠落fallback、0/1/複数候補、矛盾時PENDING、expiry、候補再利用防止。
- Repository/DAOテスト: PENDING作成、条件付きMATCHED、EXPIRED、terminal cleanup、別scope非取得、transaction失敗時rollback、二重確定防止。
- `ThreadRouteViewModelTest`: 投稿成功時にbase count付きpendingを保存してからreloadすること、各ロード理由の共通成功経路で照合すること、別スレッドpendingを渡さないこと。
- `ThreadRouteViewModelTest`: dat取得成功後の照合DB例外が投稿一覧・履歴・ロード成功状態を維持し、失敗Toastを設定しないこと、キャンセル例外は再throwすることを検証する。
- migration unit/instrumentation test: v10からv11へ移行し、nullable証拠列と既存pending/投稿履歴保持を検証する。v9→v10→v11連続pathも確認する。
- backup validatorテスト: v11 identity hash/table set、v2-v10 historical set不変、v10 backupからv11 migration pathを検証する。
- 既存UIテストまたはCompose test: MATCHED後の `myPostNumbers` により投稿行、返信ポップアップ、ミニマップの既存マークが維持されることを回帰確認する。

実装完了時はリポジトリ規約に従い、少なくとも `./gradlew assembleDebug` と `./gradlew testDebugUnitTest` を実行し、migration instrumentation testは実行可能なAndroid test環境で検証する。

## Migration Plan

1. `PostReceipt` と5ch互換parser、日時parserを純粋ロジックとして追加し、単体テストを成立させる。
2. `MIGRATION_10_11` とnullable証拠列を追加し、schema v11、DAO/migrationテストを成立させる。
3. 投稿成功responseからpendingまで `PostReceipt` を接続し、保存→reload順序を維持する。
4. `OwnPostReconciliationUseCase` を確定レス番号と階層的tie-breakerへ更新する。
5. backup validatorと関連spec固定値をv11へ更新し、全build/testを実行する。

rollbackでアプリコードをv10へ戻すとv11 DBを開けないため、リリース後の単純downgradeはサポートしない。問題が生じた場合はv11 schemaを維持した修正版を配布する。

## Open Questions

なし。日時はAsia/Tokyoで解釈して差1,000ms以下を一致とし、expiryは24時間、terminal retentionは30日、最後まで曖昧な候補は推測せず期限切れとする方針で実装する。
