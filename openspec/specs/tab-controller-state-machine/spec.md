# tab-controller-state-machine Specification

## Purpose
TBD - created by archiving change refactor-tab-controller-state-machine. Update Purpose after archive.
## Requirements
### Requirement: Domain Controller と最小 authoritative state
システムは Board と Thread に別々の domain Controller を持ち、各 Controller が load phase、Room canonical tabs、ordered pending commands、selected key、atomic presentation、command completion の責務を所有することを SHALL 要求する。各 domain は実際の command／UI 経路で参照される最小 authoritative source を使用し、未参照の immutable mirror、terminal result 履歴、汎用 reducer transition を安全性のためだけに保持してはならない。共有実装は pure projection／selection primitive と command result contract に限定し、両 domain を一つの generic Controller に統合してはならない。

#### Scenario: 初回 canonical 未受信
- **WHEN** Controller が起動し Room の初回 snapshot をまだ受信していない
- **THEN** load state は `Loading` であり loaded-empty と区別され、DB mutation を開始しない

#### Scenario: 読込済み空一覧
- **WHEN** Room が初回に空の canonical snapshot を emit する
- **THEN** load state は `Loaded` かつ presentation は `Empty` となり、後続 command を受理できる

#### Scenario: presentation の原子性
- **WHEN** canonical、pending、selection のいずれかが更新される
- **THEN** tabs と selection resolution は一つの `TabPresentationState` として公開され、未参照 state mirror の有無に依存しない

#### Scenario: terminal result の解放
- **WHEN** command が Success、NoOp、Failure のいずれかで終端する
- **THEN** 対応する waiter を一度だけ完了して pending entry を解放し、Controller lifetime 全体の result history map へ蓄積しない

### Requirement: DB canonical state と pending projection
システムは Room snapshot だけを canonical tabs とし、accepted または committed command を acceptance order で canonical tabs へ投影した effective state を表示と後続 command 導出に使用することを SHALL 要求する。Thread command の canonical confirmation は baseline より新しい snapshot と operation の最小条件を使用し、Ensure/Info は対象 identity の存在、Delete は不在、Pin は要求値との一致で判定しなければならない。

#### Scenario: stale snapshot 中の committed ensure
- **WHEN** 新規 tab の ensure write が成功した後に対象を含まない stale canonical snapshot を受信する
- **THEN** ensure pending を残して対象を effective tabs に投影し、対象 identity を含む snapshot まで accepted add を消さない

#### Scenario: committed delete
- **WHEN** delete write が成功した後に対象をまだ含む canonical snapshot を受信する
- **THEN** delete pending を残し、対象 identity を含まない snapshot まで accepted delete を canonical list から再表示しない

#### Scenario: metadata operation 後の unrelated snapshot
- **WHEN** Thread Ensure/Info write 後の新しい Room snapshot が対象 identity を含むが要求 metadata をまだ含まない
- **THEN** Controller は identity existence で command を完了でき、古い canonical metadata の一時表示を許容する

#### Scenario: metadata の最終 canonical 反映
- **WHEN** Repository が merge 済み metadata を commit し、その値を含む Room snapshot が到着する
- **THEN** canonical tabs と presentation はその値へ収束し、placeholder input による persisted metadata の破壊を生じない

### Requirement: 明示 command acceptance と terminal result
システムは各 command に一意な identity を付け、受理済み command を Controller が所有し、成功、非失敗 no-op、失敗の terminal result を一度だけ返すことを SHALL 要求する。presentation の観測から command success を推論してはならない。

#### Scenario: command 成功
- **WHEN** targeted repository command が成功し matching canonical snapshot が確認される
- **THEN** Controller は `Success` を一度だけ返し、対応 pending/result entry を安全に解放する

#### Scenario: repository failure
- **WHEN** targeted repository command が例外または明示 failure を返す
- **THEN** Controller は未commit pending を除去して残りの effective state を再計算し、`Failure` を一度返し、後続 command 処理を継続する

#### Scenario: 明示 no-op
- **WHEN** repository が既に同値または対象なしを返す
- **THEN** Controller は command 種別に対応する明示 `NoOp` result を返し、全件保存や暗黙成功を行わない

### Requirement: accepted command の cancellation ownership
システムは command 受理後の mutation execution と caller の待機 Job を分離することを SHALL 要求する。caller cancellation は待機と caller 固有 navigation を停止するが accepted mutation を取消してはならず、Controller teardown だけが未完 execution の cancellation 境界でなければならない。

#### Scenario: 受理前 cancellation
- **WHEN** caller が command acceptance より前に cancel される
- **THEN** Controller は command を pending state または repository runner へ登録しない

#### Scenario: 受理後 caller cancellation
- **WHEN** command 受理後かつ repository completion 前に caller が cancel される
- **THEN** caller は result/navigation を待たず終了するが Controller は mutation、pending projection、canonical reconciliation を継続する

#### Scenario: Controller teardown
- **WHEN** `TabSessionStore.close()` が retained Controller scope を終了する
- **THEN** Controller は新規 write を開始せず、未完 command waiter を cancellation failure で終端し、collector と runner を停止する

