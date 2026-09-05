## MODIFIED Requirements

### Requirement: 一括クローズを単一bulk mutationとして投影する
システムは、表示中ページの未固定タブ全件または選択モードで受理した固定／未固定タブを、1つのbulk commandおよび1つのpending projection operationとして受理しなければならない（SHALL）。対象件数分の単体close commandを登録してはならない（MUST NOT）。

#### Scenario: 大量の板タブを一度に投影から除外する
- **WHEN** 板ページの複数タブに対して一括クローズを受理する
- **THEN** システムは固定状態にかかわらず指定された対象を1回のprojection更新で一覧から除外し、対象外の板タブとすべてのスレッドタブを維持する

#### Scenario: 大量のスレッドタブを一度に投影から除外する
- **WHEN** スレッドページの複数タブに対して一括クローズを受理する
- **THEN** システムは固定状態にかかわらず指定された対象を1回のprojection更新で一覧から除外し、対象外のスレッドタブとすべての板タブを維持する

#### Scenario: 対象が存在しない
- **WHEN** 一括クローズとして受理できる対象が存在しない
- **THEN** システムはbulk command、永続化、GC、holder破棄を実行しない

### Requirement: 対象holderをbulk段階で破棄する
システムは、bulk対象の既存session holderを1つのbulk破棄段階でmapから除去し、各holderを正確に1回disposeしなければならない（SHALL）。固定状態にかかわらず対象として指定されたholderを破棄し、対象外holderを破棄したり未生成holderを生成したりしてはならない（MUST NOT）。

#### Scenario: 固定holderを残す
- **WHEN** 通常時の未固定タブ一括クローズ対象と同じページに固定タブのholderが存在する
- **THEN** システムは対象holderだけをdisposeし、対象外である固定holderをmapに維持する

#### Scenario: 対象外holderを残す
- **WHEN** 一括対象と同じページに対象外タブのholderが存在する
- **THEN** システムは固定状態にかかわらず対象holderだけをdisposeし、対象外holderをmapに維持する

#### Scenario: 同じ対象が重複する
- **WHEN** 同じkeyがbulk入力へ重複して含まれる
- **THEN** システムはkeyを一度だけ削除し、holderを一度だけdisposeする

#### Scenario: Store lifetimeが終了する
- **WHEN** bulk処理中にActivity-retained Storeのlifetimeが終了する
- **THEN** システムはin-flight処理をcancelしてtransactionをrollbackし、残存holderを既存close処理で破棄する
