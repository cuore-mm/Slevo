## ADDED Requirements

### Requirement: Domain Controller と単一論理 state
システムは Board と Thread に別々の domain Controller を持ち、各 Controller が load phase、canonical tabs、pending commands、selected key、atomic presentation、command results を一つの immutable logical state と reducer で管理することを SHALL 要求する。共有実装は pure reducer primitive と command lifecycle/result contract に限定し、両 domain を一つの generic Controller に統合してはならない。

#### Scenario: 初回 canonical 未受信
- **WHEN** Controller が起動し Room の初回 snapshot をまだ受信していない
- **THEN** state は `Loading` であり loaded-empty と区別され、DB mutation を開始しない

#### Scenario: 読込済み空一覧
- **WHEN** Room が初回に空の canonical snapshot を emit する
- **THEN** state は `Loaded` かつ presentation は `Empty` となり、後続 command を受理できる

#### Scenario: 派生 Flow の原子性
- **WHEN** canonical、pending、selection のいずれかを更新する event が処理される
- **THEN** tabs と selection resolution は同じ state transition から公開され、別々の mutable source の中間状態を公開しない

### Requirement: DB canonical state と pending projection
システムは Room snapshot だけを canonical tabs とし、accepted または committed command を acceptance order で canonical tabs へ投影した effective state を表示と後続 command 導出に使用することを SHALL 要求する。

#### Scenario: stale snapshot 中の committed ensure
- **WHEN** ensure write が成功した後に対象を含まない stale canonical snapshot を受信する
- **THEN** ensure pending を残して対象を effective tabs に投影し、matching snapshot まで表示を巻き戻さない

#### Scenario: matching snapshot の reconciliation
- **WHEN** Room snapshot が operation 固有の identity、値、metadata 条件に一致する
- **THEN** Controller は該当 pending だけを confirmed にし、projection を除去しても同じ effective result を維持する

#### Scenario: unrelated snapshot
- **WHEN** baseline より新しい Room snapshot が届くが対象 operation の条件に一致しない
- **THEN** Controller は該当 pending と command result 待機を維持する

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
システムは committed command の matching Flow confirmation を後続 DB command 開始の barrier にしてはならず、後続 command を canonical + pending の effective state から acceptance order で導出することを SHALL 要求する。

#### Scenario: 最初の confirmation が停止する
- **WHEN** command A の repository write は成功したが matching Room snapshot が停止し、その後 command B が受理される
- **THEN** command B の payload は A を含む effective state から導出され、B の targeted DB write は A の confirmation 前に処理可能である

#### Scenario: rapid pin toggle
- **WHEN** 同一 tab に複数の pin toggle が canonical confirmation 前に受理される
- **THEN** 各 toggle は直前 pending を含む effective pin 値を反転し、偶数回は元の値、奇数回は反転値へ収束する

#### Scenario: 複数 operation の部分確認
- **WHEN** Room snapshot が pending command 群の一部だけに一致する
- **THEN** 一致する command のうち同一 key の未確認 predecessor を持たないものだけを confirmed にし、未一致 command とその同一 key successor は順序と projection を維持する

#### Scenario: rapid pin の古い値への回帰 snapshot
- **WHEN** 同一 tab の `pin=true`、`pin=false` が順に commit 済みで、先行 `pin=true` の確認前に古い canonical `false` snapshot が到着する
- **THEN** Controller は後続 `pin=false` を先行 command より先に confirmed にせず、両 pending の acceptance order と最終 `false` projection を維持する

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
システムは repository write、pending projection、canonical confirmation に同じ pure metadata merge 規則を使用し、placeholder input が解決済み canonical metadata または tab 固有の sort、pin、scroll を破壊しないことを SHALL 要求する。

#### Scenario: placeholder re-ensure
- **WHEN** 解決済み metadata を持つ既存 tab を placeholder title、board name、board id または URL で再 ensure する
- **THEN** 解決済み canonical field、identity、sort、pin、scroll を保持し、単調増加 field を減少させない

#### Scenario: 有効 metadata 更新
- **WHEN** 対象 identity と一致する非 placeholder metadata を明示更新する
- **THEN** 更新対象 field だけを採用し、同じ merge 結果が projection と canonical matcher に使用される

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