### Requirement: confirmation による後続 DB command の非blocking 処理
システムは committed command の matching Flow confirmation を後続 DB command 開始の barrier にしてはならず、後続 command を canonical + pending の effective state から導出することを SHALL 要求する。各 pending は自身の最小 confirmation 条件だけで独立に終端でき、同一 key predecessor の confirmation dependency または pending-state revision waiter を持つことを要求しない。

#### Scenario: 最初の confirmation が停止する
- **WHEN** command A の repository write は成功したが matching Room snapshot が停止し、その後 command B が受理される
- **THEN** command B の payload は A を含む effective state から導出され、B の targeted DB write は A の confirmation 前に処理可能である

#### Scenario: rapid pin toggle
- **WHEN** 同一 tab に複数の pin toggle が canonical confirmation 前に受理される
- **THEN** 各 write は targeted persistence を使用して後続 confirmation を blocking せず、Controller は exact same-key confirmation order または toggle 回数どおりの中間 projection を保証しない

#### Scenario: 複数 operation の独立確認
- **WHEN** baseline より新しい Room snapshot が pending command 群の一部の最小条件を満たす
- **THEN** Controller は同一 key predecessor の状態にかかわらず条件を満たす pending を完了でき、条件を満たさない add/delete/single-pin pending は維持する

#### Scenario: canonical convergence
- **WHEN** rapid same-tab operation の DB write 群が完了し、Room が最終 persisted snapshot を emit する
- **THEN** presentation は DB canonical 値へ収束し、Controller は補償 write または full-list replacement を発行しない

### Requirement: targeted persistence と explicit repository result
システムは Board／Thread の通常 add/ensure、delete、pin、metadata、scroll operation を対象行単位の suspend repository/DAO command で実行し、成功、no-op、失敗を Controller が識別できる結果を返すことを SHALL 要求する。通常操作は full-list upsert/delete replacement を呼んではならない。

#### Scenario: Board single-row mutation
- **WHEN** 1,252 件の Board tab が保存済みの状態で一件を ensure、pin、更新、delete する
- **THEN** 対象行と必要な関連 state だけが変更され、他の行、順序、pin、scroll、metadata は不変である

#### Scenario: Thread single-row mutation
- **WHEN** 1,252 件の Thread tab が保存済みの状態で一件を ensure、pin、metadata 更新、delete する
- **THEN** 対象行と必要な ThreadState だけが変更され、他の行と tab 固有値は不変である

#### Scenario: bulk operation の隔離
- **WHEN** 通常 UI command が処理される
- **THEN** `upsertAll` と `deleteNotIn` を組み合わせる full replacement API は呼ばれず、明示 bulk/restore 経路だけから利用可能である

### Requirement: metadata merge invariant
システムは repository write と pending projection に同じ pure metadata merge 規則を使用し、placeholder input が解決済み canonical metadata または tab 固有の sort、pin、scroll を永続的に破壊しないことを SHALL 要求する。canonical confirmation は全 metadata field の exact match を使用する必要はなく、projection 除去後から次の Room emission まで古い metadata が一時表示されることを許容する。

#### Scenario: placeholder re-ensure
- **WHEN** 解決済み metadata を持つ既存 tab を placeholder title、board name、board id または URL で再 ensure する
- **THEN** Repository は解決済み canonical field、identity、sort、pin、scroll を保持し、単調増加 field を減少させない

#### Scenario: 有効 metadata 更新
- **WHEN** 対象 identity と一致する非 placeholder metadata を明示更新する
- **THEN** Repository と pending projection は同じ merge 規則で更新対象 field だけを採用し、tab 固有 field を保持する

#### Scenario: 一時的な metadata rollback
- **WHEN** identity-based confirmation が pending projection を除去した後、merge 済み値を含む canonical snapshot がまだ到着していない
- **THEN** presentation は古い canonical metadata を一時表示してもよいが、新しい writer、retry、compensation を開始してはならない

#### Scenario: persisted metadata safety
- **WHEN** placeholder ensure と unrelated Flow emission が競合する
- **THEN** persisted resolved metadata は Repository merge により保持され、後続 canonical snapshot で presentation に再反映される

### Requirement: atomic presentation と deterministic selection repair
システムは effective tabs と selection resolution を一つの `TabPresentationState` として公開し、有効選択、既知の一時不在、確定無効、空、初期／restore を既存 UI 挙動どおり決定論的に処理することを SHALL 要求する。

#### Scenario: 有効選択
- **WHEN** selected key が effective tabs に存在する
- **THEN** presentation は同じ key の `Selected` を公開し target page を表示する

#### Scenario: 既知の一時不在
- **WHEN** selected key が tabs にないが対応 pending cause が生存する
- **THEN** presentation は `PendingMissing` を公開し、現在 page/content を保持して page 0 へ移動しない

#### Scenario: selected tab close
- **WHEN** 選択中 tab の close が effective state に適用され残り tab が存在する
- **THEN** 削除前 index の同位置、範囲外なら末尾を一度だけ選択する

