## MODIFIED Requirements

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