#### Scenario: その他の確定無効と restore
- **WHEN** loaded non-empty state の selected key が null または pending cause なしで不在である
- **THEN** Controller は先頭 key へ repair し、同じ emission で `Selected` を公開する

#### Scenario: zero tabs
- **WHEN** loaded effective tabs が 0 件になる
- **THEN** selected key を null にし `Empty` を公開して tab content を表示しない

### Requirement: retained close ownership
システムは画面または Composition から確認済み close を `TabSessionStore` の retained scope 経由で Controller command として受理し、caller の破棄後も canonical reconciliation まで継続することを SHALL 要求する。

#### Scenario: close 後の Composition 破棄
- **WHEN** tab-list または tab-screen が close を委譲した直後に Composition が破棄される
- **THEN** accepted close は Controller ownership で継続し、対象行だけを削除して selection と presentation を修復する

#### Scenario: retained lifetime 終了
- **WHEN** close が未完の間に `TabSessionStore.close()` が呼ばれる
- **THEN** teardown cancellation boundary に従い未開始処理を止め、既commit DB state は次回 load で canonical として復元する

### Requirement: thin TabSessionStore facade
`TabSessionStore` は Activity-retained facade/lifetime owner として Controller state Flow の公開、command 委譲、retained close 起動、session holder disposal だけを行うことを SHALL 要求する。list mutation、selection repair、repository persistence、presentation 観測による command success inference を行ってはならない。

#### Scenario: store command delegation
- **WHEN** caller が Board または Thread command を Store へ要求する
- **THEN** Store は対応 Controller command/result を透過的に委譲し、独自 state mutation または confirmation Flow wait を追加しない

#### Scenario: store state exposure
- **WHEN** UI が tab state を collect する
- **THEN** Store は Controller の atomic presentation と派生互換 Flow を公開し、別の mutable list/key source を所有しない

### Requirement: large-tab performance constraint
システムは通常 repository command を tab count に依存しない対象行 DB mutation とし、reducer の canonical snapshot 処理を O(n + p)、command acceptance 処理を O(n) 以下に保ち、pending ごとの nested full-list replay を行わないことを SHALL 要求する。

#### Scenario: 1,252 tabs と rapid commands
- **WHEN** 1,252 件の canonical tabs に少なくとも 100 件の連続 command を controlled dispatcher で適用する
- **THEN** key uniqueness と安定順序を維持して決定的に完了し、通常 DB call は対象 command 数に比例し、全件 replacement call は 0 回である

#### Scenario: canonical snapshot reconciliation
- **WHEN** 大量 tabs と複数 pending がある状態で Room snapshot を受信する
- **THEN** 一回の indexed/fold reconciliation で effective state を構築し、pending 数ごとに canonical 全件を再copyする実装を必要としない

### Requirement: 専用bulk delete commandの状態遷移
BoardおよびThreadのタブControllerは、複数の対象keyを1つのpending commandとして登録し、全対象不在のcanonical snapshotを確認してからcommandを完了しなければならない（SHALL）。

#### Scenario: bulk commandを登録する
- **WHEN** Controllerが順序付きの複数対象keyを受理する
- **THEN** Controllerは対象を1つのpending entryへ登録し、集合除去を1回projectionへ適用する

#### Scenario: canonical確認を待つ
- **WHEN** Repository書き込みは完了したがcanonical snapshotに対象keyが残っている
- **THEN** Controllerはbulk pendingを維持し、対象をprojectionへ再表示しない

#### Scenario: 全対象不在を確認する
- **WHEN** baselineより新しいcanonical snapshotで全対象keyが不在になる
- **THEN** Controllerはbulk pendingを完了し、計算済みの最終選択を公開する

#### Scenario: bulk書き込みが失敗する
- **WHEN** bulk Repository操作が失敗またはcancelされる
- **THEN** Controllerはbulk pending全体を除去し、部分成功を公開せずcanonical stateへ収束する

### Requirement: bulk deleteと同一key操作の順序を維持する
Controllerはbulk対象集合とEnsure、Pin、Info、単体Deleteの競合を受理順で解決し、単一keyの競合だけでbulk command全体をsupersedeしてはならない（MUST NOT）。

#### Scenario: 先行pinを対象判定へ反映する
- **WHEN** pin操作がbulk受理前にprojectionへ反映される
- **THEN** Storeは固定済みkeyをbulk対象へ含めない

#### Scenario: bulk後に同じkeyをEnsureする
- **WHEN** bulk受理後に対象keyへのEnsureを受理する
- **THEN** Controllerはbulkを完了した後にEnsureを処理し、再作成されたタブへ収束する

#### Scenario: Thread bulkをbarrierとして扱う
- **WHEN** Thread bulk intentの処理中に後続mutation intentがqueueへ入る
- **THEN** Thread Controllerはbulkのcanonical確認完了後に後続intentを処理する

#### Scenario: teardownでwaiterを解放する
- **WHEN** Controller lifetimeがbulk pending中に終了する
- **THEN** Controllerはbulk completionをFailureで完了し、waiterをハングさせない
